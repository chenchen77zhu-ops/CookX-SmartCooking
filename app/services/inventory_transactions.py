"""File-backed inventory locking and durable, idempotent consumption receipts.

One lock is shared by inventory writers/readers across processes. A prepared
journal recovers an interrupted inventory replacement before the next operation.
No cloud database is required and inventory.json remains the existing array.
"""
from contextlib import contextmanager
from pathlib import Path
import hashlib
import json
import os
import tempfile

from fastapi import HTTPException


def atomic_json(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    filename = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent, delete=False) as file:
            filename = file.name
            json.dump(data, file, ensure_ascii=False, allow_nan=False)
            file.flush()
            os.fsync(file.fileno())
        os.replace(filename, path)
    finally:
        if filename and os.path.exists(filename):
            os.unlink(filename)


def read_json(path, default):
    try:
        return json.loads(Path(path).read_text(encoding='utf-8')) if Path(path).exists() else default
    except (OSError, ValueError) as exc:
        raise HTTPException(500, '库存或扣减记录无法读取，请保留数据并联系维护者') from exc


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()


def journal_path(path):
    return Path(path).with_name('inventory-consumption-receipts.json')


def recover(path):
    journal = read_json(journal_path(path), {})
    if not isinstance(journal, dict):
        raise HTTPException(500, '扣减记录格式损坏')
    for entry in journal.values():
        if not isinstance(entry, dict) or entry.get('status') not in ('prepared', 'committed') or not all(key in entry for key in ('fingerprint', 'result')):
            raise HTTPException(500, '扣减记录格式损坏')
        if entry.get('status') != 'prepared':
            continue
        current = read_json(path, [])
        if digest(current) == entry['before_digest']:
            atomic_json(path, entry['after_inventory'])
        elif digest(current) != digest(entry['after_inventory']):
            raise HTTPException(409, '库存与待恢复扣减不一致，需要人工核对')
        entry['status'] = 'committed'
        entry.pop('after_inventory', None)
        atomic_json(journal_path(path), journal)
    return journal


@contextmanager
def inventory_session(path):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path.with_name('inventory.lock'), 'a+b') as lock:
        if os.name == 'nt':
            import msvcrt
            lock.seek(0, 2)
            if lock.tell() == 0:
                lock.write(b'0'); lock.flush()
            lock.seek(0)
            msvcrt.locking(lock.fileno(), msvcrt.LK_LOCK, 1)
        else:
            import fcntl
            fcntl.flock(lock, fcntl.LOCK_EX)
        try:
            recover(path)
            yield
        finally:
            if os.name == 'nt':
                lock.seek(0); msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(lock, fcntl.LOCK_UN)


def consume(path, key, items, writer=atomic_json):
    """Call inside inventory_session. All validation precedes the durable intent."""
    journal = recover(path)
    fingerprint = digest(sorted(items, key=lambda row: row['item_id']))
    if key in journal:
        entry = journal[key]
        if entry['fingerprint'] != fingerprint:
            raise HTTPException(409, '同一幂等键不能用于不同扣减清单')
        return {**entry['result'], 'replayed': True}
    inventory = read_json(path, [])
    if not isinstance(inventory, list):
        raise HTTPException(500, '库存格式损坏')
    if any(not isinstance(row, dict) or not row.get('id') or type(row.get('quantity')) is not int or row['quantity'] < 0 for row in inventory):
        raise HTTPException(500, '库存记录格式损坏')
    by_id = {str(row['id']): row for row in inventory}
    if len(by_id) != len(inventory):
        raise HTTPException(409, '库存 ID 重复，需先修复数据')
    after = json.loads(json.dumps(inventory))
    changes = []
    for selected in items:
        row = by_id.get(selected['item_id'])
        if row is None or type(row.get('quantity')) is not int or row['quantity'] != selected['expected_quantity']:
            raise HTTPException(409, '库存已变化，请重新读取实际使用清单')
        if selected['quantity'] > row['quantity']:
            raise HTTPException(409, '扣减数量大于现有库存')
        changes.append({'item_id': row['id'], 'name': row['name'], 'before_quantity': row['quantity'], 'consumed_quantity': selected['quantity'], 'after_quantity': row['quantity']-selected['quantity']})
        for target in after:
            if target['id'] == row['id']:
                target['quantity'] -= selected['quantity']
    consumed_ids = {change['item_id'] for change in changes}
    after = [row for row in after if row['id'] not in consumed_ids or row['quantity'] > 0]
    result = {'status': 'success', 'schema_version': 2, 'idempotency_key': key, 'changes': changes, 'replayed': False}
    journal[key] = {'status': 'prepared', 'fingerprint': fingerprint, 'before_digest': digest(inventory), 'after_inventory': after, 'result': result}
    atomic_json(journal_path(path), journal)
    writer(path, after)
    journal[key]['status'] = 'committed'
    journal[key].pop('after_inventory')
    atomic_json(journal_path(path), journal)
    return result


def receipt(path, key):
    entry = recover(path).get(key)
    if not entry:
        raise HTTPException(404, '未找到扣减凭证；如需重试必须保留原幂等键')
    return {**entry['result'], 'replayed': True}
