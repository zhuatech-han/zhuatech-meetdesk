// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetdesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 限时限次邀请，原文不入库。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
public class Invitation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long meetingId;
  @com.fasterxml.jackson.annotation.JsonIgnore public String tokenHash;
  public Instant expiresAt;
  public int maxUses, uses = 0;
  public boolean revoked = false;
}
