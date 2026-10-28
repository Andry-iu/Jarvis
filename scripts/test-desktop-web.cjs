const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/java/it/jarviswake/DesktopWeb.kt', 'utf8');
const script = source.match(/val viewportScript = """([\s\S]*?)"""/)[1];
for (const existing of [true, false]) {
  let meta = existing ? {content: 'width=device-width, initial-scale=1'} : null;
  let additions = 0;
  const context = {document: {
    head: {appendChild(node) {meta = node; additions++;}},
    querySelector() {return meta;}, createElement() {return {};}
  }};
  vm.runInNewContext(script, context);
  assert.equal(meta.content, 'width=980');
  vm.runInNewContext(script, context);
  assert.equal(additions, existing ? 0 : 1);
}
vm.runInNewContext(script, {document: {head: null}});
console.log('PASS: desktop viewport replaces mobile layout, creates missing meta, and is idempotent.');
