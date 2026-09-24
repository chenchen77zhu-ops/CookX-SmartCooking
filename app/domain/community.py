"""Invitation-only posts. Images are authenticated, validated and stripped of metadata."""
import io
import warnings
from fastapi import APIRouter,Request,HTTPException,UploadFile,File,Response
from pydantic import Field
from PIL import Image,ImageOps,UnidentifiedImageError
from typing import Literal
from app.storage import store
from app.domain.common import Command,VersionCommand,read,put,listing,execute,new_id,now
from app.domain.recipes import normalize_recipe

router=APIRouter(prefix='/api/v3/community',tags=['Community'])
class Post(Command):
    text:str=Field(min_length=1,max_length=3000)
    image_ids:list[str]=Field(default_factory=list,max_length=4)
    recipe_copy_id:str|None=None
    confirmed:Literal[True]
class EditPost(Post): expected_version:int=Field(ge=1,strict=True)
class Report(Command): reason:str=Field(min_length=3,max_length=1000)
class Moderate(VersionCommand):
    hidden:bool
    reason:str=Field(min_length=3,max_length=1000)

def visible(id,user,author_access=False):
    post=read('post',id)
    if post.get('deleted') or (post.get('hidden') and not(author_access and (post['owner']==user['id'] or user.get('role')=='admin'))): raise HTTPException(404,'内容已删除或隐藏')
    return post

def public_post(post):
    from app.models.user import get_all_users
    author=next((u['nickname'] for u in get_all_users() if u['id']==post['owner']),'已注销用户')
    return {**post,'author_name':author}

def post_payload(body,user):
    if not body.text.strip(): raise HTTPException(422,'内容不能为空')
    with store.transaction() as db:
        for id in body.image_ids:
            row=db.execute('SELECT owner,mime FROM media WHERE id=?',(id,)).fetchone()
            if row is None or (row['owner']!=user or not row['mime'].startswith('image/')): raise HTTPException(403,'图片不存在或不属于当前账号')
    recipe=None
    if body.recipe_copy_id:
        copy=read('recipe_copy',body.recipe_copy_id)
        if copy['owner']!=user: raise HTTPException(403,'不能发布其他账号的私有菜谱')
        normalized=normalize_recipe(copy['recipe'])
        recipe={k:normalized[k] for k in ('dish_name','ingredients_list','method','tags') if k in normalized}
        recipe['steps']=[{k:s[k] for k in ('text','time_estimate','temperature') if k in s} for s in normalized['steps']]
    return {'text':body.text.strip(),'image_ids':list(dict.fromkeys(body.image_ids)),'recipe':recipe}

async def validated_image(file):
    data=await file.read(5*1024*1024+1)
    if len(data)>5*1024*1024: raise HTTPException(413,'图片不能超过 5 MB')
    try:
        with warnings.catch_warnings():
            warnings.simplefilter('error',Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(data)) as source:
                if source.format not in ('JPEG','PNG','WEBP') or source.width*source.height>12_000_000: raise ValueError('format or pixels')
                source.load();clean=ImageOps.exif_transpose(source).convert('RGB');clean.thumbnail((1920,1920));output=io.BytesIO();clean.save(output,format='JPEG',quality=88)
    except (UnidentifiedImageError,OSError,ValueError,Image.DecompressionBombError,Image.DecompressionBombWarning): raise HTTPException(422,'请上传真实 JPEG、PNG 或 WebP 图片（不超过 1200 万像素）')
    return output.getvalue()

@router.post('/media')
async def upload(request:Request,file:UploadFile=File(...)):
    content=await validated_image(file)
    id=new_id()
    with store.transaction() as db: db.execute('INSERT INTO media VALUES(?,?,?,?)',(id,request.state.user_id,'image/jpeg',content))
    return {'id':id,'mime':'image/jpeg'}

@router.get('/media/{id}')
def image(id:str,request:Request):
    with store.transaction() as db:
        row=db.execute('SELECT * FROM media WHERE id=?',(id,)).fetchone()
        if row is None: raise HTTPException(404,'图片不存在')
        if row['owner']!=request.state.user_id and not any(id in p.get('image_ids',[]) and not p.get('hidden') and not p.get('deleted') for p in listing('post')): raise HTTPException(403,'图片尚未发布或已隐藏')
        return Response(row['content'],media_type=row['mime'],headers={'Cache-Control':'private, no-store','X-Content-Type-Options':'nosniff'})

@router.get('/posts')
def feed(request:Request,mine:bool=False):
    with store.transaction():
        posts=[p for p in listing('post') if not p.get('deleted') and (p['owner']==request.state.user_id if mine else not p.get('hidden'))]
        posts.sort(key=lambda p:p['created_at'],reverse=True)
        return {'items':[public_post(p) for p in posts[:100]],'visibility':'内测成员可见'}

@router.post('/posts')
def publish(request:Request,body:Post):
    def action():return {'post':public_post(put('post',new_id(),request.state.user_id,{**post_payload(body,request.state.user_id),'created_at':now(),'hidden':False,'deleted':False}))}
    return execute(request.state.user_id,'post-publish',body,action)

@router.put('/posts/{id}')
def edit(id:str,request:Request,body:EditPost):
    with store.transaction():
        post=visible(id,request.state.user,True)
        if post['owner']!=request.state.user_id: raise HTTPException(403,'仅作者可编辑')
        return execute(request.state.user_id,'post-edit:'+id,body,lambda:{'post':public_post(put('post',id,post['owner'],{**post,**post_payload(body,request.state.user_id),'edited_at':now()},body.expected_version))})

@router.delete('/posts/{id}')
def delete(id:str,request:Request,body:VersionCommand):
    with store.transaction():
        post=visible(id,request.state.user,True)
        if post['owner']!=request.state.user_id: raise HTTPException(403,'仅作者可删除')
        return execute(request.state.user_id,'post-delete:'+id,body,lambda:{'post':put('post',id,post['owner'],{**post,'deleted':True},body.expected_version)})

@router.post('/posts/{id}/reports')
def report(id:str,request:Request,body:Report):
    with store.transaction():
        visible(id,request.state.user)
        return execute(request.state.user_id,'post-report:'+id,body,lambda:{'report':put('report',new_id(),request.state.user_id,{'post_id':id,'reason':body.reason,'created_at':now()})})

@router.get('/moderation')
def reports(request:Request):
    if request.state.user.get('role')!='admin': raise HTTPException(403,'需要管理员权限')
    return {'items':listing('report'),'posts':[public_post(p) for p in listing('post') if p.get('hidden') and not p.get('deleted')]}

@router.post('/posts/{id}/moderation')
def moderate(id:str,request:Request,body:Moderate):
    if request.state.user.get('role')!='admin': raise HTTPException(403,'需要管理员权限')
    with store.transaction():
        post=visible(id,request.state.user,True)
        def action():
            changed=put('post',id,post['owner'],{**post,'hidden':body.hidden,'moderation_reason':body.reason},body.expected_version)
            put('moderation_event',new_id(),request.state.user_id,{'post_id':id,'hidden':body.hidden,'reason':body.reason,'created_at':now()})
            return {'post':public_post(changed)}
        return execute(request.state.user_id,'post-moderate:'+id,body,action)
