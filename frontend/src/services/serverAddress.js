export const SERVER_KEY='cookx:backend-origin:v1'
export function normalizeServerOrigin(value){
 const text=String(value||'').trim();if(!text)return ''
 let url;try{url=new URL(text)}catch{throw Error('请填写完整地址，例如 http://192.168.1.20:8000')}
 if(!['http:','https:'].includes(url.protocol)||url.username||url.password||url.search||url.hash||!['','/'].includes(url.pathname))throw Error('仅填写 HTTP(S) 服务器地址和端口，不含路径、账号或参数')
 return url.origin
}
