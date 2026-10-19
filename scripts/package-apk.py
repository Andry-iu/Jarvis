#!/usr/bin/env python3
"""Name the CI artifact from the compiled APK metadata, not a duplicated version."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil

root = Path(__file__).resolve().parent.parent
build = root / "app/build/outputs/apk/debug"
metadata = json.loads((build / "output-metadata.json").read_text(encoding="utf-8"))
elements = metadata["elements"]
if len(elements) != 1 or metadata["applicationId"] != "it.jarviswake":
    raise SystemExit("Expected one JarvisWake debug APK")
version = elements[0]["versionName"]
if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
    raise SystemExit("Unexpected versionName")
source = (build / elements[0]["outputFile"]).resolve()
if source.parent != build.resolve() or not source.is_file():
    raise SystemExit("APK missing or outside the build output directory")
name = f"JarvisWake-{version}-debug-arm64"
apk = root / f"{name}.apk"
shutil.copyfile(source, apk)
checksum = root / f"{name}.apk.sha256"
checksum.write_text(f"{hashlib.sha256(apk.read_bytes()).hexdigest()}  {apk.name}\n", encoding="utf-8")
if output := os.environ.get("GITHUB_OUTPUT"):
    with open(output, "a", encoding="utf-8") as stream:
        stream.write(f"artifact_name={name}\napk_path={apk.name}\nchecksum_path={checksum.name}\n")
print(f"Packaged {apk.name} (versionCode {elements[0]['versionCode']})")
