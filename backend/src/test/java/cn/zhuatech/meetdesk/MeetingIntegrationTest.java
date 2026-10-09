// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetdesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;

/** 真实认证、迁移与会议HTTP流程；仅外部SFU调用替身，实际媒体另行独立验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class MeetingIntegrationTest {
  static final String PW = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:meetdesk;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    r.add("meetdesk.admin-password", () -> PW);
    r.add("meetdesk.voice-key", () -> "testing");
    r.add("meetdesk.voice-secret", () -> "TestOnlyMediaSecret01234567890123456789");
    r.add("meetdesk.reconcile-enabled", () -> false);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired Store db;
  @Autowired Bootstrap bootstrap;
  @Autowired TransactionTemplate tx;
  @Autowired MediaService media;
  @MockitoBean MediaGateway rtc;
  @MockitoBean Clock clock;
  final ObjectMapper json = new ObjectMapper();
  MockHttpSession admin, alice, bob;
  long aliceId, bobId;
  final Instant NOW = Instant.parse("2026-10-09T02:00:00Z");

  Map<String, Object> m(Object... v) {
    var b = new LinkedHashMap<String, Object>();
    for (int i = 0; i < v.length; i += 2) b.put(v[i].toString(), v[i + 1]);
    return b;
  }

  MvcResult req(MockHttpSession s, String path, String method, Object body) throws Exception {
    var q =
        switch (method) {
          case "GET" -> get("/api" + path);
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> throw new IllegalArgumentException();
        };
    if (s != null) q.session(s);
    if (!method.equals("GET")) q.with(csrf());
    if (body != null) q.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(q).andReturn();
  }

  JsonNode ok(MockHttpSession s, String p, String verb, Object b) throws Exception {
    var r = req(s, p, verb, b);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(MockHttpSession s, String p, String verb, Object b, int code) throws Exception {
    var r = req(s, p, verb, b);
    assertEquals(code, r.getResponse().getStatus(), r.getResponse().getContentAsString());
  }

  MockHttpSession login(String name) throws Exception {
    var r = req(null, "/auth/login", "POST", m("username", name, "password", PW));
    assertEquals(200, r.getResponse().getStatus());
    return (MockHttpSession) r.getRequest().getSession();
  }

  long user(String name, long role, long org) throws Exception {
    return ok(
            admin,
            "/admin/users",
            "POST",
            m(
                "username",
                name,
                "displayName",
                "TEST " + name,
                "password",
                PW,
                "roleId",
                role,
                "departmentId",
                org,
                "enabled",
                true))
        .path("id")
        .asLong();
  }

  Map<String, Object> plan() {
    return m(
        "title",
        "TEST 演示会议",
        "agenda",
        "TEST 真实会议流程",
        "category",
        "TRAINING",
        "capacity",
        4,
        "durationMinutes",
        60,
        "scheduledAt",
        NOW.plusSeconds(300).toString());
  }

  long meeting() throws Exception {
    return ok(admin, "/meetings", "POST", plan()).path("id").asLong();
  }

  String path(long id) {
    return "/meetings/" + id;
  }

  void start(long id) throws Exception {
    ok(admin, path(id) + "/state", "POST", m("status", "LIVE", "version", 0));
  }

  long request(MockHttpSession s, long id) throws Exception {
    return ok(s, path(id) + "/join", "POST", null).path("myAttendee").path("id").asLong();
  }

  void admit(long id, long aid) throws Exception {
    ok(admin, path(id) + "/attendees/" + aid, "POST", m("action", "ADMIT"));
  }

  String invite(long id, int uses) throws Exception {
    return ok(admin, path(id) + "/invites", "POST", m("maxUses", uses)).path("code").asString();
  }

  MockHttpSession guest(String code) throws Exception {
    var r = req(null, "/guest/join", "POST", m("code", code, "displayName", "TEST 访客"));
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession();
  }

  long aid(MockHttpSession s) throws Exception {
    return ok(s, "/guest/me", "GET", null).path("id").asLong();
  }

  JsonNode updateUser(long uid, boolean enabled, String password) throws Exception {
    var a = tx.execute(st -> db.get(Account.class, uid));
    return ok(
        admin,
        "/admin/users/" + uid,
        "PUT",
        m(
            "username",
            a.username,
            "displayName",
            a.displayName,
            "roleId",
            a.roleId,
            "departmentId",
            a.departmentId,
            "enabled",
            enabled,
            "password",
            password,
            "version",
            a.version));
  }

  @BeforeEach
  void setup() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (var table :
        List.of(
            "media_lease",
            "meeting_message",
            "invitation",
            "attendee",
            "meeting",
            "audit_event",
            "account",
            "role_permission",
            "access_role",
            "permission",
            "nav_menu",
            "system_setting",
            "dictionary_entry",
            "department")) jdbc.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    bootstrap.run(null);
    reset(rtc);
    when(rtc.token(anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn("TEST-ISOLATED-TOKEN");
    when(rtc.credential(any()))
        .thenAnswer(i -> MeetingService.hash(((Account) i.getArgument(0)).passwordHash));
    when(rtc.call(anyString(), anyString(), anyMap()))
        .thenAnswer(i -> Map.of("participants", List.of()));
    admin = login("admin");
    aliceId = user("alice", 4, 1);
    bobId = user("bob", 4, 1);
    alice = login("alice");
    bob = login("bob");
  }

  @Test
  void emptyDatabaseHasRealMenusAndNoMeetings() throws Exception {
    var p = ok(admin, "/guest/identity", "GET", null);
    assertEquals(6, p.path("menus").size());
    assertEquals(0, ok(admin, "/meetings", "GET", null).size());
    assertEquals(5, ok(admin, "/admin/roles", "GET", null).size());
  }

  @Test
  void anonymousCannotListMeetings() throws Exception {
    fail(null, "/meetings", "GET", null, 401);
  }

  @Test
  void anonymousCannotCreateOrReadByIdentifier() throws Exception {
    long id = meeting();
    fail(null, "/meetings", "POST", plan(), 401);
    fail(null, path(id), "GET", null, 401);
  }

  @Test
  void csrfIsRequiredEvenForGuestSubmission() throws Exception {
    assertEquals(
        403,
        mvc.perform(post("/api/guest/join").contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void participantCannotHostOrAdminister() throws Exception {
    fail(alice, "/meetings", "POST", plan(), 403);
    fail(alice, "/admin/users", "GET", null, 403);
  }

  @Test
  void planPersistsAndContainsHostAdmission() throws Exception {
    long id = meeting();
    var d = ok(admin, path(id), "GET", null);
    assertEquals("PLANNED", d.path("status").asString());
    assertEquals("ADMITTED", d.path("myAttendee").path("status").asString());
    assertEquals(1, d.path("admitted").asInt());
    assertEquals("TEST 真实会议流程", d.path("agenda").asString());
  }

  @Test
  void stalePlanVersionCannotOverwrite() throws Exception {
    long id = meeting();
    var b = plan();
    b.put("version", 0);
    b.put("title", "TEST 修改");
    ok(admin, path(id), "PUT", b);
    fail(admin, path(id), "PUT", b, 409);
  }

  @Test
  void invalidScheduleAndCapacityAreRejected() throws Exception {
    var b = plan();
    b.put("capacity", 33);
    fail(admin, "/meetings", "POST", b, 400);
    b = plan();
    b.put("scheduledAt", "invalid");
    fail(admin, "/meetings", "POST", b, 400);
  }

  @Test
  void livePlanCannotBeEdited() throws Exception {
    long id = meeting();
    start(id);
    var b = plan();
    b.put("version", 1);
    fail(admin, path(id), "PUT", b, 409);
  }

  @Test
  void startIsNotRepeatable() throws Exception {
    long id = meeting();
    start(id);
    fail(admin, path(id) + "/state", "POST", m("status", "LIVE", "version", 1), 409);
  }

  @Test
  void cancelledMeetingCannotRestart() throws Exception {
    long id = meeting();
    ok(admin, path(id) + "/state", "POST", m("status", "CANCELLED", "version", 0));
    fail(admin, path(id) + "/state", "POST", m("status", "LIVE", "version", 1), 409);
  }

  @Test
  void endedMeetingCannotRestartOrIssueMedia() throws Exception {
    long id = meeting();
    start(id);
    ok(admin, path(id) + "/state", "POST", m("status", "ENDED", "version", 1));
    fail(admin, path(id) + "/state", "POST", m("status", "LIVE", "version", 2), 409);
    fail(admin, path(id) + "/media", "POST", null, 409);
  }

  @Test
  void waitingAccountHasNeitherChatNorMedia() throws Exception {
    long id = meeting();
    start(id);
    request(alice, id);
    fail(alice, path(id) + "/messages", "GET", null, 403);
    fail(alice, path(id) + "/media", "POST", null, 403);
    fail(alice, path(id) + "/attendees", "GET", null, 403);
  }

  @Test
  void admittedParticipantCanJoinMedia() throws Exception {
    long id = meeting();
    start(id);
    admit(id, request(alice, id));
    var l = ok(alice, path(id) + "/media", "POST", null);
    assertTrue(l.path("identity").asString().startsWith("p-"));
    assertEquals("/media", l.path("url").asString());
  }

  @Test
  void invitationDoesNotAdmitGuest() throws Exception {
    long id = meeting();
    start(id);
    var g = guest(invite(id, 2));
    assertEquals(
        "WAITING", ok(g, path(id), "GET", null).path("myAttendee").path("status").asString());
    fail(g, path(id) + "/messages", "GET", null, 403);
    fail(g, path(id) + "/media", "POST", null, 403);
  }

  @Test
  void admittedGuestCanSendPersistedMessage() throws Exception {
    long id = meeting();
    start(id);
    var g = guest(invite(id, 2));
    admit(id, aid(g));
    var msg =
        ok(
            g,
            path(id) + "/messages",
            "POST",
            m("content", "TEST 访客消息", "nonce", "guest-message-1"));
    assertEquals("TEST 访客消息", msg.path("content").asString());
    assertEquals(1, ok(admin, path(id) + "/messages", "GET", null).size());
  }

  @Test
  void guestCannotAccessAnotherMeeting() throws Exception {
    long id = meeting(), other = meeting();
    var g = guest(invite(id, 2));
    fail(g, path(other), "GET", null, 403);
    assertEquals(1, ok(g, "/meetings", "GET", null).size());
  }

  @Test
  void guestCannotCreateMeetingOrSeeAccountDirectory() throws Exception {
    long id = meeting();
    var g = guest(invite(id, 2));
    fail(g, "/meetings", "POST", plan(), 401);
    fail(g, "/admin/users", "GET", null, 401);
  }

  @Test
  void revokedInvitationCannotCreateSession() throws Exception {
    long id = meeting();
    String code = invite(id, 2);
    long iid = ok(admin, path(id) + "/invites", "GET", null).get(0).path("id").asLong();
    ok(admin, path(id) + "/invites/" + iid + "/revoke", "POST", null);
    fail(null, "/guest/join", "POST", m("code", code, "displayName", "TEST"), 409);
  }

  @Test
  void invitationUsageLimitCannotBeExceeded() throws Exception {
    long id = meeting();
    String code = invite(id, 1);
    guest(code);
    fail(null, "/guest/join", "POST", m("code", code, "displayName", "TEST"), 409);
  }

  @Test
  void expiredInvitationIsRejected() throws Exception {
    long id = meeting();
    String code = invite(id, 1);
    when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(25)));
    fail(null, "/guest/join", "POST", m("code", code, "displayName", "TEST"), 409);
  }

  @Test
  void invitationListingDoesNotExposeRawOrHash() throws Exception {
    long id = meeting();
    String code = invite(id, 2);
    var raw = req(admin, path(id) + "/invites", "GET", null).getResponse().getContentAsString();
    assertFalse(raw.contains(code));
    assertFalse(raw.contains("tokenHash"));
    assertFalse(raw.contains(MeetingService.hash(code)));
  }

  @Test
  void guestSessionCannotConsumeAnotherInvitationRepeatedly() throws Exception {
    long id = meeting();
    String code = invite(id, 2);
    var g = guest(code);
    fail(g, "/guest/join", "POST", m("code", code, "displayName", "TEST"), 409);
    assertEquals(1, ok(admin, path(id) + "/invites", "GET", null).get(0).path("uses").asInt());
  }

  @Test
  void accountInvitationIsIdempotentAndRequiresAdmission() throws Exception {
    long id = meeting();
    String code = invite(id, 2);
    ok(alice, "/meetings/invites/redeem", "POST", m("code", code));
    ok(alice, "/meetings/invites/redeem", "POST", m("code", code));
    assertEquals(1, ok(admin, path(id) + "/invites", "GET", null).get(0).path("uses").asInt());
  }

  @Test
  void roomLockStopsNewRequestsWithoutRevokingExistingAdmission() throws Exception {
    long id = meeting();
    start(id);
    admit(id, request(alice, id));
    ok(admin, path(id) + "/controls", "POST", m("version", 1, "locked", true, "chatEnabled", true));
    fail(bob, path(id) + "/join", "POST", null, 409);
    ok(alice, path(id) + "/media", "POST", null);
  }

  @Test
  void admissionCapacityIsEnforcedServerSide() throws Exception {
    var p = plan();
    p.put("capacity", 2);
    long id = ok(admin, "/meetings", "POST", p).path("id").asLong();
    long a = request(alice, id), b = request(bob, id);
    admit(id, a);
    fail(admin, path(id) + "/attendees/" + b, "POST", m("action", "ADMIT"), 409);
  }

  @Test
  void onlyHostMayApproveParticipants() throws Exception {
    long id = meeting();
    long a = request(alice, id);
    fail(bob, path(id) + "/attendees/" + a, "POST", m("action", "ADMIT"), 403);
  }

  @Test
  void removingHostThroughParticipantControlsIsForbidden() throws Exception {
    long id = meeting();
    long host = ok(admin, path(id), "GET", null).path("myAttendee").path("id").asLong();
    fail(admin, path(id) + "/attendees/" + host, "POST", m("action", "REMOVE"), 409);
  }

  @Test
  void rejectionIsDurableAndCannotBeBypassedWithJoin() throws Exception {
    long id = meeting();
    long a = request(alice, id);
    ok(admin, path(id) + "/attendees/" + a, "POST", m("action", "REJECT"));
    fail(alice, path(id) + "/join", "POST", null, 409);
  }

  @Test
  void removalRevokesMediaAndForbidsRead() throws Exception {
    long id = meeting();
    start(id);
    long a = request(alice, id);
    admit(id, a);
    var l = ok(alice, path(id) + "/media", "POST", null);
    ok(admin, path(id) + "/attendees/" + a, "POST", m("action", "REMOVE"));
    assertEquals(
        Boolean.TRUE,
        tx.execute(st -> db.get(MediaLease.class, l.path("leaseId").asLong()).revoked));
    fail(alice, path(id) + "/messages", "GET", null, 403);
    fail(alice, path(id) + "/join", "POST", null, 409);
  }

  @Test
  void viewOnlyPermissionComesFromHostNotClient() throws Exception {
    long id = meeting();
    start(id);
    long a = request(alice, id);
    admit(id, a);
    ok(admin, path(id) + "/attendees/" + a, "POST", m("action", "SPEAK", "enabled", false));
    ok(alice, path(id) + "/media", "POST", null);
    verify(rtc).token(anyString(), anyString(), anyString(), eq(false));
  }

  @Test
  void oldPublishingTokenCannotReconnectAfterViewOnly() throws Exception {
    long id = meeting();
    start(id);
    long a = request(alice, id);
    admit(id, a);
    var l = ok(alice, path(id) + "/media", "POST", null);
    var lease = tx.execute(st -> db.get(MediaLease.class, l.path("leaseId").asLong()));
    when(rtc.verify("TEST"))
        .thenAnswer(
            i ->
                Map.of(
                    "sub",
                    lease.identity,
                    "video",
                    Map.of(
                        "roomJoin",
                        true,
                        "room",
                        lease.physicalRoom,
                        "canPublish",
                        true,
                        "canPublishData",
                        false)));
    ok(admin, path(id) + "/attendees/" + a, "POST", m("action", "SPEAK", "enabled", false));
    assertEquals(
        403,
        mvc.perform(get("/api/media/authorize").header("X-Media-Token", "TEST"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void allViewOnlyDoesNotSilenceTheHost() throws Exception {
    long id = meeting();
    start(id);
    admit(id, request(alice, id));
    ok(admin, path(id) + "/silence", "POST", null);
    var people = ok(admin, path(id) + "/attendees", "GET", null);
    for (var a : people) assertEquals(a.path("host").asBoolean(), a.path("canSpeak").asBoolean());
  }

  @Test
  void mediaAuthorizationAloneDoesNotCountAttendance() throws Exception {
    long id = meeting();
    start(id);
    ok(admin, path(id) + "/media", "POST", null);
    assertTrue(
        ok(admin, path(id) + "/attendance", "GET", null).get(0).path("connectedAt").isNull());
    assertEquals(0, ok(admin, "/stats", "GET", null).path("connections").asInt());
  }

  @Test
  void sameAttendeeNewDeviceRevokesPreviousLease() throws Exception {
    long id = meeting();
    start(id);
    long first = ok(admin, path(id) + "/media", "POST", null).path("leaseId").asLong();
    ok(admin, path(id) + "/media", "POST", null);
    assertEquals(Boolean.TRUE, tx.execute(st -> db.get(MediaLease.class, first).revoked));
  }

  @Test
  void participantCannotRevokeAnotherDeviceLease() throws Exception {
    long id = meeting();
    start(id);
    long a = request(alice, id);
    admit(id, a);
    var l = ok(admin, path(id) + "/media", "POST", null);
    fail(alice, "/meetings/media/" + l.path("leaseId").asLong() + "/leave", "POST", null, 403);
  }

  @Test
  void leavingImmediatelyRevokesOwnMedia() throws Exception {
    long id = meeting();
    start(id);
    long a = request(alice, id);
    admit(id, a);
    var l = ok(alice, path(id) + "/media", "POST", null);
    ok(alice, path(id) + "/leave", "POST", null);
    assertEquals(
        Boolean.TRUE,
        tx.execute(st -> db.get(MediaLease.class, l.path("leaseId").asLong()).revoked));
  }

  @Test
  void endMarksPendingAndActiveParticipantsTerminal() throws Exception {
    long id = meeting();
    start(id);
    long a = request(alice, id);
    admit(id, a);
    request(bob, id);
    ok(admin, path(id) + "/state", "POST", m("status", "ENDED", "version", 1));
    assertEquals(
        "LEFT", ok(alice, path(id), "GET", null).path("myAttendee").path("status").asString());
    assertEquals(
        "REJECTED", ok(bob, path(id), "GET", null).path("myAttendee").path("status").asString());
  }

  @Test
  void repeatedMessageNonceCreatesOneMessage() throws Exception {
    long id = meeting();
    start(id);
    var b = m("nonce", "same-message-id", "content", "TEST 持久消息");
    var first = ok(admin, path(id) + "/messages", "POST", b);
    assertEquals(first, ok(admin, path(id) + "/messages", "POST", b));
    assertEquals(1, ok(admin, path(id) + "/messages", "GET", null).size());
  }

  @Test
  void nonceWithDifferentBodyCannotOverwrite() throws Exception {
    long id = meeting();
    start(id);
    ok(admin, path(id) + "/messages", "POST", m("nonce", "same-message-id", "content", "TEST A"));
    fail(
        admin,
        path(id) + "/messages",
        "POST",
        m("nonce", "same-message-id", "content", "TEST B"),
        409);
  }

  @Test
  void chatDisableBlocksNewWrites() throws Exception {
    long id = meeting();
    start(id);
    ok(
        admin,
        path(id) + "/controls",
        "POST",
        m("version", 1, "locked", false, "chatEnabled", false));
    fail(admin, path(id) + "/messages", "POST", m("nonce", "message-01", "content", "TEST"), 409);
  }

  @Test
  void participantCannotRemoveSomeoneElseMessage() throws Exception {
    long id = meeting();
    start(id);
    admit(id, request(alice, id));
    long msg =
        ok(admin, path(id) + "/messages", "POST", m("nonce", "message-01", "content", "TEST"))
            .path("id")
            .asLong();
    fail(alice, path(id) + "/messages/" + msg, "DELETE", null, 403);
  }

  @Test
  void removedBodyIsNotReturned() throws Exception {
    long id = meeting();
    start(id);
    long msg =
        ok(
                admin,
                path(id) + "/messages",
                "POST",
                m("nonce", "message-01", "content", "TEST PRIVATE"))
            .path("id")
            .asLong();
    ok(admin, path(id) + "/messages/" + msg, "DELETE", null);
    assertEquals(
        "", ok(admin, path(id) + "/messages", "GET", null).get(0).path("content").asString());
  }

  @Test
  void handCanOnlyBeRaisedByLiveAdmittedParticipant() throws Exception {
    long id = meeting();
    fail(alice, path(id) + "/hand", "POST", m("raised", true), 409);
    start(id);
    long a = request(alice, id);
    fail(alice, path(id) + "/hand", "POST", m("raised", true), 403);
    admit(id, a);
    ok(alice, path(id) + "/hand", "POST", m("raised", true));
    assertTrue(ok(alice, path(id), "GET", null).path("myAttendee").path("handRaised").asBoolean());
  }

  @Test
  void guestLogoutExpiresTicketAndRevokesMedia() throws Exception {
    long id = meeting();
    start(id);
    var g = guest(invite(id, 2));
    admit(id, aid(g));
    long l = ok(g, path(id) + "/media", "POST", null).path("leaseId").asLong();
    ok(g, "/auth/logout", "POST", null);
    assertEquals(Boolean.TRUE, tx.execute(st -> db.get(MediaLease.class, l).revoked));
    fail(null, "/guest/me", "GET", null, 401);
  }

  @Test
  void disabledAccountCannotUseOldSession() throws Exception {
    updateUser(aliceId, false, "");
    fail(alice, "/meetings", "GET", null, 401);
  }

  @Test
  void passwordResetInvalidatesExistingSession() throws Exception {
    updateUser(aliceId, true, "Bb7" + UUID.randomUUID());
    fail(alice, "/meetings", "GET", null, 401);
  }

  @Test
  void lastAdministratorCannotBeDisabled() throws Exception {
    var r =
        req(
            admin,
            "/admin/users/1",
            "PUT",
            m(
                "username",
                "admin",
                "displayName",
                "Admin",
                "roleId",
                1,
                "departmentId",
                1,
                "enabled",
                false,
                "password",
                "",
                "version",
                tx.execute(st -> db.get(Account.class, 1L).version)));
    assertEquals(409, r.getResponse().getStatus());
    ok(admin, "/admin/users", "GET", null);
  }

  @Test
  void reportViewerHasNoChatOrParticipantAccess() throws Exception {
    user("viewer", 5, 1);
    var v = login("viewer");
    long id = meeting();
    ok(v, "/stats", "GET", null);
    fail(v, path(id), "GET", null, 403);
    fail(v, path(id) + "/messages", "GET", null, 403);
  }

  @Test
  void crossOrganizationCannotUseValidInvite() throws Exception {
    long org =
        ok(
                admin,
                "/admin/departments",
                "POST",
                m("name", "TEST other", "zone", "UTC", "enabled", true))
            .path("id")
            .asLong();
    user("outsider", 4, org);
    var o = login("outsider");
    long id = meeting();
    String code = invite(id, 2);
    fail(o, path(id), "GET", null, 403);
    fail(o, "/meetings/invites/redeem", "POST", m("code", code), 403);
    assertEquals(0, ok(o, "/meetings", "GET", null).size());
  }

  @Test
  void csvDoesNotContainMessageOrInviteSecrets() throws Exception {
    long id = meeting();
    start(id);
    String code = invite(id, 2);
    ok(
        admin,
        path(id) + "/messages",
        "POST",
        m("nonce", "message-01", "content", "TEST PRIVATE BODY"));
    String csv =
        req(admin, "/reports/meetings.csv", "GET", null).getResponse().getContentAsString();
    assertTrue(csv.contains("TEST 演示会议"));
    assertFalse(csv.contains("PRIVATE BODY"));
    assertFalse(csv.contains(code));
  }

  @Test
  void meetingDurationIsEnforcedWithoutUi() throws Exception {
    long id = meeting();
    start(id);
    when(clock.instant()).thenReturn(NOW.plusSeconds(3601));
    fail(admin, path(id) + "/media", "POST", null, 409);
    fail(admin, path(id) + "/messages", "POST", m("nonce", "message-01", "content", "TEST"), 409);
  }

  @Test
  void guestTicketHasFiniteLifetime() throws Exception {
    long id = meeting();
    var g = guest(invite(id, 2));
    when(clock.instant()).thenReturn(NOW.plus(Duration.ofHours(9)));
    fail(g, "/guest/me", "GET", null, 401);
  }

  @Test
  void hostMayLeaveMediaAndRejoinWithoutEndingMeeting() throws Exception {
    long id = meeting();
    start(id);
    ok(admin, path(id) + "/media", "POST", null);
    ok(admin, path(id) + "/leave", "POST", null);
    assertEquals(
        "ADMITTED", ok(admin, path(id), "GET", null).path("myAttendee").path("status").asString());
    ok(admin, path(id) + "/media", "POST", null);
  }
}
