// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref, shallowRef } from "vue";
import { api } from "./api.js";
import { t } from "./ui.js";
let library, room, lease, statsTimer;
const selectedInputs = { audioinput: "", videoinput: "" };
export const mediaState = ref("disconnected"),
  participants = shallowRef([]),
  micEnabled = ref(false),
  cameraEnabled = ref(false),
  screenEnabled = ref(false),
  canPublish = ref(true),
  canPlay = ref(true),
  mediaError = ref(""),
  mediaMode = ref("auto"),
  mediaStats = ref({
    audioPackets: 0,
    videoPackets: 0,
    frames: 0,
    relay: false,
  });
const sdk = async () => (library ||= await import("livekit-client"));
function update() {
  if (!room) return;
  const { Track } = library;
  participants.value = [
    room.localParticipant,
    ...room.remoteParticipants.values(),
  ].map((p) => ({
    identity: p.identity,
    name: p.name || p.identity,
    local: p.isLocal,
    speaking: p.isSpeaking,
    muted: !p.isMicrophoneEnabled,
    quality: p.connectionQuality,
    camera: p.isCameraEnabled
      ? p.getTrackPublication(Track.Source.Camera)?.track
      : undefined,
    screen: p.isScreenShareEnabled
      ? p.getTrackPublication(Track.Source.ScreenShare)?.track
      : undefined,
  }));
  micEnabled.value = room.localParticipant.isMicrophoneEnabled;
  cameraEnabled.value = room.localParticipant.isCameraEnabled;
  screenEnabled.value = room.localParticipant.isScreenShareEnabled;
  canPublish.value = room.localParticipant.permissions?.canPublish !== false;
}
/** 准入后连接真实SFU，默认不开设备；媒体元素绑定实际订阅轨道。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function connectMedia(id) {
  await disconnectMedia();
  mediaError.value = "";
  mediaState.value = "connecting";
  try {
    lease = await api(`/meetings/${id}/media`, { method: "POST" });
    const { Room, RoomEvent, Track } = await sdk();
    const next = new Room({
      singlePeerConnection: false,
      adaptiveStream: false,
      dynacast: false,
      audioCaptureDefaults: {
        deviceId: selectedInputs.audioinput || undefined,
      },
      videoCaptureDefaults: {
        deviceId: selectedInputs.videoinput || undefined,
        resolution: { width: 1280, height: 720, frameRate: 24 },
      },
    });
    room = next;
    next.on(RoomEvent.TrackSubscribed, (track) => {
      if (track.kind === Track.Kind.Audio) {
        const e = track.attach();
        e.dataset.meetingAudio = "true";
        document.querySelector("#meeting-audio")?.appendChild(e);
      }
      update();
    });
    next.on(RoomEvent.TrackUnsubscribed, (track) => {
      track.detach().forEach((e) => e.remove());
      update();
    });
    for (const name of [
      "ParticipantConnected",
      "ParticipantDisconnected",
      "TrackMuted",
      "TrackUnmuted",
      "LocalTrackPublished",
      "LocalTrackUnpublished",
      "TrackPublished",
      "TrackUnpublished",
      "ActiveSpeakersChanged",
      "ConnectionQualityChanged",
      "ParticipantPermissionsChanged",
    ])
      next.on(RoomEvent[name], update);
    next.on(
      RoomEvent.AudioPlaybackStatusChanged,
      () => (canPlay.value = next.canPlaybackAudio),
    );
    next.on(RoomEvent.Reconnecting, () => (mediaState.value = "reconnecting"));
    next.on(RoomEvent.Reconnected, () => {
      mediaState.value = "connected";
      update();
    });
    next.on(RoomEvent.Disconnected, () => {
      if (room === next) {
        clearInterval(statsTimer);
        mediaState.value = "disconnected";
        participants.value = [];
        micEnabled.value = false;
        cameraEnabled.value = false;
        screenEnabled.value = false;
        mediaError.value = t(
          "连接已断开，请核对会议状态后重新加入。",
          "Disconnected. Check meeting access and rejoin.",
        );
      }
    });
    const u = new URL(lease.url, location.origin);
    u.protocol = location.protocol === "https:" ? "wss:" : "ws:";
    await next.connect(u.href, lease.token, {
      rtcConfig: {
        iceTransportPolicy: mediaMode.value === "relay" ? "relay" : "all",
      },
    });
    await next.startAudio();
    mediaState.value = "connected";
    update();
    statsTimer = setInterval(async () => {
      let audioPackets = 0,
        videoPackets = 0,
        frames = 0,
        relay = false;
      for (const p of next.remoteParticipants.values())
        for (const pub of p.trackPublications.values()) {
          const stats = await pub.track?.getRTCStatsReport().catch(() => null);
          if (!stats) continue;
          for (const x of stats.values()) {
            if (x.type === "inbound-rtp") {
              if (x.kind === "audio") audioPackets += x.packetsReceived || 0;
              if (x.kind === "video") {
                videoPackets += x.packetsReceived || 0;
                frames += x.framesDecoded || 0;
              }
            }
            if (x.type === "transport" && x.selectedCandidatePairId) {
              const pair = stats.get(x.selectedCandidatePairId);
              if (
                pair &&
                stats.get(pair.localCandidateId)?.candidateType === "relay"
              )
                relay = true;
            }
          }
        }
      mediaStats.value = { audioPackets, videoPackets, frames, relay };
    }, 2000);
  } catch (e) {
    await disconnectMedia();
    if (e.status !== undefined) throw e;
    throw new Error("MEDIA_CONNECT_FAILED");
  }
}
/** 离开释放设备并撤销本人服务端租约，不自动重试提交。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function disconnectMedia() {
  clearInterval(statsTimer);
  const prior = lease;
  lease = null;
  const previous = room;
  room = null;
  if (previous) await previous.disconnect();
  document.querySelectorAll("[data-meeting-audio]").forEach((x) => x.remove());
  mediaState.value = "disconnected";
  participants.value = [];
  micEnabled.value = false;
  cameraEnabled.value = false;
  screenEnabled.value = false;
  canPlay.value = true;
  mediaStats.value = {
    audioPackets: 0,
    videoPackets: 0,
    frames: 0,
    relay: false,
  };
  if (prior)
    try {
      await api(`/meetings/media/${prior.leaseId}/leave`, { method: "POST" });
    } catch {
      /* 持久撤销巡检仍会处理，不保留凭据或重复写入 */
    }
}
/** 只有明确点击才采集，取消屏幕选择或拒绝设备不误标为已开启。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function toggleDevice(kind) {
  if (!room || mediaState.value !== "connected") return;
  mediaError.value = "";
  if (!canPublish.value) {
    mediaError.value = t(
      "主持人已将你切换为只观看。",
      "The host switched you to view-only.",
    );
    return;
  }
  try {
    if (kind === "microphone")
      await room.localParticipant.setMicrophoneEnabled(!micEnabled.value);
    if (kind === "camera")
      await room.localParticipant.setCameraEnabled(!cameraEnabled.value);
    if (kind === "screen")
      await room.localParticipant.setScreenShareEnabled(!screenEnabled.value, {
        audio: false,
      });
    update();
  } catch (e) {
    mediaError.value =
      e.name === "NotAllowedError"
        ? t(
            "未获授权或选择已取消，设备保持关闭。",
            "Permission denied or selection cancelled. Device remains off.",
          )
        : e.name === "NotFoundError"
          ? t(
              "没有找到设备，请连接后重试。",
              "No device found. Connect one and retry.",
            )
          : t(
              "设备无法启用，请检查是否被占用及浏览器权限。",
              "Device unavailable. Check browser permissions and other applications.",
            );
    update();
  }
}
/** 用户点击后解除浏览器自动播放限制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function enablePlayback() {
  if (room) {
    await room.startAudio();
    canPlay.value = room.canPlaybackAudio;
  }
}
/** 不预先请求摄像头/麦克风，名称随浏览器授权情况显示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function devices(kind) {
  const { Room } = await sdk();
  return Room.getLocalDevices(kind, false);
}
/** 切换已选设备，延续开启状态；错误由页面提示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function switchDevice(kind, id) {
  if (room) await room.switchActiveDevice(kind, id);
  selectedInputs[kind] = id;
}
