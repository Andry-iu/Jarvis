#!/usr/bin/env python3
"""Generate app asset, readable guide and embedded MCP guide from the same source."""
import json,sys
from pathlib import Path
root=Path(__file__).resolve().parent.parent
source=root/'docs/guida-jarvis.json';data=json.loads(source.read_text())
assert data['version']=='0.5.95' and data['versionCode']==117
assert len(data['sections'])>=15
ids=[s['id'] for s in data['sections']];assert len(ids)==len(set(ids))
for s in data['sections']:
 assert set(s)=={'id','title','keywords','text'} and all(isinstance(v,str) and v.strip() for v in s.values())
 assert len(s['text'])<=5000
asset=json.dumps(data,ensure_ascii=False,indent=2)+'\n'
markdown='# '+data['title']+' — '+data['version']+' / '+str(data['versionCode'])+'\n\n'+'\n\n'.join('## '+s['title']+'\n\n'+s['text'] for s in data['sections'])+'\n'
p=root/'phone-bridge/worker/index.js';worker=p.read_text()
a='// BEGIN GENERATED GUIDE';b='// END GENERATED GUIDE'
block=a+'\nconst guideData='+json.dumps(data,ensure_ascii=False,separators=(',',':'))+';\n'+b
if a in worker:
 start=worker.index(a);end=worker.index(b,start)+len(b);worker=worker[:start]+block+worker[end:]
else:worker=block+'\n'+worker
outputs={root/'app/src/main/assets/jarvis-guide.json':asset,root/'docs/GUIDA_COMPLETA.md':markdown,p:worker}
check='--check' in sys.argv
for path,content in outputs.items():
 if check:
  assert path.exists() and path.read_text()==content,'Guide output differs: '+str(path.relative_to(root))
 else:
  path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
print(('Verified' if check else 'Generated')+' one guide source: '+str(len(ids))+' topics, '+str(len(markdown.split()))+' words')
