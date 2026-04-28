#!/usr/bin/env python3
import argparse
import json
import os
import socket
import ssl
import sys
import time
import urllib.parse
from dataclasses import dataclass
from http.client import HTTPConnection, HTTPSConnection, HTTPResponse
from typing import Dict, List, Optional, Tuple

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def env_or_default(name: str, default: str) -> str:
    value = os.getenv(name)
    return value if value is not None else default


def parse_bool(raw: str) -> bool:
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="HTTPDNS dispatch/resolve request tool")
    parser.add_argument("--mode", default=env_or_default("MODE", "dispatch"), choices=["dispatch", "resolve"])
    parser.add_argument("--scheme", default=env_or_default("SCHEME", "https"), choices=["https", "http"])

    parser.add_argument("--account-id", default=os.getenv("ACCOUNT_ID", ""))
    parser.add_argument("--aes-key", default=os.getenv("AES_KEY", ""))
    parser.add_argument(
        "--aes-key-mode",
        default=env_or_default("AES_KEY_MODE", "auto"),
        choices=["auto", "hex", "utf8"],
        help="AES key parse mode: auto|hex|utf8",
    )

    parser.add_argument("--bootstrap-host", default=env_or_default("BOOTSTRAP_HOST", "r.pp.fgnlo.com"))
    parser.add_argument("--bootstrap-ip", default=env_or_default("BOOTSTRAP_IP", ""))

    parser.add_argument("--resolve-host", default=env_or_default("RESOLVE_HOST", ""))
    parser.add_argument("--resolve-ip", default=env_or_default("RESOLVE_IP", ""))

    parser.add_argument("--region", default=env_or_default("REGION", "global"))
    parser.add_argument(
        "--dispatch-payload-format",
        default=env_or_default("DISPATCH_PAYLOAD_FORMAT", "proto"),
        choices=["proto", "json"],
        help="dispatch payload plain format",
    )
    parser.add_argument("--exp-seconds", type=int, default=int(env_or_default("EXP_SECONDS", "600")))
    parser.add_argument("--dn", default=env_or_default("DN", "www.example.com"))
    parser.add_argument("--dn-list", default=env_or_default("DN_LIST", ""), help="comma/space/semicolon separated hosts")
    parser.add_argument(
        "--resolve-batch-field",
        default=env_or_default("RESOLVE_BATCH_FIELD", "none"),
        choices=["none", "dn", "dns"],
        help="when dn-list is set: send one request with dn(csv) or dns(array) payload",
    )
    parser.add_argument("--q", default=env_or_default("Q", "4,6"))
    parser.add_argument("--cip", default=env_or_default("CIP", ""))
    parser.add_argument("--client-ip", default=env_or_default("CLIENT_IP", ""), help="alias of cip")
    parser.add_argument("--sdns-os", default=env_or_default("SDNS_OS", ""))

    parser.add_argument("--timeout-seconds", type=int, default=int(env_or_default("TIMEOUT_SECONDS", "10")))
    parser.add_argument("--disable-proxy", default=env_or_default("DISABLE_PROXY", "true"))
    parser.add_argument("--tls-verify", default=env_or_default("TLS_VERIFY", "true"))
    parser.add_argument("--ip-connect-sni", default=env_or_default("IP_CONNECT_SNI", "true"))
    parser.add_argument("--auto-fallback-http", default=env_or_default("AUTO_FALLBACK_HTTP", "false"))
    parser.add_argument("--show-http-only", default=env_or_default("SHOW_HTTP_ONLY", "false"))

    return parser.parse_args()


def parse_aes_key(raw_key: str) -> bytes:
    return parse_aes_key_with_mode(raw_key, "auto")


def parse_aes_key_with_mode(raw_key: str, mode: str) -> bytes:
    key_utf8 = raw_key.encode("utf-8")
    if mode == "utf8":
        if len(key_utf8) in {16, 24, 32}:
            return key_utf8
        raise ValueError("AES_KEY invalid for utf8 mode: expect utf8 length in {16,24,32}")

    if mode == "hex":
        if len(raw_key) in {32, 48, 64}:
            try:
                return bytes.fromhex(raw_key)
            except ValueError as error:
                raise ValueError("AES_KEY invalid hex") from error
        raise ValueError("AES_KEY invalid for hex mode: expect hex length in {32,48,64}")

    if len(key_utf8) in {16, 24, 32}:
        return key_utf8
    if len(raw_key) in {32, 48, 64}:
        try:
            return bytes.fromhex(raw_key)
        except ValueError:
            pass
    raise ValueError("AES_KEY invalid: expect utf8 length in {16,24,32} or hex length in {32,48,64}")


def encrypt_hex(aes_key: bytes, plaintext: bytes) -> str:
    iv = os.urandom(12)
    cipher_text = AESGCM(aes_key).encrypt(iv, plaintext, None)
    return (iv + cipher_text).hex()


def decrypt_hex(aes_key: bytes, hex_data: str) -> str:
    raw = bytes.fromhex(hex_data)
    if len(raw) <= 12:
        raise ValueError("invalid encrypted payload")
    iv = raw[:12]
    cipher_text = raw[12:]
    plaintext = AESGCM(aes_key).decrypt(iv, cipher_text, None)
    return plaintext.decode("utf-8")


def encode_varint(value: int) -> bytes:
    out = bytearray()
    while True:
        to_write = value & 0x7F
        value >>= 7
        if value:
            out.append(to_write | 0x80)
        else:
            out.append(to_write)
            return bytes(out)


def encode_length_delimited(field_number: int, raw: bytes) -> bytes:
    return bytes([(field_number << 3) | 2]) + encode_varint(len(raw)) + raw


def encode_varint_field(field_number: int, value: int) -> bytes:
    return bytes([(field_number << 3) | 0]) + encode_varint(value)


def build_dispatch_proto_plain(region: str, exp_ts: int) -> bytes:
    region_bytes = region.encode("utf-8")
    return encode_length_delimited(1, region_bytes) + encode_varint_field(3, exp_ts)


class SniHttpsConnection(HTTPSConnection):
    def __init__(self, connect_host: str, server_hostname: str, timeout: int, context: ssl.SSLContext, with_sni: bool):
        super().__init__(host=connect_host, port=443, timeout=timeout, context=context)
        self._server_hostname_override = server_hostname
        self._with_sni = with_sni

    def connect(self) -> None:
        address = (self.host, self.port)
        sock = socket.create_connection(address, self.timeout, self.source_address)
        if self._tunnel_host:
            self.sock = sock
            self._tunnel()
            sock = self.sock
        if self._with_sni:
            self.sock = self._context.wrap_socket(sock, server_hostname=self._server_hostname_override)
        else:
            self.sock = self._context.wrap_socket(sock)


@dataclass
class HttpResult:
    status: int
    reason: str
    headers: Dict[str, str]
    body: str


def make_request(
    scheme: str,
    host: str,
    path_with_query: str,
    connect_ip: str,
    timeout_seconds: int,
    disable_proxy: bool,
    tls_verify: bool,
    ip_connect_sni: bool,
) -> HttpResult:
    if disable_proxy:
        for key in ["http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY", "all_proxy", "ALL_PROXY"]:
            os.environ.pop(key, None)

    headers = {"Host": host} if connect_ip else {}
    target = connect_ip if connect_ip else host

    if scheme == "https":
        context = ssl.create_default_context() if tls_verify else ssl._create_unverified_context()
        context.check_hostname = tls_verify
        context.verify_mode = ssl.CERT_REQUIRED if tls_verify else ssl.CERT_NONE
        if connect_ip:
            conn = SniHttpsConnection(
                connect_host=target,
                server_hostname=host,
                timeout=timeout_seconds,
                context=context,
                with_sni=ip_connect_sni,
            )
        else:
            conn = HTTPSConnection(host=target, port=443, timeout=timeout_seconds, context=context)
    else:
        conn = HTTPConnection(host=target, port=80, timeout=timeout_seconds)

    try:
        conn.request("GET", path_with_query, headers=headers)
        response: HTTPResponse = conn.getresponse()
        raw = response.read()
        body = raw.decode("utf-8", errors="replace")
        return HttpResult(
            status=response.status,
            reason=response.reason,
            headers={k: v for k, v in response.getheaders()},
            body=body,
        )
    finally:
        conn.close()


def build_payload(args: argparse.Namespace, exp_ts: int) -> Tuple[bytes, str, str, str]:
    if args.mode == "dispatch":
        if args.dispatch_payload_format == "proto":
            payload = build_dispatch_proto_plain(region=args.region, exp_ts=exp_ts)
            payload_debug = f"proto_hex={payload.hex()}"
        else:
            payload_obj = {"region": args.region, "exp": exp_ts}
            payload = json.dumps(payload_obj, separators=(",", ":")).encode("utf-8")
            payload_debug = json.dumps(payload_obj, separators=(",", ":"))
        host = args.bootstrap_host
        connect_ip = args.bootstrap_ip
    else:
        if not args.resolve_host:
            raise ValueError("RESOLVE_HOST is required when MODE=resolve")
        hosts = parse_host_list(args.dn_list)
        if hosts and args.resolve_batch_field in {"dn", "dns"}:
            if args.resolve_batch_field == "dn":
                content = {"exp": exp_ts, "dn": ",".join(hosts), "q": args.q}
            else:
                content = {"exp": exp_ts, "dns": hosts, "q": args.q}
        else:
            target_dn = hosts[0] if hosts else args.dn
            content = {"exp": exp_ts, "dn": target_dn, "q": args.q}
        if args.cip:
            content["cip"] = args.cip
        if args.sdns_os:
            content["sdns-os"] = args.sdns_os
        payload_debug = json.dumps(content, separators=(",", ":"))
        payload = payload_debug.encode("utf-8")
        host = args.resolve_host
        connect_ip = args.resolve_ip
    return payload, host, connect_ip, payload_debug


def build_path(args: argparse.Namespace, account_id: str, enc: str) -> str:
    encoded_account = urllib.parse.quote(account_id, safe="")
    encoded_enc = urllib.parse.quote(enc, safe="")
    if args.mode == "dispatch":
        return f"/dnps-apis/v1/httpdns/endpoints?account_id={encoded_account}&enc={encoded_enc}"
    return f"/v1/d?id={encoded_account}&enc={encoded_enc}"


def print_request_context(args: argparse.Namespace, host: str, connect_ip: str, payload_debug: str, path: str) -> None:
    url = f"{args.scheme}://{host}{path}"
    print("=== Request Context ===")
    print(f"mode       : {args.mode}")
    print(f"scheme     : {args.scheme}")
    print(f"host       : {host}")
    print(f"connect_ip : {connect_ip if connect_ip else '<none>'}")
    print(f"cip        : {args.cip if args.cip else '<none>'}")
    print(f"payload    : {payload_debug}")
    print()
    print("=== HTTP Request ===")
    print(f"GET {path} HTTP/1.1")
    print(f"Host: {host}")
    print(f"URL : {url}")


def print_response(resp: HttpResult) -> None:
    print()
    print("=== Raw Response ===")
    print(f"status: {resp.status} {resp.reason}")
    print("headers:")
    for key, value in resp.headers.items():
        print(f"  {key}: {value}")
    print("body:")
    print(resp.body)


def parse_host_list(raw: str) -> List[str]:
    if not raw:
        return []
    normalized = raw.replace("x", ",").replace("X", ",")
    out: List[str] = []
    for token in normalized.replace(";", ",").replace("\n", ",").replace("\t", ",").replace(" ", ",").split(","):
        value = token.strip()
        if value:
            out.append(value)
    return out


def run_single(
    args: argparse.Namespace,
    aes_key: bytes,
    disable_proxy: bool,
    tls_verify: bool,
    ip_connect_sni: bool,
    auto_fallback_http: bool,
    show_http_only: bool,
) -> int:
    exp_ts = int(time.time()) + args.exp_seconds
    try:
        payload, host, connect_ip, payload_debug = build_payload(args, exp_ts)
    except ValueError as error:
        print(f"ERROR: {error}")
        return 1

    enc = encrypt_hex(aes_key, payload)
    path = build_path(args, args.account_id, enc)
    print_request_context(args, host, connect_ip, payload_debug, path)

    if show_http_only:
        return 0

    try:
        response = make_request(
            scheme=args.scheme,
            host=host,
            path_with_query=path,
            connect_ip=connect_ip,
            timeout_seconds=args.timeout_seconds,
            disable_proxy=disable_proxy,
            tls_verify=tls_verify,
            ip_connect_sni=ip_connect_sni,
        )
        print_response(response)
    except Exception as error:
        if args.scheme == "https" and auto_fallback_http:
            print()
            print(f"HTTPS request failed ({error}), retrying with HTTP...")
            try:
                response = make_request(
                    scheme="http",
                    host=host,
                    path_with_query=path,
                    connect_ip=connect_ip,
                    timeout_seconds=args.timeout_seconds,
                    disable_proxy=disable_proxy,
                    tls_verify=tls_verify,
                    ip_connect_sni=ip_connect_sni,
                )
                print_response(response)
            except Exception as second_error:
                print(f"ERROR: HTTP fallback failed: {second_error}")
                return 1
        else:
            print(f"ERROR: request failed: {error}")
            return 1

    print()
    print("=== Parsed Response ===")
    try:
        root = json.loads(response.body)
    except json.JSONDecodeError:
        print("Response is not JSON.")
        return 0

    data_hex = root.get("data", "")
    if not data_hex:
        print("No data field found in JSON response.")
        return 0

    print(f"data(hex): {data_hex}")
    print()
    print("=== Decrypted Data ===")
    try:
        print(decrypt_hex(aes_key, data_hex))
    except Exception as error:
        print(f"ERROR: decrypt failed: {error}")
        return 1

    return 0


def main() -> int:
    args = parse_args()
    if args.client_ip and not args.cip:
        args.cip = args.client_ip

    disable_proxy = parse_bool(args.disable_proxy)
    tls_verify = parse_bool(args.tls_verify)
    ip_connect_sni = parse_bool(args.ip_connect_sni)
    auto_fallback_http = parse_bool(args.auto_fallback_http)
    show_http_only = parse_bool(args.show_http_only)

    if not args.account_id or not args.aes_key:
        print("ERROR: ACCOUNT_ID and AES_KEY are required.")
        return 1

    try:
        aes_key = parse_aes_key_with_mode(args.aes_key, args.aes_key_mode)
    except ValueError as error:
        print(f"ERROR: {error}")
        return 1

    hosts = parse_host_list(args.dn_list)
    if args.mode == "resolve" and len(hosts) > 1 and args.resolve_batch_field == "none":
        exit_code = 0
        for index, host in enumerate(hosts, start=1):
            child_args = argparse.Namespace(**vars(args))
            child_args.dn = host
            child_args.dn_list = ""
            print()
            print(f"===== Resolve Host {index}/{len(hosts)}: {host} =====")
            code = run_single(
                child_args,
                aes_key,
                disable_proxy,
                tls_verify,
                ip_connect_sni,
                auto_fallback_http,
                show_http_only,
            )
            if code != 0:
                exit_code = code
        return exit_code

    return run_single(
        args,
        aes_key,
        disable_proxy,
        tls_verify,
        ip_connect_sni,
        auto_fallback_http,
        show_http_only,
    )


if __name__ == "__main__":
    sys.exit(main())
