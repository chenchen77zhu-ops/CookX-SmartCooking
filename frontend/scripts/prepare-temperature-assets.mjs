import { mkdirSync, copyFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
const require=createRequire(import.meta.url)
const base=path.resolve(path.dirname(require.resolve('onnxruntime-web')), '..')
const destination=new URL('../public/temperature/runtime/',import.meta.url)
mkdirSync(destination,{recursive:true})
for(const file of ['ort-wasm-simd-threaded.wasm','ort-wasm-simd-threaded.mjs']) copyFileSync(path.join(base,'dist',file),new URL(file,destination))
