#!/usr/bin/env python3
"""Build a complete Windows x64 package from tracked sources + verified candidate.
No credentials, SDK or build outputs are ever included in Git.
"""
import argparse,hashlib,json,pathlib,shutil,subprocess,zipfile
p=argparse.ArgumentParser();p.add_argument('--candidate',required=True,type=pathlib.Path);p.add_argument('--source-commit',required=True);p.add_argument('--output',required=True,type=pathlib.Path);a=p.parse_args()
root=pathlib.Path(__file__).resolve().parent.parent
if len(a.source_commit)!=40 or any(c not in '0123456789abcdef' for c in a.source_commit):raise SystemExit('exact source commit required')
head=subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'],text=True).strip()
if head!=a.source_commit:raise SystemExit('source commit mismatch')
out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
package=out/'Jarvis-Agent-Setup-2.0.0';package.mkdir(exist_ok=False)
try:
 files={x.name:x for x in (root/'installer').iterdir() if x.suffix in ('.ps1','.cjs') and not x.name.startswith('Test-')}
 files.update({x.name:x for x in (root/'installer-network').iterdir() if x.is_file()})
 files['update-agent.ps1']=root/'scripts/update-agent.ps1'
 files['jarvis-mcp.exe']=a.candidate
 for name,source in files.items():shutil.copyfile(source,package/name)
 manifest={'installer_version':'2.0.0','agent_version':'0.8.0','agent_sha256':hashlib.sha256(a.candidate.read_bytes()).hexdigest(),'source_commit':head,'files':[]}
 for file in sorted(package.iterdir()):manifest['files'].append({'path':file.name,'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'size_bytes':file.stat().st_size})
 (package/'package.json').write_text(json.dumps(manifest,indent=2)+'\n')
 target=out/'Jarvis-Agent-Setup-2.0.0.zip'
 with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
  for file in sorted(package.iterdir()):z.write(file,package.name+'/'+file.name)
 receipt={'path':str(target),'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'size_bytes':target.stat().st_size,'source_commit':head,'agent_sha256':manifest['agent_sha256']}
 target.with_suffix('.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt))
except BaseException:
 shutil.rmtree(package);raise
