"""Real SQLite/auth loopback server; external vision and recipe providers are fixtures."""
import json
import os
import sys
import tempfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
os.environ.setdefault('DEEPSEEK_API_KEY','test-placeholder-not-a-real-key')
os.environ.setdefault('DASHSCOPE_API_KEY','test-placeholder-not-a-real-key')
import pytest
import uvicorn
from test_sqlite_auth import sqlite_app, signup

if __name__=='__main__':
    with tempfile.TemporaryDirectory(prefix='cookx-sqlite-') as directory,pytest.MonkeyPatch.context() as patch:
        fixture=sqlite_app.__wrapped__(patch,Path(directory));client,main=next(fixture)
        try:
            for name in ('sqlite-alice','sqlite-bob'): signup(client,name)
            async def recipe(*args):
                yield json.dumps({'dish_name':'测试番茄炒蛋','steps':[{'text':'检查食材','time_estimate':30}]},ensure_ascii=False)
            patch.setattr(main,'get_recipe_suggestion',recipe)
            uvicorn.run(main.app,host='127.0.0.1',port=8001,log_level='warning')
        finally: fixture.close()
