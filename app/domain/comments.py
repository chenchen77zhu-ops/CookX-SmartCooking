"""Single-level comments with author deletion and administrator handling."""
from fastapi import APIRouter,Request,HTTPException
from pydantic import Field
from app.storage import store
from app.domain.common import Command,VersionCommand,read,put,listing,execute,new_id,now
from app.domain.community import visible
router=APIRouter(prefix='/api/v3/community/posts/{post_id}/comments',tags=['Comments'])
class Comment(Command): text:str=Field(min_length=1,max_length=1000)

@router.get('')
def comments(post_id:str,request:Request):
    with store.transaction():
        visible(post_id,request.state.user)
        from app.models.user import get_all_users
        names={u['id']:u['nickname'] for u in get_all_users()}
        rows=[{**r,'author_name':names.get(r['owner'],'已注销用户')} for r in listing('comment') if r['post_id']==post_id and not r.get('deleted')]
        return {'items':sorted(rows,key=lambda r:r['created_at'])}

@router.post('')
def add(post_id:str,request:Request,body:Comment):
    with store.transaction():
        visible(post_id,request.state.user)
        if not body.text.strip(): raise HTTPException(422,'评论不能为空')
        return execute(request.state.user_id,'comment-add:'+post_id,body,lambda:{'comment':put('comment',new_id(),request.state.user_id,{'post_id':post_id,'text':body.text.strip(),'created_at':now(),'deleted':False})})

@router.delete('/{id}')
def delete(post_id:str,id:str,request:Request,body:VersionCommand):
    with store.transaction():
        visible(post_id,request.state.user,True)
        row=read('comment',id)
        if row['post_id']!=post_id: raise HTTPException(404,'评论不存在')
        if row['owner']!=request.state.user_id and request.state.user.get('role')!='admin': raise HTTPException(403,'仅评论作者或管理员可删除')
        return execute(request.state.user_id,'comment-delete:'+id,body,lambda:{'comment':put('comment',id,row['owner'],{**row,'deleted':True,'removed_by':request.state.user_id},body.expected_version)})
