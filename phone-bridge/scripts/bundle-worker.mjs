import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const main=fs.readFileSync(path.join(root,'worker/index.js'),'utf8');
const statement="import {cloudTools,cloudCall,cloudRoute} from './cloud.js';\n";
if(!main.startsWith(statement))throw new Error('Unexpected cloud module import');
const cloud=fs.readFileSync(path.join(root,'worker/cloud.js'),'utf8').replace(/^export /gm,'');
fs.writeFileSync(path.join(root,'dist/server/index.js'),cloud+'\n'+main.slice(statement.length));
