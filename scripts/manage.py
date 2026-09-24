"""Operator commands. Run from repository root with the backend stopped for migration."""
import argparse
import getpass
import json
import os
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from app.storage import store, database_path, encode
from app.migration import inspect_legacy, migrate, backup_database, restore_database
from app.models.user import create_user, USERS_FILE
from app.auth import create_invitation

def run():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database',help='SQLite destination (COOKX_DATABASE)')
    commands=parser.add_subparsers(dest='command',required=True)
    init=commands.add_parser('init');init.add_argument('--admin',required=True)
    for command in ('precheck','migrate'):
        cmd=commands.add_parser(command);cmd.add_argument('--source',default='app/data/users')
        if command=='migrate': cmd.add_argument('--server-stopped',action='store_true',required=True);cmd.add_argument('--backup-dir',default='backups')
    invite=commands.add_parser('invite');invite.add_argument('--uses',type=int,default=1)
    backup=commands.add_parser('backup');backup.add_argument('destination')
    restore=commands.add_parser('restore');restore.add_argument('source');restore.add_argument('destination');restore.add_argument('--server-stopped',action='store_true',required=True)
    admin=commands.add_parser('admin');admin.add_argument('--username',required=True)
    args=parser.parse_args()
    if args.database: os.environ['COOKX_DATABASE']=args.database
    if args.command=='init':
        if database_path().exists(): raise ValueError('数据库已存在，请勿重新初始化')
        if not args.database and (Path('app/data/users/users.json').exists() or Path('app/users.json').exists()): raise ValueError('检测到旧账号，请使用 migrate，或明确指定独立新数据库')
        password=getpass.getpass('管理员密码（至少 8 位）: ')
        if password!=getpass.getpass('再次输入: '): raise ValueError('密码不一致')
        if len(password)<8: raise ValueError('密码过短')
        store.initialize()
        with store.transaction(ready=False) as db:
            db.execute("INSERT INTO meta VALUES('ready','1')")
            db.execute("INSERT OR REPLACE INTO meta VALUES('schema_version','1')")
            account=create_user(args.admin,'',args.admin,password)
            users=store.read(USERS_FILE,[]);users[0]['role']='admin';store.write(USERS_FILE,users)
        result={'status':'initialized','admin_id':account['id']}
    elif args.command=='precheck':
        data=inspect_legacy(args.source);result={k:len(data[k]) for k in ('accounts','documents')};result['fingerprint']=data['fingerprint']
    elif args.command=='migrate': result=migrate(args.source,args.backup_dir)
    elif args.command=='invite':
        if not 1<=args.uses<=100: raise ValueError('邀请次数应为 1–100')
        result={'invitation_code':create_invitation('operator',uses=args.uses)}
    elif args.command=='restore':result=restore_database(args.source,args.destination)
    elif args.command=='admin':
        with store.transaction():
            users=store.read(USERS_FILE,[]);user=next((u for u in users if u.get('username',u.get('nickname'))==args.username),None)
            if user is None:raise ValueError('账号不存在')
            user['role']='admin';store.write(USERS_FILE,users);result={'status':'administrator_assigned','user_id':user['id']}
    else: result=backup_database(args.destination)
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=='__main__': run()
