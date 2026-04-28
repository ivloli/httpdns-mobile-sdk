#!/usr/bin/env bash
set -euo pipefail

# =====================
# Config (override by env)
# =====================
MODE="${MODE:-dispatch}"                 # dispatch | resolve
SCHEME="${SCHEME:-https}"               # https | http
ACCOUNT_ID="${ACCOUNT_ID:-}"
AES_KEY="${AES_KEY:-}"

# Bootstrap endpoint (for dispatch)
BOOTSTRAP_HOST="${BOOTSTRAP_HOST:-resolve.pp.fgnlo.com}"
BOOTSTRAP_IP="${BOOTSTRAP_IP:-}"        # optional

# Resolve endpoint (for resolve)
RESOLVE_HOST="${RESOLVE_HOST:-}"         # required when MODE=resolve
RESOLVE_IP="${RESOLVE_IP:-}"             # optional

# Payload fields
REGION="${REGION:-global}"               # cn | os | global
EXP_SECONDS="${EXP_SECONDS:-600}"        # exp = now + EXP_SECONDS
DN="${DN:-www.example.com}"              # resolve target host(s)
Q="${Q:-4,6}"                            # 4 | 6 | 4,6
CIP="${CIP:-}"
SDNS_OS="${SDNS_OS:-}"

TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-10}"
SHOW_CURL_ONLY="${SHOW_CURL_ONLY:-false}"

if [[ -z "$ACCOUNT_ID" || -z "$AES_KEY" ]]; then
  echo "ERROR: ACCOUNT_ID and AES_KEY are required."
  echo "Usage example:"
  echo "  ACCOUNT_ID=123 AES_KEY=1234567890abcdef MODE=dispatch $0"
  exit 1
fi

if ! python3 - <<'PY' >/dev/null 2>&1; then
import importlib
importlib.import_module('cryptography')
PY
  echo "ERROR: python package 'cryptography' is required."
  echo "Install with: python3 -m pip install cryptography"
  exit 1
fi

encrypt_hex() {
  local key="$1"
  local plaintext="$2"
  python3 - "$key" "$plaintext" <<'PY'
import os
import sys
import re
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

raw_key = sys.argv[1]
pt = sys.argv[2].encode('utf-8')

# Support two key formats:
# 1) 16-byte UTF-8 plain key, e.g. 1234567890abcdef
# 2) 32-hex chars key representing 16 bytes, e.g. 001122...aabbccdd
if len(raw_key.encode('utf-8')) == 16:
    key = raw_key.encode('utf-8')
elif re.fullmatch(r'[0-9a-fA-F]{32}', raw_key):
    key = bytes.fromhex(raw_key)
else:
    raise SystemExit(
        "AES_KEY invalid: expect 16-byte plain text or 32-hex string (representing 16 bytes)"
    )

iv = os.urandom(12)
ct = AESGCM(key).encrypt(iv, pt, None)
print((iv + ct).hex())
PY
}

decrypt_hex() {
  local key="$1"
  local hex_data="$2"
  python3 - "$key" "$hex_data" <<'PY'
import sys
import re
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

raw_key = sys.argv[1]
hex_data = sys.argv[2]

if len(raw_key.encode('utf-8')) == 16:
    key = raw_key.encode('utf-8')
elif re.fullmatch(r'[0-9a-fA-F]{32}', raw_key):
    key = bytes.fromhex(raw_key)
else:
    raise SystemExit(
        "AES_KEY invalid: expect 16-byte plain text or 32-hex string (representing 16 bytes)"
    )

raw = bytes.fromhex(hex_data)
iv, ct = raw[:12], raw[12:]
pt = AESGCM(key).decrypt(iv, ct, None)
print(pt.decode('utf-8'))
PY
}

urlencode() {
  python3 - "$1" <<'PY'
import sys, urllib.parse
print(urllib.parse.quote(sys.argv[1], safe=''))
PY
}

now_ts="$(date +%s)"
exp_ts="$((now_ts + EXP_SECONDS))"

payload=""
host=""
connect_ip=""
path=""

if [[ "$MODE" == "dispatch" ]]; then
  payload="$(python3 - "$REGION" "$exp_ts" <<'PY'
import json, sys
print(json.dumps({"region": sys.argv[1], "exp": int(sys.argv[2])}, separators=(",", ":")))
PY
)"
  host="$BOOTSTRAP_HOST"
  connect_ip="$BOOTSTRAP_IP"
elif [[ "$MODE" == "resolve" ]]; then
  if [[ -z "$RESOLVE_HOST" ]]; then
    echo "ERROR: RESOLVE_HOST is required when MODE=resolve"
    exit 1
  fi
  payload="$(python3 - "$exp_ts" "$DN" "$Q" "$CIP" "$SDNS_OS" <<'PY'
import json, sys
obj = {
  "exp": int(sys.argv[1]),
  "dn": sys.argv[2],
  "q": sys.argv[3],
}
if sys.argv[4]:
  obj["cip"] = sys.argv[4]
if sys.argv[5]:
  obj["sdns-os"] = sys.argv[5]
print(json.dumps(obj, separators=(",", ":")))
PY
)"
  host="$RESOLVE_HOST"
  connect_ip="$RESOLVE_IP"
else
  echo "ERROR: MODE must be dispatch or resolve"
  exit 1
fi

enc="$(encrypt_hex "$AES_KEY" "$payload")"
enc_url="$(urlencode "$enc")"

if [[ "$MODE" == "dispatch" ]]; then
  path="/dnps-apis/v1/httpdns/endpoints?account_id=$(urlencode "$ACCOUNT_ID")&enc=$enc_url"
else
  path="/v1/d?id=$(urlencode "$ACCOUNT_ID")&enc=$enc_url"
fi

url="${SCHEME}://${host}${path}"
curl_cmd=(curl -sS --max-time "$TIMEOUT_SECONDS")

if [[ -n "$connect_ip" ]]; then
  if [[ "$SCHEME" == "https" ]]; then
    curl_cmd+=(--resolve "${host}:443:${connect_ip}")
  else
    curl_cmd+=(--connect-to "${host}:80:${connect_ip}:80")
  fi
fi

curl_cmd+=("$url")

echo "=== Request Context ==="
echo "mode       : $MODE"
echo "scheme     : $SCHEME"
echo "host       : $host"
echo "connect_ip : ${connect_ip:-<none>}"
echo "payload    : $payload"
echo ""
echo "=== Curl Command ==="
printf '%q ' "${curl_cmd[@]}"
echo

if [[ "$SHOW_CURL_ONLY" == "true" ]]; then
  exit 0
fi

echo ""
echo "=== Raw Response ==="
raw_resp="$(${curl_cmd[@]})"
echo "$raw_resp"

echo ""
echo "=== Parsed Response ==="
data_hex="$(python3 - "$raw_resp" <<'PY'
import json, sys
try:
    obj = json.loads(sys.argv[1])
    print(obj.get('data', ''))
except Exception:
    print('')
PY
)"

if [[ -z "$data_hex" ]]; then
  echo "No data field found or response is not JSON."
  exit 0
fi

echo "data(hex): $data_hex"
echo ""
echo "=== Decrypted data ==="
decrypt_hex "$AES_KEY" "$data_hex"
