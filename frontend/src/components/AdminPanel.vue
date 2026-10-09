<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, watch } from "vue";
import { Plus, Search, Pencil, X } from "@lucide/vue";
import { api } from "../api.js";
import { t, cloneRecord, ask } from "../ui.js";
const props = defineProps({
  section: { type: String, required: true },
  pending: Boolean,
  message: { type: String, default: "" },
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error"]);
const kind = ref(props.section === "settings" ? "departments" : props.section);
const rows = ref([]);
const options = ref({ roles: [], departments: [] });
const q = ref("");
const page = ref(1);
const editing = ref(null);
const initialForm = ref("");
async function canLeave() {
  if (
    editing.value &&
    JSON.stringify(editing.value) !== initialForm.value &&
    !(await ask(t("放弃尚未保存的修改？", "Discard unsaved changes?")))
  )
    return false;
  editing.value = null;
  return true;
}
async function switchKind(k) {
  if (await canLeave()) kind.value = k;
}
defineExpose({ canLeave });
const ready = ref(false);
const labels = {
  users: () => t("登录账号", "Accounts"),
  roles: () => t("角色", "Roles"),
  permissions: () => t("权限目录", "Permissions"),
  departments: () => t("组织", "Organizations"),
  dictionaries: () => t("会议分类", "Categories"),
  settings: () => t("系统参数", "Parameters"),
  menus: () => t("菜单", "Menus"),
};
const codes = [
  "join",
  "host",
  "moderate",
  "reports",
  "users",
  "roles",
  "settings",
  "audit",
];
const permissionName = (c) =>
  ({
    join: t("进入获准会议", "Join approved meetings"),
    host: t("创建与主持会议", "Create and host meetings"),
    moderate: t("成员与消息管理", "Member and message moderation"),
    reports: t("统计与导出", "Statistics and export"),
    users: t("账号管理", "Accounts"),
    roles: t("角色权限", "Roles"),
    settings: t("组织与设置", "Organizations and settings"),
    audit: t("审计记录", "Audit"),
  })[c] || c;
const settingsDescription = (c) =>
  ({
    message_limit: t(
      "单条消息长度上限（100–4000字）",
      "Message length limit (100–4000)",
    ),
    meeting_limit: t("新会议人数上限（2–32）", "New meeting capacity (2–32)"),
    duration_limit: t(
      "最长会议分钟（15–480）",
      "Maximum meeting minutes (15–480)",
    ),
    invite_hours: t(
      "邀请有效时长（1–720小时）",
      "Invitation validity (1–720 hours)",
    ),
  })[c] || "";
const filtered = computed(() =>
  rows.value.filter((r) =>
    JSON.stringify(r).toLowerCase().includes(q.value.toLowerCase()),
  ),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * 15, page.value * 15),
);
watch([q, kind], () => (page.value = 1));
watch(
  () => props.section,
  () => {
    kind.value = props.section === "settings" ? "departments" : props.section;
    editing.value = null;
    q.value = "";
  },
);
watch(kind, load, { immediate: true });
/** 独立后台目录读取，不把账号散列或业务私有字段交给浏览器。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  ready.value = false;
  try {
    rows.value = await api("/admin/" + kind.value);
    if (kind.value === "users") options.value = await api("/admin/options");
  } catch (e) {
    emit("error", e);
  } finally {
    ready.value = true;
  }
}
/** 准备受控目录表单；密码留空表示保留当前密码。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function edit(row) {
  if (row) {
    editing.value = cloneRecord(row);
    if (kind.value === "users") editing.value.password = "";
    initialForm.value = JSON.stringify(editing.value);
    return;
  }
  editing.value =
    kind.value === "users"
      ? {
          username: "",
          displayName: "",
          roleId: "",
          departmentId: options.value.departments[0]?.id || "",
          enabled: true,
          password: "",
        }
      : kind.value === "roles"
        ? { name: "", scope: "DEPARTMENT", permissions: ["join"] }
        : kind.value === "departments"
          ? { name: "", zone: "UTC", enabled: true }
          : { code: "", name: "", nameEn: "", enabled: true };
  initialForm.value = JSON.stringify(editing.value);
}
/** 只在服务端保存成功后关闭表单并重读目录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function save() {
  const row = editing.value;
  const result = await props.perform(
    `/admin/${kind.value}${row.id ? "/" + row.id : ""}`,
    row.id ? "PUT" : "POST",
    row,
  );
  if (result) {
    editing.value = null;
    await load();
  }
}
const optionName = (rows, id) =>
  rows.find((r) => r.id === id)?.name || `#${id}`;
</script>
<template>
  <section class="admin-section">
    <header class="page-heading">
      <div>
        <h1>
          {{
            section === "settings"
              ? t("组织与设置", "Organizations & settings")
              : section === "roles"
                ? t("角色与权限", "Roles & permissions")
                : t("登录账号", "Accounts")
          }}
        </h1>
        <p class="muted">
          {{
            section === "users"
              ? t(
                  "账号停用或重置密码后，旧会话立即失效。",
                  "Disabling an account or resetting its password revokes old sessions.",
                )
              : section === "roles"
                ? t(
                    "服务端检查权限、组织范围和会议准入关系。",
                    "The server checks permissions, organization scope and room membership.",
                  )
                : t(
                    "修改系统目录与业务参数，保留历史数据。",
                    "Manage system directories and parameters while preserving history.",
                  )
          }}
        </p>
      </div>
      <div class="actions">
        <button :disabled="pending" @click="load">
          {{ t("刷新", "Refresh") }}</button
        ><button
          v-if="
            ['users', 'roles', 'departments', 'dictionaries'].includes(kind)
          "
          class="primary"
          :disabled="pending || !ready"
          @click="edit(null)"
        >
          <Plus :size="16" />{{ t("新增", "Add") }} {{ labels[kind]() }}
        </button>
      </div>
    </header>
    <nav v-if="section === 'settings' || section === 'roles'" class="sub-tabs">
      <button
        v-for="k in section === 'roles'
          ? ['roles', 'permissions']
          : ['departments', 'dictionaries', 'settings', 'menus']"
        :key="k"
        :class="{ active: kind === k }"
        @click="switchKind(k)"
      >
        {{ labels[k]() }}
      </button>
    </nav>
    <div class="panel">
      <div class="toolbar">
        <label class="search-box"
          ><Search :size="16" /><input
            v-model="q"
            :placeholder="t('搜索记录', 'Search records')"
            :aria-label="t('搜索记录', 'Search records')" /></label
        ><span class="muted"
          >{{ filtered.length }} {{ t("条", "records") }}</span
        >
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <template v-if="kind === 'users'"
                ><th>{{ t("账号", "Username") }}</th>
                <th>{{ t("显示名", "Display name") }}</th>
                <th>{{ t("角色", "Role") }}</th>
                <th>{{ t("组织", "Organization") }}</th>
                <th>{{ t("状态", "Status") }}</th></template
              ><template v-else-if="kind === 'roles'"
                ><th>{{ t("角色", "Role") }}</th>
                <th>{{ t("数据范围", "Scope") }}</th>
                <th>{{ t("权限", "Permissions") }}</th></template
              ><template v-else-if="kind === 'departments'"
                ><th>{{ t("组织", "Organization") }}</th>
                <th>{{ t("时区", "Time zone") }}</th>
                <th>{{ t("状态", "Status") }}</th></template
              ><template v-else-if="kind === 'settings'"
                ><th>{{ t("参数", "Parameter") }}</th>
                <th>{{ t("当前值", "Value") }}</th>
                <th>{{ t("说明", "Description") }}</th></template
              ><template v-else
                ><th>{{ t("代码", "Code") }}</th>
                <th>{{ t("名称", "Name") }}</th>
                <th v-if="kind === 'menus' || kind === 'dictionaries'">
                  English
                </th>
                <th v-if="kind !== 'permissions'">
                  {{ t("状态", "Status") }}
                </th></template
              >
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in visible" :key="r.id">
              <template v-if="kind === 'users'"
                ><td class="mono">{{ r.username }}</td>
                <td>{{ r.displayName }}</td>
                <td>{{ optionName(options.roles, r.roleId) }}</td>
                <td>{{ optionName(options.departments, r.departmentId) }}</td>
                <td>
                  <span
                    class="badge"
                    :class="r.enabled ? 'approved' : 'paused'"
                    >{{
                      r.enabled ? t("启用", "Enabled") : t("停用", "Disabled")
                    }}</span
                  >
                </td></template
              ><template v-else-if="kind === 'roles'"
                ><td>{{ r.name }}</td>
                <td>
                  {{
                    {
                      ALL: t("全部", "All"),
                      DEPARTMENT: t("所在组织", "This organization"),
                    }[r.scope]
                  }}
                </td>
                <td>
                  <div class="permission-list">
                    <span v-for="c in r.permissions" :key="c">{{
                      permissionName(c)
                    }}</span>
                  </div>
                </td></template
              ><template v-else-if="kind === 'departments'"
                ><td>{{ r.name }}</td>
                <td>{{ r.zone }}</td>
                <td>
                  {{ r.enabled ? t("启用", "Enabled") : t("停用", "Disabled") }}
                </td></template
              ><template v-else-if="kind === 'settings'"
                ><td class="mono">{{ r.code }}</td>
                <td>{{ r.value }}</td>
                <td>{{ settingsDescription(r.code) }}</td></template
              ><template v-else
                ><td class="mono">{{ r.code }}</td>
                <td>{{ r.name }}</td>
                <td v-if="kind === 'menus' || kind === 'dictionaries'">
                  {{ r.nameEn }}
                </td>
                <td v-if="kind !== 'permissions'">
                  {{ r.enabled ? t("启用", "Enabled") : t("停用", "Disabled") }}
                </td></template
              >
              <td>
                <button
                  class="icon-button"
                  :disabled="pending"
                  :aria-label="
                    t('编辑', 'Edit') + ' ' + (r.username || r.name || r.code)
                  "
                  @click="edit(r)"
                >
                  <Pencil :size="16" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!ready" class="empty">{{ t("读取中…", "Loading…") }}</div>
        <div v-else-if="!filtered.length" class="empty">
          {{ t("没有匹配记录", "No matching records") }}
        </div>
      </div>
      <footer class="pagination">
        <span
          >{{ page }} / {{ Math.max(1, Math.ceil(filtered.length / 15)) }}</span
        ><button :disabled="page <= 1" @click="page--">
          {{ t("上一页", "Previous") }}</button
        ><button :disabled="page * 15 >= filtered.length" @click="page++">
          {{ t("下一页", "Next") }}
        </button>
      </footer>
    </div>
    <div v-if="editing" class="modal-backdrop">
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        :aria-label="labels[kind]()"
      >
        <header>
          <h2>
            {{ editing.id ? t("编辑", "Edit") : t("新增", "Add") }}
            {{ labels[kind]() }}
          </h2>
          <button
            class="icon-button"
            :aria-label="t('关闭', 'Close')"
            :disabled="pending"
            @click="canLeave"
          >
            <X :size="20" />
          </button>
        </header>
        <p v-if="message" class="form-error" role="alert">{{ message }}</p>
        <form @submit.prevent="save">
          <template v-if="kind === 'users'"
            ><label
              >{{ t("账号", "Username")
              }}<input
                v-model="editing.username"
                required
                maxlength="60"
                :readonly="!!editing.id"
                :disabled="pending"
                autocomplete="off"
                pattern="[a-zA-Z0-9_.\-]{3,60}" /></label
            ><label
              >{{ t("显示名", "Display name")
              }}<input
                v-model="editing.displayName"
                required
                maxlength="120"
                :disabled="pending"
            /></label>
            <div class="form-grid">
              <label
                >{{ t("角色", "Role")
                }}<select v-model="editing.roleId" :disabled="pending" required>
                  <option v-for="r in options.roles" :key="r.id" :value="r.id">
                    {{ r.name }}
                  </option>
                </select></label
              ><label
                >{{ t("组织", "Organization")
                }}<select
                  v-model="editing.departmentId"
                  :disabled="pending"
                  required
                >
                  <option
                    v-for="d in options.departments.filter(
                      (d) => d.enabled || d.id === editing.departmentId,
                    )"
                    :key="d.id"
                    :value="d.id"
                  >
                    {{ d.name }}
                  </option>
                </select></label
              >
            </div>
            <label
              >{{
                editing.id
                  ? t(
                      "重置密码（留空不变）",
                      "Reset password (blank = unchanged)",
                    )
                  : t("初始密码", "Initial password")
              }}<input
                v-model="editing.password"
                type="password"
                :required="!editing.id"
                :disabled="pending"
                autocomplete="new-password"
                maxlength="72"
            /></label>
            <p class="hint">
              {{
                t(
                  "12–72 字节，包含大写、小写字母和数字；不要使用共享演示密码。",
                  "12–72 bytes, uppercase, lowercase and a digit. Do not use shared demo passwords.",
                )
              }}
            </p></template
          ><template v-else-if="kind === 'roles'"
            ><label
              >{{ t("角色名称", "Role name")
              }}<input
                v-model="editing.name"
                required
                maxlength="120"
                :disabled="pending" /></label
            ><label
              >{{ t("数据范围", "Data scope")
              }}<select v-model="editing.scope" :disabled="pending">
                <option value="ALL">
                  {{ t("全部组织", "All organizations") }}
                </option>
                <option value="DEPARTMENT">
                  {{ t("所在组织", "This organization") }}
                </option>
              </select></label
            >
            <fieldset>
              <legend>{{ t("权限", "Permissions") }}</legend>
              <label v-for="c in codes" :key="c" class="check"
                ><input
                  v-model="editing.permissions"
                  type="checkbox"
                  :value="c"
                  :disabled="pending"
                />{{ permissionName(c) }}</label
              >
            </fieldset>
            <p class="hint">
              {{
                t(
                  "账号、角色和系统设置另需“全部组织”范围。会议访问还需有效准入关系。",
                  "Account, role and settings administration requires All organizations scope. Meeting access also requires valid admission.",
                )
              }}
            </p></template
          ><template v-else-if="kind === 'departments'"
            ><label
              >{{ t("组织名称", "Organization name")
              }}<input
                v-model="editing.name"
                required
                maxlength="120"
                :disabled="pending" /></label
            ><label
              >{{ t("IANA 时区", "IANA time zone")
              }}<input
                v-model="editing.zone"
                required
                maxlength="80"
                :disabled="pending"
                placeholder="Asia/Shanghai / Europe/Berlin / UTC" /></label></template
          ><template v-else-if="kind === 'settings'"
            ><p class="mono">{{ editing.code }}</p>
            <label
              >{{ t("参数值", "Value")
              }}<input
                v-model="editing.value"
                type="number"
                :min="
                  editing.code === 'message_limit'
                    ? 100
                    : editing.code === 'duration_limit'
                      ? 15
                      : editing.code === 'meeting_limit'
                        ? 2
                        : 1
                "
                :max="
                  editing.code === 'message_limit'
                    ? 4000
                    : editing.code === 'meeting_limit'
                      ? 32
                      : editing.code === 'duration_limit'
                        ? 480
                        : 720
                "
                required
                :disabled="pending"
            /></label>
            <p class="hint">
              {{ settingsDescription(editing.code) }}
            </p></template
          ><template v-else
            ><label
              >{{ t("稳定代码", "Stable code")
              }}<input
                v-model="editing.code"
                required
                maxlength="60"
                :readonly="!!editing.id"
                :disabled="pending" /></label
            ><label
              >{{ t("中文名称", "Chinese name")
              }}<input
                v-model="editing.name"
                required
                maxlength="120"
                :disabled="pending" /></label
            ><label v-if="kind === 'menus' || kind === 'dictionaries'"
              >English<input
                v-model="editing.nameEn"
                required
                maxlength="120"
                :disabled="pending" /></label
            ><label v-if="kind === 'menus'"
              >{{ t("排序", "Order")
              }}<input
                v-model.number="editing.position"
                type="number"
                min="0"
                max="100"
                required
                :disabled="pending" /></label></template
          ><label
            v-if="
              ['users', 'departments', 'menus', 'dictionaries'].includes(kind)
            "
            class="check"
            ><input
              v-model="editing.enabled"
              type="checkbox"
              :disabled="pending"
            />{{ t("启用", "Enabled") }}</label
          >
          <footer>
            <button type="button" :disabled="pending" @click="canLeave">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("保存", "Save") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>
