# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Create a fresh disposable TEST guest after a media test removes the previous guest.
Only for local acceptance fixtures; never use against customer meetings.
"""

import json, requests
from quality import ROOT, ENV, login, request, save_state

state = json.loads((ROOT / "private-quality-state.json").read_text())
admin = login(ENV["ADMIN_USERNAME"], ENV["ADMIN_PASSWORD"])
p = "/meetings/" + str(state["meetingId"])
m = request(admin, p)
assert (
    m["title"].startswith("TEST ") and m["status"] == "LIVE"
), "Live TEST fixture required"
i = request(admin, p + "/invites", "POST", {"maxUses": 1})
guest = requests.Session()
x = request(
    guest,
    "/guest/join",
    "POST",
    {"code": i["code"], "displayName": "TEST 受邀演示成员"},
)
request(admin, p + "/attendees/" + str(x["id"]), "POST", {"action": "ADMIT"})
state["guest"] = {"id": x["id"], "cookies": guest.cookies.get_dict()}
save_state("private-quality-state.json", state)
print("Fresh admitted TEST guest prepared; code and session stay private.")
