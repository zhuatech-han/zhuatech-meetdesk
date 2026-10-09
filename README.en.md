[中文](README.md) · [English](README.en.md)

# MeetDesk · Self-hosted video meetings and remote presentations

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Website](https://www.zhuatech.cn/)

Java 21, Spring Boot, Vue 3, MySQL and LiveKit source for meeting schedules, guest waiting rooms, audio/video, screen sharing, host controls, chat and observed connection records. **Public source for learning and non-commercial use. Commercial use requires written authorization.**

Use it to learn and evaluate team meetings, remote product demonstrations and training Q&A. Organization accounts manage and attend meetings. External guests use time-limited, use-limited invitations and require host approval. Guest display names are not verified real-world identities.

No public meeting service is operated. No paid model or hosted media account is required: Compose runs the media service locally. Remote deployment still requires a reachable address, HTTPS and media network configuration.

## The meeting workflow

1. An administrator creates organizations, roles and accounts and assigns host or attendance capabilities.
2. A host schedules a meeting with category, agenda, time, capacity and duration. Planned meetings can be edited or cancelled.
3. A host creates an invitation. Account users and guests wait for approval; waiting participants cannot read chat or connect media.
4. After the meeting starts, participants explicitly enable their own microphone, camera or screen sharing. Hosts can lock new admission, switch participants to view-only, remove participants or disable chat.
5. The host ends the meeting, or the configured duration automatically expires. Ended meetings cannot restart; media authorization is revoked.
6. Review connection records, statistics and CSV meeting registers. Records come from media-server observations: **issuing a join token does not prove attendance**.

## Implemented interfaces and capabilities

| Module | Function and boundary |
| --- | --- |
| Meeting directory | Search, status filters, sorting, pagination; create, version-protected edit, cancel, start and end |
| Participant interface | Join, leave, raise hand, persisted chat and paged history, remove own messages; audio/video, screen sharing without system audio, device selection, connection and receive statistics |
| Guest interface | Invitation expiration and usage limits, isolated session, waiting room and invited-meeting access only; no chat/media before approval |
| Host interface | Admit, reject, remove; individual/all-participant view-only controls, chat toggle, admission lock, invitation creation/revocation and observed connection records |
| Administration | Login/logout, BCrypt, create/edit/disable accounts, password reset, roles and API permissions, organization scope, menu activation, category dictionary and settings |
| Reports and audit | Meeting states and observed connections, CSV register, organization-scoped operation records; no chat/invitation credentials in exports |
| Maintenance | Persistent MySQL volume, Flyway migrations, health checks, private configuration generation, consistent backup and isolated restore |

Each participant has one active media lease; a new connection replaces the old connection. View-only disables microphone, camera and screen publication while retaining receiving access. A host cannot remotely force devices on. Revoking an invitation stops future redemption; removing an admitted participant is a separate operation.

## Actual application screens

Records are labelled TEST. Media views show synthetic camera and screen tracks transmitted through the actual media service, with no real people or desktop captured.

**Login: separate account and invited-guest entry.**

![Account login](docs/screenshots/01-login.jpg)

**Participant home: meeting directory, states, capacity and search.**

![Meeting directory](docs/screenshots/02-meetings.jpg)

**Core meeting: camera, shared screen, participants and chat.**

![Video conference and screen sharing](docs/screenshots/03-conference.jpg)

**Administration: account status, organization and role assignment.**

![Account administration](docs/screenshots/04-users.jpg)

**Statistics: observed connections and meeting states.**

![Meeting statistics](docs/screenshots/05-dashboard.jpg)

**Authorization: role capabilities and organization scope.**

![Roles and permissions](docs/screenshots/06-roles.jpg)

**Guest interface: invitation redemption waits for host admission; chat/media are unavailable beforehand.**

![Guest waiting room](docs/screenshots/12-guest-lobby.jpg)

## Architecture and source layout

Browsers use same-origin Nginx for Vue and the Spring Boot API. Business records reside in MySQL. Media signaling passes backend admission authorization before being proxied to LiveKit; ICE/TURN use separate network ports. LiveKit management, backend and database ports are not directly exposed.

| Layer | Versions |
| --- | --- |
| Backend | Java 21, Spring Boot 4.0.7, Spring Security, JPA |
| Frontend | Vue 3.5.43, Vite 8.1.5, livekit-client 2.22.3, Node.js 24.19+ |
| Storage | MySQL 8.4, Flyway, Hibernate schema validation |
| Media | LiveKit Server 1.13.9; WebRTC camera, microphone and screen sharing |
| Deployment | Docker Compose v2, Nginx 1.29 |

```text
backend/                         Java APIs, authorization, admission, media polling
  src/main/resources/db/migration/  V1 identity/catalogs, V2 meeting business
  src/test/                        Integration and media protocol unit tests
frontend/                        Vue UI, actual media tracks, Nginx
scripts/                         Configuration, acceptance, media tests, backup/restore
compose.yaml                     Four services and isolated persistent MySQL volume
.env.example                     Configuration names and safety notes
runtime/                         Private media configuration; never commit
README.md / README.en.md         Chinese and English documentation
docs/                            Operations, deployment, API, licenses, actual screenshots
```

## Requirements, startup and database initialization

Docker Engine / Desktop, Compose v2, Python 3.11+, preferably 4 CPU and 4 GB free memory. Independent backend development requires Java 21 and Maven 3.9+. Frontend development requires Node.js 24.19+ and npm.

```bash
git clone https://github.com/zhuatech-han/zhuatech-meetdesk.git
cd zhuatech-meetdesk
python3 scripts/init-env.py
docker compose -p meetdesk config --quiet
docker compose -p meetdesk up -d --build --wait --wait-timeout 240
```

Open [the local application](http://127.0.0.1:8131/) and [health endpoint](http://127.0.0.1:8131/health). The initial username is `admin`. Read the randomly generated password from the **private local `.env` field `ADMIN_PASSWORD`**. There is no shared default password. Never publish `.env`, terminal credentials or private backups.

Flyway applies `V1__identity.sql` and `V2__meetings.sql` to the empty database and initializes an organization, five roles, menus, categories and settings. The administrator is created only if absent. Normal startup inserts no test meetings or participants. Database `zhuatech_meetdesk`, user `meetdesk`; a Compose volume persists accounts, meetings, messages, invitation hashes and connection records.

Back up before upgrading, then rebuild/start with the same project name. Flyway validates existing migrations and applies new ones. Do not change applied migrations. Do not use `down -v` on data you need to retain. Changing the initial password environment variable does not reset an existing account.

See [deployment instructions](docs/DEPLOY.md) for separate development and isolated recovery.

## Configuration

| Variable | Purpose |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` / `DATABASE_PASSWORD` | Independent database secrets, randomly generated |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | Initial administrator and strong password |
| `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` | Matching backend/media credentials, never public client keys |
| `WEB_PORT` / `BIND_ADDRESS` | Web entry, defaults 8131 / 127.0.0.1 |
| `RTC_BIND_ADDRESS` / `RTC_NODE_IP` | Media port binding and client-reachable media IP |
| `RTC_TCP_PORT` / `RTC_UDP_PORT` | Media TCP / UDP, defaults 7921 / 7922 |
| `TURN_UDP_PORT` | TURN UDP, default 7923 |
| `TURN_RELAY_START` / `TURN_RELAY_END` | TURN relay UDP range, defaults 7930–7940 |
| `LIVEKIT_CONFIG_PATH` | Private media configuration inside `runtime/` |
| `COOKIE_SECURE` | true for HTTPS deployment; false for local HTTP tests |

Override conflicting ports in `.env`. After changing media ports/addresses, use `python3 scripts/init-env.py --render`. This overwrites the media configuration: preserve custom edits first. Without `--render`, existing configuration is retained.

Administrator settings default to 16 admitted participants, 120 minutes, invitation validity 24 hours and message length 2000. Allowed ranges are 2–32 participants, 15–480 minutes, 1–720 hours and 100–4000 characters. The admission limit is not proof of a load test at that size.

## Tests

```bash
cd backend
mvn -B spotless:check test package
cd ../frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..
python3 scripts/test-config.py
docker compose -p meetdesk config --quiet
git diff --check
```

Actual HTTP acceptance runs only against a **fresh disposable instance**, creating TEST accounts and records:

```bash
python3 -m venv .venv-quality
.venv-quality/bin/pip install -r scripts/requirements-quality.txt
.venv-quality/bin/python scripts/quality.py
.venv-quality/bin/python scripts/verify-media.py
```

Ignored `private-*.json` contains test sessions; never upload it. Media checks use two independent clients with distinct tones and synthetic camera/screen tracks, checking audio frequencies and decoded colors. They do not access physical cameras, microphones or desktops. Short media acceptance removes its test guest; repeated and long tests require a newly admitted guest. See [testing and limitations](docs/TESTING.md).

## Deployment, security and troubleshooting

- Loopback defaults are for same-machine tests. Remote devices need reachable web and media addresses configured by the deployer.
- Microphone, camera and screen capture require HTTPS outside localhost. The proxy must support WebSocket, and ICE TCP/UDP/TURN must be reachable.
- When connected login cannot join media, check the live state, admission, view-only restrictions, revoked tickets and media ports. Media failure does not itself mean the chat session expired.
- Check device/playback restrictions for silent audio; the UI provides resume playback. Shared screens contain no system audio.
- Account and guest HTTP sessions are held in one backend's memory. Backend restart requires a new login or redemption of a valid invitation. Business records persist.
- CSRF, API permissions and organization scope are enforced server-side. Password changes, disabled accounts or revoked role permissions, logout, removal and meeting end revoke eligibility. Media polling runs approximately every two seconds and retries during faults; instantaneous removal is not guaranteed.
- Invitations are shown only when created; only their hashes are stored. Protect shared links. Chat is normal server-persisted content, not end-to-end encrypted.
- Do not expose LiveKit management ports. Protect infrastructure/proxy logs, database backups and private environment files; raw media credentials are not logged by the application gateway.

## Known limitations

Single backend and single media node. Catalog queries are limited to 10000 rows with client-side search/pagination; large-scale server pagination and archival are not implemented. No clustering, load testing, 32-party acceptance, real hardware capture tests, broad OS/browser compatibility or public-internet/weak-network acceptance. No recording, replay, transcription, AI minutes, PSTN, system audio sharing, file uploads, SSO, business-system embedding or payments. Cross-site iframe embedding is blocked by default. Ended meetings no longer expose chat to clients; persisted messages require deployer-defined retention policies.

The documented source can be built, deployed and its workflows evaluated. **Local acceptance is not a production-readiness promise.** Enterprise delivery requires target-device/network/capacity/security/recovery acceptance and commercial authorization.

## License, contribution and feedback

Own code follows [LICENSE](LICENSE): personal learning, technical research and non-commercial exchange only. Commercial use requires written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. This is neither free-commercial MIT/Apache licensing nor an OSI open-source license. Third-party components retain their own licenses and copyright; see [third-party notices](docs/THIRD_PARTY.md).

Issues should include the version, sanitized steps and expected behavior. Never publish passwords, invitations, cookies, customer data or SQL dumps. Small improvements with tests and documentation are welcome. Report vulnerabilities privately to ZhiHua Technology. Software is provided as-is; see LICENSE for the disclaimer.

## Contact ZhiHua Technology

For commercial source licensing, deployment, integration or deep customization:

- [Website](https://www.zhuatech.cn/)
- [han@zhuatech.cn](mailto:han@zhuatech.cn)
- [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- [WhatsApp +86 17521234993](https://wa.me/8617521234993)

ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.) provides this public source learning edition for personal learning, technical research and non-commercial exchange. Written authorization is required for commercial use. Services include enterprise digitization, SME digital and AI transformation, private deployment, software outsourcing and implementation, FDE outsourcing, OPC support and deep customization. Contact us through the website or the business contacts above.
