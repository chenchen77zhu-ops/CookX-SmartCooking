"""Deterministic seven-day ranking; hidden/deleted content is excluded on every read."""
from datetime import datetime,timedelta,timezone
from fastapi import APIRouter,Request
from app.storage import store
from app.domain.common import listing
from app.domain.community import public_post
router=APIRouter(prefix='/api/v3/community',tags=['Community ranking'])

def ranked(reference=None):
    reference=reference or datetime.now(timezone.utc);cutoff=reference-timedelta(days=7)
    def recent(value):
        try: stamp=datetime.fromisoformat(value.replace('Z','+00:00'))
        except (AttributeError,ValueError,TypeError): return False
        return stamp.tzinfo is not None and cutoff<=stamp<=reference
    with store.transaction():
        likes=listing('like');comments=listing('comment');posts=[]
        for post in listing('post'):
            if post.get('hidden') or post.get('deleted'): continue
            voters={r['owner'] for r in likes if r['post_id']==post['id'] and r['liked'] and recent(r['liked_at'])}
            commenters={r['owner'] for r in comments if r['post_id']==post['id'] and not r.get('deleted') and recent(r['created_at'])}
            posts.append({**public_post(post),'hot':{'likes':len(voters),'commenters':len(commenters)}})
        posts.sort(key=lambda p:(p['hot']['likes'],p['hot']['commenters'],p['created_at'],p['id']),reverse=True)
        return {'items':posts[:100],'window_start':cutoff.isoformat(),'evaluated_at':reference.isoformat(),'rule':'近七天有效点赞人数优先，同分按评论人数、发布时间依次排序；不代表菜谱质量或食品安全。'}

@router.get('/hot')
def hot(request:Request): return ranked()
