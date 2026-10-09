// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetdesk;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 仅空库初始化独立管理员与会议目录，不创建虚假参会记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  static final List<String> CODES =
      List.of("join", "host", "moderate", "reports", "users", "roles", "settings", "audit");
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;

  public Bootstrap(
      Store d,
      BCryptPasswordEncoder e,
      @Value("${meetdesk.admin-username}") String u,
      @Value("${meetdesk.admin-password}") String p) {
    db = d;
    encoder = e;
    username = u;
    password = p;
  }

  /** 已有数据不重置账号或权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}"))
      throw new IllegalStateException("Invalid administrator name");
    for (var c : CODES) {
      var p = new Permission();
      p.code = c;
      p.name = c;
      db.save(p);
    }
    var d = new Department();
    d.name = "默认组织 / Main organization";
    d.zone = "Asia/Shanghai";
    db.save(d);
    var ar = role("管理员 / Administrator", "ALL", CODES);
    role(
        "组织会议管理员 / Organization moderator",
        "DEPARTMENT",
        List.of("join", "host", "moderate", "reports", "audit"));
    role("会议主持人 / Host", "DEPARTMENT", List.of("join", "host"));
    role("参会成员 / Attendee", "DEPARTMENT", List.of("join"));
    role("只读统计 / Report viewer", "DEPARTMENT", List.of("reports"));
    var a = new Account();
    a.username = username.toLowerCase(Locale.ROOT);
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = ar.id;
    a.departmentId = d.id;
    db.save(a);
    String[][] menus = {
      {"meetings", "会议", "Meetings", "join"},
      {"dashboard", "会议统计", "Statistics", "reports"},
      {"users", "登录账号", "Accounts", "users"},
      {"roles", "角色与权限", "Roles & permissions", "roles"},
      {"settings", "组织与设置", "Organizations & settings", "settings"},
      {"audit", "操作记录", "Audit trail", "audit"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var x :
        new String[][] {
          {"TEAM", "团队会议", "Team meeting"},
          {"TRAINING", "培训演示", "Training"},
          {"OTHER", "其他", "Other"}
        }) {
      var c = new DictionaryEntry();
      c.type = "CATEGORY";
      c.code = x[0];
      c.name = x[1];
      c.nameEn = x[2];
      db.save(c);
    }
    for (var x :
        new String[][] {
          {"message_limit", "2000"},
          {"meeting_limit", "16"},
          {"invite_hours", "24"},
          {"duration_limit", "120"}
        }) {
      var s = new SystemSetting();
      s.code = x[0];
      s.value = x[1];
      db.save(s);
    }
  }

  private AccessRole role(String n, String s, List<String> p) {
    var r = new AccessRole();
    r.name = n;
    r.scope = s;
    r.permissions.addAll(p);
    return db.save(r);
  }
}
