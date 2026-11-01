#!/usr/bin/env python3
"""Bundle licenses of the pinned native module dependencies without copying runtime data."""
import json,os,subprocess
from pathlib import Path
root=Path(__file__).resolve().parent.parent
raw=subprocess.check_output(['go','list','-m','-json','all'],cwd=root/'whatsapp-native',text=True)
decoder=json.JSONDecoder();modules=[]
while raw.strip():
 raw=raw.lstrip();item,end=decoder.raw_decode(raw);raw=raw[end:];modules.append(item)
parts=['Third-party notices for JarvisWake WhatsApp linked-device engine.\nPinned versions and source checksums: whatsapp-native/go.mod and go.sum.\n']
for m in modules:
 if m.get('Main'):continue
 if not m.get('Dir'):
  m.update(json.loads(subprocess.check_output(['go','mod','download','-json',m['Path']+'@'+m['Version']],cwd=root/'whatsapp-native',text=True)))
 directory=Path(m['Dir']);files=[p for p in directory.iterdir() if p.is_file() and (p.name.upper().startswith('LICENSE') or p.name.upper().startswith('COPYING') or p.name.upper()=='NOTICE')]
 parts.append('\n===== '+m['Path']+' '+m['Version']+' =====\n')
 for p in sorted(files):parts.append(p.read_text(errors='replace'))
 if not files:parts.append('License information: https://pkg.go.dev/'+m['Path']+'@'+m['Version']+'?tab=licenses\n')
gotool=Path(subprocess.check_output(['go','env','GOROOT'],text=True).strip())
parts+=['\n===== Go runtime =====\n',(gotool/'LICENSE').read_text()]
output=root/'app/src/main/assets/whatsapp-native-notices.txt'
content='\n'.join(parts)
if not output.exists() or output.read_text()!=content:output.write_text(content)
print('Bundled native dependency notices:',len(modules)-1)
