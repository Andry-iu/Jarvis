#!/usr/bin/env bash
# Configure this command as the Jarvis cloud setup/start command:
# bash /workspace/Jarvis/cloud-runner/bootstrap.sh --repair --start
set -euo pipefail
umask 077
jarvis_repo=/workspace/Jarvis
jarvis_base=/workspace/.jarvis-cloud
jarvis_source="$jarvis_repo/cloud-runner"
jarvis_repair=false
jarvis_start=false
for jarvis_arg in "$@"; do
    case "$jarvis_arg" in
        --repair) jarvis_repair=true ;;
        --start) jarvis_start=true ;;
        --check) ;;
        *) echo 'Unsupported bootstrap argument' >&2; exit 2 ;;
    esac
done
mkdir -p "$jarvis_base/cloud-runner"
exec 9>"$jarvis_base/cloud-runner/setup.lock"
flock 9
# Base Android/JDK setup belongs to the published Jarvis environment.
if [[ ! -f "$jarvis_base/activate.sh" ]]; then
    echo 'Jarvis base environment is unavailable; use its published configuration.' >&2
    exit 1
fi
source "$jarvis_base/activate.sh"
if "$jarvis_repair"; then
    # This script is idempotent and also fills missing module cache entries.
    env -u Passphrase -u JARVIS_SITE_ACCESS -u JARVIS_RUNNER_TOKEN \
        bash "$jarvis_repo/scripts/setup-whatsapp-native.sh"
    if ! PYTHONPATH="$jarvis_base/signing-python" python -c 'import pyrage' 2>/dev/null; then
        python -m pip install --disable-pip-version-check \
            --cache-dir "$jarvis_base/downloads/pip" \
            --target "$jarvis_base/signing-python" 'pyrage==1.4.0'
    fi
    bash "$jarvis_repo/scripts/prepare-model.sh"
    # Only the wrapper/tool version: this never compiles the application.
    (cd "$jarvis_repo"; bash gradlew --version --no-daemon)
fi
python "$jarvis_source/readiness.py"
if ! "$jarvis_start"; then exit 0; fi
# The private config is preserved. On a fresh instance it may be restored from
# existing environment secret bindings, never from Git or generated credentials.
python - "$jarvis_base/cloud-runner/config.json" <<'PY'
import json, os, pathlib, re, sys
sys.path.insert(0, '/workspace/Jarvis/cloud-runner')
from runner import ORIGIN, private_json
p = pathlib.Path(sys.argv[1])
if not p.exists():
    token = os.environ.get('JARVIS_RUNNER_TOKEN')
    access = os.environ.get('JARVIS_SITE_ACCESS')
    if not token or not access:
        raise SystemExit('runner_config_missing: restore private config or bind JARVIS_RUNNER_TOKEN and JARVIS_SITE_ACCESS in environment settings')
    if not re.fullmatch(r'[A-Za-z0-9_-]{43}', token):
        raise SystemExit('runner_token_invalid')
    private_json(p, {'origin': ORIGIN, 'runner_token': token, 'site_access': access})
if p.stat().st_mode & 0o077:
    raise SystemExit('runner_config_permissions_invalid')
d = json.loads(p.read_text())
if d.get('origin') != ORIGIN or not re.fullmatch(r'[A-Za-z0-9_-]{43}', d.get('runner_token', '')) or not d.get('site_access'):
    raise SystemExit('runner_config_invalid')
PY
# Hold the same inherited runner lock while installing and launching. Another
# bootstrap cannot overwrite code under an active runner or build subprocess.
exec 8>"$jarvis_base/cloud-runner/runner.lock"
if ! flock -n 8; then
    echo 'Runner/build already holds the lock; nothing restarted.'
    exit 0
fi
mkdir -p "$jarvis_base/cloud-runner/current"
for jarvis_file in runner.py signing.py readiness.py; do
    cp "$jarvis_source/$jarvis_file" "$jarvis_base/cloud-runner/current/$jarvis_file.tmp"
    chmod 600 "$jarvis_base/cloud-runner/current/$jarvis_file.tmp"
    mv "$jarvis_base/cloud-runner/current/$jarvis_file.tmp" "$jarvis_base/cloud-runner/current/$jarvis_file"
done
# The Python launcher acquires this same lock before the shell releases it.
# Pass the already-held descriptor to avoid a lock handoff race.
flock -u 9
exec python "$jarvis_base/cloud-runner/current/runner.py" --lock-fd 8
