#!/usr/bin/env bash
# Extend the already configured cloud Jarvis toolchain; never create another environment.
set -euo pipefail
source /workspace/.jarvis-cloud/activate.sh
jarvis_go=/workspace/.jarvis-cloud/go-1.27.3
if [[ ! -x "$jarvis_go/bin/go" ]]; then
    jarvis_archive=/workspace/.jarvis-cloud/downloads/go1.27.3.linux-amd64.tar.gz
    jarvis_digest=65efbc80fdc1fa2cd2631cad5b481777a68e8725f34e89f50fc6ac714e379b40
    if ! printf '%s  %s\n' "$jarvis_digest" "$jarvis_archive" | sha256sum -c - >/dev/null 2>&1; then
        curl --fail --location --silent --show-error --retry 3 --connect-timeout 20 --max-time 600 \
            https://go.dev/dl/go1.27.3.linux-amd64.tar.gz --output "$jarvis_archive.partial"
        printf '%s  %s\n' "$jarvis_digest" "$jarvis_archive.partial" | sha256sum -c -
        mv "$jarvis_archive.partial" "$jarvis_archive"
    fi
    jarvis_staging=$(mktemp -d /workspace/.jarvis-cloud/go-install.XXXXXX)
    trap 'rm -rf "$jarvis_staging"' EXIT
    tar -xzf "$jarvis_archive" --strip-components=1 -C "$jarvis_staging"
    # Do not expose a half-extracted Go installation after an interrupted setup.
    if [[ -e "$jarvis_go" ]]; then
        echo 'Incomplete Go directory exists; inspect it before replacement.' >&2
        exit 1
    fi
    mv "$jarvis_staging" "$jarvis_go"
    trap - EXIT
fi
export PATH=$jarvis_go/bin:/workspace/.jarvis-cloud/go-bin:$PATH
export GOPATH=/workspace/.jarvis-cloud/go-work
export GOCACHE=/workspace/.jarvis-cloud/go-cache
export GOBIN=/workspace/.jarvis-cloud/go-bin
export GOPROXY=https://proxy.golang.org
export GOTOOLCHAIN=local
if [[ ! -f "$ANDROID_HOME/ndk/28.2.13676358/source.properties" ||
      ! -x "$ANDROID_HOME/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]]; then
    "$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" 'ndk;28.2.13676358'
fi
jarvis_mobile_version=v0.0.0-20261011034528-197770444867
jarvis_mobile_changed=false
for jarvis_binary in gomobile gobind; do
    if ! go version -m "$GOBIN/$jarvis_binary" 2>/dev/null | grep -Fq "$jarvis_mobile_version"; then
        go install "golang.org/x/mobile/cmd/$jarvis_binary@$jarvis_mobile_version"
        jarvis_mobile_changed=true
    fi
done
if "$jarvis_mobile_changed"; then gomobile init; fi
# Cache the application's pinned modules as setup, without building an APK.
jarvis_repo=$(cd "$(dirname "$0")/.." && pwd)
(cd "$jarvis_repo/whatsapp-native"; go mod download)
