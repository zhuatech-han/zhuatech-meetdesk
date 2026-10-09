// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetdesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 独立会议计划与不可逆结束状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
public class Meeting {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long departmentId, hostId;
  public String title, category;

  @Column(length = 2000)
  public String agenda;

  public String status = "PLANNED";
  public Instant scheduledAt, createdAt, startedAt, endedAt;
  public int capacity, durationMinutes;
  public long version = 0, generation = 0;
  public boolean locked = false, chatEnabled = true;
}
