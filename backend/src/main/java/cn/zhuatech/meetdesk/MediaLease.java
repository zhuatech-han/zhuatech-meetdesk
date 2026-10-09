// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetdesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 媒体接入租约与SFU实际观察到的连接时段；发票据不等于实际出席。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
public class MediaLease {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long meetingId, attendeeId;
  public String identity, physicalRoom, endReason;
  @com.fasterxml.jackson.annotation.JsonIgnore public String credential, sessionKey;
  public Instant issuedAt, connectedAt, disconnectedAt;
  public boolean revoked = false, appliedSpeak = true;
}
