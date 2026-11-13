"""Régressions des scripts de test, sans Minecraft, réseau ni données réelles."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


TOOLS = Path(__file__).resolve().parents[1]


class ServerHarnessTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="terracraft-harness-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        tools = self.repo / "tools"
        tools.mkdir(parents=True)
        for name in ("smoke_server.sh", "scenario_test.sh", "geo_bench.sh", "test-server-lib.sh"):
            shutil.copy2(TOOLS / name, tools / name)
            (tools / name).chmod(0o755)
        (tools / "install_mods.py").write_text("# Aucun téléchargement dans ce test.\n")
        config = self.repo / "serveur-local" / "config"
        config.mkdir(parents=True)
        (config / "openpartiesandclaims-server.toml").write_text("# Test\n")
        self.jar = self.root / "mod.jar"
        self.jar.write_text("JAR simulé\n")
        self.work = self.root / "serveur de test"
        self.work.mkdir()
        (self.work / ".terracraft-test-workdir").write_text("TerraCraft tests v1\n")
        (self.work / "server.jar").write_text("Serveur simulé\n")
        self.bin = self.root / "bin"
        self.bin.mkdir()
        java = self.bin / "java"
        java.write_text('''#!/usr/bin/env python3
import os
from pathlib import Path
import sys
import time
Path("java.pid").write_text(str(os.getpid()))
mode = os.environ.get("FAKE_SERVER_MODE", "success")
if mode == "crash":
    print("Exception: démarrage impossible", flush=True)
    sys.exit(3)
print("Done (0.1s)!", flush=True)
for command in sys.stdin:
    if command.strip() == "stop":
        if mode == "hang":
            continue
        time.sleep(0.2)
        Path("saved").write_text("Sauvegarde terminée")
        sys.exit(4 if mode == "bad_stop" else 0)
''')
        java.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                        SMOKE_TIMEOUT="3", STOP_TIMEOUT="2")

    def run_script(self, script="smoke_server.sh", jar=None, work=None, mode="success"):
        env = dict(self.env, FAKE_SERVER_MODE=mode)
        return subprocess.run(
            ["bash", str(self.repo / "tools" / script),
             str(self.jar if jar is None else jar), str(self.work if work is None else work)],
            env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=15,
        )

    def assert_server_stopped(self):
        pid = int((self.work / "java.pid").read_text())
        with self.assertRaises(ProcessLookupError):
            os.kill(pid, 0)
        self.assertFalse((self.work / ".terracraft-console").exists())

    def test_success_waits_for_save_and_is_local(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertTrue((self.work / "saved").exists())
        properties = (self.work / "server.properties").read_text()
        self.assertIn("server-ip=127.0.0.1\n", properties)
        self.assert_server_stopped()

    def test_repeated_run_in_marked_directory(self):
        for _ in range(2):
            result = self.run_script()
            self.assertEqual(result.returncode, 0, result.stdout)
            self.assert_server_stopped()

    def test_nonempty_unmarked_directory_is_untouched(self):
        work = self.root / "donnees existantes"
        work.mkdir()
        sentinel = work / "server.properties"
        sentinel.write_text("configuration à conserver")
        result = self.run_script(work=work)
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertEqual(sentinel.read_text(), "configuration à conserver")
        self.assertEqual(list(work.iterdir()), [sentinel])

    def test_missing_jar_does_not_create_workdir(self):
        work = self.root / "absent"
        result = self.run_script(jar=self.root / "absent.jar", work=work)
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertFalse(work.exists())

    def test_crash_before_ready_fails(self):
        result = self.run_script(mode="crash")
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertNotIn("Test de démarrage réussi", result.stdout)
        self.assert_server_stopped()

    def test_nonzero_shutdown_fails(self):
        result = self.run_script(mode="bad_stop")
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertNotIn("Test de démarrage réussi", result.stdout)
        self.assert_server_stopped()

    def test_shutdown_timeout_fails_and_cleans_up(self):
        result = self.run_script(mode="hang")
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertNotIn("Test de démarrage réussi", result.stdout)
        self.assert_server_stopped()

    def test_scenario_preserves_unmarked_world(self):
        (self.work / ".terracraft-test-workdir").unlink()
        world = self.work / "world"
        world.mkdir()
        sentinel = world / "a-conserver"
        sentinel.write_text("monde existant")
        result = self.run_script(script="scenario_test.sh")
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertEqual(sentinel.read_text(), "monde existant")

    def test_scenario_does_not_reset_world_after_failed_smoke(self):
        world = self.work / "world"
        world.mkdir()
        sentinel = world / "a-conserver"
        sentinel.write_text("monde de test précédent")
        result = self.run_script(script="scenario_test.sh", mode="bad_stop")
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertEqual(sentinel.read_text(), "monde de test précédent")
        self.assert_server_stopped()

    def test_benchmark_preserves_unmarked_world(self):
        (self.work / ".terracraft-test-workdir").unlink()
        world = self.work / "world"
        world.mkdir()
        sentinel = world / "a-conserver"
        sentinel.write_text("monde existant")
        result = self.run_script(script="geo_bench.sh")
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertEqual(sentinel.read_text(), "monde existant")

    def test_benchmark_does_not_reset_world_after_failed_smoke(self):
        world = self.work / "world"
        world.mkdir()
        sentinel = world / "a-conserver"
        sentinel.write_text("monde du banc précédent")
        result = self.run_script(script="geo_bench.sh", mode="bad_stop")
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertEqual(sentinel.read_text(), "monde du banc précédent")
        self.assert_server_stopped()


if __name__ == "__main__":
    unittest.main()
