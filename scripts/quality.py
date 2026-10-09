# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Actual HTTP acceptance against a fresh disposable MeetDesk instance. Never use on customer data."""

from pathlib import Path
import json, os, secrets, requests

ROOT = Path(__file__).resolve().parents[1]
ENV = dict(
    line.split("=", 1)
    for line in (ROOT / os.getenv("MEETDESK_TEST_ENV", ".env")).read_text().splitlines()
    if line and not line.startswith("#")
)
BASE = os.getenv("MEETDESK_TEST_BASE", "http://127.0.0.1:" + ENV["WEB_PORT"])
passed = 0


def check(condition, label):
    global passed
    if not condition:
        raise AssertionError(label)
    passed += 1


def request(session, path, method="GET", body=None, status=200):
    headers = {}
    if method != "GET":
        c = session.get(BASE + "/api/auth/csrf", timeout=10)
        check(c.status_code == 200, "CSRF bootstrap")
        token = c.json()
        headers[token["header"]] = token["token"]
    r = session.request(
        method, BASE + "/api" + path, json=body, headers=headers, timeout=15
    )
    check(
        r.status_code == status,
        method
        + " "
        + path
        + " status "
        + str(r.status_code)
        + " expected "
        + str(status),
    )
    if status != 200:
        return None
    if "text/csv" in r.headers.get("Content-Type", ""):
        return r.text
    return r.json()


def login(name, password):
    s = requests.Session()
    request(s, "/auth/login", "POST", {"username": name, "password": password})
    return s


def save_state(name, obj):
    p = ROOT / name
    p.write_text(json.dumps(obj, ensure_ascii=False, indent=2))
    p.chmod(0o600)


def main():
    health = requests.get(BASE + "/health", timeout=15)
    check(
        health.status_code == 200 and health.json()["status"] == "UP",
        "actual backend health",
    )
    html = requests.get(BASE + "/", timeout=15)
    check(html.status_code == 200 and "MeetDesk" in html.text, "built frontend")
    check(
        requests.get(BASE + "/brand/logo.jpg", timeout=15).status_code == 200,
        "logo served",
    )
    admin = login(ENV["ADMIN_USERNAME"], ENV["ADMIN_PASSWORD"])
    me = request(admin, "/guest/identity")
    check(len(me["menus"]) == 6, "enabled administrator menus")
    check(request(admin, "/meetings") == [], "fresh empty business database required")
    roles = request(admin, "/admin/roles")
    check(len(roles) == 5, "real role initialization")
    check(
        all("passwordHash" not in u for u in request(admin, "/admin/users")),
        "account hashes private",
    )
    dept = request(admin, "/admin/departments")[0]["id"]
    role = lambda codes: next(
        r["id"] for r in roles if set(r["permissions"]) == set(codes)
    )
    password = "Aa9" + secrets.token_urlsafe(24)
    suffix = secrets.token_hex(3)

    def account(key, permissions, org=dept):
        b = {
            "username": key + "-" + suffix,
            "displayName": "TEST "
            + {
                "alice": "协作成员",
                "bob": "演示成员",
                "other": "其他组织",
                "viewer": "统计成员",
            }[key],
            "password": password,
            "roleId": role(permissions),
            "departmentId": org,
            "enabled": True,
        }
        a = request(admin, "/admin/users", "POST", b)
        return a, login(b["username"], password)

    alice, sa = account("alice", ["join"])
    bob, sb = account("bob", ["join"])
    viewer, sv = account("viewer", ["reports"])
    foreign = request(
        admin,
        "/admin/departments",
        "POST",
        {"name": "TEST 外部组织", "zone": "UTC", "enabled": True},
    )
    other, so = account("other", ["join"], foreign["id"])
    request(sa, "/admin/users", status=403)
    request(sa, "/meetings", "POST", {}, 403)
    request(sv, "/meetings", status=403)
    from datetime import datetime, timezone, timedelta

    now = datetime.now(timezone.utc)

    def plan(title, capacity=8, duration=120):
        return {
            "title": title,
            "agenda": "TEST 团队演示、屏幕共享与问题讨论。",
            "category": "TRAINING",
            "capacity": capacity,
            "durationMinutes": duration,
            "scheduledAt": (now + timedelta(minutes=15)).isoformat(),
        }

    main = request(admin, "/meetings", "POST", plan("TEST 远程产品演示"))
    mid = main["id"]
    p = "/meetings/" + str(mid)
    check(
        main["myAttendee"]["status"] == "ADMITTED" and main["admitted"] == 1,
        "host persisted admission",
    )
    request(so, p, status=403)
    check(request(so, "/meetings") == [], "organization discovery isolation")
    waiting = request(sa, p + "/join", "POST")
    aid = waiting["myAttendee"]["id"]
    check(waiting["myAttendee"]["status"] == "WAITING", "account waiting state")
    request(sa, p + "/media", "POST", status=409)
    main = request(
        admin, p + "/state", "POST", {"version": main["version"], "status": "LIVE"}
    )
    request(
        admin, p + "/state", "POST", {"version": main["version"], "status": "LIVE"}, 409
    )
    request(sa, p + "/media", "POST", status=403)
    request(sa, p + "/messages", status=403)
    request(sb, p + "/attendees/" + str(aid), "POST", {"action": "ADMIT"}, 403)
    request(admin, p + "/attendees/" + str(aid), "POST", {"action": "ADMIT"})
    check(
        request(sa, p)["myAttendee"]["status"] == "ADMITTED",
        "actual admission persisted",
    )

    def invitation(uses=2):
        return request(admin, p + "/invites", "POST", {"maxUses": uses})

    private_invite = invitation()
    guest = requests.Session()
    g = request(
        guest,
        "/guest/join",
        "POST",
        {"code": private_invite["code"], "displayName": "TEST 受邀演示成员"},
    )
    gid = g["id"]
    check(g["guest"] and g["permissions"] == ["join"], "guest minimal identity")
    request(guest, p + "/messages", status=403)
    request(guest, p + "/media", "POST", status=403)
    request(guest, "/admin/users", status=401)
    request(guest, "/meetings", "POST", plan("TEST unauthorized"), 401)
    request(admin, p + "/attendees/" + str(gid), "POST", {"action": "ADMIT"})
    check(request(guest, p)["myAttendee"]["status"] == "ADMITTED", "guest admission")
    msg = request(
        sa,
        p + "/messages",
        "POST",
        {
            "content": "TEST 已准备好演示，加入时设备关闭。",
            "nonce": "acceptance-message-01",
        },
    )
    same = request(
        sa,
        p + "/messages",
        "POST",
        {"content": msg["content"], "nonce": "acceptance-message-01"},
    )
    check(same["id"] == msg["id"], "persistent message deduplication")
    request(
        sa,
        p + "/messages",
        "POST",
        {"content": "TEST changed", "nonce": "acceptance-message-01"},
        409,
    )
    gm = request(
        guest,
        p + "/messages",
        "POST",
        {"content": "TEST 已收到，请开始共享画面。", "nonce": "acceptance-message-02"},
    )
    request(sa, p + "/messages/" + str(gm["id"]), "DELETE", status=403)
    request(admin, p + "/messages/" + str(gm["id"]), "DELETE")
    check(
        request(guest, p + "/messages")[-1]["content"] == "",
        "removed content not returned",
    )
    request(
        guest,
        p + "/messages",
        "POST",
        {"content": "TEST 受邀成员正在等候演示。", "nonce": "acceptance-message-03"},
    )
    request(guest, p + "/hand", "POST", {"raised": True})
    check(request(guest, p)["myAttendee"]["handRaised"], "hand state persisted")
    main = request(
        admin,
        p + "/controls",
        "POST",
        {"version": main["version"], "locked": True, "chatEnabled": False},
    )
    request(sb, p + "/join", "POST", status=409)
    request(
        sa,
        p + "/messages",
        "POST",
        {"content": "TEST", "nonce": "disabled-chat-01"},
        409,
    )
    main = request(
        admin,
        p + "/controls",
        "POST",
        {"version": main["version"], "locked": False, "chatEnabled": True},
    )
    bad = invitation(1)
    request(admin, p + "/invites/" + str(bad["id"]) + "/revoke", "POST")
    request(
        requests.Session(),
        "/guest/join",
        "POST",
        {"code": bad["code"], "displayName": "TEST 无效邀请"},
        409,
    )
    once = invitation(1)
    temp = requests.Session()
    tg = request(
        temp,
        "/guest/join",
        "POST",
        {"code": once["code"], "displayName": "TEST 等候名额"},
    )
    request(
        requests.Session(),
        "/guest/join",
        "POST",
        {"code": once["code"], "displayName": "TEST 超额"},
        409,
    )
    request(admin, p + "/attendees/" + str(tg["id"]), "POST", {"action": "REJECT"})
    # A separate meeting exercises terminal states without ending the media-test fixture.
    ended = request(admin, "/meetings", "POST", plan("TEST 已完成会议"))
    ep = "/meetings/" + str(ended["id"])
    ended = request(
        admin, ep + "/state", "POST", {"version": ended["version"], "status": "LIVE"}
    )
    bjoin = request(sb, ep + "/join", "POST")
    bid = bjoin["myAttendee"]["id"]
    request(admin, ep + "/attendees/" + str(bid), "POST", {"action": "ADMIT"})
    lease = request(sb, ep + "/media", "POST")
    check(
        lease["url"] == "/media" and "secret" not in lease,
        "real restricted media token",
    )
    request(admin, ep + "/attendees/" + str(bid), "POST", {"action": "REMOVE"})
    request(sb, ep + "/join", "POST", status=409)
    request(sb, ep + "/messages", status=403)
    ended = request(
        admin, ep + "/state", "POST", {"version": ended["version"], "status": "ENDED"}
    )
    request(
        admin,
        ep + "/state",
        "POST",
        {"version": ended["version"], "status": "LIVE"},
        409,
    )
    request(guest, ep, status=403)
    request(guest, ep + "/messages", status=403)
    check(len(request(guest, "/meetings")) == 1, "guest only discovers invited meeting")
    check(
        request(admin, ep + "/attendance")[0]["connectedAt"] is None,
        "authorization alone not attendance",
    )
    stats = request(sv, "/stats")
    check(
        stats["meetings"] == 2 and stats["live"] == 1 and stats["ended"] == 1,
        "actual aggregate statistics",
    )
    csv = request(admin, "/reports/meetings.csv")
    check(
        "TEST 远程产品演示" in csv
        and msg["content"] not in csv
        and private_invite["code"] not in csv,
        "export excludes private content and invitations",
    )
    audit = request(admin, "/audit")
    check(any(x["action"] == "ATTENDEE_REMOVE" for x in audit), "real operation audit")
    check(
        not any(msg["content"] in json.dumps(x, ensure_ascii=False) for x in audit),
        "audit excludes message bodies",
    )
    ilist = request(admin, p + "/invites")
    check(
        all("tokenHash" not in i and "code" not in i for i in ilist),
        "invite listing contains no secrets",
    )
    check(
        requests.get(BASE + "/api/media/authorize", timeout=10).status_code == 404,
        "gateway auth is private",
    )
    check(
        requests.get(
            BASE + "/twirp/livekit.RoomService/ListRooms", timeout=10
        ).status_code
        == 404,
        "media administration blocked",
    )
    request(
        admin,
        "/admin/users/" + str(alice["id"]),
        "PUT",
        {**alice, "enabled": False, "password": ""},
    )
    request(sa, p, status=401)
    fresh = {**alice, "version": alice["version"] + 1, "enabled": True, "password": ""}
    request(admin, "/admin/users/" + str(alice["id"]), "PUT", fresh)
    sa = login(alice["username"], password)
    # Preserve only private labelled test state for subsequent media/restore verification.
    state = {
        "base": BASE,
        "meetingId": mid,
        "password": password,
        "users": {"alice": alice, "bob": bob},
        "guest": {
            "id": gid,
            "cookies": requests.utils.dict_from_cookiejar(guest.cookies),
        },
    }
    save_state("private-quality-state.json", state)
    print(
        json.dumps(
            {"passed": passed, "meetingId": mid, "state": "private-quality-state.json"}
        ),
        flush=True,
    )


if __name__ == "__main__":
    main()
