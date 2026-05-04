# ── Build stage ───────────────────────────────────────────────────────────────
FROM rust:slim-bookworm AS builder

RUN apt-get update && apt-get install -y --no-install-recommends \
    pkg-config \
    libmp3lame-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Cache dependencies separately from source
COPY Cargo.toml Cargo.lock ./
COPY crates/muorg-core/Cargo.toml crates/muorg-core/
COPY crates/muorg-server/Cargo.toml crates/muorg-server/
COPY vendor/ vendor/

# Dummy source so cargo can resolve and compile deps
RUN mkdir -p crates/muorg-core/src crates/muorg-server/src \
    && echo 'pub fn placeholder() {}' > crates/muorg-core/src/lib.rs \
    && echo 'fn main() {}' > crates/muorg-server/src/main.rs \
    && cargo build --release --bin muorg-server \
    && rm -rf crates/muorg-core/src crates/muorg-server/src

# Real source
COPY crates/ crates/
# Touch to force recompile
RUN touch crates/muorg-core/src/lib.rs crates/muorg-server/src/main.rs
RUN cargo build --release --bin muorg-server

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    libmp3lame0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/target/release/muorg-server /usr/local/bin/muorg-server

# /data holds the SQLite database and backups.
# Mount your music library at /music (or any path matching content_paths in the config).
VOLUME ["/data"]

EXPOSE 7700

ENTRYPOINT ["muorg-server"]
