# 本机部署

作用：Compose 启动五业务服务、网关、算法、前端、MySQL 和 Nacos。仅暴露回环端口。五服务使用五个独立数据库账号，通过 Nacos 调用，不直接跨库查询。

日常启动已有部署：双击根目录“启动系统.cmd”，或执行 `pwsh -File scripts/start-system.ps1`。此入口不构建、不重建、不初始化配置；缺少原容器则停止。下述 start-local 是开发构建流程。

前提：Docker Desktop 的 Linux 引擎运行，Java 17、Maven、Node、pnpm、PowerShell 7 可用。工程根目录执行 `pwsh -File scripts/start-local.ps1`，依次生成本机凭据、构建后端、构建前端、启动容器和验证连接。预览地址 http://127.0.0.1:15173 。

执行 `pwsh -File tests/check-compose.ps1` 会短暂停止行情服务以验证 Sentinel，然后自动恢复。普通连接检查使用 `-SkipFault`。执行 `pwsh -File scripts/stop-local.ps1` 停止本项目容器，保留数据库卷。不要使用 `down -v`。

MySQL 初始化脚本只在空卷初次运行。已有卷修改 .env 不会自动修改数据库密码；先保留原凭据，按数据库迁移流程更新。Nacos 为本机 standalone 开发实例，认证关闭，禁止将其直接用于公网部署。账号注册、会话及管理员权限已接入；股票业务具体进度以docs/implementation-status.md为准。

## 账号业务使用

访问 /register，以邮箱接收验证码并注册；/login 登录；/profile 查看本人资料、修改昵称和密码。验证码与SMTP授权码不写入Git。SMTP通过 scripts/配置QQ邮箱.cmd 在本机隐藏输入配置。

首次管理员由工程根目录运行 pwsh -File scripts/init-admin.ps1，按提示隐藏输入密码；普通注册不会生成管理员，也不会自动提升现用账号。后台仅管理普通用户及其独立实验授权。

本阶段验收见 docs/verification-sdd002.md。执行 tests/check-user-business.ps1 -Fault 或 -GatewayFault 会短暂停止对应服务并在finally恢复，不能与用户正在操作的请求同时运行。

管理员任务查询由调度服务逐请求校验管理员会话，使用USER_SERVICE_URL定位账号服务；不开放内部任务创建/重试/取消。

## 管理员任务受控操作部署

SDD-009B新增调度库V9版本字段与操作记录表。ADMIN_TASK_ACTIONS_ENABLED默认false；ADMIN_TASK_RETRY_COOLDOWN_SECONDS默认60。先备份调度库和旧镜像，部署时保持新入口关闭，完成只读检查；真实重试/取消需另行限定具体任务及供应商调用预算，验证后才开放。关闭入口不会阻止原回执/历史读取，不修改既有worker开关。回退旧调度镜像前先关闭入口，不能混跑不维护revision的旧worker，不自动删除新增表列或倒灌旧备份。

## 2026-10-28：行情版本引用检查（SDD010B2A）

本机已按owner预测/调度/通知→行情聚合→网关/前端顺序部署，无schema迁移。保留原账户/算法/数据库卷。仅新增三个内部引用服务地址（容器DNS），网关market-reference-public专用18秒传输不改变既有行情5秒配置；不对公网暴露内部Token接口。

部署前五库和六旧镜像在runtime/sdd010b2a/backup-20261028保全。若回滚，按old-images.json与镜像归档恢复这六个旧镜像并重建相应容器，不还原或删除数据库卷；本次无结构变更，因此不为回滚覆盖现存业务记录。恢复演练尚未执行。后台暂停仅用于只读指纹比对，结束后原八项开关已恢复；人工行情重取开关false。核验见docs/verification-sdd010b2a.md。

## 2026-10-29：版本审核记录（SDD010B2B）

新增市场库V14（market_version_review及market_version_review_operation），仅保存审核修订和追加历史，不改变行情状态。市场迁移成功后再部署网关及前端；七应用健康及运行产物匹配。暂停只读核验期间后台八项开关，结束恢复原值，人工行情重取开关仍false。

当前行情库备份在runtime/sdd010b2b/backup-20261029/before.sql；旧市场/网关/前端镜像身份与runtime/sdd010b2b/backup-20261028/old-images.tar匹配。回滚只恢复旧镜像，保留V14及审核历史，不drop新增表、不覆盖业务库；旧程序忽略这些新表。恢复演练未做。原数据库卷不得删除。验收详见docs/verification-sdd010b2b.md。

## SDD010B3 Task3A协议暂存配置（未部署）

MARKET_VERSION_USE_PROTOCOL_ENABLED、MARKET_VERSION_USE_RECONCILE_ENABLED、MARKET_VERSION_USE_ENFORCED均默认false；环境模板与Compose仅预留，不修改已有.env。当前构建不允许强制enforcement开启，管理员动作仍不可用。新接口仅在/internal/v1/market-version-uses下使用服务认证，不新增公共网关路由。

Task3A只有内部协议和固定owner核对底座；真实证据入口/发布路径门禁及全部owner尚未接入，不将本阶段单独部署或开启flag用于演示停用保护。后续按原维护窗口与在途清理规则受控上线，不因表迁移有代码就提前启用。

Task3B新增MARKET_VERSION_EVIDENCE_GATES_ENABLED=false，保持原实际.env和运行容器不变。代码已保护原计算证据路由并固定RAW，但未完成转发这些头的全部owner；只能在隔离测试中显式开启，不能提前用于现有系统。全局enforced仍拒绝开启，管理员动作始终关闭。

Task4B仅新增PREDICTION_MARKET_USE_ANALYSIS_ENABLED与PREDICTION_MARKET_USE_RECOVERY_ENABLED，模板均false。不要在生产单独开启MARKET_VERSION_EVIDENCE_GATES_ENABLED或只启分析owner，预测/验证/Python和其余owner尚待接入。证明接口即使停止新分析登记仍可核对原已绑定终态；迁移和部署须在Task8统一维护窗口验收。

Task4C新增PREDICTION_MARKET_USE_FORECAST_ENABLED、PREDICTION_MARKET_USE_VERIFICATION_ENABLED、ALGORITHM_MARKET_VERSION_USE_REQUIRED，模板均false。Java各owner及Python正式推理只在完整消费者部署与维护窗口验收后统一启用；不能只凭三个目的已接入就开启管理员停用。Python健康报告版本支持与required实际配置，不表示模型准确率。

Task5A新增PREDICTION_MARKET_USE_COMPARISON_ENABLED，模板false。仍未齐备持仓、通知和市场本地生产者，不单独打开全局门禁或管理员动作。部署前必须等完整Task5—8检查。

Task5B PREDICTION_MARKET_USE_PORTFOLIO_ENABLED默认false；全局尚未完成消费者检查，不部署开启。

Task5C新增通知侧使用登记与恢复开关，默认false；独立固定MARKET_USE_URL映射容器行情服务，不能提前开启全局动作。

Task5D2 MARKET_LOCAL_COMPARISON_USE_ENABLED/RECOVERY_ENABLED默认false；开启前必须同版本启用protocol和evidence gates。其他本地派生尚未完成，全局enforced仍拒绝开启，当前不部署。

## 周训练部署

使用 `deploy/compose.training.yaml` 叠加主 Compose 配置。构建前打包预测服务和调度服务。部署时为训练配置独立环境文件，并与项目原环境文件一起传给 Compose。

- `TRAINING_INPUT_DIR` 指向训练输入目录。其 `current` 保存 calendar.json 和 readiness.json，`presets` 按配置 SHA-256 命名 JSON 文件，`snapshots` 按数据 SHA-256 命名子目录。保留已接单任务引用的全部历史快照和预设。算法执行入口还需要存在 `legacy/manifest.json`；只使用滚动预设时可用经校验的冻结快照填充 legacy。
- `TRAINING_ARTIFACT_DIR` 指向已有模型制品目录，保留原制品。算法容器 UID 10001 需要写权限，以便候选制品能落盘并由同一算法服务验签。执行回执写入独立持久卷。
- `TRAINING_WEEKLY_OWNER` 配置固定 UUID，`TRAINING_WEEKLY_SCAN_ENABLED` 默认 false。调度端仍处理已接单任务。首次部署先核对快照和推荐模型，再打开扫描。停用新接单时将该开关设为 false 并重建预测服务。

在仓库根目录使用 `docker compose --env-file .env --env-file <训练环境文件> -f deploy/compose.yaml -f deploy/compose.training.yaml config --quiet` 检查配置。通过后使用相同参数执行 `up -d --build algorithm stock-prediction-service stock-scheduler-service`。候选登记为待审核，由管理员决定是否发布。

### 定时采集入口

周训练目录的 `seed` 下提供 `archive/index.json`、同目录的 40 股原始归档、`cohort.json` 及 `preset-template.json`。预设模板包含 configurationId、seed、epochs、train、validation 和 holdout。训练与验证区间各包含 from 和 through，留出区间提供 from，through 由最近交易日生成。启用前在训练环境文件中填写 TRAINING_SEED_INDEX_SHA256、TRAINING_COHORT_SHA256、TRAINING_SEED_START。最近的成功归档会作为下一批的起点。容器 UID 10001 需要写入训练输入目录。

先按上节构建算法镜像，再用相同 Compose 配置执行 `up -d training-refresh`。首次启动即尝试，默认每小时轮询，已完成同一截止日后跳过重复取数。每轮在容器日志记录 PUBLISHED、UNCHANGED 或失败状态。验证输出后配置 TRAINING_WEEKLY_SCAN_ENABLED=true。可用 `docker compose ... stop training-refresh` 停止采集，已发布报告和历史快照保存在挂载目录。
