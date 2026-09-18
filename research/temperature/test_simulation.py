import unittest
from simulate import simulate

class SimulationTests(unittest.TestCase):
    def test_repeatable_grouped_runs(self):
        self.assertEqual(simulate(1000),simulate(1000))
        self.assertNotEqual(simulate(1000)["parameters"],simulate(1001)["parameters"])
    def test_no_nonfinite_temperatures(self):
        import math
        run=simulate(1005)
        self.assertTrue(all(s["temperature"] is None or math.isfinite(s["temperature"]) for s in run["samples"]))
        self.assertTrue(any(not s["valid"] for s in run["samples"]))
        self.assertEqual(run["source"],"physics_simulation")
    def test_domain_holdout(self):
        self.assertGreater(simulate(4000,"ood")["parameters"]["pan_capacity_J_K"],1000)
        self.assertLess(simulate(1000)["parameters"]["pan_capacity_J_K"],1000)
if __name__=="__main__": unittest.main()
