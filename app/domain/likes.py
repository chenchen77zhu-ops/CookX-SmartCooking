"""One active vote per account and post, with optimistic versions and retries."""
from fastapi import APIRouter,Request,HTTPException
from pydantic import Field
from app.storage import store
from app.domain.common import Command,put,listing,execute,now
from app.domain.community import visible
router=APIRouter(prefix='/api/v3/community/posts/{post_id}/likes',tags=['Likes'])
class Vote(Command):
    liked:bool=Field(strict=True)
    expected_version:int=Field(ge=0,strict=True)

def snapshot(post_id,user):
    rows=[r for r in listing('like') if r['post_id']==post_id]
    mine=next((r for r in rows if r['owner']==user),None)
    return {'count':sum(r['liked'] for r in rows),'liked':bool(mine and mine['liked']),'version':mine['version'] if mine else 0}

@router.get('')
def get(post_id:str,request:Request):
    with store.transaction():
        visible(post_id,request.state.user)
        return snapshot(post_id,request.state.user_id)

@router.put('')
def vote(post_id:str,request:Request,body:Vote):
    user=request.state.user_id
    with store.transaction():
        visible(post_id,request.state.user)
        def action():
            current=snapshot(post_id,user)
            if current['version']!=body.expected_version: raise HTTPException(409,'点赞状态已变化，请刷新后重试')
            if current['liked']==body.liked: return snapshot(post_id,user)
            put('like',post_id+':'+user,user,{'post_id':post_id,'liked':body.liked,'liked_at':now()},current['version'] or None)
            return snapshot(post_id,user)
        return execute(user,'like:'+post_id,body,action)
