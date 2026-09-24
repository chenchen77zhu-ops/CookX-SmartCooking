import test from 'node:test'
import assert from 'node:assert/strict'
import {normalizeServerOrigin} from './serverAddress.js'
test('LAN origin is normalized and credentials paths or non-HTTP schemes rejected',()=>{assert.equal(normalizeServerOrigin(' http://192.168.1.20:8000/ '),'http://192.168.1.20:8000');assert.equal(normalizeServerOrigin(''),'');for(const bad of ['javascript:alert(1)','http://name:pass@host','https://host/api','https://host/?token=abc','not a url'])assert.throws(()=>normalizeServerOrigin(bad))})
