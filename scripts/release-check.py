# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Validate public artifacts, real image references, contact rules, attribution and absence of local credentials."""

from pathlib import Path
import re, json, hashlib, subprocess

ROOT = Path(__file__).resolve().parents[1]
files = [
    Path(x)
    for x in subprocess.check_output(
        ["git", "ls-files"], cwd=ROOT, text=True
    ).splitlines()
]
assert files, "Stage the release candidate first"
for name in ["README.md", "README.en.md"]:
    s = (ROOT / name).read_text()
    assert "[中文](README.md) · [English](README.en.md)" in s
    assert "https://www.zhuatech.cn/" in s
    paths = re.findall(r'!\[[^]]*\]\(([^)]+)\)|<img[^>]+src="([^"]+)"', s)
    for pair in paths:
        p = next(x for x in pair if x)
        assert not p.startswith(("/", "http", "file:"))
        assert (ROOT / p).is_file(), p
        assert Path(p) in files, p
cn = (ROOT / "README.md").read_text()
en = (ROOT / "README.en.md").read_text()
assert "上海如静知华信息科技有限公司" in cn and "未经" in cn and "不得商用" in cn
assert (
    "docs/images/wechat-zhuatech.png" in cn and "docs/images/wechat-zhuatech2.png" in cn
)
assert "wechat-" not in en and "<img" not in en
for x in ["han@zhuatech.cn", "jack@zhuatech.cn", "https://wa.me/8617521234993"]:
    assert x in en
license = (ROOT / "LICENSE").read_text()
assert "未经书面授权不得商用" in license and "第三方依赖保持" in license
for p in files:
    assert not any(
        x in p.parts for x in ["runtime", "node_modules", "target", "private-backups"]
    ), str(p)
    assert not p.name.startswith("private") and (
        p.name == ".env.example" or not p.name.startswith(".env")
    ), str(p)
    if (
        p.suffix in [".java", ".js", ".vue", ".py", ".sql"]
        and "licenses" not in p.parts
    ):
        source = (ROOT / p).read_text()
        assert "zhuatech" in source and "https://www.zhuatech.cn/" in source, str(p)
    if p.suffix in [
        ".java",
        ".js",
        ".vue",
        ".py",
        ".yaml",
        ".yml",
        ".xml",
        ".md",
        ".example",
    ] or p.name in ["Dockerfile", "LICENSE"]:
        source = (ROOT / p).read_text()
        assert not re.search(
            r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}",
            source,
        ), str(p)
# Compare actual locally-generated private values without printing any value.
for private in [
    *(p.name for p in ROOT.glob(".env*") if p.name != ".env.example"),
    *(p.name for p in ROOT.glob("private*state*.json")),
]:
    f = ROOT / private
    if not f.exists():
        continue
    if f.suffix == ".json":
        data = json.loads(f.read_text())
        values = [
            data.get("password", ""),
            *data.get("guest", {}).get("cookies", {}).values(),
        ]
    else:
        values = [
            line.split("=", 1)[1]
            for line in f.read_text().splitlines()
            if "=" in line
            and any(
                k in line.split("=", 1)[0] for k in ["PASSWORD", "SECRET", "API_KEY"]
            )
        ]
    for value in values:
        if len(value) < 12:
            continue
        for p in files:
            assert (
                value.encode() not in (ROOT / p).read_bytes()
            ), "Private value detected in " + str(p)
qr = {
    str(p): hashlib.sha256((ROOT / p).read_bytes()).hexdigest()
    for p in files
    if p.name.startswith("wechat-")
}
print(
    json.dumps(
        {
            "files": len(files),
            "screenshots": len(list((ROOT / "docs/screenshots").glob("*.jpg"))),
            "contactImages": qr,
            "publicArtifactChecks": "passed",
        },
        ensure_ascii=False,
    )
)
