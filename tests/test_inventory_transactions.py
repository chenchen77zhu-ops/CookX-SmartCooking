import json
from concurrent.futures import ThreadPoolExecutor
import pytest
from test_inventory_freshness_fields import inventory_api, _add
from app.services.inventory_transactions import atomic_json, consume, inventory_session


def seed(api):
    client, main, user, root = api
    path = root / user / 'inventory.json'
    atomic_json(path, [{'id':'batch-a','name':'土豆','quantity':3}, {'id':'batch-b','name':'土豆','quantity':2}])
    return client, main, user, path


def payload(user, key='receipt-123', **changes):
    return {'user_id':user,'idempotency_key':key,'items':[{'item_id':'batch-a','quantity':1,'expected_quantity':3,**changes}]}


def test_selected_batch_replay_and_payload_conflict(inventory_api):
    client, _, user, path = seed(inventory_api)
    body = payload(user)
    first = client.post('/api/inventory/consume', json=body)
    assert first.status_code == 200
    assert first.json()['changes'][0]['after_quantity'] == 2
    assert client.post('/api/inventory/consume', json=body).json()['replayed'] is True
    assert [r['quantity'] for r in json.loads(path.read_text())] == [2,2]
    assert client.post('/api/inventory/consume', json=payload(user,quantity=2)).status_code == 409
    assert client.get('/api/inventory/consumption/receipt-123',params={'user_id':user}).json()['status']=='success'


@pytest.mark.parametrize('change', [{'expected_quantity':2},{'item_id':'absent'},{'quantity':4}])
def test_stale_or_invalid_batch_is_atomic(inventory_api, change):
    client, _, user, path = seed(inventory_api)
    before = path.read_bytes()
    assert client.post('/api/inventory/consume',json=payload(user,**change)).status_code == 409
    assert path.read_bytes() == before


@pytest.mark.parametrize('quantity', [0,-1,1.5,True,'1'])
def test_quantity_strict(inventory_api, quantity):
    client, _, user, _ = seed(inventory_api)
    assert client.post('/api/inventory/consume',json=payload(user,quantity=quantity)).status_code == 422


def test_duplicate_batch_rejected(inventory_api):
    client, _, user, _ = seed(inventory_api)
    body=payload(user);body['items']*=2
    assert client.post('/api/inventory/consume',json=body).status_code==422


def test_recovery_after_durable_intent(inventory_api, monkeypatch):
    client, main, user, path = seed(inventory_api)
    def fail(*args): raise OSError('simulated interrupted replacement')
    monkeypatch.setattr(main,'_write_inventory_atomic',fail)
    assert client.post('/api/inventory/consume',json=payload(user)).status_code==500
    result=client.get('/api/inventory/consumption/receipt-123',params={'user_id':user})
    assert result.status_code==200
    assert json.loads(path.read_text())[0]['quantity']==2
    assert client.post('/api/inventory/consume',json=payload(user)).status_code==200
    assert json.loads(path.read_text())[0]['quantity']==2


def test_concurrent_replays_lock_once(tmp_path):
    path=tmp_path/'inventory.json'
    atomic_json(path,[{'id':'batch-a','name':'土豆','quantity':3}])
    def attempt(_):
        with inventory_session(path): return consume(path,'same-key',payload('u')['items'])
    with ThreadPoolExecutor(max_workers=4) as pool: results=list(pool.map(attempt,range(4)))
    assert sum(not r['replayed'] for r in results)==1
    assert json.loads(path.read_text())[0]['quantity']==2


def test_decimal_batches_not_merged(inventory_api):
    client, _, user, _=inventory_api
    for days in [2.1,2.9]: assert _add(client,user,{'name':'土豆','quantity':1,'shelf_life':days}).status_code==200
    rows=client.get('/api/inventory',params={'user_id':user}).json()
    assert len(rows)==2
    assert {r['shelf_life'] for r in rows}=={2.1,2.9}


def test_clear_legacy_aliases(inventory_api):
    client, _, user, path=seed(inventory_api)
    atomic_json(path,[{'id':'old','name':'土豆','quantity':1,'purchase_date':'2026-01-01','storage_method':'冷藏','storage':'冷藏'}])
    response=client.put('/api/inventory/old',params={'user_id':user},json={'purchase_time':None,'storage_type':None})
    assert response.status_code==200
    row=json.loads(path.read_text())[0]
    assert row['purchase_time'] is None and row['storage_type'] is None
    assert not any(k in row for k in ['purchase_date','storage','storage_method'])


def test_unknown_user_no_orphan_and_missing_receipt(inventory_api):
    client, _, user, root=inventory_api
    assert client.post('/api/inventory/consume',json=payload('absent')).status_code==404
    assert not (root/'absent').exists()
    assert client.get('/api/inventory/consumption/absent-key',params={'user_id':user}).status_code==404


def test_corrupt_inventory_never_overwritten(inventory_api):
    client, _, user, path=seed(inventory_api)
    path.write_text('{bad json')
    assert _add(client,user,{'name':'土豆','quantity':1}).status_code==500
    assert path.read_text()=='{bad json'
