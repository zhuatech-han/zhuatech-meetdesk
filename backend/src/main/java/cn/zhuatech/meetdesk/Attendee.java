// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetdesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 会议准入与访客会话摘要；姓名为显示名称，不声称实名验证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
public class Attendee {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long meetingId, accountId;
  public String displayName, status = "WAITING";
  @com.fasterxml.jackson.annotation.JsonIgnore public String guestKey;
  public Instant requestedAt, admittedAt, leftAt, expiresAt;
  public boolean canSpeak = true, handRaised = false;
}
