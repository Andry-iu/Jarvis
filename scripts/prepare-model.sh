#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f app/src/main/assets/model/.source-verified ]; then exit 0; fi
archive=$(mktemp)
trap 'rm -f "$archive"' EXIT
curl --fail --location --retry 3 --output "$archive" https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
printf '%s  %s\n' '30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498' "$archive" | sha256sum -c -
mkdir -p app/src/main/assets
staging=$(mktemp -d)
trap 'rm -f "$archive"; rm -rf "$staging"' EXIT
unzip -q "$archive" -d "$staging"
rm -rf app/src/main/assets/model
mv "$staging/vosk-model-small-en-us-0.15" app/src/main/assets/model
touch app/src/main/assets/model/.source-verified
