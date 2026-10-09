// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref } from "vue";
export const language = ref("zh");
/** 双语操作文案，不改写聊天内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function t(zh, en) {
  return language.value === "zh" ? zh : en;
}
const errors = {
  NOT_FOUND: [
    "记录不存在，请刷新列表。",
    "Record not found. Refresh the list.",
  ],
  DEPARTMENT_DISABLED: [
    "组织已停用，请选择有效组织。",
    "Select an enabled organization.",
  ],
  IDENTITY_LOCKED: [
    "账号或稳定代码不可修改，请保留原值。",
    "Keep the original account identifier or stable code.",
  ],
  RESOURCE_LIMIT: [
    "目录数据量已达查询上限，请联系管理员。",
    "Catalog query limit reached. Contact the administrator.",
  ],
  SYSTEM_DIRECTORY_FIXED: [
    "系统目录项不能新增，请编辑现有项。",
    "System catalog is fixed. Edit an existing entry.",
  ],
  UNKNOWN_PERMISSION: [
    "权限代码无效，请从权限目录选择。",
    "Select a valid capability from the permission catalog.",
  ],
  MEETING_FORBIDDEN: [
    "该会议不在你的访问范围。",
    "Meeting outside your access scope.",
  ],
  ADMISSION_REQUIRED: [
    "尚未获准入，不能读取聊天或连接媒体。",
    "Admission required for chat and media.",
  ],
  MEETING_CLOSED: [
    "会议尚未开始、已结束或已到最长时限。",
    "Meeting not live, ended or duration expired.",
  ],
  MEETING_LOCKED: [
    "会议已锁定入会或已结束。",
    "Meeting admission locked or meeting ended.",
  ],
  MEETING_STATE: [
    "会议状态不允许此操作，请刷新。",
    "Meeting state changed. Refresh.",
  ],
  MEETING_FULL: ["准入人数已满，请先让成员离开。", "Meeting capacity reached."],
  WAITING_FULL: [
    "等候室已满，请稍后再申请。",
    "Waiting room full. Retry later.",
  ],
  ATTENDEE_REMOVED: [
    "主持人已拒绝或移出此身份，不能再次申请。",
    "This identity was rejected or removed.",
  ],
  ATTENDEE_STATE: [
    "成员状态已变化，请刷新。",
    "Participant state changed. Refresh.",
  ],
  HOST_PROTECTED: [
    "不能通过成员操作移出主持人。",
    "The host is protected from participant removal.",
  ],
  GUEST_ALREADY_JOINED: [
    "此会话已有访客身份，请先退出后再加入。",
    "Sign out of the current guest session before joining again.",
  ],
  GUEST_THROTTLED: [
    "申请过多，请一分钟后重试。",
    "Too many guest requests. Retry in a minute.",
  ],
  USE_ACCOUNT_JOIN: [
    "请使用当前正式账号申请加入。",
    "Use your signed-in account to join.",
  ],
  SCHEDULE_INVALID: [
    "计划时间须在最近一天至未来一年范围内。",
    "Schedule must be within the last day and the next year.",
  ],
  CHAT_DISABLED: ["主持人已关闭文字聊天。", "Chat disabled by the host."],
  MEDIA_DENIED: [
    "媒体权限已失效，请核对准入状态。",
    "Media access revoked. Check admission.",
  ],
  MEDIA_UNAVAILABLE: [
    "媒体服务暂不可用，请检查部署。",
    "Media service unavailable. Check deployment.",
  ],
  MEDIA_CONNECT_FAILED: [
    "音视频连接失败，请检查媒体地址、端口和中继配置。",
    "Media connection failed. Check addresses, ports and TURN.",
  ],

  SERVER_UNAVAILABLE: [
    "服务暂时不可用，请稍后刷新。",
    "Service temporarily unavailable. Refresh shortly.",
  ],
  UNAUTHENTICATED: [
    "会话失效，请重新登录。",
    "Session expired. Sign in again.",
  ],
  LOGIN_FAILED: [
    "账号或密码不正确，或账号已停用。",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: [
    "尝试过多，请五分钟后重试。",
    "Too many attempts. Retry in five minutes.",
  ],
  FORBIDDEN: ["没有操作权限。", "Permission denied."],
  INVITE_INVALID: [
    "邀请已失效、用完或不适用于你的组织。",
    "Invitation invalid, expired or exhausted.",
  ],
  INVITE_LIMIT: [
    "有效邀请已达上限，请先撤销旧邀请。",
    "Revoke an old invitation before creating another.",
  ],
  PASSWORD_WEAK: [
    "密码需12–72字节，含大小写字母和数字。",
    "Password needs 12–72 bytes with uppercase, lowercase and digits.",
  ],
  LAST_ADMIN: [
    "必须保留一位启用的完整管理员。",
    "Keep an enabled full administrator.",
  ],
  VERSION_CONFLICT: [
    "记录已被修改，请刷新核对后保存。",
    "Record changed. Refresh before saving.",
  ],
  CATEGORY_DISABLED: [
    "分类已停用，请选择有效分类。",
    "Select an enabled category.",
  ],
  INVALID_INPUT: [
    "字段格式不正确，请检查表单。",
    "Invalid fields. Check the form.",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确。", "Current password is incorrect."],
  CONFLICT: [
    "重复记录或数据仍被使用，请核对。",
    "Duplicate or referenced data. Check input.",
  ],
  NETWORK_ERROR: [
    "连接失败，请检查网络后刷新。",
    "Connection failed. Check network and refresh.",
  ],
  RESULT_UNKNOWN: [
    "连接中断，提交结果未知。请刷新核对，勿重复提交。",
    "Connection interrupted; result unknown. Refresh before resubmitting.",
  ],
  NONCE_CONFLICT: [
    "消息提交标识重复，内容未保存。",
    "Message identifier reused with different content.",
  ],
  OUT_OF_SCOPE: ["记录不在你的组织范围。", "Record outside your organization."],
};
/** 显示业务错误及下一步操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function errorMessage(e) {
  return errors[e.message]
    ? t(...errors[e.message])
    : `${t("操作未完成", "Action failed")} (${e.message})`;
}
export const confirmation = ref(null);
/** 页面内操作确认。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function ask(message) {
  if (confirmation.value) return Promise.resolve(false);
  return new Promise((resolve) => {
    confirmation.value = { message, resolve };
  });
}
/** 完成确认。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function answerConfirmation(ok) {
  const p = confirmation.value;
  confirmation.value = null;
  p?.resolve(ok);
}
/** 编辑记录副本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function cloneRecord(r) {
  return JSON.parse(JSON.stringify(r));
}
/** 时间按当前语言显示，不假造时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function dateTime(s) {
  return s
    ? new Intl.DateTimeFormat(language.value === "zh" ? "zh-CN" : "en", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(s))
    : "—";
}
/** 将媒体观察终止代码转成可理解的双语原因。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function connectionReason(code) {
  const labels = {
    REPLACED: ["由新连接替换", "Replaced by a new connection"],
    LEFT_MEDIA: ["主动断开媒体", "Media left voluntarily"],
    LOGOUT: ["已退出登录", "Signed out"],
    ACCESS_REVOKED: ["访问资格失效", "Access revoked"],
    NOT_CONNECTED: ["未建立连接", "No connection established"],
    CONNECTION_CLOSED: ["连接已关闭", "Connection closed"],
    MEETING_ENDED: ["会议结束", "Meeting ended"],
    REMOVED: ["主持人移出", "Removed by host"],
    LEFT: ["已离开会议", "Left the meeting"],
  };
  return code ? (labels[code] ? t(...labels[code]) : code) : "—";
}
/** 显示可理解的审计动作，数据库仍保留稳定代码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actionName(code) {
  const names = {
    MEETING_CREATED: ["创建会议", "Meeting created"],
    MEETING_UPDATED: ["更新计划", "Plan updated"],
    MEETING_LIVE: ["开始会议", "Meeting started"],
    MEETING_ENDED: ["结束会议", "Meeting ended"],
    MEETING_CANCELLED: ["取消会议", "Meeting cancelled"],
    MEETING_CONTROLS: ["更新入会与聊天控制", "Admission/chat controls"],
    MEETING_SILENCE: ["全体仅观看", "All view-only"],
    JOIN_REQUESTED: ["申请入会", "Join requested"],
    GUEST_REQUESTED: ["访客申请入会", "Guest requested admission"],
    ATTENDEE_ADMIT: ["批准准入", "Admission approved"],
    ATTENDEE_REJECT: ["拒绝准入", "Admission rejected"],
    ATTENDEE_REMOVE: ["移出成员", "Participant removed"],
    ATTENDEE_SPEAK: ["更改发布权限", "Publication permissions changed"],
    MEDIA_AUTHORIZED: ["发放媒体接入票据", "Media access authorized"],

    LOGIN: ["账号登录", "Sign-in"],
    PASSWORD_CHANGE: ["修改本人密码", "Own password changed"],
    INVITE_CREATED: ["生成邀请", "Invitation created"],
    INVITE_REVOKED: ["撤销邀请", "Invitation revoked"],
    INVITE_REDEEMED: ["兑换邀请", "Invitation redeemed"],
    MESSAGE_REMOVED: ["移除消息", "Message removed"],
    ADMIN_USERS: ["维护登录账号", "Account updated"],
    ADMIN_ROLES: ["维护角色", "Role updated"],
    ADMIN_PERMISSIONS: ["维护权限目录", "Permission label updated"],
    ADMIN_DEPARTMENTS: ["维护组织", "Organization updated"],
    ADMIN_DICTIONARIES: ["维护分类", "Category updated"],
    ADMIN_MENUS: ["维护菜单", "Menu updated"],
    ADMIN_SETTINGS: ["维护参数", "Parameter updated"],
  };
  return names[code] ? t(...names[code]) : code;
}
