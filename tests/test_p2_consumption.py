from test_application_acceptance import application, register


def test_single_batch_consumption_roundtrip(application):
    client, _ = application
    uid = register(client, "p2-consume")
    params = {"user_id": uid}
    assert client.post("/api/add-to-inventory", params=params, json=[{"name": "牛奶", "quantity": 2}]).json()["status"] == "success"
    before = client.get("/api/inventory", params=params).json()
    assert client.post("/api/consume-ingredients", params=params, json=["牛奶"]).json()["status"] == "success"
    after = client.get("/api/inventory", params=params).json()
    assert len(after) == 1
    assert after[0]["id"] == before[0]["id"]
    assert after[0]["quantity"] == 1
    # Existing endpoint has no transaction/idempotency field. Client must not retry a lost response.
    assert client.post("/api/consume-ingredients", params={"user_id": "missing-user"}, json=["牛奶"]).json()["status"] == "error"
