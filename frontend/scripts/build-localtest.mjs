import {build} from 'vite'
import {writeFileSync} from 'node:fs'
// Explicit build-time constant prevents a shell variable from silently disabling the test sandbox.
await build({mode:'localtest',define:{'import.meta.env.VITE_COOKX_LOCAL_TEST':JSON.stringify('true')}})
writeFileSync('dist/local-test-build.json',JSON.stringify({edition:'local-test',schemaVersion:1}))
