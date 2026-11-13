"""Les tolérances réseau du scénario ne doivent pas masquer une erreur du mod."""

from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_scenario_log import classify_exceptions


class ScenarioLogTest(unittest.TestCase):
    def profile_error(self, header="Couldn't find profile with name: alice", status=429):
        return (f"[15:45:31] [Server thread/WARN]: {header}\n"
                f"MinecraftClientHttpException[type=HTTP_ERROR, status={status}, response=null]\n"
                "\tat knot//com.mojang.authlib.minecraft.client.MinecraftClient.buildHttpException(MinecraftClient.java:167)\n"
                "\tat knot//com.mojang.authlib.services.MinecraftServicesSessionService.fetchProfile(MinecraftServicesSessionService.java:161)\n")

    def test_clean_log(self):
        self.assertEqual(classify_exceptions("[15:45:31] [Server thread/INFO]: Done"), ([], []))

    def test_fake_profile_rate_limit_is_reported_separately(self):
        unexpected, external = classify_exceptions(self.profile_error())
        self.assertFalse(unexpected)
        self.assertEqual(len(external), 1)

    def test_real_profile_properties_rate_limit(self):
        log = self.profile_error("Couldn't look up profile properties for fa0f91ac-8dbf-4d7c-b2d0-296ee7631986")
        self.assertEqual(len(classify_exceptions(log)[1]), 1)

    def test_fake_profile_properties_rate_limit(self):
        log = self.profile_error("Couldn't look up profile properties for faa5dca3-c3d4-354b-ae1b-dde9e5a14b3b")
        self.assertEqual(len(classify_exceptions(log)[1]), 1)

    def test_other_player_or_status_still_fails(self):
        for log in (self.profile_error("Couldn't find profile with name: inconnu"),
                    self.profile_error(status=500),
                    self.profile_error("Couldn't look up profile properties for identifiant-invalide")):
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
        weather = ("[15:49:34] [worker/WARN]: Météo Open-Meteo indisponible "
                   "(java.util.concurrent.CompletionException: java.net.http.HttpConnectTimeoutException: "
                   "HTTP connect timed out; java.lang.IllegalStateException: autre erreur)")
        self.assertEqual(len(classify_exceptions(weather)[0]), 1)


if __name__ == "__main__":
    unittest.main()
