"""Uncalibrated two-node heat simulator. Synthetic != hardware measurements."""
import math
import numpy as np

DT = .5
PHASES = ["preheat", "drop", "recovery", "steady", "cooling", "unknown"]

def simulate(seed, split="train", motion=True):
    rng = np.random.default_rng(seed)
    # Engineering assumptions, deliberately varied; see sources.json.
    ambient = rng.uniform(18, 32)
    capacity = rng.uniform(500, 1000) if split != "ood" else rng.uniform(1100, 1500)
    power = rng.uniform(850, 1900)
    loss = rng.uniform(2, 5)
    conduct = rng.uniform(10, 24)
    target = rng.uniform(145, 200)
    added = int(rng.integers(130, 200))
    off = int(rng.integers(430, 510))
    food_mass = rng.uniform(.1, .35)
    food_capacity = food_mass * rng.uniform(2800, 3900)
    pan, food, sensor = ambient, rng.uniform(5, 25), ambient
    moisture = food_mass * rng.uniform(.3, .8)
    rows, truth, events = [], [], []
    disturbance = int(rng.integers(220, 360))
    duration = int(rng.integers(10, 40) if split != "ood" else rng.integers(50, 90))
    drift = rng.uniform(-.08, .08)
    for i in range(600):
        previous_pan = pan
        heating = power if i < off else 0
        # Thermostat cycling and one optional late overtemperature scenario.
        if i < off and pan > target and seed % 4 != 0: heating *= .15
        transfer = conduct * (pan - food) if i >= added else 0
        radiation = .85 * 5.670374419e-8 * .035 * ((pan + 273.15)**4 - (ambient + 273.15)**4)
        pan += DT * (heating - loss * (pan - ambient) - radiation - transfer) / capacity
        if i >= added:
            energy = DT * (transfer - .8 * (food - ambient))
            if food >= 99 and moisture > 0 and energy > 0:
                evaporated = min(moisture, energy / 2256000)
                moisture -= evaporated
                energy -= evaporated * 2256000
            food += energy / food_capacity
        # Simple changing pan/food field-of-view after addition.
        view = pan if i < added else .65 * pan + .35 * food
        disturbed = motion and disturbance <= i < disturbance + duration
        if disturbed:
            alpha = .35 + .25 * math.sin(i / 3)
            view = alpha * view + (1-alpha) * ambient
        sensor += min(1, DT / 1.0) * (view - sensor)
        observed = sensor + rng.normal(0, .5) + drift * i / 20
        valid = not (motion and seed % 5 == 0 and disturbance + 2 <= i < disturbance + 8)
        slope = (pan - previous_pan) / DT
        if i < added: phase = 0 if abs(slope) > .25 else 3
        elif i < added + 20: phase = 1
        elif i >= off: phase = 4
        elif slope > .25: phase = 2
        else: phase = 3
        # Label unknowable observations as unknown, never label a sensor move as food.
        if disturbed or not valid: phase = 5
        if i == added: events.append({"index": i, "type": "ingredient_added", "confirmed": seed % 3 != 0})
        if i == off: events.append({"index": i, "type": "heat_off", "confirmed": seed % 3 != 0})
        rows.append({"schemaVersion": 1, "updatedAt": i * 500, "temperature": round(observed, 5) if valid else None,
                     "ambientTemperature": round(ambient, 3), "valid": valid, "source": "simulation", "discontinuity": False})
        truth.append({"phase": phase, "quality": not disturbed and valid, "temperature": view,
                      "high": view > min(230, target + 20) and not disturbed})
    return {"id": f"{split}-{seed}", "split": split, "seed": seed, "source": "physics_simulation",
            "context": {"schemaVersion": 1, "recipeId": "synthetic", "stepId": "0", "targetRange": [target-10, target+10], "method": "pan"},
            "parameters": {"pan_capacity_J_K": capacity, "power_W": power, "loss_W_K": loss, "conductance_W_K": conduct,
                           "food_mass_kg": food_mass, "ambient_C": ambient},
            "events": events, "samples": rows, "truth": truth}
