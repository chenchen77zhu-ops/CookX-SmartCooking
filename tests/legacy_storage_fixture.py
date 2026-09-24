"""Explicit old file-contract coverage, distinct from SQLite production acceptance."""
from app.storage import FileStore
from app.auth import require_auth

def install_legacy_storage(monkeypatch, main):
    import app.models.user as users
    adapter=FileStore()
    monkeypatch.setattr(main, 'storage', adapter)
    monkeypatch.setattr(users, 'storage', adapter)
    monkeypatch.setitem(main.app.dependency_overrides, require_auth, lambda: None)
