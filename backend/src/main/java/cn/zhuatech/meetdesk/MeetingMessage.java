// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetdesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 准入参会人的持久文字消息及客户端去重键。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
public class MeetingMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long meetingId, attendeeId;
  public String nonce;

  @Column(length = 4000)
  public String content;

  public Instant createdAt;
  public boolean removed = false;
}
