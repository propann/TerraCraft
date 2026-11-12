"""Les tolérances réseau du scénario ne doivent pas masquer une erreur du mod."""

from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_scenario_log import classify_exceptions, offline_uuid


class ScenarioLogTest(unittest.TestCase):
    def profile_error(self, header="Couldn't find profile with name: alice", status=429):
        return (f"[15:45:31] [Server thread/WARN]: {header}\n"
                f"MinecraftClientHttpException[type=HTTP_ERROR, status={status}, response=null]\n"
                "\tat knot//com.mojang.authlib.minecraft.client.MinecraftClient.buildHttpException(MinecraftClient.java:167)\n")

    def test_clean_log(self):
        self.assertEqual(classify_exceptions("[15:45:31] [Server thread/INFO]: Done"), ([], []))

    def test_fake_profile_rate_limit_is_reported_separately(self):
        unexpected, external = classify_exceptions(self.profile_error())
        self.assertFalse(unexpected)
        self.assertEqual(len(external), 1)

    def test_offline_uuid_matches_minecraft(self):
        self.assertEqual(offline_uuid("alice"), "40f5db53-a47a-33ee-b1f6-db0e20deded4")
        self.assertEqual(offline_uuid("Alice"), "10920508-d5d8-3eed-93d2-92f193afe7d7")

    def test_fake_profile_properties_rate_limit(self):
        log = self.profile_error("Couldn't look up profile properties for " + offline_uuid("Bob"))
        self.assertEqual(len(classify_exceptions(log)[1]), 1)

    def test_other_player_or_status_still_fails(self):
        for log in (self.profile_error("Couldn't find profile with name: inconnu"),
                    self.profile_error(status=500),
                    self.profile_error("Couldn't look up profile properties for 00000000-0000-0000-0000-000000000000")):
            with self.subTest(log=log):
                self.assertEqual(len(classify_exceptions(log)[0]), 1)

    def test_weather_timeout_is_reported_separately(self):
        log = ("[15:49:34] [ForkJoinPool.commonPool-worker-3/WARN]: Météo Open-Meteo indisponible "
               "(java.util.concurrent.CompletionException: java.net.http.HttpConnectTimeoutException: HTTP connect timed out)")
        self.assertEqual(len(classify_exceptions(log)[1]), 1)

    def test_weather_parsing_error_still_fails(self):
        log = "[15:49:34] [worker/WARN]: Réponse Open-Meteo illisible\njava.lang.IllegalStateException: format invalide"
        self.assertEqual(len(classify_exceptions(log)[0]), 1)

    def test_mod_failure_after_external_outage_still_fails(self):
        log = self.profile_error() + "[15:45:32] [Server thread/ERROR]: Échec du marché\njava.lang.IllegalStateException: transaction\n"
        unexpected, external = classify_exceptions(log)
        self.assertEqual(len(unexpected), 1)
        self.assertEqual(len(external), 1)

    def test_extra_exception_in_allowed_event_still_fails(self):
        log = self.profile_error() + "Caused by: java.lang.IllegalStateException: autre erreur\n"
        self.assertEqual(len(classify_exceptions(log)[0]), 1)


if __name__ == "__main__":
    unittest.main()
