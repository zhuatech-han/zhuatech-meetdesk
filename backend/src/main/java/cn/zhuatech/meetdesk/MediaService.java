// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetdesk;

import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 媒体鉴权、持续撤销、发布权限同步与实际连接时段观察。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class MediaService {
  final Store db;
  final MeetingService meetings;
  final MediaGateway rtc;
  final Clock clock;

  @org.springframework.beans.factory.annotation.Value("${meetdesk.reconcile-enabled:true}")
  boolean reconcileEnabled;

  public MediaService(Store d, MeetingService m, MediaGateway r, Clock c) {
    db = d;
    meetings = m;
    rtc = r;
    clock = c;
  }

  String credential(Attendee a) {
    return a.accountId == null ? a.guestKey : rtc.credential(db.get(Account.class, a.accountId));
  }

  boolean valid(MediaLease l) {
    var a = db.get(Attendee.class, l.attendeeId);
    var m = db.get(Meeting.class, l.meetingId);
    return !l.revoked
        && l.disconnectedAt == null
        && a.status.equals("ADMITTED")
        && m.status.equals("LIVE")
        && m.startedAt.plusSeconds(m.durationMinutes * 60L).isAfter(clock.instant())
        && meetings.eligible(a, m)
        && l.credential.equals(credential(a))
        && l.physicalRoom.equals("meet-" + m.id + "-" + m.generation);
  }

  /** 只有实时有效的准入者获得媒体权限，同一成员新设备替换旧租约。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object join(Long id, String session) {
    var m = db.lock(Meeting.class, id);
    meetings.live(id);
    var a = meetings.admitted(id);
    meetings.revoke(id, a.id, "REPLACED");
    String physical = "meet-" + id + "-" + m.generation;
    rtc.create(physical, m.capacity);
    var l = new MediaLease();
    l.meetingId = id;
    l.attendeeId = a.id;
    l.identity = "p-" + a.id + "-" + UUID.randomUUID();
    l.physicalRoom = physical;
    l.credential = credential(a);
    l.sessionKey = MeetingService.hash(session);
    l.issuedAt = clock.instant();
    l.appliedSpeak = a.canSpeak;
    db.save(l);
    meetings.audit("MEDIA_AUTHORIZED", l.id, m);
    return Map.of(
        "url",
        "/media",
        "token",
        rtc.token(l.identity, a.displayName, physical, a.canSpeak),
        "leaseId",
        l.id,
        "identity",
        l.identity);
  }

  /**
   * WebSocket每次重连核对最新准入和发布权，旧可发言票据不能绕过主持人禁发。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
   */
  @Transactional(readOnly = true)
  public void authorize(String token) {
    var c = rtc.verify(token);
    var list = db.query(MediaLease.class, "from MediaLease where identity=?1", c.get("sub"));
    if (list.size() != 1) throw new Problem(403, "MEDIA_DENIED");
    var l = list.getFirst();
    var grant = (Map<?, ?>) c.get("video");
    var a = db.get(Attendee.class, l.attendeeId);
    if (!valid(l)
        || !l.physicalRoom.equals(grant.get("room"))
        || !Boolean.valueOf(a.canSpeak).equals(grant.get("canPublish"))
        || Boolean.TRUE.equals(grant.get("roomAdmin"))
        || Boolean.TRUE.equals(grant.get("canPublishData"))) throw new Problem(403, "MEDIA_DENIED");
  }

  /** 本人离开撤销租约；巡检负责确认实际SFU移除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object leave(Long id) {
    var l = db.get(MediaLease.class, id);
    if (!meetings.actor(l.meetingId).id.equals(l.attendeeId)) throw new Problem(403, "FORBIDDEN");
    l.revoked = true;
    l.endReason = "LEFT_MEDIA";
    return Map.of("ok", true);
  }

  /** 退出只撤销当前会话，也让匿名访客票据到期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void logout(String session) {
    var key = MeetingService.hash(session);
    for (var l :
        db.query(MediaLease.class, "from MediaLease where sessionKey=?1 and revoked=false", key)) {
      l.revoked = true;
      l.endReason = "LOGOUT";
    }
    for (var a : db.query(Attendee.class, "from Attendee where guestKey=?1", key)) {
      a.expiresAt = clock.instant();
      if (Set.of("WAITING", "ADMITTED").contains(a.status)) {
        a.status = "LEFT";
        a.leftAt = clock.instant();
      }
      meetings.revoke(a.meetingId, a.id, "LOGOUT");
    }
  }

  /** 单实例两秒巡检；服务故障保留撤销和未应用权限，恢复后继续，不虚报成功。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Scheduled(fixedDelay = 2000)
  public void reconcile() {
    if (!reconcileEnabled) return;
    for (var m : db.query(Meeting.class, "from Meeting where status='LIVE'"))
      if (!m.startedAt.plusSeconds(m.durationMinutes * 60L).isAfter(clock.instant())) {
        db.lock(Meeting.class, m.id);
        meetings.end(m, "ENDED");
        m.version++;
      }
    var groups = new LinkedHashMap<String, List<MediaLease>>();
    for (var l : db.query(MediaLease.class, "from MediaLease where disconnectedAt is null"))
      groups.computeIfAbsent(l.physicalRoom, k -> new ArrayList<>()).add(l);
    for (var group : groups.entrySet()) {
      Map<?, ?> response;
      try {
        response = rtc.call("ListParticipants", group.getKey(), Map.of("room", group.getKey()));
      } catch (Problem e) {
        continue;
      }
      var participants = response.get("participants") instanceof List<?> p ? p : List.of();
      for (var l : group.getValue()) {
        try {
          if (!valid(l)) {
            rtc.remove(l.physicalRoom, l.identity);
            l.revoked = true;
            l.disconnectedAt = clock.instant();
            if (l.endReason == null) l.endReason = "ACCESS_REVOKED";
            continue;
          }
          boolean present =
              participants.stream()
                  .anyMatch(p -> p instanceof Map<?, ?> x && l.identity.equals(x.get("identity")));
          if (present) {
            if (l.connectedAt == null) l.connectedAt = clock.instant();
            var a = db.get(Attendee.class, l.attendeeId);
            if (l.appliedSpeak != a.canSpeak) {
              rtc.call(
                  "UpdateParticipant",
                  l.physicalRoom,
                  Map.of(
                      "room",
                      l.physicalRoom,
                      "identity",
                      l.identity,
                      "permission",
                      Map.of(
                          "canSubscribe",
                          true,
                          "canPublish",
                          a.canSpeak,
                          "canPublishData",
                          false,
                          "canPublishSources",
                          List.of(1, 2, 3))));
              l.appliedSpeak = a.canSpeak;
            }
          } else if (l.issuedAt.plusSeconds(50).isBefore(clock.instant())) {
            l.revoked = true;
            l.disconnectedAt = clock.instant();
            l.endReason = l.connectedAt == null ? "NOT_CONNECTED" : "CONNECTION_CLOSED";
          }
        } catch (Problem e) {
          /* 保留本条未完成处理，下次继续 */
        }
      }
    }
  }
}
