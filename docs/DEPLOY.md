# MeetDesk 部署与恢复 / Deployment and recovery

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2。

## 同机 Compose / Same-machine Compose

Python 3.11+ 生成独立私有配置，Docker Compose v2 启动 MySQL 8.4、LiveKit 1.13.9、Java 21 API 和 Nginx 前端。管理员密码随机生成，只从本机 `.env` 读取。不要发布真实环境文件。

```bash
python3 scripts/init-env.py
docker compose -p meetdesk config --quiet
docker compose -p meetdesk up -d --build --wait --wait-timeout 240
```

Web `http://127.0.0.1:8131/`; health `/health`. Container ports for MySQL, Java and LiveKit management are internal only. Normal startup has no TEST business fixtures. Backend Docker builds execute tests; do not skip failures.

已有私有配置不会被脚本覆盖。修改 `.env` 媒体地址/端口后，保存自定义 YAML，再显式执行 `python3 scripts/init-env.py --render` 并重新启动服务。端口映射与 LiveKit 配置必须一致。

Existing private configuration is retained. Save custom YAML before explicitly regenerating it with `--render` after media address/port edits. Compose port mappings must match the media configuration.

## 本地分开开发 / Separate development

仍需启动自己的 MySQL 和 LiveKit。后端默认数据库地址使用 Compose 服务名；本机运行 Java 时改成自己的数据库 URL。禁止将真实密码写入命令历史或提交配置。

MySQL and LiveKit are still required. The backend's default database URL uses the Compose service name. For a host-run backend, configure a reachable database and media URL through environment variables, never committed secrets. MySQL requires TLS in the supplied URL; use the bundled server or configure valid TLS for your own database.

```bash
# Java 21 / Maven 3.9+, with environment variables supplied privately
cd backend
mvn -B spotless:check test
mvn spring-boot:run
# In a separate terminal, Node.js 24.19+
cd frontend
npm ci
npm run dev
```

`frontend/vite.config.js` 只代理 API 到本机后端 8080，不代理媒体。单独 Vite 模式用于页面/API 开发；完整媒体验收使用 Compose 网页入口和 Nginx 准入层。

The dev proxy targets backend port 8080 for APIs only. Use Vite for UI/API development; use the Compose web entry and Nginx admission gateway for complete media testing.

## 远程设备与公网 / Remote devices and public networks

本机回环配置不能供另一台手机/电脑使用。部署者设置网页绑定与 HTTPS 反向代理、可达媒体 IP、ICE TCP/UDP 和 TURN。网页 HTTPS 443 正常并不能证明媒体网络可用。

Loopback is not reachable from another device. Configure the HTTPS web proxy, reachable media IP and ICE/TURN ports. Working HTTPS alone does not prove media reachability. WebRTC capture requires secure contexts. Test target browsers, microphone/camera selection, screen picker, autoplay, device changes and network recovery on the actual deployment.

- `.env`: `BIND_ADDRESS`, `RTC_BIND_ADDRESS`, `RTC_NODE_IP`, media ports and `COOKIE_SECURE=true`.
- 支持 WebSocket upgrade，保留同源 `/api` 和 `/media/rtc`，不要对外代理 Twirp 管理 API。
- Allow only the required web and media ports; never expose database/backend/LiveKit API credentials.
- TURN 默认只允许精确的媒体节点地址作为受限 peer；Docker 私网容器地址可能随重建变化，需部署者核实。不要开放整个内网 CIDR。
- Built-in TURN here is UDP; TLS TURN 443, corporate firewall traversal and load balancing are not configured or validated.
- 同一台主机/同一域多实例要独立项目名、端口、凭据与 cookie 路径/名称，默认 cookie 名相同，端口不会隔离 cookie。
- This source is not approved as a public operated conferencing service. Operating requirements are outside this source delivery.

`configure-local-relay.py` 只针对本项目回环绑定的可丢弃测试配置，将精确 SFU 地址加入受限 peer 清单。Docker Desktop 的回环地址可用于直连，但强制 TURN 可能仍不可达；中继验收需将 `RTC_NODE_IP` 和 `RTC_BIND_ADDRESS` 设为客户端可达的实际节点地址，保留精确节点 peer CIDR 并验证端口。该助手不是生产网络自动配置工具。

Docker Desktop loopback may work for direct media while forced TURN fails. Relay acceptance requires a reachable node address, matching media bindings and exact SFU peer CIDRs. The local helper alone is not proof of relay reachability.

## 备份与独立恢复 / Backup and isolated restore

备份会短暂停止命名实例后端，暂停写入后使用一致性 SQL dump；包含账号散列、聊天及业务资料，文件权限 600，必须保持私有。不会备份 `.env`，部署者需独立保存原凭据。只能恢复可信的本项目备份，SQL 不是安全沙盒。

```bash
python3 scripts/backup.py --project meetdesk --output private-backups/meetdesk.zip
```

Backup temporarily stops the named backend while making a consistent SQL dump. The private archive includes account hashes and chat; protect it. Environment credentials are not included and must be retained separately.

Prepare a new private environment with non-conflicting web, media and relay ports and a separate runtime YAML. Both the configuration file and restore project must be new. Example names below are illustrative; choose unused ports.

```bash
python3 scripts/init-env.py --env-file .env.restore --web-port 8132 --tcp-port 7941 --udp-port 7942 --turn-port 7943 --runtime-file ./runtime/livekit-restore.yaml
# Privately edit .env.restore TURN_RELAY_START=7950 and TURN_RELAY_END=7960
python3 scripts/init-env.py --env-file .env.restore --render
python3 scripts/restore.py private-backups/meetdesk.zip --project meetdesk-restore --env-file .env.restore
```

恢复脚本校验内容与 SHA256，只创建全新 localhost 实例，拒绝覆盖已有项目、卷或网络。新管理员环境密码不覆盖恢复的账号；使用原数据库内账号密码登录。验证迁移、会议、角色、消息、审计及记录后再考虑切换。媒体本身不备份；会话需重新登录，旧媒体连接不继续。

Restore validates archive contents/hash, creates a new localhost instance, and refuses existing resources. Use the original account password, not the new bootstrap password. Verify migrations and business data before switching. HTTP sessions and media connections do not survive restart/restore.

## 维护 / Maintenance

同项目名 `up -d --build --wait` 保留数据卷。升级前备份、核对迁移、先测试独立实例。`down` 停容器但保留数据；`down -v` 删除卷，只用于明确可丢弃的测试实例。不要停止或删除其他项目。数据保留、备份加密、监控和安全补丁由部署者负责。

Commercial enterprise deployment and customization require written authorization. Contact https://www.zhuatech.cn/; English business contacts: han@zhuatech.cn, jack@zhuatech.cn, WhatsApp https://wa.me/8617521234993.
