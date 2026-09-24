from test_application_acceptance import application, register

def test_edit_fraction_dates_and_clear_followed_by_freshness(application):
    client, _ = application
    uid = register(client, "p2-fields")
    params = {"user_id": uid}
    item = {"name": "牛肉", "quantity": 1, "shelf_life": 2.5, "storage_type": "冷藏", "purchase_time": "2025-01-01T00:00:00Z"}
    assert client.post("/api/add-to-inventory", params=params, json=[item]).json()["status"] == "success"
    row = client.get("/api/inventory", params=params).json()[0]
    change = {"add_time": "2025-01-01T12:00:00+08:00", "purchase_time": "2025-01-01T08:00:00+08:00", "expiry_date": "2025-01-03T08:00:00+08:00", "shelf_life": 1.5}
    assert client.put("/api/inventory/"+row["id"], params=params, json=change).json()["status"] == "success"
    saved = client.get("/api/inventory", params=params).json()[0]
    assert all(saved[k] == v for k,v in change.items())
    cleared = {k: None for k in ["add_time", "purchase_time", "expiry_date", "shelf_life", "storage_type"]}
    assert client.put("/api/inventory/"+row["id"], params=params, json=cleared).json()["status"] == "success"
    saved = client.get("/api/inventory", params=params).json()[0]
    assert all(saved[k] is None for k in cleared)
    detail = client.get(f"/api/users/{uid}/inventory/freshness").json()["items"][0]
    assert detail["fresh_score"] is None
    assert detail["component_scores"]["T"] is None
