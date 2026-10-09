# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Allow TURN only to this disposable localhost SFU's exact container addresses; never open a whole private range."""

import argparse, json, subprocess, ipaddress, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument("--project", required=True)
p.add_argument("--env-file", default=".env")
a = p.parse_args()
if not re.fullmatch("[a-z][a-z0-9-]{2,50}", a.project):
    raise SystemExit("Invalid project")
e = dict(
    x.split("=", 1)
    for x in (ROOT / a.env_file).read_text().splitlines()
    if x and not x.startswith("#")
)
if (
    e.get("RTC_NODE_IP") != "127.0.0.1"
    or e.get("RTC_BIND_ADDRESS", "127.0.0.1") != "127.0.0.1"
):
    raise SystemExit("This helper is only for loopback-bound local tests")
cmd = ["docker", "compose", "--env-file", str(ROOT / a.env_file), "-p", a.project]
cid = subprocess.check_output(
    cmd + ["ps", "-q", "livekit"], cwd=ROOT, text=True
).strip()
if not cid:
    raise SystemExit("Start this project LiveKit service first")
info = json.loads(subprocess.check_output(["docker", "inspect", cid], text=True))[0]
labels = info["Config"]["Labels"]
assert (
    labels["com.docker.compose.project"] == a.project
    and labels["com.docker.compose.service"] == "livekit"
)
cidrs = ["127.0.0.1/32"]
for n in info["NetworkSettings"]["Networks"].values():
    if n.get("IPAddress"):
        cidrs.append(str(ipaddress.ip_address(n["IPAddress"])) + "/32")
f = (ROOT / e.get("LIVEKIT_CONFIG_PATH", "./runtime/livekit.yaml")).resolve()
if f.parent != (ROOT / "runtime").resolve():
    raise SystemExit("Runtime config must be private runtime directory")
s = f.read_text()
s = re.sub(
    r"  allow_restricted_peer_cidrs: .*",
    "  allow_restricted_peer_cidrs: " + json.dumps(cidrs),
    s,
)
f.write_text(s)
f.chmod(0o600)
subprocess.run(
    cmd + ["restart", "livekit"], cwd=ROOT, check=True, stdout=subprocess.DEVNULL
)
print(
    "Local TURN policy permits only this project SFU addresses; runtime config remains private."
)
