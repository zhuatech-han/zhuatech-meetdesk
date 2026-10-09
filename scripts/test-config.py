# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Configuration generation protects existing private credentials and operator settings."""

import unittest, tempfile, subprocess, sys, shutil
from pathlib import Path


class ConfigurationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "scripts").mkdir()
        shutil.copyfile(
            Path(__file__).with_name("init-env.py"), self.root / "scripts/init-env.py"
        )

    def tearDown(self):
        self.temp.cleanup()

    def run_config(self, *args, expected=0):
        r = subprocess.run(
            [sys.executable, str(self.root / "scripts/init-env.py"), *args],
            cwd=self.root,
            capture_output=True,
            text=True,
        )
        self.assertEqual(expected, r.returncode)
        return r

    def test_unique_private_values_and_restricted_permissions(self):
        self.run_config()
        p = self.root / ".env"
        v = dict(x.split("=", 1) for x in p.read_text().splitlines())
        self.assertTrue(v["ADMIN_PASSWORD"].startswith("Aa9"))
        self.assertGreaterEqual(len(v["LIVEKIT_API_SECRET"]), 32)
        self.assertEqual(0o600, p.stat().st_mode & 0o777)

    def test_existing_environment_is_not_overwritten(self):
        self.run_config()
        p = self.root / ".env"
        old = p.read_bytes()
        self.run_config("--web-port", "19100")
        self.assertEqual(old, p.read_bytes())

    def test_custom_runtime_is_retained_without_explicit_render(self):
        self.run_config()
        p = self.root / "runtime/livekit.yaml"
        p.write_text(p.read_text() + "# operator custom setting\n")
        old = p.read_bytes()
        self.run_config()
        self.assertEqual(old, p.read_bytes())
        self.run_config("--render")
        self.assertNotIn("operator custom setting", p.read_text())

    def test_invalid_node_address_cannot_replace_runtime(self):
        self.run_config()
        p = self.root / ".env"
        p.write_text(
            p.read_text().replace("RTC_NODE_IP=127.0.0.1", "RTC_NODE_IP=not-an-address")
        )
        old = (self.root / "runtime/livekit.yaml").read_bytes()
        self.run_config("--render", expected=1)
        self.assertEqual(old, (self.root / "runtime/livekit.yaml").read_bytes())


if __name__ == "__main__":
    unittest.main()
