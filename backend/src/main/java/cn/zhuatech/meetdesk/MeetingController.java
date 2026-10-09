// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetdesk;

import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 会议及目录接口；访客可到达业务路径但必须逐请求通过会话、会议与准入校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class MeetingController {
  final MeetingService meetings;
  final MediaService media;
  final AdminService admin;
  final Store db;
  final AccessService access;

  public MeetingController(
      MeetingService m, MediaService v, AdminService a, Store d, AccessService x) {
    meetings = m;
    media = v;
    admin = a;
    db = d;
    access = x;
  }

  /** 最小身份查询用于区分失效会话与访客业务错误。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/guest/identity")
  @Transactional(readOnly = true)
  public Object identity() {
    return meetings.signedIn() ? access.profile() : meetings.guestProfile();
  }

  /** 有效访客只读取本人信息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/guest/me")
  public Object guest() {
    return meetings.guestProfile();
  }

  private record Attempt(int count, java.time.Instant until) {}

  final Map<String, Attempt> guestAttempts = new LinkedHashMap<>();

  /** 邀请访问每地址一分钟最多二十次，创建访客不等于准入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/guest/join")
  public synchronized Object guestJoin(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    var now = java.time.Instant.now();
    guestAttempts.entrySet().removeIf(e -> e.getValue().until().isBefore(now));
    var key = r.getRemoteAddr();
    var old = guestAttempts.get(key);
    if (old != null && old.count() >= 20) throw new Problem(429, "GUEST_THROTTLED");
    if (guestAttempts.size() > 2000) guestAttempts.remove(guestAttempts.keySet().iterator().next());
    guestAttempts.put(key, new Attempt(old == null ? 1 : old.count() + 1, now.plusSeconds(60)));
    return meetings.guestJoin(b, r);
  }

  /** 可见会议列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/meetings")
  public Object list() {
    return meetings.list();
  }

  /** 正式账号使用分享代码申请准入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/invites/redeem")
  public Object redeem(@RequestBody Map<String, Object> body) {
    return meetings.redeem(body);
  }

  /** 同范围会议摘要。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/meetings/{id}")
  public Object detail(@PathVariable Long id) {
    return meetings.detail(id);
  }

  /** 主持人创建计划。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings")
  public Object create(@RequestBody Map<String, Object> b) {
    return meetings.create(b);
  }

  /** 版本保护计划编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/meetings/{id}")
  public Object edit(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return meetings.edit(id, b);
  }

  /** 正式开始、结束与取消。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/{id}/state")
  public Object state(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return meetings.state(id, b);
  }

  /** 入会锁和聊天开关。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/{id}/controls")
  public Object controls(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return meetings.controls(id, b);
  }

  /** 正式账号请求等待准入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/{id}/join")
  public Object join(@PathVariable Long id) {
    return meetings.requestJoin(id);
  }

  /** 本人退出准入与媒体。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/{id}/leave")
  public Object leave(@PathVariable Long id) {
    return meetings.leave(id);
  }

  /** 本会成员目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/meetings/{id}/attendees")
  public Object attendees(@PathVariable Long id) {
    return meetings.attendees(id);
  }

  /** 主持人准入、拒绝、移出和观看模式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/{id}/attendees/{aid}")
  public Object attendee(
      @PathVariable Long id, @PathVariable Long aid, @RequestBody Map<String, Object> b) {
    return meetings.attendee(id, aid, b);
  }

  /** 全体只观看/收听。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/{id}/silence")
  public Object silence(@PathVariable Long id) {
    return meetings.silence(id);
  }

  /** 本人举手状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/{id}/hand")
  public Object hand(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return meetings.hand(id, b);
  }

  /** 生成限期限次邀请。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/{id}/invites")
  public Object invite(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return meetings.invitation(id, b);
  }

  /** 不返回邀请原文或摘要。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/meetings/{id}/invites")
  public Object invites(@PathVariable Long id) {
    return meetings.invitations(id);
  }

  /** 撤销邀请。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/{id}/invites/{iid}/revoke")
  public Object revoke(@PathVariable Long id, @PathVariable Long iid) {
    return meetings.revokeInvitation(id, iid);
  }

  /** 后台观测到的实际接入时段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/meetings/{id}/attendance")
  public Object attendance(@PathVariable Long id) {
    return meetings.attendance(id);
  }

  /** 50条消息历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/meetings/{id}/messages")
  public Object messages(
      @PathVariable Long id, @RequestParam(defaultValue = "9223372036854775807") long before) {
    return meetings.messages(id, before);
  }

  /** 持久消息去重。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/{id}/messages")
  public Object send(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return meetings.send(id, b);
  }

  /** 移除本人或被主持人处理的消息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/meetings/{id}/messages/{mid}")
  public Object remove(@PathVariable Long id, @PathVariable Long mid) {
    return meetings.removeMessage(id, mid);
  }

  /** 准入媒体票据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/{id}/media")
  public Object media(@PathVariable Long id, HttpServletRequest r) {
    return media.join(id, r.getSession().getId());
  }

  /** 本人媒体连接撤销。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/meetings/media/{lid}/leave")
  public Object mediaLeave(@PathVariable Long lid) {
    return media.leave(lid);
  }

  /** 网关内部请求，外部Nginx路径返回404。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/media/authorize")
  public Object authorize(@RequestHeader(value = "X-Media-Token", required = false) String token) {
    media.authorize(token);
    return Map.of("ok", true);
  }

  /** 已登录账号会议选项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return meetings.options();
  }

  /** 组织范围统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/stats")
  public Object stats() {
    return meetings.stats();
  }

  /** 无消息与分享凭据的会议台账。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports/meetings.csv")
  public ResponseEntity<String> csv() {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=meetdesk-meetings.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(meetings.csv());
  }

  /** 后台目录读取。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{kind}")
  public Object read(@PathVariable String kind) {
    return kind.equals("options") ? admin.options() : admin.read(kind);
  }

  /** 后台目录新建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{kind}")
  public Object save(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return admin.save(kind, null, b);
  }

  /** 后台目录版本化编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{kind}/{id}")
  public Object editAdmin(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return admin.save(kind, id, b);
  }

  /** 审计依组织过滤；不包含消息、密码和邀请正文。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(e -> access.department(e.departmentId))
        .toList();
  }
}
