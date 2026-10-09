# MeetDesk 操作手册 / User guide

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。

## 管理与计划 / Administration and planning

管理员在“登录账号”创建用户并分配组织与角色，在“角色与权限”设置能力和本组织/全部组织的数据范围。在“组织与设置”维护组织、菜单、分类与参数。停用账号或撤销角色参会权限会撤销媒体资格；不能停用最后一个有管理权限的管理员。密码重置不会把明文密码回写列表或日志。

Administrators create users, assign organizations and roles, configure permissions/data scope, and maintain menus, categories and limits. Disabling an account or revoking its role attendance capability revokes eligibility. The last eligible administrator is protected. Password reset does not return stored plaintext or log passwords.

主持人点击“创建会议”，填写名称、议程、分类、时间、人数与分钟数。计划可编辑或取消，开始后不能编辑计划。会议按主持人组织划分，其他组织账号没有越权参会资格。修改表单版本冲突时刷新后再操作。

Hosts create a plan with name, agenda, category, time, capacity and duration. Only planned meetings can be edited/cancelled. Organization scope is enforced server-side. Refresh a stale version instead of repeatedly submitting it.

## 邀请与等候 / Invitation and waiting

打开会议“邀请”，创建限次代码并自行分享。代码只在生成时显示，关闭后无法再次查看原文。撤销邀请不移出已批准成员。访客登录页输入代码和显示名，等待主持人批准；正式账号也可通过“使用邀请”申请。不要把真实邀请截图发到公开 Issue。

Create a limited-use invitation and share it yourself. Raw codes are shown once only. Revocation blocks further redemption but does not remove admitted participants. Guests enter the code and display name; account users use “Use invitation”. Both require host admission. Never post active invitation screenshots publicly.

等候者不能看成员聊天或接入媒体。拒绝或移出的同一身份不能自行返回；其他有效身份仍按新的准入申请处理，不声称具有跨身份防滥用能力。入会锁阻止新申请，不影响已有准入。

Waiting participants cannot read chat or join media. Rejected/removed identities cannot re-enter directly. A different valid identity still goes through admission; there is no cross-identity abuse prevention claim. Admission locks stop new requests without disconnecting admitted participants.

## 音视频、共享和主持 / Media and hosting

“连接”只建立媒体接收，麦克风和摄像头初始关闭，成员自行点击打开。设备菜单允许选择输入。分享使用浏览器系统选择器，需要成员主动选择屏幕/窗口，不包含系统音频。观看模式禁止音频、摄像头与共享发布，但可观看收听。没有强制远程开麦或开摄像头。

Connect first; microphone/camera remain off until explicitly enabled. Select inputs in the device menu. Sharing uses the browser's picker, requires the participant's choice and includes no system audio. View-only permits receiving but forbids all publication. Hosts cannot force devices on.

每个成员同时只保留一份有效媒体连接。新连接替换旧设备。连接统计展示实际接收数据，不把连接按钮点击或票据颁发当成成功。浏览器阻止声音播放时点击“恢复播放”。权限/设备不可用时页面给出错误，成员可更正后重试。

A participant has one active lease. A new device replaces the previous device. Receive diagnostics describe actual data, not clicks or token issuance. Use resume playback if autoplay is blocked. Correct the device or permission error before retrying.

主持人可以批准/拒绝等候、移出成员、切换观看权限、全体观看、锁定新入会、关闭聊天和结束会议。主持人仅离开媒体时保留主持资格，可以回来；结束会议不可撤回或重新开始。系统按开始时间与时长上限自动结束。

Hosts admit/reject waiting users, remove participants, control view-only, lock admission, toggle chat and end meetings. A host may leave media and return without ending the meeting. End is terminal. Duration expiry automatically ends the meeting.

## 聊天、记录和隐私 / Chat, records and privacy

聊天正文持久化，当前页显示最近 50 条并支持查看更早记录。同一请求 nonce 去重。本人可移除自己的消息，主持人可移除本会消息；正文清除后保留移除标记。会议结束后客户端不能再读取聊天，部署者仍需确定数据库内数据保留期限。

Chat persists, with 50-message pages and older history. Request nonces deduplicate retries. Authors/hosts can remove eligible messages; removed content is cleared. Chat becomes inaccessible to clients after meeting end, but deployers must define database retention.

“参会记录”从媒体服务约两秒一次观察连接起止，可能有采样误差。未连接的票据没有 connectedAt；断连时段和原因保留。后台统计连接次数是连接记录数量，不是去重参会人数或真实身份人数。CSV 不导出消息、邀请或媒体凭据。

Attendance samples the media server approximately every two seconds and has sampling uncertainty. Unused tickets have no connectedAt. Connection counts are session records, not unique attendees or verified identities. CSV excludes chat and credentials.

## 非商业使用 / Non-commercial use

公开源码学习版，未获上海如静知华信息科技有限公司书面授权不得商用。商业源码授权、定制、部署与集成请访问 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。English business contacts: han@zhuatech.cn, jack@zhuatech.cn, WhatsApp https://wa.me/8617521234993.
