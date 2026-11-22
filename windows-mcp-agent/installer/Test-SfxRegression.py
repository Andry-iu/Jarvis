"""Compile the actual upstream SFX argument loop without Windows dependencies.

Usage: python Test-SfxRegression.py --sfx-source SfxSetup.cpp
Upstream: git-for-windows/7-Zip, v26.05-VS2022-sfx,
CPP/7zip/Bundles/SFXSetup/SfxSetup.cpp (Git blob below).
The upstream source is supplied separately, not vendored or reimplemented.
This verifies its command parser; it does not execute a Windows installer.
"""
import argparse
import hashlib
from pathlib import Path
import subprocess
import tempfile

PINNED_BLOB = "1776a01141307a2af250c5e4200f24fe954dcb8c"

ADAPTER = r'''
#include <string>
#include <cwctype>
#include <cassert>
#include <iostream>
class UString {
    std::wstring value;
public:
    UString() = default;
    UString(const std::wstring &s): value(s) {}
    void Trim() {
        auto first=value.find_first_not_of(L" \t\r\n");
        if(first==std::wstring::npos){value.clear();return;}
        value=value.substr(first,value.find_last_not_of(L" \t\r\n")-first+1);
    }
    bool IsPrefixedBy_Ascii_NoCase(const char *p) const {
        for(size_t i=0;p[i];++i)
            if(i>=value.size() || std::towlower(value[i])!=std::towlower(p[i])) return false;
        return true;
    }
    const wchar_t *Ptr(size_t n) const {return value.c_str()+n;}
    UString &operator=(const wchar_t *p){std::wstring copy(p);value=copy;return *this;}
    wchar_t operator[](size_t n) const {return value[n];}
    UString &operator+=(wchar_t c){value+=c;return *this;}
    unsigned Len() const {return static_cast<unsigned>(value.size());}
    std::wstring text() const {return value;}
};
struct Result {bool yes;std::wstring path;std::wstring rest;};
Result parse(const std::wstring &input) {
    UString switches(input);
    bool assumeYes=false;
    UString installPath;
'''

TESTS = r'''
    return {assumeYes,installPath.text(),switches.text()};
}
int main(){
    auto old=parse(LR"(-y -gm2 -InstallPath="C:\\Users\\Andrea\\Tools\\Git")");
    assert(old.yes && old.path.empty());
    assert(old.rest.rfind(L"-gm2",0)==0);
    std::cout << "PASS old_110_flags_leave_target_unset_and_forward_unknown_arguments\n";
    std::wstring paths[]={LR"(C:\Users\Andrea\Tools\Git)",LR"(C:\Users\Name With Spaces\Git)",LR"(D:\Jarvis Tests\PortableGit)",LR"(C:\Utenti\André\Git)",LR"(C:\Users\用户\Git)"};
    for(const auto &path: paths){
        auto result=parse(L"-y -o\""+path+L"\"");
        assert(result.yes && result.path==path && result.rest.empty());
    }
    std::cout << "PASS new_flags_preserve_exact_target_spaces_unicode_and_no_trailing_arguments\n";
}
'''

def checked_source(path):
    raw = Path(path).read_bytes()
    # apply_patch text materialization can add one final empty line.
    for body in (raw, raw[:-1] if raw.endswith(b"\n\n") else raw):
        digest = hashlib.sha1(b"blob " + str(len(body)).encode() + b"\0" + body).hexdigest()
        if digest == PINNED_BLOB:
            return body.decode("utf-8")
    raise AssertionError("Upstream source does not match the pinned Git blob")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sfx-source", required=True)
    args = parser.parse_args()
    source = checked_source(args.sfx_source)
    start = source.index("  for (;;)", source.index("  UString installPath;"))
    end = source.index("\n  AString config;", start)
    loop = source[start:end]
    worker = (Path(__file__).parent / "Setup-Worker.ps1").read_text()
    assert "('-y -o\"'+$target+'\"')" in worker
    assert "-gm2 -InstallPath=" not in worker
    assert "Get-SetupToolProbe $r.kind $exe" in worker
    assert worker.index("if(-not $probe.available)") < worker.index("$result.state='completed'")
    prereq = (Path(__file__).parent / "Prerequisites.ps1").read_text()
    assert "Test-LegacyGitReceiptRepair $Context $Kind $AssetPath $Target $r" in prereq
    assert "$Receipt.state -ne 'completed'" in prereq
    assert "$Receipt.PSObject.Properties['installer_version']" in prereq
    print("PASS worker_contract_and_legacy_repair_guards")
    with tempfile.TemporaryDirectory(prefix="jarvis-sfx-regression-") as directory:
        cpp = Path(directory) / "sfx.cpp"
        binary = Path(directory) / "sfx-test"
        cpp.write_text(ADAPTER + loop + TESTS)
        subprocess.run(["g++", "-std=c++17", "-Wall", "-Wextra", "-Werror", str(cpp), "-o", str(binary)], check=True)
        subprocess.run([str(binary)], check=True)
    print("PASS upstream_parser_regression_complete")

if __name__ == "__main__":
    main()
