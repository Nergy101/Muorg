# ── Build stage ───────────────────────────────────────────────────────────────
FROM rust:slim-bookworm AS builder

RUN apt-get update && apt-get install -y --no-install-recommends \
    pkg-config \
    libssl-dev \
    libmp3lame-dev \
    build-essential \
    protobuf-compiler \
    file \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . .

RUN cargo build --release --bin muorg-server

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    libssl3 \
    libmp3lame0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/target/release/muorg-server /usr/local/bin/muorg-server

# /data holds the SQLite database and backups.
# Mount your music library at /music (or any path matching content_paths in the config).
VOLUME ["/data"]

EXPOSE 7700

ENTRYPOINT ["muorg-server"]
