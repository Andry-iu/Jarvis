#!/usr/bin/env bash
# Passwords are entered only in Termux, never in chat or command arguments.
# Signs only the verified local 0.5.47 diagnostic APK and checks the permanent certificate.
set +x
set -Eeuo pipefail
umask 077
export LC_ALL=C
ulimit -c 0
unset JARVIS_STORE_PASSWORD JARVIS_KEY_PASSWORD
phase='preparazione'
on_error() {
    local status=$1
    trap - ERR
    unset JARVIS_STORE_PASSWORD JARVIS_KEY_PASSWORD
    printf '\nSTOP: fase "%s", codice %s. Firma finale non confermata.\n' "$phase" "$status" >&2
    exit "$status"
}
trap 'on_error "$?"' ERR

check_tools_only=false
if [[ "${1:-}" == '--check-tools' ]]; then
    check_tools_only=true
    shift
fi

expected_input_sha256='cfdce6f36140dcfe2d6719bc049e1a942e065747db69e3bc19cfb23caf1f8c91'
expected_cert_sha256='a8ddfbe2b78be5460e72df57afbc3434411e3cc27328b13bc16b4daaba13389a'
input=${1:-"$HOME/storage/downloads/JarvisWake-0.5.47-debug-arm64-unsigned.apk"}
output=${2:-"$(dirname -- "$input")/JarvisWake-0.5.47-debug-arm64-permanent.apk"}
keystore=${JARVIS_KEYSTORE_PATH:-"$HOME/jarvis-signing/jarvis-signing.jks"}

for tool in apksigner keytool sha256sum awk grep mktemp timeout uname; do
    command -v "$tool" >/dev/null || {
        echo 'Installa prima gli strumenti in Termux: pkg install apksigner coreutils' >&2
        exit 1
    }
done
if ! "$check_tools_only"; then
[[ -f "$input" ]] || { echo "APK non trovato: $input" >&2; exit 1; }
actual_input_sha256=$(sha256sum -- "$input" | awk '{print $1}')
[[ "$actual_input_sha256" == "$expected_input_sha256" ]] || {
    echo 'STOP: questo APK non coincide con la compilazione locale verificata. Nessuna firma eseguita.' >&2
    exit 1
}
[[ -f "$keystore" ]] || { echo "Keystore permanente non trovato: $keystore" >&2; exit 1; }
[[ ! -e "$output" ]] || { echo "Il file di destinazione esiste già: $output" >&2; exit 1; }
[[ -t 0 ]] || { echo 'Esegui lo script in un terminale interattivo Termux.' >&2; exit 1; }
fi

# Keep native code and temporary signing output in private Termux storage.
if [[ -n "${PREFIX:-}" && -d "$PREFIX/tmp" ]]; then
    temporary=$(mktemp -d "$PREFIX/tmp/jarvis-sign.XXXXXXXX")
else
    temporary=$(mktemp -d)
fi
cleanup() {
    unset JARVIS_STORE_PASSWORD JARVIS_KEY_PASSWORD
    rm -rf -- "$temporary"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

compat_dir=''
java_tool() {
    if [[ -n "$compat_dir" ]]; then
        LD_LIBRARY_PATH="$compat_dir${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" "$@"
    else
        "$@"
    fi
}
probe_java_tools() {
    : > "$temporary/java-probe.log"
    java_tool timeout --foreground --kill-after=2s 15s apksigner version \
        >> "$temporary/java-probe.log" 2>&1 || return 1
    java_tool timeout --foreground --kill-after=2s 15s keytool -help \
        >> "$temporary/java-probe.log" 2>&1 || return 1
}

phase='verifica strumenti Java'
printf 'JARVIS - procedura di firma 0.5.47: verifica strumenti...\n'
if ! probe_java_tools; then
    if ! grep -Fq 'com/android/internal/dev/perfetto/sdk/PerfettoTrace' "$temporary/java-probe.log"; then
        cat "$temporary/java-probe.log" >&2
        echo 'STOP: Java non si avvia. Nessuna password richiesta e nessuna firma eseguita.' >&2
        exit 1
    fi
    [[ -n "${PREFIX:-}" && "$(uname -m)" == 'aarch64' ]] || {
        echo 'STOP: il workaround Perfetto è previsto per Termux ARM64.' >&2; exit 1;
    }
    command -v clang >/dev/null || {
        echo 'Rilevato il crash Perfetto. Installa clang: pkg install clang' >&2
        echo 'Poi riesegui questo script. Nessuna password richiesta.' >&2
        exit 1
    }
    printf 'Rilevato PerfettoTrace: preparo compatibilità temporanea per gli strumenti Java.\n'
    compat_dir="$temporary/java-compat"
    mkdir -m 700 "$compat_dir"
    # This standalone JNI entry point uses no Android services, I/O or crypto.
    # jint is a signed 32-bit int; JavaVM* and reserved are opaque pointers.
    # Technique documented by https://github.com/ZarShevak/kotlinagrad
    cat > "$compat_dir/perfetto-compat.c" <<'JARVIS_C'
__attribute__((visibility("default")))
int JNI_OnLoad(void *vm, void *reserved) {
    (void)vm;
    (void)reserved;
    return 0x00010006; /* JNI_VERSION_1_6 */
}
JARVIS_C
    clang -shared -fPIC -O2 -Wall -Wextra -Werror -nostdlib -fno-stack-protector \
        -Wl,-soname,libperfetto_framework_jni.so -Wl,--no-undefined \
        -Wl,-z,max-page-size=16384 -Wl,-z,noexecstack -Wl,-z,relro,-z,now \
        -o "$compat_dir/libperfetto_framework_jni.so" "$compat_dir/perfetto-compat.c"
    if ! probe_java_tools; then
        cat "$temporary/java-probe.log" >&2
        echo 'STOP: compatibilità Java non riuscita. Nessuna password richiesta e nessuna firma eseguita.' >&2
        exit 1
    fi
fi
printf 'Strumenti Java operativi.\n'
if "$check_tools_only"; then
    printf 'VERIFICA STRUMENTI RIUSCITA. Keystore non aperto, nessun APK firmato.\n'
    exit 0
fi

phase='lettura password keystore'
if ! IFS= read -r -s -p 'Password del keystore (resta nel telefono): ' JARVIS_STORE_PASSWORD; then
    printf '\nSTOP: inserimento password interrotto. Nessuna firma eseguita.\n' >&2
    exit 1
fi
printf '\n'
export JARVIS_STORE_PASSWORD
phase='verifica certificato permanente'
java_tool keytool -exportcert -keystore "$keystore" -alias jarvis \
    -storepass:env JARVIS_STORE_PASSWORD -file "$temporary/certificate.der"
actual_cert_sha256=$(sha256sum "$temporary/certificate.der" | awk '{print $1}')
[[ "$actual_cert_sha256" == "$expected_cert_sha256" ]] || {
    echo 'STOP: il certificato non corrisponde alla chiave permanente concordata.' >&2
    exit 1
}
printf 'Certificato permanente verificato.\n'
phase='lettura password chiave'
if ! IFS= read -r -s -p 'Password della chiave jarvis (Invio se uguale al keystore): ' JARVIS_KEY_PASSWORD; then
    printf '\nSTOP: inserimento password interrotto. Nessuna firma eseguita.\n' >&2
    exit 1
fi
printf '\n'
JARVIS_KEY_PASSWORD=${JARVIS_KEY_PASSWORD:-$JARVIS_STORE_PASSWORD}
export JARVIS_KEY_PASSWORD

# The APK was already aligned and checked with Build Tools 36.0.0 on Linux.
# Signing does not repackage it. Never run zipalign or edit it after this step.
phase='firma APK'
printf 'Firma APK in corso...\n'
java_tool apksigner sign --ks "$keystore" --ks-key-alias jarvis \
    --ks-pass env:JARVIS_STORE_PASSWORD --key-pass env:JARVIS_KEY_PASSWORD \
    --min-sdk-version 31 --v1-signing-enabled false \
    --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
    --out "$temporary/signed.apk" "$input"
unset JARVIS_STORE_PASSWORD JARVIS_KEY_PASSWORD

phase='verifica firma per Android API 31 e successive'
printf 'Verifica crittografica della firma...\n'
if ! java_tool apksigner verify --verbose --print-certs --min-sdk-version 31 "$temporary/signed.apk" > "$temporary/verification.txt" 2>&1; then
    cat "$temporary/verification.txt" >&2
    echo 'STOP: verifica crittografica non riuscita. Nessun APK consegnato.' >&2
    exit 1
fi
signed_cert=$(awk '/^Signer #1 certificate SHA-256 digest:/ {print tolower($NF)}' "$temporary/verification.txt")
[[ "$signed_cert" == "$expected_cert_sha256" ]] || {
    echo 'STOP: verifica finale del certificato non riuscita.' >&2; exit 1;
}
if ! grep -q '^Verified using v3 scheme .*: true$' "$temporary/verification.txt"; then
    cat "$temporary/verification.txt" >&2
    echo 'STOP: firma v3 non verificata.' >&2; exit 1
fi
# For minSdk >= 28, ApkVerifier skips v2 when v3 exists. Its v2=false then
# means "not checked", not "invalid". Exercise v2 separately on API 24-27.
# This changes only verification, never the APK manifest or supported minSdk.
phase='verifica separata dello schema v2'
if ! java_tool apksigner verify --verbose --print-certs --min-sdk-version 24 --max-sdk-version 27 \
        "$temporary/signed.apk" > "$temporary/verification-v2.txt" 2>&1; then
    cat "$temporary/verification-v2.txt" >&2
    echo 'STOP: verifica crittografica v2 non riuscita.' >&2; exit 1
fi
if ! grep -q '^Verified using v2 scheme .*: true$' "$temporary/verification-v2.txt"; then
    cat "$temporary/verification-v2.txt" >&2
    echo 'STOP: firma v2 non verificata.' >&2; exit 1
fi
v2_cert=$(awk '/^Signer #1 certificate SHA-256 digest:/ {print tolower($NF)}' "$temporary/verification-v2.txt")
[[ "$v2_cert" == "$expected_cert_sha256" ]] || {
    echo 'STOP: certificato dello schema v2 differente dalla chiave permanente.' >&2; exit 1
}
printf 'Schemi v2 e v3 verificati con il certificato permanente.\n'
phase='salvataggio APK verificato'
mkdir -p -- "$(dirname -- "$output")"
# noclobber protects an existing file even if it appeared during signing.
(set -o noclobber; cat "$temporary/signed.apk" > "$output")
java_tool apksigner verify --verbose --print-certs --min-sdk-version 31 "$output" > "$output.verify.txt"
cat "$temporary/verification-v2.txt" > "$output.verify-v2.txt"
sha256sum -- "$output" > "$output.sha256"
printf 'FIRMA PERMANENTE VERIFICATA. APK pronto: %s\n' "$output"
printf 'Certificato SHA-256: %s\n' "$signed_cert"
