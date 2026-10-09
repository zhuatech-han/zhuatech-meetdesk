// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetdesk;

import jakarta.servlet.http.HttpServletRequest;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.*;

/** 会议计划、限时访客、等候室和准入闭环；所有越权及状态约束由服务器落实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class MeetingService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final Clock clock;

  public MeetingService(Store d, AccessService a, AdminService s, Clock c) {
    db = d;
    access = a;
    admin = s;
    clock = c;
  }

  /** 分享凭据与会话只存SHA256摘要。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  HttpServletRequest request() {
    return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
        .getRequest();
  }

  String sessionKey() {
    var s = request().getSession(false);
    if (s == null) throw new Problem(401, "UNAUTHENTICATED");
    return hash(s.getId());
  }

  boolean signedIn() {
    var a =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    return a != null && a.isAuthenticated() && !a.getName().equals("anonymousUser");
  }

  Attendee accountAttendee(Long id, Long uid) {
    return db
        .query(Attendee.class, "from Attendee where meetingId=?1 and accountId=?2", id, uid)
        .stream()
        .findFirst()
        .orElse(null);
  }

  Attendee guest() {
    var rows = db.query(Attendee.class, "from Attendee where guestKey=?1", sessionKey());
    if (rows.size() != 1 || !rows.getFirst().expiresAt.isAfter(clock.instant()))
      throw new Problem(401, "UNAUTHENTICATED");
    return rows.getFirst();
  }

  boolean terminal(Meeting m) {
    return Set.of("ENDED", "CANCELLED").contains(m.status);
  }

  boolean manages(Meeting m) {
    return signedIn()
        && access.has("host")
        && access.department(m.departmentId)
        && (m.hostId.equals(access.current().id) || access.has("moderate"));
  }

  Meeting managed(Long id) {
    access.require("host");
    var m = db.lock(Meeting.class, id);
    if (!manages(m)) throw new Problem(403, "FORBIDDEN");
    return m;
  }

  /** 主持人只读查询不申请写锁，兼容 MySQL 只读事务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  Meeting managedRead(Long id) {
    access.require("host");
    var m = db.get(Meeting.class, id);
    if (!manages(m)) throw new Problem(403, "FORBIDDEN");
    return m;
  }

  /** 组织权限或访客有效期变化实时失效，等候者没有读取聊天或媒体的资格。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean eligible(Attendee a, Meeting m) {
    if (!db.get(Department.class, m.departmentId).enabled) return false;
    if (a.accountId == null) return a.expiresAt != null && a.expiresAt.isAfter(clock.instant());
    var u = db.get(Account.class, a.accountId);
    var r = db.get(AccessRole.class, u.roleId);
    return u.enabled
        && db.get(Department.class, u.departmentId).enabled
        && r.permissions.contains("join")
        && (r.scope.equals("ALL") || u.departmentId.equals(m.departmentId));
  }

  /** 当前会议身份独立于其他会议，访客不能借ID进入别的会议。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Attendee actor(Long id) {
    Attendee a;
    if (signedIn()) {
      access.require("join");
      a = accountAttendee(id, access.current().id);
    } else {
      a = guest();
    }
    var m = db.get(Meeting.class, id);
    if (a == null || !a.meetingId.equals(id) || !eligible(a, m))
      throw new Problem(403, "MEETING_FORBIDDEN");
    return a;
  }

  Attendee admitted(Long id) {
    var a = actor(id);
    if (!a.status.equals("ADMITTED")) throw new Problem(403, "ADMISSION_REQUIRED");
    return a;
  }

  Meeting live(Long id) {
    var m = db.get(Meeting.class, id);
    Rules.check(
        m.status.equals("LIVE")
            && m.startedAt.plusSeconds(m.durationMinutes * 60L).isAfter(clock.instant()),
        "MEETING_CLOSED");
    return m;
  }

  void audit(String code, Object id, Meeting m) {
    var e = new AuditEvent();
    e.actor = signedIn() ? access.current().username : "guest:" + guest().id;
    e.action = code;
    e.objectId = String.valueOf(id);
    e.departmentId = m.departmentId;
    e.createdAt = clock.instant();
    db.save(e);
  }

  Map<String, Object> view(Meeting m) {
    var own = signedIn() ? accountAttendee(m.id, access.current().id) : guest();
    var out = new LinkedHashMap<String, Object>();
    out.put("id", m.id);
    out.put("title", m.title);
    out.put("agenda", m.agenda);
    out.put("category", m.category);
    out.put("status", m.status);
    out.put("capacity", m.capacity);
    out.put("durationMinutes", m.durationMinutes);
    out.put("scheduledAt", m.scheduledAt);
    out.put("startedAt", m.startedAt);
    out.put("endedAt", m.endedAt);
    out.put("version", m.version);
    out.put("hostId", m.hostId);
    out.put("hostName", db.get(Account.class, m.hostId).displayName);
    out.put("departmentId", m.departmentId);
    out.put("locked", m.locked);
    out.put("chatEnabled", m.chatEnabled);
    out.put("canManage", manages(m));
    out.put(
        "admitted",
        db.count(
            "select count(a) from Attendee a where a.meetingId=?1 and a.status='ADMITTED'", m.id));
    out.put(
        "waiting",
        manages(m)
            ? db.count(
                "select count(a) from Attendee a where a.meetingId=?1 and a.status='WAITING'", m.id)
            : 0L);
    out.put("myAttendee", own == null ? Map.of() : attendeeView(own, m));
    return out;
  }

  Map<String, Object> attendeeView(Attendee a, Meeting m) {
    var o = new LinkedHashMap<String, Object>();
    o.put("id", a.id);
    o.put(
        "name",
        a.accountId == null ? a.displayName : db.get(Account.class, a.accountId).displayName);
    o.put("guest", a.accountId == null);
    o.put("host", Objects.equals(a.accountId, m.hostId));
    o.put("status", a.status);
    o.put("canSpeak", a.canSpeak);
    o.put("handRaised", a.handRaised);
    o.put("requestedAt", a.requestedAt);
    o.put("admittedAt", a.admittedAt);
    o.put("leftAt", a.leftAt);
    return o;
  }

  /** 账号按组织发现会议；访客只返回当前邀请对应的会议。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list() {
    if (!signedIn()) {
      var a = guest();
      return List.of(view(db.get(Meeting.class, a.meetingId)));
    }
    access.require("join");
    return db.all(Meeting.class).stream()
        .filter(
            m ->
                access.department(m.departmentId)
                    && db.get(Department.class, m.departmentId).enabled)
        .sorted(Comparator.comparing((Meeting m) -> m.scheduledAt).reversed())
        .map(this::view)
        .toList();
  }

  /** 会议详情不授予准入；同组织可申请进入，跨组织须由管理员调整正式账号范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    var m = db.get(Meeting.class, id);
    if (signedIn()) {
      access.require("join");
      if (!access.department(m.departmentId) || !db.get(Department.class, m.departmentId).enabled)
        throw new Problem(403, "MEETING_FORBIDDEN");
    } else actor(id);
    return view(m);
  }

  void fields(Meeting m, Map<String, Object> b) {
    m.title = Rules.text(b.get("title"), 120, true);
    m.agenda = Rules.paragraph(b.get("agenda"), 2000, false);
    m.category = Rules.text(b.get("category"), 60, true);
    Rules.check(
        !db.query(
                DictionaryEntry.class,
                "from DictionaryEntry where type='CATEGORY' and code=?1 and enabled=true",
                m.category)
            .isEmpty(),
        "CATEGORY_DISABLED");
    m.capacity = Rules.integer(b.get("capacity"), 2, admin.setting("meeting_limit"));
    m.durationMinutes =
        Rules.integer(b.get("durationMinutes"), 15, admin.setting("duration_limit"));
    try {
      m.scheduledAt = Instant.parse(Objects.toString(b.get("scheduledAt"), ""));
    } catch (Exception e) {
      throw new Problem(400, "INVALID_INPUT");
    }
    Rules.check(
        m.scheduledAt.isBefore(clock.instant().plus(Duration.ofDays(366)))
            && m.scheduledAt.isAfter(clock.instant().minus(Duration.ofDays(1))),
        "SCHEDULE_INVALID");
  }

  /** 创建只生成计划和主持人身份，不把静态页面当作已开会。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object create(Map<String, Object> b) {
    access.require("host");
    access.require("join");
    admin.lock();
    var m = new Meeting();
    m.departmentId = access.current().departmentId;
    m.hostId = access.current().id;
    m.createdAt = clock.instant();
    fields(m, b);
    db.save(m);
    var a = new Attendee();
    a.meetingId = m.id;
    a.accountId = m.hostId;
    a.displayName = access.current().displayName;
    a.requestedAt = m.createdAt;
    a.admittedAt = m.createdAt;
    a.status = "ADMITTED";
    db.save(a);
    audit("MEETING_CREATED", m.id, m);
    return view(m);
  }

  /** 只编辑未开始计划，保存必须携带当前版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object edit(Long id, Map<String, Object> b) {
    var m = managed(id);
    version(m, b);
    Rules.check(m.status.equals("PLANNED"), "MEETING_STATE");
    fields(m, b);
    m.version++;
    audit("MEETING_UPDATED", id, m);
    return view(m);
  }

  void version(Meeting m, Map<String, Object> b) {
    Rules.check(m.version == Rules.version(b.get("version")), "VERSION_CONFLICT");
  }

  /** 开始、结束或取消不可逆；结束后旧媒体代际立即无效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object state(Long id, Map<String, Object> b) {
    var m = managed(id);
    version(m, b);
    var s = Rules.text(b.get("status"), 20, true);
    Rules.check(
        (m.status.equals("PLANNED") && Set.of("LIVE", "CANCELLED").contains(s))
            || (m.status.equals("LIVE") && s.equals("ENDED")),
        "MEETING_STATE");
    if (s.equals("LIVE")) {
      m.startedAt = clock.instant();
      m.generation++;
    } else end(m, s);
    m.status = s;
    m.version++;
    audit("MEETING_" + s, id, m);
    return view(m);
  }

  /** 超时由巡检结束；保存真实结束时间，保留消息及连接历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void end(Meeting m, String state) {
    m.status = state;
    m.endedAt = clock.instant();
    m.generation++;
    for (var a : db.query(Attendee.class, "from Attendee where meetingId=?1", m.id)) {
      if (a.status.equals("ADMITTED")) {
        a.status = "LEFT";
        a.leftAt = m.endedAt;
      } else if (a.status.equals("WAITING")) a.status = "REJECTED";
      a.handRaised = false;
    }
    revoke(m.id, null, "MEETING_ENDED");
  }

  /** 锁会只限制新的申请；关闭聊天不影响既有音视频。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object controls(Long id, Map<String, Object> b) {
    var m = managed(id);
    version(m, b);
    Rules.check(!terminal(m), "MEETING_CLOSED");
    m.locked = Rules.flag(b.get("locked"));
    m.chatEnabled = Rules.flag(b.get("chatEnabled"));
    m.version++;
    audit("MEETING_CONTROLS", id, m);
    return view(m);
  }

  /** 正式账号申请同组织会议，一律等待主持人准入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object requestJoin(Long id) {
    access.require("join");
    var m = db.lock(Meeting.class, id);
    Rules.check(!terminal(m) && !m.locked, "MEETING_LOCKED");
    if (!access.department(m.departmentId)) throw new Problem(403, "MEETING_FORBIDDEN");
    var a = accountAttendee(id, access.current().id);
    if (a != null) {
      Rules.check(!Set.of("REMOVED", "REJECTED").contains(a.status), "ATTENDEE_REMOVED");
      if (Set.of("WAITING", "ADMITTED").contains(a.status)) return view(m);
    }
    pendingCapacity(m);
    if (a == null) {
      a = new Attendee();
      a.meetingId = id;
      a.accountId = access.current().id;
      a.displayName = access.current().displayName;
      a.requestedAt = clock.instant();
      db.save(a);
    }
    a.status = "WAITING";
    a.requestedAt = clock.instant();
    a.leftAt = null;
    a.canSpeak = true;
    Rules.check(eligible(a, m), "MEETING_FORBIDDEN");
    audit("JOIN_REQUESTED", a.id, m);
    return view(m);
  }

  void pendingCapacity(Meeting m) {
    Rules.check(
        db.count(
                "select count(a) from Attendee a where a.meetingId=?1 and a.status='WAITING'", m.id)
            < m.capacity * 2L,
        "WAITING_FULL");
  }

  /** 访客会话不生成后台账号，邀请码只允许创建等待身份。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object guestJoin(Map<String, Object> b, HttpServletRequest req) {
    Rules.check(!signedIn(), "USE_ACCOUNT_JOIN");
    var code = Rules.text(b.get("code"), 64, true);
    var list = db.query(Invitation.class, "from Invitation where tokenHash=?1", hash(code));
    if (list.isEmpty()) throw new Problem(404, "INVITE_INVALID");
    var inv = list.getFirst();
    var m = db.lock(Meeting.class, inv.meetingId);
    db.refresh(inv);
    Rules.check(
        !inv.revoked
            && inv.uses < inv.maxUses
            && inv.expiresAt.isAfter(clock.instant())
            && !terminal(m)
            && !m.locked
            && db.get(Department.class, m.departmentId).enabled,
        "INVITE_INVALID");
    var session = req.getSession();
    var old = db.query(Attendee.class, "from Attendee where guestKey=?1", hash(session.getId()));
    if (!old.isEmpty()) throw new Problem(409, "GUEST_ALREADY_JOINED");
    pendingCapacity(m);
    req.changeSessionId();
    var a = new Attendee();
    a.meetingId = m.id;
    a.displayName = Rules.text(b.get("displayName"), 60, true);
    a.guestKey = hash(req.getSession().getId());
    a.expiresAt = clock.instant().plus(Duration.ofHours(8));
    a.requestedAt = clock.instant();
    db.save(a);
    inv.uses++;
    audit("GUEST_REQUESTED", a.id, m);
    return guestProfile();
  }

  /** 正式账号兑换邀请仍受本组织范围约束；重复申请不重复扣次数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object redeem(Map<String, Object> b) {
    access.require("join");
    var code = Rules.text(b.get("code"), 64, true);
    var rows = db.query(Invitation.class, "from Invitation where tokenHash=?1", hash(code));
    if (rows.isEmpty()) throw new Problem(404, "INVITE_INVALID");
    var inv = rows.getFirst();
    var m = db.lock(Meeting.class, inv.meetingId);
    db.refresh(inv);
    Rules.check(
        !inv.revoked
            && inv.uses < inv.maxUses
            && inv.expiresAt.isAfter(clock.instant())
            && !terminal(m)
            && !m.locked,
        "INVITE_INVALID");
    if (!access.department(m.departmentId)) throw new Problem(403, "MEETING_FORBIDDEN");
    var a = accountAttendee(m.id, access.current().id);
    boolean already = a != null && Set.of("WAITING", "ADMITTED").contains(a.status);
    var result = requestJoin(m.id);
    if (!already) inv.uses++;
    return result;
  }

  /** 限制访客菜单与组织信息；无有效会话返回401。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object guestProfile() {
    var a = guest();
    return Map.of(
        "id",
        a.id,
        "displayName",
        a.displayName,
        "guest",
        true,
        "meetingId",
        a.meetingId,
        "permissions",
        List.of("join"),
        "menus",
        List.of(Map.of("code", "meetings", "name", "我的会议", "nameEn", "My meeting", "position", 0)));
  }

  /** 主持人决定等候、移出及发布权限，不能自行打开参会者设备。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object attendee(Long id, Long aid, Map<String, Object> b) {
    var m = managed(id);
    Rules.check(!terminal(m), "MEETING_CLOSED");
    var a = db.get(Attendee.class, aid);
    Rules.check(a.meetingId.equals(id) && !Objects.equals(a.accountId, m.hostId), "HOST_PROTECTED");
    var action = Rules.text(b.get("action"), 20, true);
    switch (action) {
      case "ADMIT" -> {
        Rules.check(a.status.equals("WAITING") && eligible(a, m), "ATTENDEE_STATE");
        Rules.check(
            db.count(
                    "select count(a) from Attendee a where a.meetingId=?1 and a.status='ADMITTED'",
                    id)
                < m.capacity,
            "MEETING_FULL");
        a.status = "ADMITTED";
        a.admittedAt = clock.instant();
      }
      case "REJECT" -> {
        Rules.check(a.status.equals("WAITING"), "ATTENDEE_STATE");
        a.status = "REJECTED";
      }
      case "REMOVE" -> {
        Rules.check(a.status.equals("ADMITTED"), "ATTENDEE_STATE");
        a.status = "REMOVED";
        a.leftAt = clock.instant();
        a.handRaised = false;
        revoke(id, aid, "REMOVED");
      }
      case "SPEAK" -> {
        Rules.check(a.status.equals("ADMITTED"), "ATTENDEE_STATE");
        a.canSpeak = Rules.flag(b.get("enabled"));
      }
      default -> throw new Problem(400, "INVALID_INPUT");
    }
    audit("ATTENDEE_" + action, aid, m);
    return attendeeView(a, m);
  }

  /** 全体切为只收听观看，后台巡检更新真实SFU权限，不自动开设备。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object silence(Long id) {
    var m = managed(id);
    live(id);
    for (var a :
        db.query(Attendee.class, "from Attendee where meetingId=?1 and status='ADMITTED'", id))
      if (!Objects.equals(a.accountId, m.hostId)) a.canSpeak = false;
    audit("MEETING_SILENCE", id, m);
    return Map.of("ok", true);
  }

  /** 本人举手是持久状态，已离开或未准入者不能举手。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object hand(Long id, Map<String, Object> b) {
    live(id);
    var a = admitted(id);
    a.handRaised = Rules.flag(b.get("raised"));
    return Map.of("ok", true);
  }

  /** 本人离开撤销所有媒体设备租约；主持人离开不等于结束所有人的会议。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object leave(Long id) {
    var m = db.lock(Meeting.class, id);
    var a = actor(id);
    if (Objects.equals(a.accountId, m.hostId) && !terminal(m)) {
      a.handRaised = false;
      revoke(id, a.id, "LEFT_MEDIA");
      return Map.of("ok", true);
    }
    if (!Set.of("REMOVED", "REJECTED").contains(a.status)) {
      a.status = "LEFT";
      a.leftAt = clock.instant();
      a.handRaised = false;
      revoke(id, a.id, "LEFT");
    }
    return Map.of("ok", true);
  }

  /** 列表仅限本会准入者，主持人另可处理等候队列。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object attendees(Long id) {
    var m = db.get(Meeting.class, id);
    if (!manages(m)) admitted(id);
    return db.query(Attendee.class, "from Attendee where meetingId=?1", id).stream()
        .filter(a -> manages(m) || a.status.equals("ADMITTED"))
        .map(a -> attendeeView(a, m))
        .toList();
  }

  /** 邀请原文仅创建时返回，列表不返回摘要或原文。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object invitation(Long id, Map<String, Object> b) {
    var m = managed(id);
    Rules.check(!terminal(m), "MEETING_CLOSED");
    Rules.check(
        db.count(
                "select count(i) from Invitation i where i.meetingId=?1 and i.revoked=false and i.expiresAt>?2",
                id,
                clock.instant())
            < 10,
        "INVITE_LIMIT");
    var x = new Invitation();
    x.meetingId = id;
    x.maxUses = Rules.integer(b.get("maxUses"), 1, 64);
    x.expiresAt = clock.instant().plusSeconds(admin.setting("invite_hours") * 3600L);
    byte[] bytes = new byte[24];
    new SecureRandom().nextBytes(bytes);
    var code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    x.tokenHash = hash(code);
    db.save(x);
    audit("INVITE_CREATED", x.id, m);
    return Map.of("id", x.id, "code", code, "expiresAt", x.expiresAt);
  }

  /** 主持人查看邀请使用量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object invitations(Long id) {
    managedRead(id);
    return db.query(Invitation.class, "from Invitation where meetingId=?1", id);
  }

  /** 撤销只阻止新使用，不冒充移出已批准参会者。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object revokeInvitation(Long id, Long iid) {
    var m = managed(id);
    var x = db.get(Invitation.class, iid);
    Rules.check(x.meetingId.equals(id), "INVITE_INVALID");
    x.revoked = true;
    audit("INVITE_REVOKED", iid, m);
    return Map.of("ok", true);
  }

  /** 服务端撤销落库先于媒体移除，故障时巡检继续重试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void revoke(Long mid, Long aid, String reason) {
    for (var l :
        db.query(MediaLease.class, "from MediaLease where meetingId=?1 and revoked=false", mid))
      if (aid == null || aid.equals(l.attendeeId)) {
        l.revoked = true;
        l.endReason = reason;
      }
  }

  /** 最近50条游标读取；等候室无法读取正文。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object messages(Long id, long before) {
    admitted(id);
    return db.messages(id, before).reversed().stream().map(this::messageView).toList();
  }

  Map<String, Object> messageView(MeetingMessage x) {
    var a = db.get(Attendee.class, x.attendeeId);
    var m = db.get(Meeting.class, x.meetingId);
    return Map.of(
        "id",
        x.id,
        "attendeeId",
        a.id,
        "author",
        attendeeView(a, m).get("name"),
        "content",
        x.removed ? "" : x.content,
        "removed",
        x.removed,
        "createdAt",
        x.createdAt);
  }

  /** 持久消息串行去重，关闭聊天或结束会议不能发送。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object send(Long id, Map<String, Object> b) {
    db.lock(Meeting.class, id);
    var m = live(id);
    var a = admitted(id);
    Rules.check(m.chatEnabled, "CHAT_DISABLED");
    var nonce = Rules.text(b.get("nonce"), 40, true);
    Rules.check(nonce.matches("[A-Za-z0-9-]{8,40}"), "INVALID_INPUT");
    var body = Rules.paragraph(b.get("content"), admin.setting("message_limit"), true);
    var prior =
        db.query(
            MeetingMessage.class,
            "from MeetingMessage where meetingId=?1 and attendeeId=?2 and nonce=?3",
            id,
            a.id,
            nonce);
    if (!prior.isEmpty()) {
      Rules.check(
          !prior.getFirst().removed && body.equals(prior.getFirst().content), "NONCE_CONFLICT");
      return messageView(prior.getFirst());
    }
    var x = new MeetingMessage();
    x.meetingId = id;
    x.attendeeId = a.id;
    x.nonce = nonce;
    x.content = body;
    x.createdAt = clock.instant();
    db.save(x);
    return messageView(x);
  }

  /** 本人或主持人移除正文并留下审计，不暴露原始内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object removeMessage(Long id, Long messageId) {
    var a = admitted(id);
    var m = db.get(Meeting.class, id);
    var x = db.get(MeetingMessage.class, messageId);
    if (!x.meetingId.equals(id) || (!x.attendeeId.equals(a.id) && !manages(m)))
      throw new Problem(403, "FORBIDDEN");
    x.content = "";
    x.removed = true;
    audit("MESSAGE_REMOVED", messageId, m);
    return Map.of("ok", true);
  }

  /** 会议目录与可创建上限不包含私有账号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("join");
    return Map.of(
        "categories",
        db.all(DictionaryEntry.class).stream().filter(x -> x.enabled).toList(),
        "capacity",
        admin.setting("meeting_limit"),
        "duration",
        admin.setting("duration_limit"),
        "messageLimit",
        admin.setting("message_limit"));
  }

  /** 实际连接记录来自媒体服务观察，不把批准时间充当出席时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object attendance(Long id) {
    var m = managedRead(id);
    return db.query(MediaLease.class, "from MediaLease where meetingId=?1", id).stream()
        .map(
            l -> {
              var o = new LinkedHashMap<String, Object>();
              o.put("id", l.id);
              o.put("name", attendeeView(db.get(Attendee.class, l.attendeeId), m).get("name"));
              o.put("issuedAt", l.issuedAt);
              o.put("connectedAt", l.connectedAt);
              o.put("disconnectedAt", l.disconnectedAt);
              o.put("reason", l.endReason);
              return o;
            })
        .toList();
  }

  /** 范围指标不读取聊天正文或把接入授权计为实际连接。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object stats() {
    access.require("reports");
    var meetings =
        db.all(Meeting.class).stream().filter(m -> access.department(m.departmentId)).toList();
    return Map.of(
        "meetings",
        meetings.size(),
        "live",
        meetings.stream().filter(m -> m.status.equals("LIVE")).count(),
        "ended",
        meetings.stream().filter(m -> m.status.equals("ENDED")).count(),
        "connections",
        meetings.stream()
            .mapToLong(
                m ->
                    db.count(
                        "select count(l) from MediaLease l where l.meetingId=?1 and l.connectedAt is not null",
                        m.id))
            .sum());
  }

  /** 导出会议状态及连接数，无消息正文或任何分享凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String csv() {
    access.require("reports");
    var o =
        new StringBuilder(
            "id,title,status,scheduled_at,started_at,ended_at,observed_connections\r\n");
    for (var m : db.all(Meeting.class))
      if (access.department(m.departmentId))
        o.append(m.id)
            .append(',')
            .append(Rules.csv(m.title))
            .append(',')
            .append(m.status)
            .append(',')
            .append(m.scheduledAt)
            .append(',')
            .append(m.startedAt == null ? "" : m.startedAt)
            .append(',')
            .append(m.endedAt == null ? "" : m.endedAt)
            .append(',')
            .append(
                db.count(
                    "select count(l) from MediaLease l where l.meetingId=?1 and l.connectedAt is not null",
                    m.id))
            .append("\r\n");
    return o.toString();
  }
}
