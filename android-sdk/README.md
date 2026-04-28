# HTTPDNS Android SDK (Kotlin)

This is a Kotlin-first Android SDK scaffold aligned with current HTTPDNS server APIs:

- Dispatch endpoint: `/dnps-apis/v1/httpdns/endpoints`
- Resolve endpoint: `/v1/d`
- Encrypted payload via `enc` (AES-GCM)

## Public API

- `HttpDns.init(accountId, config)`
- `HttpDns.getService(accountId)`
- `HttpDnsService.getHttpDnsResultForHostSync(...)`
- `HttpDnsService.getHttpDnsResultForHostAsync(...)`
- `HttpDnsService.getHttpDnsResultForHostSyncNonBlocking(...)`
- `HttpDnsService.setPreResolveHosts(...)`
- `HttpDnsService.cleanHostCache(...)`

Java/Kotlin convenience overloads are also provided:

- `setPreResolveHosts(hostList)` (default `RequestIpType.v4`)
- `getHttpDnsResultForHostSync(host)` (default `RequestIpType.v4`)
- `getHttpDnsResultForHostAsync(host, callback)` (default `RequestIpType.v4`)
- `getHttpDnsResultForHostSyncNonBlocking(host)` (default `RequestIpType.v4`)

## Bootstrap endpoint strategy

The SDK ships with internal bootstrap endpoints and does not expose them to app developers.

Fallback order when refreshing dispatch target:

1. bootstrap domain (`resolve.pp.fgnlo.com`)
2. region-preferred bootstrap IPs
3. all bootstrap IPs

## HTTPS host and certificate behavior

When HTTPS requests fall back to bootstrap IPs, SDK keeps host consistency by:

- setting `Host` header to original host
- setting TLS SNI to original host
- verifying certificate against original host

## Notes

- `setAesSecretKey(...)` is required (supports 16-byte plain text or 32-char hex key).
- HTTPS transport defaults to Cronet (`setUseCronet(true)` by default).
- `Context` should be `applicationContext`.
- This directory is a standalone SDK module scaffold under current repo for implementation and review.
