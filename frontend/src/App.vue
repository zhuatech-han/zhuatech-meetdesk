<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import {
  CalendarDays,
  Video,
  Mic,
  MicOff,
  VideoOff,
  MonitorUp,
  Users,
  MessageSquare,
  ShieldCheck,
  Settings,
  BarChart3,
  ScrollText,
  LogOut,
  Plus,
  Search,
  X,
  ArrowLeft,
  LockKeyhole,
  Link,
  Hand,
  Info,
  Check,
  ChevronLeft,
  ChevronRight,
} from "@lucide/vue";
import { api, resetApi, download } from "./api.js";
import {
  t,
  language,
  errorMessage,
  dateTime,
  ask,
  confirmation,
  answerConfirmation,
  actionName,
  connectionReason,
} from "./ui.js";
import {
  mediaState,
  participants,
  micEnabled,
  cameraEnabled,
  screenEnabled,
  canPublish,
  canPlay,
  mediaError,
  mediaStats,
  mediaMode,
  connectMedia,
  disconnectMedia,
  toggleDevice,
  enablePlayback,
  devices,
  switchDevice,
} from "./media.js";
import AdminPanel from "./components/AdminPanel.vue";
import VideoTile from "./components/VideoTile.vue";
const profile = ref(null),
  ready = ref(false),
  pending = ref(false),
  message = ref(""),
  page = ref("meetings"),
  adminPanel = ref(null),
  meetings = ref([]),
  selected = ref(null),
  attendees = ref([]),
  messages = ref([]),
  metrics = ref({}),
  audit = ref([]),
  options = ref({
    categories: [],
    capacity: 16,
    duration: 120,
    messageLimit: 2000,
  });
const login = ref({ username: "", password: "" }),
  guestName = ref(""),
  entryMode = ref("account"),
  query = ref(""),
  statusFilter = ref(""),
  sort = ref("new"),
  listPage = ref(1),
  side = ref("people"),
  compose = ref(""),
  dialog = ref(null),
  meetingForm = ref(null),
  formBaseline = ref(""),
  inviteCode = ref(""),
  invites = ref([]),
  inviteUses = ref(4),
  createdInvite = ref(""),
  attendance = ref([]),
  deviceLists = ref({ audioinput: [], videoinput: [] }),
  chosenMic = ref(""),
  chosenCamera = ref("");
let timer,
  refreshing = false;
const navIcons = {
  meetings: CalendarDays,
  dashboard: BarChart3,
  users: Users,
  roles: ShieldCheck,
  settings: Settings,
  audit: ScrollText,
};
const stateName = (s) =>
  ({
    PLANNED: t("待开始", "Planned"),
    LIVE: t("进行中", "Live"),
    ENDED: t("已结束", "Ended"),
    CANCELLED: t("已取消", "Cancelled"),
    WAITING: t("等候准入", "Waiting"),
    ADMITTED: t("已准入", "Admitted"),
    LEFT: t("已离开", "Left"),
    REMOVED: t("已移出", "Removed"),
    REJECTED: t("未获准入", "Not admitted"),
  })[s] || s;
const me = computed(() => selected.value?.myAttendee || {}),
  canManage = computed(() => !!selected.value?.canManage),
  connected = computed(() => mediaState.value === "connected"),
  terminal = computed(() =>
    ["ENDED", "CANCELLED"].includes(selected.value?.status),
  );
const filtered = computed(() =>
  meetings.value
    .filter(
      (m) =>
        (!statusFilter.value || m.status === statusFilter.value) &&
        [m.title, m.agenda, m.hostName]
          .join(" ")
          .toLowerCase()
          .includes(query.value.toLowerCase()),
    )
    .sort((a, b) =>
      sort.value === "title"
        ? a.title.localeCompare(b.title)
        : new Date(b.scheduledAt) - new Date(a.scheduledAt),
    ),
);
const visible = computed(() =>
  filtered.value.slice((listPage.value - 1) * 10, listPage.value * 10),
);
const waiting = computed(() =>
    attendees.value.filter((a) => a.status === "WAITING"),
  ),
  active = computed(() =>
    attendees.value.filter((a) => a.status === "ADMITTED"),
  ),
  screens = computed(() => participants.value.filter((p) => p.screen));
const menus = computed(() =>
  (profile.value?.menus || []).slice().sort((a, b) => a.position - b.position),
);
/** 统一写操作不重复重放；成功后才更新页面。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function perform(path, method = "POST", body) {
  if (pending.value) return null;
  pending.value = true;
  message.value = "";
  try {
    return await api(path, { method, body });
  } catch (e) {
    handleError(e);
    return null;
  } finally {
    pending.value = false;
  }
}
function handleError(e) {
  message.value = errorMessage(e);
  if (e.status === 401 && profile.value) {
    profile.value = null;
    selected.value = null;
    resetApi();
    void disconnectMedia();
  }
}
async function loadIdentity() {
  try {
    profile.value = await api("/guest/identity");
    page.value = profile.value.menus[0]?.code || "meetings";
    if (profile.value.permissions.includes("join")) await loadMeetings();
    if (!profile.value.guest && profile.value.permissions.includes("join"))
      options.value = await api("/options");
    if (profile.value.guest) {
      await chooseMeeting(profile.value.meetingId);
    } else if (inviteCode.value) {
      dialog.value = "joinInvite";
    }
    if (page.value !== "meetings") await loadPage();
  } catch (e) {
    if (e.status !== 401) handleError(e);
  } finally {
    ready.value = true;
  }
}
async function signIn() {
  const r = await perform("/auth/login", "POST", login.value);
  login.value.password = "";
  if (r) {
    resetApi();
    await loadIdentity();
  }
}
async function joinGuest() {
  const r = await perform("/guest/join", "POST", {
    code: inviteCode.value.trim(),
    displayName: guestName.value,
  });
  if (r) {
    resetApi();
    inviteCode.value = "";
    await loadIdentity();
  }
}
async function signOut() {
  if (
    !(await ask(
      t(
        "退出登录并断开本次媒体连接？",
        "Sign out and disconnect this session?",
      ),
    ))
  )
    return;
  await disconnectMedia();
  const r = await perform("/auth/logout");
  if (r) {
    profile.value = null;
    selected.value = null;
    dialog.value = null;
    meetings.value = [];
    resetApi();
  }
}
async function loadMeetings() {
  meetings.value = await api("/meetings");
  if (listPage.value > Math.max(1, Math.ceil(filtered.value.length / 10)))
    listPage.value = 1;
}
async function loadPage() {
  try {
    if (page.value === "dashboard") metrics.value = await api("/stats");
    if (page.value === "audit")
      audit.value = (await api("/audit")).reverse().slice(0, 200);
    if (page.value === "meetings") await loadMeetings();
  } catch (e) {
    handleError(e);
  }
}
async function switchPage(code) {
  if (pending.value) return;
  if (adminPanel.value && !(await adminPanel.value.canLeave())) return;
  if (
    selected.value &&
    mediaState.value !== "disconnected" &&
    !(await ask(
      t(
        "离开会议页面会断开媒体，继续？",
        "Leaving this page disconnects media. Continue?",
      ),
    ))
  )
    return;
  await disconnectMedia();
  selected.value = null;
  page.value = code;
  message.value = "";
  await loadPage();
}
async function chooseMeeting(id) {
  if (pending.value) return;
  try {
    if (selected.value?.id !== id) {
      await disconnectMedia();
      messages.value = [];
      compose.value = "";
      attendees.value = [];
    }
    selected.value = await api(`/meetings/${id}`);
    side.value = "people";
    await refreshMeeting();
  } catch (e) {
    handleError(e);
  }
}
async function refreshMeeting() {
  if (!selected.value || refreshing || pending.value) return;
  refreshing = true;
  const id = selected.value.id;
  try {
    const m = await api(`/meetings/${id}`);
    if (selected.value?.id !== id) return;
    selected.value = m;
    if (m.canManage || m.myAttendee.status === "ADMITTED") {
      const rows = await api(`/meetings/${id}/attendees`);
      if (selected.value?.id !== id) return;
      attendees.value = rows;
    } else attendees.value = [];
    if (m.myAttendee.status === "ADMITTED") {
      const next = await api(`/meetings/${id}/messages`);
      if (selected.value?.id === id) {
        const seen = new Set(messages.value.map((x) => x.id));
        messages.value = [
          ...messages.value.filter((x) => x.id < next[0]?.id),
          ...next,
        ];
        if (next.some((x) => !seen.has(x.id))) await Promise.resolve();
      }
    } else {
      messages.value = [];
      compose.value = "";
      if (mediaState.value !== "disconnected") await disconnectMedia();
    }
    if (m.status !== "LIVE" && mediaState.value !== "disconnected")
      await disconnectMedia();
  } catch (e) {
    handleError(e);
  } finally {
    refreshing = false;
  }
}
async function backToMeetings() {
  await disconnectMedia();
  selected.value = null;
  messages.value = [];
  compose.value = "";
  await loadMeetings();
}
async function editMeeting(m = null) {
  if (pending.value) return;
  pending.value = true;
  try {
    options.value = await api("/options");
    meetingForm.value = m
      ? {
          ...m,
          scheduledAt: new Date(
            new Date(m.scheduledAt).getTime() -
              new Date(m.scheduledAt).getTimezoneOffset() * 60000,
          )
            .toISOString()
            .slice(0, 16),
        }
      : {
          title: "",
          agenda: "",
          category: options.value.categories[0]?.code || "TEAM",
          capacity: Math.min(8, options.value.capacity),
          durationMinutes: Math.min(60, options.value.duration),
          scheduledAt: new Date(
            Date.now() + 15 * 60000 - new Date().getTimezoneOffset() * 60000,
          )
            .toISOString()
            .slice(0, 16),
        };
    formBaseline.value = JSON.stringify(meetingForm.value);
    dialog.value = "meeting";
  } catch (e) {
    handleError(e);
  } finally {
    pending.value = false;
  }
}
async function saveMeeting() {
  const f = {
    ...meetingForm.value,
    scheduledAt: new Date(meetingForm.value.scheduledAt).toISOString(),
  };
  const r = await perform(
    `/meetings${f.id ? "/" + f.id : ""}`,
    f.id ? "PUT" : "POST",
    f,
  );
  if (r) {
    dialog.value = null;
    selected.value = r;
    await loadMeetings();
    await refreshMeeting();
  }
}
async function closeDialog() {
  if (pending.value) return;
  if (
    dialog.value === "meeting" &&
    JSON.stringify(meetingForm.value) !== formBaseline.value &&
    !(await ask(
      t("放弃尚未保存的会议计划？", "Discard the unsaved meeting plan?"),
    ))
  )
    return;
  dialog.value = null;
  createdInvite.value = "";
}
async function changeState(status) {
  const text =
    status === "ENDED"
      ? t(
          "结束会议会移出所有媒体连接，且不能重新开始。确认结束？",
          "Ending disconnects everyone and cannot be reversed. End meeting?",
        )
      : status === "CANCELLED"
        ? t(
            "取消此会议计划？取消后不能重新开始。",
            "Cancel this plan? It cannot be restarted.",
          )
        : t(
            "开始会议并开放已批准成员的音视频接入？",
            "Start this meeting and allow admitted participants to connect?",
          );
  if (!(await ask(text))) return;
  const r = await perform(`/meetings/${selected.value.id}/state`, "POST", {
    status,
    version: selected.value.version,
  });
  if (r) {
    selected.value = r;
    await refreshMeeting();
  }
}
async function control(key) {
  const m = selected.value;
  const r = await perform(`/meetings/${m.id}/controls`, "POST", {
    version: m.version,
    locked: m.locked,
    chatEnabled: m.chatEnabled,
    [key]: !m[key],
  });
  if (r) selected.value = r;
}
async function requestJoin() {
  const r = await perform(`/meetings/${selected.value.id}/join`);
  if (r) {
    selected.value = r;
    await refreshMeeting();
  }
}
async function joinInvite() {
  const r = await perform("/meetings/invites/redeem", "POST", {
    code: inviteCode.value.trim(),
  });
  if (r) {
    inviteCode.value = "";
    dialog.value = null;
    await loadMeetings();
    await chooseMeeting(r.id);
  }
}
async function enterMedia() {
  if (pending.value) return;
  pending.value = true;
  message.value = "";
  try {
    await connectMedia(selected.value.id);
  } catch (e) {
    handleError(e);
  } finally {
    pending.value = false;
  }
}
async function deviceAction(kind) {
  if (pending.value) return;
  pending.value = true;
  try {
    await toggleDevice(kind);
  } finally {
    pending.value = false;
  }
}
async function leaveMeeting() {
  if (
    !(await ask(
      t("离开会议并停止媒体？", "Leave this meeting and stop media?"),
    ))
  )
    return;
  await disconnectMedia();
  const r = await perform(`/meetings/${selected.value.id}/leave`);
  if (r) await refreshMeeting();
}
async function attendeeAction(a, action, enabled) {
  if (
    ["REMOVE", "REJECT"].includes(action) &&
    !(await ask(
      (action === "REMOVE"
        ? t("移出成员：", "Remove participant: ")
        : t("拒绝准入：", "Reject admission: ")) +
        a.name +
        "？",
    ))
  )
    return;
  const r = await perform(
    `/meetings/${selected.value.id}/attendees/${a.id}`,
    "POST",
    { action, enabled },
  );
  if (r) await refreshMeeting();
}
async function silence() {
  if (
    !(await ask(
      t(
        "将其他所有成员切换为只观看/收听？不会打开任何设备。",
        "Switch everyone else to view-only? No device will be enabled.",
      ),
    ))
  )
    return;
  const r = await perform(`/meetings/${selected.value.id}/silence`);
  if (r) await refreshMeeting();
}
async function raiseHand() {
  const r = await perform(`/meetings/${selected.value.id}/hand`, "POST", {
    raised: !me.value.handRaised,
  });
  if (r) await refreshMeeting();
}
async function openInvites() {
  try {
    invites.value = await api(`/meetings/${selected.value.id}/invites`);
    createdInvite.value = "";
    dialog.value = "invites";
  } catch (e) {
    handleError(e);
  }
}
async function createInvite() {
  const r = await perform(`/meetings/${selected.value.id}/invites`, "POST", {
    maxUses: inviteUses.value,
  });
  if (r) {
    createdInvite.value = new URL(
      "/#invite=" + encodeURIComponent(r.code),
      location.origin,
    ).href;
    invites.value = await api(`/meetings/${selected.value.id}/invites`);
  }
}
async function revokeInvite(i) {
  const r = await perform(
    `/meetings/${selected.value.id}/invites/${i.id}/revoke`,
  );
  if (r) {
    createdInvite.value = "";
    invites.value = await api(`/meetings/${selected.value.id}/invites`);
  }
}
async function sendMessage() {
  const text = compose.value.trim();
  if (!text) return;
  const r = await perform(`/meetings/${selected.value.id}/messages`, "POST", {
    content: text,
    nonce: crypto.randomUUID(),
  });
  if (r) {
    compose.value = "";
    await refreshMeeting();
  }
}
async function removeMessage(m) {
  if (!(await ask(t("移除此条消息正文？", "Remove this message content?"))))
    return;
  const r = await perform(
    `/meetings/${selected.value.id}/messages/${m.id}`,
    "DELETE",
  );
  if (r) await refreshMeeting();
}
async function olderMessages() {
  try {
    const first = messages.value[0];
    if (!first) return;
    const rows = await api(
      `/meetings/${selected.value.id}/messages?before=${first.id}`,
    );
    messages.value = [...rows, ...messages.value];
    if (!rows.length) message.value = t("已到最早消息。", "No older messages.");
  } catch (e) {
    handleError(e);
  }
}
async function openAttendance() {
  try {
    attendance.value = await api(`/meetings/${selected.value.id}/attendance`);
    dialog.value = "attendance";
  } catch (e) {
    handleError(e);
  }
}
async function openDevices() {
  try {
    deviceLists.value = {
      audioinput: await devices("audioinput"),
      videoinput: await devices("videoinput"),
    };
    dialog.value = "devices";
  } catch (e) {
    handleError(e);
  }
}
async function selectDevice(kind, id) {
  try {
    await switchDevice(kind, id);
  } catch {
    message.value = t(
      "设备切换失败，请检查设备占用与权限。",
      "Device switch failed. Check permissions and availability.",
    );
  }
}
async function exportMeetings() {
  try {
    await download("/reports/meetings.csv", "meetdesk-meetings.csv");
  } catch (e) {
    handleError(e);
  }
}
async function copyInvite() {
  try {
    await navigator.clipboard.writeText(createdInvite.value);
  } catch {
    message.value = t(
      "无法自动复制，请手动选择链接复制。",
      "Select and copy the link manually.",
    );
  }
}
onMounted(async () => {
  const params = new URLSearchParams(location.hash.slice(1));
  inviteCode.value = params.get("invite") || "";
  if (inviteCode.value) {
    entryMode.value = "guest";
    history.replaceState(null, "", location.pathname + location.search);
  }
  await loadIdentity();
  timer = setInterval(() => {
    if (profile.value && !dialog.value && page.value === "meetings") {
      if (selected.value) void refreshMeeting();
      else if (!pending.value) void loadMeetings().catch(handleError);
    }
  }, 2000);
});
onBeforeUnmount(() => {
  clearInterval(timer);
  void disconnectMedia();
});
</script>
<template>
  <div v-if="!ready" class="loading-page">
    {{ t("正在连接…", "Connecting…") }}
  </div>
  <main v-else-if="!profile" class="login-shell">
    <aside class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" />
      <div>
        <h1>MeetDesk</h1>
        <p>{{ t("视频会议与远程演示", "Video meetings & presentations") }}</p>
      </div>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
        t("知华科技", "ZhiHua Technology")
      }}</a>
    </aside>
    <section class="login-form">
      <button
        class="language"
        @click="language = language === 'zh' ? 'en' : 'zh'"
      >
        {{ language === "zh" ? "English" : "中文" }}
      </button>
      <h2>
        {{
          entryMode === "guest"
            ? t("加入受邀会议", "Join an invited meeting")
            : t("登录 MeetDesk", "Sign in to MeetDesk")
        }}
      </h2>
      <div class="tabs">
        <button
          :class="{ active: entryMode === 'account' }"
          @click="
            entryMode = 'account';
            message = '';
          "
        >
          {{ t("账号登录", "Account") }}</button
        ><button
          :class="{ active: entryMode === 'guest' }"
          @click="
            entryMode = 'guest';
            message = '';
          "
        >
          {{ t("访客加入", "Guest") }}
        </button>
      </div>
      <form v-if="entryMode === 'account'" @submit.prevent="signIn">
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="login.username"
            autocomplete="username"
            required
            maxlength="60"
            :disabled="pending" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="login.password"
            autocomplete="current-password"
            type="password"
            required
            :disabled="pending"
        /></label>
        <p class="hint">
          {{
            t(
              "使用管理员分配的账号。",
              "Use an administrator-assigned account.",
            )
          }}
        </p>
        <button class="primary" type="submit" :disabled="pending">
          {{ pending ? t("登录中…", "Signing in…") : t("登录", "Sign in") }}
        </button>
      </form>
      <form v-else @submit.prevent="joinGuest">
        <label
          >{{ t("邀请代码", "Invitation code")
          }}<input
            v-model="inviteCode"
            autocomplete="off"
            required
            maxlength="64"
            :disabled="pending" /></label
        ><label
          >{{ t("显示名称", "Display name")
          }}<input
            v-model="guestName"
            required
            maxlength="60"
            autocomplete="off"
            :disabled="pending"
        /></label>
        <p class="hint">
          {{
            t(
              "加入等候室，主持人批准后进入。",
              "Wait for the host to admit you.",
            )
          }}
        </p>
        <button class="primary" type="submit" :disabled="pending">
          {{
            pending
              ? t("加入中…", "Joining…")
              : t("进入等候室", "Enter waiting room")
          }}
        </button>
      </form>
      <p v-if="message" role="alert" class="form-error">{{ message }}</p>
      <p class="license-note">
        {{
          t(
            "公开源码学习版 · 商用需授权",
            "Non-commercial source edition · Commercial license required",
          )
        }}
      </p>
    </section>
  </main>
  <div v-else class="app-shell">
    <nav class="rail" :aria-label="t('主菜单', 'Main navigation')">
      <img src="/brand/logo.jpg" alt="知华科技" /><button
        v-for="m in menus"
        :key="m.code"
        :class="{ active: page === m.code }"
        :aria-label="language === 'zh' ? m.name : m.nameEn"
        :title="language === 'zh' ? m.name : m.nameEn"
        @click="switchPage(m.code)"
      >
        <component :is="navIcons[m.code] || CalendarDays" :size="21" />
      </button>
      <div class="rail-spacer"></div>
      <button :aria-label="t('关于系统', 'About')" @click="dialog = 'about'">
        <Info :size="21" /></button
      ><button :aria-label="t('退出登录', 'Sign out')" @click="signOut">
        <LogOut :size="21" />
      </button>
    </nav>
    <div class="workspace">
      <header class="app-header">
        <strong>MeetDesk</strong
        ><span class="header-product">{{
          t("视频会议", "Video meetings")
        }}</span>
        <div class="header-spacer"></div>
        <button
          class="language"
          @click="language = language === 'zh' ? 'en' : 'zh'"
        >
          {{ language === "zh" ? "EN" : "中文" }}</button
        ><span class="account-name"
          ><span class="avatar">{{ profile.displayName.slice(0, 1) }}</span
          >{{ profile.displayName
          }}{{ profile.guest ? " · " + t("访客", "Guest") : "" }}</span
        >
      </header>
      <p v-if="message" role="alert" class="notice">
        {{ message
        }}<button :aria-label="t('关闭提示', 'Dismiss')" @click="message = ''">
          <X :size="16" />
        </button>
      </p>
      <section v-if="page === 'meetings' && !selected" class="page-content">
        <div class="page-heading">
          <div>
            <h1>{{ t("会议", "Meetings") }}</h1>
            <p>
              {{ t("计划、主持与参加会议", "Plan, host and attend meetings") }}
            </p>
          </div>
          <div class="actions">
            <button
              v-if="!profile.guest"
              class="secondary"
              @click="
                dialog = 'joinInvite';
                inviteCode = '';
              "
            >
              <Link :size="16" />{{ t("使用邀请", "Use invitation") }}</button
            ><button
              v-if="profile.permissions.includes('host')"
              class="primary"
              @click="editMeeting()"
            >
              <Plus :size="16" />{{ t("创建会议", "New meeting") }}
            </button>
          </div>
        </div>
        <div class="toolbar">
          <label class="search"
            ><Search :size="17" /><input
              v-model="query"
              :placeholder="t('搜索会议或主持人', 'Search meetings or hosts')"
              :aria-label="t('搜索会议', 'Search meetings')"
              @input="listPage = 1" /></label
          ><select
            v-model="statusFilter"
            :aria-label="t('会议状态', 'Meeting status')"
            @change="listPage = 1"
          >
            <option value="">{{ t("全部状态", "All states") }}</option>
            <option
              v-for="s in ['PLANNED', 'LIVE', 'ENDED', 'CANCELLED']"
              :key="s"
              :value="s"
            >
              {{ stateName(s) }}
            </option></select
          ><select v-model="sort" :aria-label="t('排序', 'Sort')">
            <option value="new">{{ t("最近计划", "Recent plans") }}</option>
            <option value="title">{{ t("名称", "Title") }}</option></select
          ><button class="plain" @click="loadPage">
            {{ t("刷新", "Refresh") }}
          </button>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("会议", "Meeting") }}</th>
                <th>{{ t("时间", "Scheduled") }}</th>
                <th>{{ t("主持人", "Host") }}</th>
                <th>{{ t("状态", "Status") }}</th>
                <th>{{ t("准入人数", "Admitted") }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in visible" :key="m.id">
                <td>
                  <strong>{{ m.title }}</strong
                  ><small>{{ m.agenda || "—" }}</small>
                </td>
                <td>{{ dateTime(m.scheduledAt) }}</td>
                <td>{{ m.hostName }}</td>
                <td>
                  <span :class="['badge', m.status.toLowerCase()]">{{
                    stateName(m.status)
                  }}</span>
                </td>
                <td>{{ m.admitted }} / {{ m.capacity }}</td>
                <td>
                  <button class="row-action" @click="chooseMeeting(m.id)">
                    {{ t("查看", "Open") }} →
                  </button>
                </td>
              </tr>
              <tr v-if="!visible.length">
                <td colspan="6" class="empty">
                  {{ t("暂无符合条件的会议。", "No matching meetings.") }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination">
          <span>{{ filtered.length }} {{ t("场会议", "meetings") }}</span
          ><button
            :disabled="listPage === 1"
            :aria-label="t('上一页', 'Previous page')"
            @click="listPage--"
          >
            <ChevronLeft :size="17" /></button
          ><span
            >{{ listPage }} /
            {{ Math.max(1, Math.ceil(filtered.length / 10)) }}</span
          ><button
            :disabled="listPage * 10 >= filtered.length"
            :aria-label="t('下一页', 'Next page')"
            @click="listPage++"
          >
            <ChevronRight :size="17" />
          </button>
        </div>
      </section>
      <section
        v-else-if="page === 'meetings' && selected"
        class="meeting-workspace"
      >
        <header class="meeting-header">
          <button
            class="icon-button"
            :aria-label="t('返回会议列表', 'Back to meetings')"
            @click="backToMeetings"
          >
            <ArrowLeft :size="20" />
          </button>
          <div class="meeting-title">
            <h1>{{ selected.title }}</h1>
            <span
              >{{ dateTime(selected.scheduledAt) }} ·
              {{ selected.hostName }}</span
            >
          </div>
          <span :class="['badge', selected.status.toLowerCase()]">{{
            stateName(selected.status)
          }}</span>
          <div class="actions">
            <button
              v-if="canManage"
              class="secondary compact"
              @click="openInvites"
            >
              <Link :size="16" />{{ t("邀请", "Invite") }}</button
            ><button
              v-if="canManage"
              class="secondary compact"
              @click="openAttendance"
            >
              <ScrollText :size="16" />{{ t("参会记录", "Attendance") }}</button
            ><button
              v-if="canManage && selected.status === 'PLANNED'"
              class="primary compact"
              :disabled="pending"
              @click="changeState('LIVE')"
            >
              {{ t("开始会议", "Start meeting") }}</button
            ><button
              v-if="canManage && selected.status === 'LIVE'"
              class="danger compact"
              :disabled="pending"
              @click="changeState('ENDED')"
            >
              {{ t("结束会议", "End meeting") }}
            </button>
          </div>
        </header>
        <div class="meeting-body">
          <div class="conference">
            <div
              v-if="connected || mediaState === 'reconnecting'"
              class="stage"
            >
              <div v-if="screens.length" class="screen-stage">
                <VideoTile
                  v-for="p in screens"
                  :key="p.identity + 'screen'"
                  :person="p"
                  screen
                />
              </div>
              <div
                :class="['camera-grid', { strip: screens.length }]"
                :style="{ '--tiles': Math.min(participants.length, 4) }"
              >
                <VideoTile
                  v-for="p in participants"
                  :key="p.identity"
                  :person="p"
                />
              </div>
            </div>
            <div v-else class="join-stage">
              <span class="stage-symbol"><Video :size="34" /></span>
              <h2>
                {{
                  terminal
                    ? stateName(selected.status)
                    : me.status === "WAITING"
                      ? t("正在等候主持人准入", "Waiting for the host")
                      : me.status === "ADMITTED"
                        ? selected.status === "LIVE"
                          ? t("会议已开始", "Meeting is ready")
                          : t("会议尚未开始", "Meeting has not started")
                        : ["REMOVED", "REJECTED"].includes(me.status)
                          ? stateName(me.status)
                          : t("申请加入会议", "Request to join")
                }}
              </h2>
              <p v-if="selected.agenda" class="agenda">{{ selected.agenda }}</p>
              <p v-if="me.status === 'WAITING'">
                {{
                  t(
                    "准入前不会采集或接收任何音视频。",
                    "No media is captured or received before admission.",
                  )
                }}
              </p>
              <p v-else-if="me.status === 'ADMITTED' && !terminal">
                {{
                  t(
                    "加入时麦克风和摄像头保持关闭。",
                    "Microphone and camera stay off on joining.",
                  )
                }}
              </p>
              <button
                v-if="selected.status === 'LIVE' && me.status === 'ADMITTED'"
                class="primary"
                :disabled="pending || mediaState === 'connecting'"
                @click="enterMedia"
              >
                {{
                  mediaState === "connecting"
                    ? t("连接中…", "Connecting…")
                    : t("加入音视频", "Join audio & video")
                }}</button
              ><button
                v-else-if="
                  !terminal &&
                  !profile.guest &&
                  (!me.status || me.status === 'LEFT')
                "
                class="primary"
                :disabled="pending || selected.locked"
                @click="requestJoin"
              >
                {{
                  selected.locked
                    ? t("已锁定入会", "Admission locked")
                    : t("申请进入等候室", "Enter waiting room")
                }}
              </button>
              <div
                v-if="canManage && selected.status === 'PLANNED'"
                class="actions"
              >
                <button class="secondary" @click="editMeeting(selected)">
                  {{ t("编辑计划", "Edit plan") }}</button
                ><button class="secondary" @click="changeState('CANCELLED')">
                  {{ t("取消会议", "Cancel meeting") }}
                </button>
              </div>
            </div>
            <div v-if="mediaError" class="media-alert" role="alert">
              {{ mediaError }}
            </div>
            <div v-if="!canPlay && connected" class="media-alert">
              <button @click="enablePlayback">
                {{ t("点击启用收听", "Enable playback") }}
              </button>
            </div>
            <div class="call-controls">
              <div class="connection-state">
                <span :class="{ online: connected }"></span
                >{{
                  connected
                    ? t("已连接", "Connected")
                    : mediaState === "reconnecting"
                      ? t("重连中…", "Reconnecting…")
                      : t("未连接", "Disconnected")
                }}<small v-if="connected && !canPublish">{{
                  t("只观看 / 收听", "View-only")
                }}</small>
              </div>
              <div class="device-controls">
                <button
                  :class="{ on: micEnabled }"
                  :disabled="!connected || pending || !canPublish"
                  :aria-label="
                    micEnabled
                      ? t('关闭麦克风', 'Turn microphone off')
                      : t('开启麦克风', 'Turn microphone on')
                  "
                  @click="deviceAction('microphone')"
                >
                  <Mic v-if="micEnabled" :size="21" /><MicOff
                    v-else
                    :size="21"
                  /><span>{{ t("麦克风", "Microphone") }}</span></button
                ><button
                  :class="{ on: cameraEnabled }"
                  :disabled="!connected || pending || !canPublish"
                  :aria-label="
                    cameraEnabled
                      ? t('关闭摄像头', 'Turn camera off')
                      : t('开启摄像头', 'Turn camera on')
                  "
                  @click="deviceAction('camera')"
                >
                  <Video v-if="cameraEnabled" :size="21" /><VideoOff
                    v-else
                    :size="21"
                  /><span>{{ t("摄像头", "Camera") }}</span></button
                ><button
                  :class="{ on: screenEnabled }"
                  :disabled="!connected || pending || !canPublish"
                  :aria-label="
                    screenEnabled
                      ? t('停止共享', 'Stop sharing')
                      : t('共享屏幕', 'Share screen')
                  "
                  @click="deviceAction('screen')"
                >
                  <MonitorUp :size="21" /><span>{{
                    t("共享", "Share")
                  }}</span></button
                ><button
                  :class="{ on: me.handRaised }"
                  :disabled="
                    selected.status !== 'LIVE' ||
                    me.status !== 'ADMITTED' ||
                    pending
                  "
                  :aria-label="t('举手或放下', 'Raise or lower hand')"
                  @click="raiseHand"
                >
                  <Hand :size="21" /><span>{{
                    t("举手", "Hand")
                  }}</span></button
                ><button
                  :aria-label="t('设备与连接诊断', 'Devices & diagnostics')"
                  @click="openDevices"
                >
                  <Settings :size="21" /><span>{{ t("设备", "Devices") }}</span>
                </button>
              </div>
              <button
                v-if="me.status === 'ADMITTED' || me.status === 'WAITING'"
                class="leave-button"
                :disabled="pending"
                @click="leaveMeeting"
              >
                <LogOut :size="18" />{{ t("离开", "Leave") }}
              </button>
            </div>
          </div>
          <aside class="meeting-sidebar">
            <div class="side-tabs">
              <button
                :class="{ active: side === 'people' }"
                @click="side = 'people'"
              >
                <Users :size="17" />{{ t("成员", "People") }}
                {{ active.length }}</button
              ><button
                :class="{ active: side === 'chat' }"
                :disabled="me.status !== 'ADMITTED'"
                @click="side = 'chat'"
              >
                <MessageSquare :size="17" />{{ t("聊天", "Chat") }}
              </button>
            </div>
            <div v-if="side === 'people'" class="people-panel">
              <div v-if="canManage && !terminal" class="host-controls">
                <button :disabled="pending" @click="control('locked')">
                  <LockKeyhole :size="15" />{{
                    selected.locked
                      ? t("允许新加入", "Allow new joins")
                      : t("锁定入会", "Lock admission")
                  }}</button
                ><button
                  :disabled="pending || selected.status !== 'LIVE'"
                  @click="silence"
                >
                  {{ t("全体只观看", "All view-only") }}</button
                ><button :disabled="pending" @click="control('chatEnabled')">
                  {{
                    selected.chatEnabled
                      ? t("关闭聊天", "Disable chat")
                      : t("允许聊天", "Enable chat")
                  }}
                </button>
              </div>
              <section v-if="canManage && waiting.length" class="waiting-list">
                <h3>
                  {{ t("等候室", "Waiting room") }} · {{ waiting.length }}
                </h3>
                <div v-for="a in waiting" :key="a.id" class="waiting-person">
                  <span
                    ><strong>{{ a.name }}</strong
                    ><small v-if="a.guest">{{
                      t("访客", "Guest")
                    }}</small></span
                  ><button
                    class="admit"
                    :disabled="pending"
                    :aria-label="t('准入 ', 'Admit ') + a.name"
                    @click="attendeeAction(a, 'ADMIT')"
                  >
                    <Check :size="17" /></button
                  ><button
                    class="icon-button"
                    :disabled="pending"
                    :aria-label="t('拒绝 ', 'Reject ') + a.name"
                    @click="attendeeAction(a, 'REJECT')"
                  >
                    <X :size="16" />
                  </button>
                </div>
              </section>
              <h3>
                {{ t("已准入", "Admitted") }} · {{ active.length }} /
                {{ selected.capacity }}
              </h3>
              <div v-for="a in active" :key="a.id" class="person-row">
                <span class="avatar">{{ a.name.slice(0, 1) }}</span>
                <div class="person-name">
                  <strong>{{ a.name }}</strong
                  ><small
                    >{{
                      a.host
                        ? t("主持人", "Host")
                        : a.guest
                          ? t("访客", "Guest")
                          : t("成员", "Member")
                    }}{{
                      !a.canSpeak ? " · " + t("只观看", "View-only") : ""
                    }}</small
                  >
                </div>
                <Hand v-if="a.handRaised" :size="16" class="raised" />
                <div
                  v-if="canManage && !a.host && !terminal"
                  class="person-actions"
                >
                  <button
                    :disabled="pending"
                    :aria-label="
                      (a.canSpeak
                        ? t('切为只观看 ', 'View-only ')
                        : t('允许发言 ', 'Allow publishing ')) + a.name
                    "
                    @click="attendeeAction(a, 'SPEAK', !a.canSpeak)"
                  >
                    <MicOff v-if="a.canSpeak" :size="15" /><Mic
                      v-else
                      :size="15"
                    /></button
                  ><button
                    :disabled="pending"
                    :aria-label="t('移出 ', 'Remove ') + a.name"
                    @click="attendeeAction(a, 'REMOVE')"
                  >
                    <X :size="15" />
                  </button>
                </div>
              </div>
              <p v-if="!active.length" class="hint">
                {{
                  t(
                    "尚无可查看的准入成员。",
                    "No admitted participants to display.",
                  )
                }}
              </p>
            </div>
            <div v-else class="chat-panel">
              <button
                v-if="messages.length"
                class="older"
                @click="olderMessages"
              >
                {{ t("读取更早消息", "Load earlier messages") }}
              </button>
              <div class="chat-history">
                <article v-for="m in messages" :key="m.id" class="chat-message">
                  <div>
                    <strong>{{ m.author }}</strong
                    ><time>{{ dateTime(m.createdAt) }}</time
                    ><button
                      v-if="!m.removed && (m.attendeeId === me.id || canManage)"
                      :aria-label="t('移除消息 ', 'Remove message ') + m.id"
                      :disabled="pending"
                      @click="removeMessage(m)"
                    >
                      <X :size="12" />
                    </button>
                  </div>
                  <p :class="{ removed: m.removed }">
                    {{
                      m.removed ? t("消息已移除", "Message removed") : m.content
                    }}
                  </p>
                </article>
                <p v-if="!messages.length" class="empty">
                  {{ t("还没有消息。", "No messages yet.") }}
                </p>
              </div>
              <form class="message-composer" @submit.prevent="sendMessage">
                <textarea
                  v-model="compose"
                  :maxlength="options.messageLimit"
                  :disabled="
                    pending ||
                    !admitted ||
                    selected.status !== 'LIVE' ||
                    !selected.chatEnabled
                  "
                  :aria-label="t('消息内容', 'Message content')"
                  :placeholder="
                    selected.chatEnabled
                      ? t('发送给会议成员', 'Message meeting participants')
                      : t('主持人已关闭聊天', 'Chat disabled by host')
                  "
                ></textarea>
                <div>
                  <small
                    >{{ compose.length }} / {{ options.messageLimit }}</small
                  ><button
                    class="primary compact"
                    :disabled="
                      pending ||
                      !admitted ||
                      !compose.trim() ||
                      selected.status !== 'LIVE' ||
                      !selected.chatEnabled
                    "
                  >
                    {{ t("发送", "Send") }}
                  </button>
                </div>
              </form>
            </div>
          </aside>
        </div>
        <div id="meeting-audio" class="hidden-audio"></div>
      </section>
      <section v-else-if="page === 'dashboard'" class="page-content">
        <div class="page-heading">
          <h1>{{ t("会议统计", "Meeting statistics") }}</h1>
          <div class="actions">
            <button class="secondary" @click="loadPage">
              {{ t("刷新", "Refresh") }}</button
            ><button class="primary" @click="exportMeetings">
              {{ t("导出会议台账", "Export meetings") }}
            </button>
          </div>
        </div>
        <div class="metrics">
          <div>
            <span>{{ t("会议计划", "Meetings") }}</span
            ><strong>{{ metrics.meetings ?? "—" }}</strong>
          </div>
          <div>
            <span>{{ t("进行中", "Live") }}</span
            ><strong>{{ metrics.live ?? "—" }}</strong>
          </div>
          <div>
            <span>{{ t("已结束", "Ended") }}</span
            ><strong>{{ metrics.ended ?? "—" }}</strong>
          </div>
          <div>
            <span>{{ t("观测到的连接", "Observed connections") }}</span
            ><strong>{{ metrics.connections ?? "—" }}</strong>
          </div>
        </div>
        <p class="hint">
          {{
            t(
              "连接次数来自媒体服务观察，不等于唯一人数或出席时长。",
              "Connections are observed by the media server, not unique headcount or attendance duration.",
            )
          }}
        </p>
      </section>
      <section v-else-if="page === 'audit'" class="page-content">
        <div class="page-heading">
          <h1>{{ t("操作记录", "Audit trail") }}</h1>
          <button class="secondary" @click="loadPage">
            {{ t("刷新", "Refresh") }}
          </button>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("时间", "Time") }}</th>
                <th>{{ t("账号", "Account") }}</th>
                <th>{{ t("操作", "Action") }}</th>
                <th>{{ t("对象", "Object") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in audit" :key="r.id">
                <td>{{ dateTime(r.createdAt) }}</td>
                <td>{{ r.actor }}</td>
                <td>{{ actionName(r.action) }}</td>
                <td>{{ r.objectId }}</td>
              </tr>
              <tr v-if="!audit.length">
                <td colspan="4" class="empty">
                  {{ t("暂无记录。", "No records.") }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <AdminPanel
        v-else
        ref="adminPanel"
        :section="page"
        :pending="pending"
        :message="message"
        :perform="perform"
        @error="handleError"
      />
    </div>
  </div>
  <div v-if="dialog" class="modal-backdrop" @click.self="closeDialog">
    <section
      :class="['modal', { wide: dialog === 'attendance' }]"
      role="dialog"
      aria-modal="true"
      :aria-label="
        {
          meeting: t('会议计划', 'Meeting plan'),
          joinInvite: t('使用邀请加入', 'Join by invitation'),
          invites: t('会议邀请', 'Meeting invitations'),
          devices: t('设备与连接诊断', 'Devices & diagnostics'),
          attendance: t('参会记录', 'Attendance'),
          about: t('关于 MeetDesk', 'About MeetDesk'),
        }[dialog]
      "
    >
      <header>
        <h2>
          {{
            {
              meeting: t("会议计划", "Meeting plan"),
              joinInvite: t("使用邀请加入", "Join by invitation"),
              invites: t("会议邀请", "Meeting invitations"),
              devices: t("设备与连接诊断", "Devices & diagnostics"),
              attendance: t("参会记录", "Attendance"),
              about: t("关于 MeetDesk", "About MeetDesk"),
            }[dialog]
          }}
        </h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          :disabled="pending"
          @click="closeDialog"
        >
          <X :size="20" />
        </button>
      </header>
      <form v-if="dialog === 'meeting'" @submit.prevent="saveMeeting">
        <label
          >{{ t("会议名称", "Meeting title")
          }}<input
            v-model="meetingForm.title"
            required
            maxlength="120"
            :disabled="pending" /></label
        ><label
          >{{ t("议程", "Agenda")
          }}<textarea
            v-model="meetingForm.agenda"
            maxlength="2000"
            :disabled="pending"
            rows="3"
          ></textarea>
        </label>
        <div class="form-grid">
          <label
            >{{ t("计划开始时间", "Scheduled start")
            }}<input
              v-model="meetingForm.scheduledAt"
              type="datetime-local"
              required
              :disabled="pending" /></label
          ><label
            >{{ t("分类", "Category")
            }}<select v-model="meetingForm.category" :disabled="pending">
              <option
                v-for="c in options.categories"
                :key="c.code"
                :value="c.code"
              >
                {{ language === "zh" ? c.name : c.nameEn }}
              </option>
            </select></label
          ><label
            >{{ t("人数上限（含主持人）", "Capacity including host")
            }}<input
              v-model="meetingForm.capacity"
              type="number"
              min="2"
              :max="options.capacity"
              required
              :disabled="pending" /></label
          ><label
            >{{ t("最长持续分钟", "Maximum duration (minutes)")
            }}<input
              v-model="meetingForm.durationMinutes"
              type="number"
              min="15"
              :max="options.duration"
              required
              :disabled="pending"
          /></label>
        </div>
        <p class="hint">
          {{
            t(
              "主持人手动开始；到达最长时限后自动结束。",
              "The host starts manually; duration expiry ends the meeting.",
            )
          }}
        </p>
        <footer>
          <button type="button" class="secondary" @click="closeDialog">
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="pending">
            {{ t("保存计划", "Save plan") }}
          </button>
        </footer>
      </form>
      <form v-else-if="dialog === 'joinInvite'" @submit.prevent="joinInvite">
        <label
          >{{ t("邀请代码", "Invitation code")
          }}<input
            v-model="inviteCode"
            required
            maxlength="64"
            autocomplete="off"
            :disabled="pending"
        /></label>
        <p class="hint">
          {{
            t(
              "正式账号仍受组织范围约束，使用邀请后等待主持人准入。",
              "Account organization scope still applies. Wait for admission after redeeming.",
            )
          }}
        </p>
        <footer>
          <button class="primary" :disabled="pending">
            {{ t("申请加入", "Request to join") }}
          </button>
        </footer>
      </form>
      <template v-else-if="dialog === 'invites'"
        ><form class="invite-create" @submit.prevent="createInvite">
          <label
            >{{ t("最多使用次数", "Maximum uses")
            }}<input
              v-model="inviteUses"
              type="number"
              min="1"
              max="64"
              required
              :disabled="pending || terminal" /></label
          ><button class="primary" :disabled="pending || terminal">
            {{ t("生成邀请链接", "Create invitation link") }}
          </button>
        </form>
        <div v-if="createdInvite" class="invite-result">
          <label
            >{{ t("邀请链接（仅此刻显示）", "Invitation link (shown once)")
            }}<input
              :value="createdInvite"
              readonly
              autocomplete="off" /></label
          ><button class="secondary" @click="copyInvite">
            {{ t("复制", "Copy") }}
          </button>
        </div>
        <p class="hint">
          {{
            t(
              "持有链接者可申请进入等候室；不会直接获得聊天或媒体权限。",
              "A link permits a waiting-room request, not chat or media access.",
            )
          }}
        </p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("有效期至", "Expires") }}</th>
                <th>{{ t("使用次数", "Uses") }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="i in invites" :key="i.id">
                <td>{{ dateTime(i.expiresAt) }}</td>
                <td>{{ i.uses }} / {{ i.maxUses }}</td>
                <td>
                  <span v-if="i.revoked" class="hint">{{
                    t("已撤销", "Revoked")
                  }}</span
                  ><button
                    v-else
                    class="row-action"
                    :disabled="pending"
                    @click="revokeInvite(i)"
                  >
                    {{ t("撤销", "Revoke") }}
                  </button>
                </td>
              </tr>
              <tr v-if="!invites.length">
                <td colspan="3" class="empty">
                  {{ t("尚无邀请。", "No invitations.") }}
                </td>
              </tr>
            </tbody>
          </table>
        </div></template
      >
      <template v-else-if="dialog === 'devices'"
        ><label
          >{{ t("麦克风输入", "Microphone input")
          }}<select
            v-model="chosenMic"
            @change="selectDevice('audioinput', chosenMic)"
          >
            <option value="">{{ t("系统默认", "System default") }}</option>
            <option
              v-for="(d, i) in deviceLists.audioinput"
              :key="d.deviceId"
              :value="d.deviceId"
            >
              {{ d.label || t("麦克风 ", "Microphone ") + (i + 1) }}
            </option>
          </select></label
        ><label
          >{{ t("摄像头", "Camera")
          }}<select
            v-model="chosenCamera"
            @change="selectDevice('videoinput', chosenCamera)"
          >
            <option value="">{{ t("系统默认", "System default") }}</option>
            <option
              v-for="(d, i) in deviceLists.videoinput"
              :key="d.deviceId"
              :value="d.deviceId"
            >
              {{ d.label || t("摄像头 ", "Camera ") + (i + 1) }}
            </option>
          </select></label
        ><label
          >{{ t("下次连接方式", "Next connection mode")
          }}<select v-model="mediaMode">
            <option value="auto">
              {{ t("自动（直接连接或中继）", "Auto (direct or relay)") }}
            </option>
            <option value="relay">
              {{ t("仅中继（需正确配置 TURN）", "Relay only (requires TURN)") }}
            </option>
          </select></label
        >
        <div class="diagnostics">
          <p>
            {{ t("连接状态", "Connection") }}
            <strong>{{
              t(
                {
                  connected: "已连接",
                  disconnected: "未连接",
                  connecting: "连接中",
                  reconnecting: "重连中",
                }[mediaState] || "状态变化中",
                mediaState,
              )
            }}</strong>
          </p>
          <p>
            {{ t("已收音频包", "Audio packets") }}
            <strong>{{ mediaStats.audioPackets }}</strong>
          </p>
          <p>
            {{ t("已收视频包", "Video packets") }}
            <strong>{{ mediaStats.videoPackets }}</strong>
          </p>
          <p>
            {{ t("已解码视频帧", "Decoded frames") }}
            <strong>{{ mediaStats.frames }}</strong>
          </p>
          <p>
            {{ t("检测到中继", "Relay detected") }}
            <strong>{{
              mediaStats.relay ? t("是", "Yes") : t("否", "No")
            }}</strong>
          </p>
        </div>
        <p class="hint">
          {{
            t(
              "设备名称遵守浏览器权限；修改连接方式后需离开并重新加入媒体。",
              "Device labels respect browser permission. Reconnect after changing mode.",
            )
          }}
        </p></template
      >
      <template v-else-if="dialog === 'attendance'"
        ><p class="hint">
          {{
            t(
              "“观测接入”来自媒体服务周期查询；仅发放票据不会算作已接入。时间为观察值，不是计费或精确考勤依据。",
              "Observed connection times come from periodic media-server queries. A token alone is not attendance. These are observation times, not billing or precise timekeeping.",
            )
          }}
        </p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("成员", "Participant") }}</th>
                <th>{{ t("授权时间", "Authorized") }}</th>
                <th>{{ t("观测接入", "Observed join") }}</th>
                <th>{{ t("观测离开", "Observed leave") }}</th>
                <th>{{ t("结束原因", "Reason") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="a in attendance" :key="a.id">
                <td>{{ a.name }}</td>
                <td>{{ dateTime(a.issuedAt) }}</td>
                <td>{{ dateTime(a.connectedAt) }}</td>
                <td>{{ dateTime(a.disconnectedAt) }}</td>
                <td>{{ connectionReason(a.reason) }}</td>
              </tr>
              <tr v-if="!attendance.length">
                <td colspan="5" class="empty">
                  {{ t("尚无媒体接入记录。", "No media connection records.") }}
                </td>
              </tr>
            </tbody>
          </table>
        </div></template
      >
      <template v-else-if="dialog === 'about'"
        ><div class="about-brand">
          <img src="/brand/logo.jpg" alt="知华科技" />
          <div>
            <strong>MeetDesk 1.0.0</strong>
            <p>
              {{ t("视频会议与远程演示", "Video meetings & presentations") }}
            </p>
          </div>
        </div>
        <p>
          {{
            t(
              "知华科技（上海如静知华信息科技有限公司）",
              "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
            )
          }}
        </p>
        <p>
          {{
            t(
              "公开源码学习版，仅限非商业学习交流，未经书面授权不得商用。",
              "Source-available learning edition. Commercial use requires written authorization.",
            )
          }}
        </p>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >https://www.zhuatech.cn/</a
        >
        <p>
          {{
            t(
              "商业授权或深度定制开发请联系知华科技。",
              "Contact ZhiHua for commercial licensing and custom development.",
            )
          }}
        </p>
        <div v-if="language === 'zh'" class="contact-images">
          <figure>
            <img src="/brand/wechat-zhuatech.png" alt="知华科技微信 zhuatech" />
            <figcaption>微信：zhuatech</figcaption>
          </figure>
          <figure>
            <img
              src="/brand/wechat-zhuatech2.png"
              alt="知华科技微信 zhuatech2"
            />
            <figcaption>微信：zhuatech2</figcaption>
          </figure>
        </div>
        <ul v-else>
          <li><a href="mailto:han@zhuatech.cn">han@zhuatech.cn</a></li>
          <li><a href="mailto:jack@zhuatech.cn">jack@zhuatech.cn</a></li>
          <li>
            <a href="https://wa.me/8617521234993" target="_blank" rel="noopener"
              >WhatsApp: +86 17521234993</a
            >
          </li>
        </ul></template
      >
      <p v-if="message" class="form-error" role="alert">{{ message }}</p>
    </section>
  </div>
  <div v-if="confirmation" class="modal-backdrop confirm-backdrop">
    <section
      class="modal confirm-modal"
      role="alertdialog"
      aria-modal="true"
      :aria-label="t('确认操作', 'Confirm action')"
    >
      <h2>{{ t("确认操作", "Confirm action") }}</h2>
      <p>{{ confirmation.message }}</p>
      <footer>
        <button class="secondary" @click="answerConfirmation(false)">
          {{ t("取消", "Cancel") }}</button
        ><button class="primary" @click="answerConfirmation(true)">
          {{ t("确认", "Confirm") }}
        </button>
      </footer>
    </section>
  </div>
</template>
