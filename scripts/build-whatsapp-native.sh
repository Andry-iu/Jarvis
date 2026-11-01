#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../whatsapp-native"
source /workspace/.jarvis-cloud/activate.sh
export PATH=/workspace/.jarvis-cloud/go-1.27.3/bin:/workspace/.jarvis-cloud/go-bin:$PATH
export GOPATH=/workspace/.jarvis-cloud/go-work
export GOCACHE=/workspace/.jarvis-cloud/go-cache
export GOPROXY=https://proxy.golang.org
export GOTOOLCHAIN=local
export GOFLAGS='-buildvcs=false -mod=readonly'
export ANDROID_NDK_HOME=/workspace/.jarvis-cloud/android-sdk/ndk/28.2.13676358
python3 ../scripts/whatsapp-native-notices.py
mkdir -p ../app/build/whatsapp-native
gomobile bind -target=android/arm64 -androidapi=31 -o ../app/build/whatsapp-native/wabridge.aar .
