// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetdesk;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

/** 真正HS256签名验证及内部媒体HTTP协议测试，不以静态令牌冒充连接。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class MediaGatewayTest {
  final String secret = "TestOnlyGeneratedSecretWith32Characters999";
  final Clock clock = Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneOffset.UTC);
  MediaGateway gateway = new MediaGateway("key", secret, "http://127.0.0.1:1", clock);

  @Test
  void tokenHasRestrictedMeetingSources() {
    var c = gateway.verify(gateway.token("u-1", "test", "meet-1-1", true));
    var v = (Map<?, ?>) c.get("video");
    assertEquals(List.of("microphone", "camera", "screen_share"), v.get("canPublishSources"));
    assertEquals(false, v.get("canPublishData"));
    assertFalse(v.containsKey("roomAdmin"));
  }

  @Test
  void joinExpiryIsShort() {
    var c = gateway.verify(gateway.token("u-1", "test", "meet-1-1", true));
    assertEquals(clock.instant().getEpochSecond() + 45, ((Number) c.get("exp")).longValue());
  }

  @Test
  void tamperedTokenRejected() {
    var t = gateway.token("u-1", "test", "meet-1-1", true);
    assertThrows(Problem.class, () -> gateway.verify(t.substring(1)));
  }

  @Test
  void wrongSecretRejected() {
    var t = gateway.token("u-1", "test", "meet-1-1", true);
    assertThrows(
        Problem.class,
        () -> new MediaGateway("key", secret + "a", "http://localhost", clock).verify(t));
  }

  @Test
  void wrongIssuerRejected() {
    var t = gateway.token("u-1", "test", "meet-1-1", true);
    assertThrows(
        Problem.class,
        () -> new MediaGateway("other", secret, "http://localhost", clock).verify(t));
  }

  @Test
  void expiredTokenRejected() {
    var t = gateway.token("u-1", "test", "meet-1-1", true);
    assertThrows(
        Problem.class,
        () ->
            new MediaGateway(
                    "key", secret, "http://localhost", Clock.offset(clock, Duration.ofSeconds(46)))
                .verify(t));
  }

  @Test
  void malformedTokenRejected() {
    for (var s : List.of("", "a.b.c", "a.b", "a".repeat(6000)))
      assertThrows(Problem.class, () -> gateway.verify(s));
  }

  @Test
  void managementTokenCannotJoin() {
    var t = gateway.sign(Map.of("video", Map.of("roomAdmin", true, "room", "meet-1-1")), 30);
    assertThrows(Problem.class, () -> gateway.verify(t));
  }

  @Test
  void passwordFingerprintDoesNotExposeBcrypt() {
    var a = new Account();
    a.passwordHash = "TEST-BCrypt";
    assertEquals(64, gateway.credential(a).length());
    assertFalse(gateway.credential(a).contains(a.passwordHash));
  }

  @Test
  void weakServerSecretRejected() {
    assertThrows(
        IllegalStateException.class,
        () -> new MediaGateway("key", "short", "http://localhost", clock));
  }

  @Test
  void actualInternalProtocolCarriesRoomAndCapacity() throws Exception {
    var received = new AtomicReference<Map<?, ?>>();
    var authorization = new AtomicReference<String>();
    var server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/twirp/livekit.RoomService/CreateRoom",
        ex -> {
          received.set(new ObjectMapper().readValue(ex.getRequestBody(), Map.class));
          authorization.set(ex.getRequestHeaders().getFirst("Authorization"));
          byte[] bytes = "{}".getBytes();
          ex.sendResponseHeaders(200, bytes.length);
          ex.getResponseBody().write(bytes);
          ex.close();
        });
    server.start();
    try {
      var client =
          new MediaGateway(
              "key", secret, "http://127.0.0.1:" + server.getAddress().getPort(), clock);
      client.create("meet-1-1", 8);
      assertEquals(8, ((Number) received.get().get("max_participants")).intValue());
      assertEquals("meet-1-1", received.get().get("name"));
      assertTrue(authorization.get().startsWith("Bearer "));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void unavailableMediaReturns503() {
    var e = assertThrows(Problem.class, () -> gateway.create("meet-1-1", 8));
    assertEquals(503, e.status);
  }
}
