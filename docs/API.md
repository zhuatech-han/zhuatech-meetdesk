# MeetDesk 接口与状态 / API and state

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。

接口为同源 `/api`，使用 HttpOnly、SameSite=Strict 的会话 Cookie。修改前获取 `GET /auth/csrf` 返回的 header/token，再将 token 放入指定请求头。不要将会话、CSRF 或媒体 token 发布到日志/Issue。成功 JSON；无身份 401、越权 403、非法输入/状态 400、版本或持久约束冲突 409、限流 429。

Same-origin session APIs. Bootstrap CSRF before each write. Keep all credentials private. Errors use structured codes; unauthorized 401, forbidden 403, invalid input/state 400, persistence/version conflicts 409, throttling 429.

| Routes | Access and behavior |
| --- | --- |
| `POST /auth/login`, `POST /auth/logout`, `GET /auth/csrf` | Account/session lifecycle |
| `GET /guest/identity`, `GET /guest/me` | Current minimal identity; expired sessions denied |
| `POST /guest/join` | Code/displayName invitation redemption, wait for admission |
| `GET /meetings`, `GET /meetings/{id}` | Organization scope or the guest's invited meeting |
| `POST /meetings`, `PUT /meetings/{id}` | Host plan creation and version-protected edit |
| `POST /meetings/{id}/state` | version + status LIVE/ENDED/CANCELLED |
| `POST /meetings/invites/redeem` | Signed account invitation redemption |
| `POST /meetings/{id}/join`, `/leave` | Own admission lifecycle |
| `GET /meetings/{id}/attendees` | Admitted members; waiting users visible to host only |
| `POST /meetings/{id}/attendees/{aid}` | Host ADMIT/REJECT/REMOVE/SPEAK |
| `POST /meetings/{id}/silence`, `/controls` | Host all-view-only, admissionLock/chatEnabled |
| `POST /meetings/{id}/hand` | Own raised boolean |
| `GET/POST /meetings/{id}/invites`, `POST /.../invites/{iid}/revoke` | Host-only, raw code returned only once |
| `GET /meetings/{id}/attendance` | Host-only actual media observations |
| `GET/POST /meetings/{id}/messages`, `DELETE /.../messages/{mid}` | Admitted only, send while live; nonce deduplication |
| `POST /meetings/{id}/media`, `POST /meetings/media/{lid}/leave` | Own media ticket and revocation |
| `GET /options`, `/stats`, `/reports/meetings.csv` | Account category options, reports scope |
| `GET/POST /admin/{kind}`, `PUT /admin/{kind}/{id}` | users/roles/departments/menus/dictionary/settings per capability |
| `GET /audit` | Organization-scoped audit capability |

会议状态：`PLANNED → LIVE → ENDED`，或 `PLANNED → CANCELLED`。终态不能重新打开。准入状态：WAITING → ADMITTED/REJECTED；ADMITTED → LEFT/REMOVED。普通 LEFT 可重新申请，REMOVED/REJECTED 不允许同一身份自行返回。主持人媒体离开保留主持准入。

Meeting transitions are terminal after ENDED/CANCELLED. LEFT users may request admission again; removed/rejected identities cannot return directly. A host's media leave retains host admission.

Media tokens expire after 45 seconds for connection authorization; an established connection is polled and revoked separately. Tokens allow only camera, microphone and screen-video tracks according to current permission. `/api/media/authorize` is internal to Nginx and returns 404 externally. Never expose LiveKit Twirp or its raw port. This API does not implement SSO or cross-site embedding.

发布的源码以 LICENSE 非商业条款为准。商业授权、接口对接与集成：https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
