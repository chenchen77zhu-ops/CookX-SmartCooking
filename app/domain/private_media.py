"""Private avatars and generated audio, including owner-only legacy avatar access."""
from pathlib import Path
from fastapi import APIRouter,Request,HTTPException,Response
from app.storage import store
from app.domain.common import put,read,new_id
from app.domain.community import validated_image
router=APIRouter(prefix='/api/v3/profile',tags=['Private profile media'])

def save_media(user,content,mime,kind):
    id=new_id()
    with store.transaction() as db:
        db.execute('INSERT INTO media VALUES(?,?,?,?)',(id,user,mime,content))
        put('private_media',id,user,{'kind':kind})
    return '/api/v3/profile/media/'+id

async def upload_avatar(request,file):
    return {'status':'success','avatar_url':save_media(request.state.user_id,await validated_image(file),'image/jpeg','avatar')}

def validate_avatar(user,value,current):
    if not value:return
    if value==current and value.startswith('/static/uploads/avatars/'):
        legacy_path(value) # Validate even unchanged historical values.
        return
    prefix='/api/v3/profile/media/'
    if not value.startswith(prefix):raise HTTPException(422,'请选择本账号上传的头像')
    row=read('private_media',value[len(prefix):])
    if row['owner']!=user or row['kind']!='avatar':raise HTTPException(403,'头像不属于当前账号')

def legacy_path(value):
    value=value or ''
    prefix='/static/uploads/avatars/'
    name=value[len(prefix):] if value.startswith(prefix) else ''
    if not name or Path(name).name!=name or '/' in name or '\\' in name or name in ('.','..'):raise HTTPException(404,'旧头像不可用，请重新上传')
    root=Path('app/static/uploads/avatars').resolve();path=(root/name).resolve()
    if path.parent!=root:raise HTTPException(404,'旧头像不可用')
    return path

@router.get('/avatar/legacy')
async def legacy_avatar(request:Request):
    from starlette.datastructures import UploadFile
    import io
    path=legacy_path(request.state.user.get('avatar',''))
    if not path.is_file() or path.stat().st_size>5*1024*1024:raise HTTPException(404,'旧头像不存在，请重新上传')
    data=await validated_image(UploadFile(io.BytesIO(path.read_bytes()),filename=path.name))
    return Response(data,media_type='image/jpeg',headers={'Cache-Control':'private, no-store','X-Content-Type-Options':'nosniff'})

@router.get('/media/{id}')
def get_media(id:str,request:Request):
    with store.transaction() as db:
        item=read('private_media',id)
        if item['owner']!=request.state.user_id:raise HTTPException(403,'不能读取其他账号文件')
        row=db.execute('SELECT * FROM media WHERE id=? AND owner=?',(id,request.state.user_id)).fetchone()
        if row is None:raise HTTPException(404,'文件不存在')
        return Response(row['content'],media_type=row['mime'],headers={'Cache-Control':'private, no-store','X-Content-Type-Options':'nosniff'})

class PrivateStaticBoundary:
    def __init__(self,app):self.app=app
    async def __call__(self,scope,receive,send):
        # All old static files are user uploads/audio; no public frontend assets live here.
        if scope['type']=='http' and store.is_sqlite and scope.get('path','').startswith('/static/'):
            from starlette.responses import JSONResponse
            await JSONResponse({'detail':'请通过本人授权文件接口读取'},status_code=404)(scope,receive,send)
            return
        await self.app(scope,receive,send)
