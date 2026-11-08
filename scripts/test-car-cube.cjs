const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const html = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/car-cube.html'), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
let next = 0, fills = [], moves = [], logs = [], frames = new Map(), listeners = {};
const drawing = {setTransform(){},fillRect(){},beginPath(){},moveTo(x,y){moves.push([x,y])},lineTo(){},closePath(){},
  fill(){fills.push(this.fillStyle)},stroke(){}};
const canvas = {width:0,height:0,getContext:()=>drawing};
const document = {hidden:false,querySelector:()=>canvas,addEventListener:(n,f)=>listeners[n]=f};
const context = {document,innerWidth:800,innerHeight:480,devicePixelRatio:2,
  console:{log:m=>logs.push(m)},window:{},addEventListener:(n,f)=>listeners[n]=f,
  requestAnimationFrame:f=>{frames.set(++next,f);return next},cancelAnimationFrame:id=>frames.delete(id)};
vm.createContext(context); vm.runInContext(script, context);
const cube = context.window.JarvisCube;
assert.equal(frames.size,0); assert.equal(logs.length,0); // No animation before native activation.
for(let t=0;t<60;t+=.125){
  const points = cube.geometry(t);
  assert.equal(points.length,8);
  for(const p of points) for(const v of p) assert.ok(Number.isFinite(v));
  assert.ok(points.every(p=>Math.abs(p[0])<3 && Math.abs(p[1])<3));
}
assert.notDeepEqual(JSON.stringify(cube.geometry(0)),JSON.stringify(cube.geometry(1)));
cube.update(true,false); assert.equal(frames.size,1); assert.deepEqual(logs,['jw_cube_frame']);
function step(ts){const [id,fn]=frames.entries().next().value;frames.delete(id);fn(ts);}
step(1000); step(1016); assert.equal(frames.size,1); assert.equal(logs.length,1);
cube.update(true,true); assert.equal(frames.size,0); assert.ok(fills.at(-1).includes('170,170,170'));
const mutedCount=fills.length; assert.equal(frames.size,0); assert.equal(fills.length,mutedCount);
cube.update(true,false); assert.equal(frames.size,1);
const beforeResume = JSON.stringify(moves.slice(-6));
step(100000); assert.equal(JSON.stringify(moves.slice(-6)),beforeResume); // Paused time is excluded.
document.hidden=true; listeners.visibilitychange(); assert.equal(frames.size,0);
document.hidden=false; listeners.visibilitychange(); assert.equal(frames.size,1);
cube.update(false,false); assert.equal(frames.size,0);
for(const [w,h] of [[1920,484],[480,800],[1,1]]){
  context.innerWidth=w;context.innerHeight=h;cube.update(true,false);
  assert.equal(canvas.width,w*2); assert.equal(canvas.height,h*2);
}
cube.update(false,false);
assert.equal(frames.size,0);
assert.ok(!/fetch\s*\(|WebSocket|XMLHttpRequest|<iframe|<audio|<video/.test(html));
console.log('PASS car cube: geometry, first frame, mute, visibility, lifecycle, sizes, no network');
