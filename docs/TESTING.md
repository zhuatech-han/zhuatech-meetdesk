# MeetDesk 测试说明 / Testing

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。

## 自动化层级 / Automated layers

- 后端 66 项测试：54 项真实 Security/JPA/Flyway 集成，使用独立 H2 测试数据库，RTC 网关与时钟替身；12 项媒体 JWT/管理协议单元测试。
- Frontend: 8 API/session tests, ESLint, Prettier and production build.
- Vue 模板使用 `vue/no-undef-properties` 检查未定义状态。浏览器另验证聊天输入：已准入主持人、成员及访客可以发送；等候、移出、会议结束或主持人关闭聊天时不能发送。API/session 测试不替代这些页面状态验收。
- Vue templates check undefined state with `vue/no-undef-properties`. Browser acceptance separately verifies the chat composer for admitted hosts, members and guests, and blocked waiting, removed, ended or host-disabled states. API/session tests do not replace UI-state acceptance.
- 配置脚本 4 项保护测试：保留已有私有配置、显式渲染、强密钥与私有路径边界。
- Actual HTTP acceptance: 162 checks against a fresh MySQL/Compose deployment, covering login, permissions, organization isolation, meetings, admission, guests, invitations, chat, controls, terminal state, audit and reports.
- 媒体验收使用两台独立 SDK 客户端通过真实 Nginx 准入层和 LiveKit，发布不同语音、合成摄像头与合成屏幕轨道；校验远端频率和解码颜色、持续接收、实际出席观察。短测另验证观看模式、移出断连及未过期旧票据拒绝。

H2 tests do not replace MySQL acceptance: the actual deployment check also validates schema migrations and read-only report behavior. No media service is mocked in the actual media tests.

## 可复现的独立实例 / Reproducible disposable instance

仅对空业务数据库运行 `quality.py`，不可指向客户环境。脚本会创建 TEST 用户与会议。配置覆盖使用 `MEETDESK_TEST_ENV`、`MEETDESK_TEST_BASE`；默认 `.env` 和本机 WEB_PORT。状态、测试密码及 cookie 写入忽略的私有文件，禁止进入发布内容。

```bash
python3 scripts/init-env.py
docker compose -p meetdesk-test up -d --build --wait --wait-timeout 240
python3 -m venv .venv-quality
.venv-quality/bin/pip install -r scripts/requirements-quality.txt
.venv-quality/bin/python scripts/quality.py
.venv-quality/bin/python scripts/verify-media.py
```

短测移出其访客，不复用已封禁身份。准备新访客后可以运行独立持续测试：

```bash
.venv-quality/bin/python scripts/prepare-media-guest.py
MEETDESK_MEDIA_SECONDS=1800 .venv-quality/bin/python scripts/verify-media.py
```

Short tests remove the disposable guest. `prepare-media-guest.py` admits a new TEST guest while preserving previous bans. The long test runs two real peers, sends media continuously, renews REST activity and checks increasing receives; it does not remove the guest at completion. A new join replaces any previous lease for that participant.

编码源为 320×180、6 fps 的 TEST 合成画面和 48 kHz 单声道低幅测试音。发布开始后预热两秒才计量，排除源尚未提供数据时的空白帧；实际接收与内容门限继续保留。不同音调通过 FFT 分辨，视频通过解码后 RGB 样本比对。合成轨道验证媒体通路，不能证明物理采集或公网兼容。

## 实际持续传输 / Actual continuous transmission

两个独立客户端完成 30 分钟持续传输及最终参会记录核验；接收音频帧分别为 180305 / 180306，摄像头两路和共享一路各解码 10818 帧，内容匹配。可达本机私网接口的强制 TURN 短测也通过真实音视频、共享、观看权限和移出重连拒绝。没有物理设备采集、跨公网或并发容量验收。

Two independent clients completed a 30-minute continuous media and attendance check. Received audio frames: 180305 / 180306. Each of the two camera tracks and one shared-screen track decoded 10818 matching frames. Forced TURN on a reachable local private interface also passed real media and moderation checks. This is not hardware, WAN or capacity acceptance.

## 手工验收与范围 / Manual acceptance and scope

浏览器检查登录、创建/编辑、成员、聊天、后台账号/角色/设置、统计、关于和响应式布局；真实媒体绑定与接收诊断可观察。截图必须为运行页面，TEST 标识不可隐藏。发布检查会核对相对图片、两个原始二维码、CN/EN 联系方式、LICENSE、源文件署名和私有凭据泄漏。

No physical camera/microphone/desktop capture, WAN/weak-network, browser matrix, load test or 32-participant test is implied. Target deployment must verify these separately. TURN forced-relay needs a reachable configured server and exact SFU peer restrictions; localhost results are not public-internet traversal proof.

备份恢复应在另一个全新项目与卷内验证，保留数据记录，重启后重新登录。清理只针对自己的可丢弃测试项目。商业授权与技术交付请访问 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
