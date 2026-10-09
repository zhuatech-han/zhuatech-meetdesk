// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetdesk;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * LiveKit令牌和服务端房间接口；最小摄像头/麦克风/共享权限，密钥不进入浏览器。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
 */
@Service
public class MediaGateway {
  final String key, secret, url;
  final Clock clock;
  final ObjectMapper json = new ObjectMapper();
  final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public MediaGateway(
      @Value("${meetdesk.voice-key}") String k,
      @Value("${meetdesk.voice-secret}") String s,
      @Value("${meetdesk.voice-internal-url}") String u,
      Clock c) {
    key = k;
    secret = s;
    url = u;
    clock = c;
    if (s.length() < 32) throw new IllegalStateException("Voice secret requires 32 characters");
  }

  String b64(byte[] v) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(v);
  }

  byte[] mac(String v) {
    try {
      var m = Mac.getInstance("HmacSHA256");
      m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return m.doFinal(v.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** 密码指纹用服务端密钥再次摘要，不泄露BCrypt。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String credential(Account a) {
    return HexFormat.of().formatHex(mac(a.passwordHash));
  }

  /** 固定HS256，短期加入票据只授予批准的媒体来源，不授予录制、数据或管理权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String token(String identity, String name, String room, boolean canSpeak) {
    var grant = new LinkedHashMap<String, Object>();
    grant.put("room", room);
    grant.put("roomJoin", true);
    grant.put("canPublish", canSpeak);
    grant.put("canSubscribe", true);
    grant.put("canPublishData", false);
    grant.put("canPublishSources", List.of("microphone", "camera", "screen_share"));
    return sign(Map.of("sub", identity, "name", name, "video", grant), 45);
  }

  String sign(Map<String, Object> body, int ttl) {
    var claims = new LinkedHashMap<String, Object>(body);
    claims.put("iss", key);
    claims.put("nbf", clock.instant().getEpochSecond() - 2);
    claims.put("exp", clock.instant().getEpochSecond() + ttl);
    String raw =
        b64(json.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")))
            + "."
            + b64(json.writeValueAsBytes(claims));
    return raw + "." + b64(mac(raw));
  }

  /** 网关只接受自身签名、签发者、有效期和房间加入票据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<?, ?> verify(String token) {
    try {
      if (token == null || token.length() > 5000) throw new IllegalArgumentException();
      var parts = token.split("\\.", -1);
      if (parts.length != 3
          || !MessageDigest.isEqual(
              mac(parts[0] + "." + parts[1]), Base64.getUrlDecoder().decode(parts[2])))
        throw new IllegalArgumentException();
      var head = json.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
      var c = json.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
      if (!"HS256".equals(head.get("alg"))
          || !key.equals(c.get("iss"))
          || !(c.get("exp") instanceof Number e)
          || e.longValue() <= clock.instant().getEpochSecond()
          || !(c.get("video") instanceof Map<?, ?> v)
          || !Boolean.TRUE.equals(v.get("roomJoin"))) throw new IllegalArgumentException();
      return c;
    } catch (Exception e) {
      throw new Problem(401, "MEDIA_DENIED");
    }
  }

  /** 调用自部署LiveKit内部管理接口，不开放浏览器管理入口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<?, ?> call(String method, String room, Map<String, Object> b) {
    try {
      var req =
          HttpRequest.newBuilder(URI.create(url + "/twirp/livekit.RoomService/" + method))
              .timeout(Duration.ofSeconds(4))
              .header(
                  "Authorization",
                  "Bearer "
                      + sign(
                          Map.of(
                              "video",
                              Map.of(
                                  "room",
                                  room,
                                  "roomAdmin",
                                  true,
                                  "roomCreate",
                                  true,
                                  "roomList",
                                  true)),
                          30))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(b)))
              .build();
      var res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() == 404) return Map.of();
      if (res.statusCode() != 200) throw new Problem(503, "MEDIA_UNAVAILABLE");
      return json.readValue(res.body(), Map.class);
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new Problem(503, "MEDIA_UNAVAILABLE");
    }
  }

  /** 房间人数在实际语音服务同样受限，不以页面人数替代。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void create(String room, int capacity) {
    call(
        "CreateRoom",
        room,
        Map.of(
            "name",
            room,
            "max_participants",
            capacity,
            "empty_timeout",
            60,
            "departure_timeout",
            10));
  }

  /** 移除参与者；已离开视为完成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void remove(String room, String identity) {
    call("RemoveParticipant", room, Map.of("room", room, "identity", identity));
  }
}
