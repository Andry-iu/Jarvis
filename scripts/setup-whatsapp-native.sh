#!/usr/bin/env bash
# Extend the already configured cloud Jarvis toolchain; never create another environment.
set -euo pipefail
source /workspace/.jarvis-cloud/activate.sh
jarvis_go=/workspace/.jarvis-cloud/go-1.27.3
if [[ ! -x "$jarvis_go/bin/go" ]]; then
    curl --fail --location --silent --show-error https://go.dev/dl/go1.27.3.linux-amd64.tar.gz --output /workspace/.jarvis-cloud/downloads/go1.27.3.linux-amd64.tar.gz
    printf '%s  %s\n' '65efbc80fdc1fa2cd2631cad5b481777a68e8725f34e89f50fc6ac714e379b40' '/workspace/.jarvis-cloud/downloads/go1.27.3.linux-amd64.tar.gz' | sha256sum -c -
    mkdir -p "$jarvis_go"
    tar -xzf /workspace/.jarvis-cloud/downloads/go1.27.3.linux-amd64.tar.gz --strip-components=1 -C "$jarvis_go"
fi
export PATH=$jarvis_go/bin:/workspace/.jarvis-cloud/go-bin:$PATH
export GOPATH=/workspace/.jarvis-cloud/go-work
export GOCACHE=/workspace/.jarvis-cloud/go-cache
export GOBIN=/workspace/.jarvis-cloud/go-bin
export GOPROXY=https://proxy.golang.org
export GOTOOLCHAIN=local
if [[ ! -d "$ANDROID_HOME/ndk/28.2.13676358" ]]; then
    "$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" 'ndk;28.2.13676358'
fi
go install golang.org/x/mobile/cmd/gomobile@v0.0.0-20261011034528-197770444867
go install golang.org/x/mobile/cmd/gobind@v0.0.0-20261011034528-197770444867
gomobile init
