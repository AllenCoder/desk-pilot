#!/bin/bash
exec "$(dirname "$0")/android/build_apk.sh" "$@"
