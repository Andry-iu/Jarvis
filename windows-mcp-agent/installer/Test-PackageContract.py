"""Verify source-only installer manifest and canonical updater contract."""
from pathlib import Path
import hashlib
import json
import re

root = Path(__file__).resolve().parent
module = root.parent
manifest = json.loads((root / 'package.json').read_text(encoding='utf-8-sig'))
assert manifest['installer_version'] == '1.3.0'
assert manifest['agent_version'] == '0.7.0'
assert re.fullmatch(r'[a-f0-9]{40}', manifest['source_commit'])
assert manifest['agent_sha256'] == '1542248e430042d1dab7e913c889a1242318e6cdfd276ebec22a927f4ff16b22'
names = [f['path'] for f in manifest['files']]
assert len(names) == len(set(names))
assert 'update-agent.ps1' in names
for f in manifest['files']:
    name = f['path']
    assert re.fullmatch(r'[A-Za-z0-9_.-]+', name), name
    assert not name.startswith('setup.local.env')
    assert not name.endswith('.secret.env')
    assert not name.endswith('.env') or name == 'setup.example.env'
    if name == 'jarvis-mcp.exe':
        assert f['sha256'] == manifest['agent_sha256'] and f['bytes'] == 12193792
        continue
    p = module / 'scripts' / name if name == 'update-agent.ps1' else root / name
    data = p.read_text(encoding='utf-8').replace('\r\n', '\n').encode('utf-8')
    assert len(data) == f['bytes'], name
    assert hashlib.sha256(data).hexdigest() == f['sha256'], name
install = (root / 'Install.ps1').read_text()
assert "'Agent\\jarvis-mcp.exe'" in install
assert "agent_version='0.7.0'" in install
assert "installer_version='1.3.0'" in install
assert 'AGENT_UPDATE_REQUIRED_USE_EXISTING_AGENT_BATCH' in (root / 'Prerequisites.ps1').read_text()
assert 'Assert-SetupExistingAgentVersion $existing' in (root / 'Setup.ps1').read_text()
updater = (module / 'scripts/update-agent.ps1').read_text()
assert 'Stop-Process -Id $proc.Id -Force' in updater
assert not re.search(r'(?im)^\s*(Register|Start)-ScheduledTask\b', updater)
assert not re.search(r'(?im)^\s*(?:&\s*)?taskkill\b', updater)
assert not re.search(r'(?i)\[string\]\s*\$home\b', updater)
for p in root.iterdir():
    assert p.suffix.lower() not in {'.exe', '.dll', '.zip', '.pdb'}, p.name
print('PASS installer_130_agent_070_manifest_canonical_updater_sources_only')
