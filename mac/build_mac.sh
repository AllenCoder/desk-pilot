#!/bin/bash
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"

UNIVERSAL="${UNIVERSAL:-false}"

build_binary() {
    local src="$1"
    local out="$2"
    if [ "$UNIVERSAL" = "true" ]; then
        echo "==> Building Universal Binary for $(basename "$src")..."
        swiftc -target arm64-apple-macos12.0 -O "$src" -o "${out}_arm64"
        swiftc -target x86_64-apple-macos12.0 -O "$src" -o "${out}_x86_64"
        lipo -create "${out}_arm64" "${out}_x86_64" -output "$out"
        rm -f "${out}_arm64" "${out}_x86_64"
    else
        echo "==> Building native binary for $(basename "$src")..."
        swiftc -O "$src" -o "$out"
    fi
    file "$out"
}

build_binary "$DIR/stats_server.swift" "$DIR/stats_server"
build_binary "$DIR/audio_bridge.swift" "$DIR/audio_bridge"

echo "==> Done! Binaries built in $DIR"
