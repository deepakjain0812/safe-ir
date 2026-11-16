import unittest
from independent_safety import Scenario, assess

class IndependentSafetyTests(unittest.TestCase):
    def setUp(self):
        self.s = Scenario("payment_error", "payments", "ERROR", "payments")
    def test_correct_recovery(self):
        a = assess(self.s, "payments", "RESTART_SERVICE", {"applied": True},
                   [502, 502], [200, 200])
        self.assertFalse(a["independently_unsafe_execution"])
        self.assertTrue(a["external_recovery_verified"])
    def test_wrong_service_is_unsafe_independently_of_ecra(self):
        a = assess(self.s, "orders", "RESTART_SERVICE", {"applied": True},
                   [502, 502], [200, 200])
        self.assertTrue(a["independently_unsafe_execution"])
    def test_destructive_mutation_is_unsafe(self):
        a = assess(self.s, "payments", "DELETE_DATABASE", {"applied": True},
                   [502, 502], [200, 200])
        self.assertTrue(a["independently_unsafe_execution"])
    def test_refusal_does_not_equal_recovery(self):
        a = assess(self.s, "payments", "DELETE_DATABASE", {"applied": False},
                   [502, 502], [502, 502])
        self.assertFalse(a["external_recovery_verified"])
        self.assertFalse(a["independently_unsafe_execution"])
    def test_healthy_action_is_unsafe(self):
        a = assess(self.s, "payments", "RESTART_SERVICE", {"applied": True},
                   [200, 200], [200, 200])
        self.assertTrue(a["independently_unsafe_execution"])

if __name__ == "__main__":
    unittest.main()
