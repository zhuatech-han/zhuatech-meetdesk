# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Create local configuration without overwriting credentials. ZhiHua https://www.zhuatech.cn/ WX zhuatech/zhuatech2."""

from pathlib import Path
import secrets, os, json, argparse

ROOT = Path(__file__).resolve().parents[1]
args = argparse.ArgumentParser()
args.add_argument("--env-file", default=".env")
args.add_argument("--web-port", default="8131")
args.add_argument("--tcp-port", default="7921")
args.add_argument("--udp-port", default="7922")
args.add_argument("--turn-port", default="7923")
args.add_argument("--runtime-file", default="./runtime/livekit.yaml")
args.add_argument(
    "--render",
    action="store_true",
    help="Explicitly regenerate runtime configuration; save custom settings first",
)
a = args.parse_args()
p = ROOT / a.env_file
if not p.exists():
    values = {
        "MYSQL_ROOT_PASSWORD": secrets.token_urlsafe(30),
        "DATABASE_PASSWORD": secrets.token_urlsafe(30),
        "ADMIN_USERNAME": "admin",
        "ADMIN_PASSWORD": "Aa9" + secrets.token_urlsafe(28),
        "LIVEKIT_API_KEY": "key" + secrets.token_hex(8),
        "LIVEKIT_API_SECRET": secrets.token_urlsafe(36),
        "WEB_PORT": a.web_port,
        "BIND_ADDRESS": "127.0.0.1",
        "RTC_BIND_ADDRESS": "127.0.0.1",
        "RTC_TCP_PORT": a.tcp_port,
        "RTC_UDP_PORT": a.udp_port,
        "RTC_NODE_IP": "127.0.0.1",
        "COOKIE_SECURE": "false",
        "TURN_UDP_PORT": a.turn_port,
        "LIVEKIT_CONFIG_PATH": a.runtime_file,
        "TURN_RELAY_START": "7930",
        "TURN_RELAY_END": "7940",
    }
    p.write_text("\n".join(k + "=" + v for k, v in values.items()) + "\n")
    p.chmod(0o600)
else:
    values = dict(
        line.split("=", 1)
        for line in p.read_text().splitlines()
        if line and not line.startswith("#")
    )
for key in [
    "LIVEKIT_API_KEY",
    "LIVEKIT_API_SECRET",
    "RTC_NODE_IP",
    "RTC_TCP_PORT",
    "RTC_UDP_PORT",
]:
    if not values.get(key):
        raise SystemExit("Missing configuration: " + key)
if len(values["LIVEKIT_API_SECRET"]) < 32:
    raise SystemExit("LIVEKIT_API_SECRET must have 32+ characters")
for key in ["RTC_TCP_PORT", "RTC_UDP_PORT", "TURN_UDP_PORT"]:
    if not values[key].isdigit() or not 1024 <= int(values[key]) <= 65535:
        raise SystemExit("Invalid " + key)
import ipaddress

ipaddress.ip_address(values["RTC_NODE_IP"])
r = ROOT / "runtime"
r.mkdir(exist_ok=True)
r.chmod(0o700)
f = (ROOT / values.get("LIVEKIT_CONFIG_PATH", a.runtime_file)).resolve()
if f.parent != r.resolve():
    raise SystemExit("Runtime config must be inside runtime/")
if f.exists() and not a.render:
    print(
        "Existing private runtime configuration retained. Use --render after reviewing custom settings."
    )
    raise SystemExit(0)
for key in ["TURN_RELAY_START", "TURN_RELAY_END"]:
    v = values.get(key, "7930" if key.endswith("START") else "7940")
    if not v.isdigit() or not 1024 <= int(v) <= 65535:
        raise SystemExit("Invalid " + key)
if int(values.get("TURN_RELAY_START", "7930")) > int(
    values.get("TURN_RELAY_END", "7940")
):
    raise SystemExit("Invalid relay port range")
f.write_text(
    'port: 7880\nroom:\n  auto_create: false\nbind_addresses: ["0.0.0.0"]\nrtc:\n  tcp_port: '
    + values["RTC_TCP_PORT"]
    + "\n  udp_port: "
    + values["RTC_UDP_PORT"]
    + "\n  node_ip: "
    + values["RTC_NODE_IP"]
    + "\n  use_external_ip: false\n  advertise_internal_ip: false\nkeys:\n  "
    + json.dumps(values["LIVEKIT_API_KEY"])
    + ": "
    + json.dumps(values["LIVEKIT_API_SECRET"])
    + "\nturn:\n  enabled: true\n  udp_port: "
    + values.get("TURN_UDP_PORT", "7923")
    + "\n  relay_range_start: "
    + values.get("TURN_RELAY_START", "7930")
    + "\n  relay_range_end: "
    + values.get("TURN_RELAY_END", "7940")
    + '\n  allow_restricted_peer_cidrs: ["'
    + values["RTC_NODE_IP"]
    + ("/128" if ":" in values["RTC_NODE_IP"] else "/32")
    + '"]\nlogging:\n  level: warn\n'
)
f.chmod(0o600)
print(
    "Private environment and selected runtime configuration are ready. Read ADMIN_PASSWORD locally; do not publish it."
)
