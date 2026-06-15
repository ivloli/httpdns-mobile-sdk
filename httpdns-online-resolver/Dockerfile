FROM golang:1.25-bookworm AS builder

WORKDIR /src

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl jq tar gzip \
    && rm -rf /var/lib/apt/lists/*

ARG IP2REGION_RELEASES_URL="https://api.github.com/repos/lionsoul2014/ip2region/releases/latest"
ARG GITHUB_TOKEN=""

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /out/httpdns-online-resolver-go .
RUN mkdir -p /out/ip2region \
    && release_url="$(curl -fsSL -H 'Accept: application/vnd.github+json' ${GITHUB_TOKEN:+-H "Authorization: Bearer ${GITHUB_TOKEN}"} "$IP2REGION_RELEASES_URL" | jq -r '.tarball_url')" \
    && curl -fsSL ${GITHUB_TOKEN:+-H "Authorization: Bearer ${GITHUB_TOKEN}"} "$release_url" -o /tmp/ip2region.tar.gz \
    && tar -xzf /tmp/ip2region.tar.gz --wildcards --no-anchored 'ip2region_v4.xdb' --no-same-owner -O > /out/ip2region/ip2region_v4.xdb \
    && tar -xzf /tmp/ip2region.tar.gz --wildcards --no-anchored 'ip2region_v6.xdb' --no-same-owner -O > /out/ip2region/ip2region_v6.xdb \
    && rm -f /tmp/ip2region.tar.gz

FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates tzdata \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 65532 app \
    && useradd --uid 65532 --gid 65532 --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin app

WORKDIR /app

COPY --from=builder /out/httpdns-online-resolver-go /app/httpdns-online-resolver-go
COPY --from=builder /out/ip2region/ip2region_v4.xdb /opt/ip2region/data/ip2region_v4.xdb
COPY --from=builder /out/ip2region/ip2region_v6.xdb /opt/ip2region/data/ip2region_v6.xdb

RUN mkdir -p /etc/httpdns-online-resolver-go /opt/ip2region/data \
    && chown -R 65532:65532 /app /etc/httpdns-online-resolver-go /opt/ip2region

EXPOSE 8088 38088

USER 65532:65532

ENTRYPOINT ["/app/httpdns-online-resolver-go"]
