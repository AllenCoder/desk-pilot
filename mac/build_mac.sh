#!/bin/bash
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
echo "==> Compiling stats_server..."
swiftc -O "$DIR/stats_server.swift" -o "$DIR/stats_server"
echo "==> Compiling audio_bridge..."
swiftc -O "$DIR/audio_bridge.swift" -o "$DIR/audio_bridge"
echo "==> Done! Binaries built in $DIR"
