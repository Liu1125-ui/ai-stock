# 开发脚本

init-local-env.ps1：使用密码学随机数生成被 Git 忽略的本机 .env，已有文件不覆盖，不打印凭据。

start-local.ps1：PowerShell 7 下执行 Maven 测试打包、pnpm 安装与类型检查构建、Compose 启动和连接检查，失败时终止。

stop-local.ps1：只停止当前固定 Compose 项目的容器，保留数据库卷。

start-auth-test-db.ps1：启动专用临时MySQL8测试容器，端口13316、数据库stock_user_test，凭据只存忽略的runtime，数据使用tmpfs，不连接现用业务卷。

check-auth-task1.ps1：注入专用测试地址及临时凭据，以auth-integration配置执行整个后端clean verify，包含真实MySQL测试；失败则终止。普通Maven构建排除mysql标签，不能用普通构建代表数据库验收。

stop-auth-test-db.ps1：校验测试标签后仅停止该临时容器，tmpfs测试数据会丢失，不删除现用业务卷。

配置QQ邮箱.cmd / configure-smtp.ps1：隐藏输入SMTP授权码，保存至被Git忽略的本机.env；不打印凭据，不发送邮件。默认QQ邮箱465端口SSL并验证证书。

prepare-market-candidates.py：只读既有研究SQLite，核验源版本并输出三个RAW候选和来源清单到被忽略的runtime。
import-market-candidates.ps1：从被忽略的.env读取服务凭据，经行情服务HTTP发布候选，不直写业务库。
check-market-source.ps1：有界调用Python在线Baostock采集，报告状态，不自动发布。

refresh-market.ps1：本机服务身份读取忽略.env，通过MS-04内部接口提交/查询行情任务、查看尝试、重试失败任务或取消等待任务；不直写行情或调度库，不输出凭证。参数Action为Submit/List/Get/Retry/Cancel，默认三只已发布股票，可指定InstrumentIds与TargetDate。同一KeyPrefix重复提交复用既有任务。

SDD-004数据库测试新增stock_scheduler_test；check-auth-task1.ps1以auth-integration,cli-build运行全部后端真实MySQL测试。自动更新需在核验来源后显式设置MARKET_WORKER_ENABLED、MARKET_PLAN_ENABLED；MARKET_PLAN_INSTRUMENTS决定股票列表，本机已开启三股计划，提交的示例默认关闭。

prepare-forecast-input.ps1：以忽略.env中的内部服务凭据读取forecast-raw-window当前RAW版本末60行，采集同来源未复权/后复权配对及未来日历，经MS-02校验发布独立快照并复读核验。输出至忽略runtime/sdd005a/live/<KeyPrefix>，不打印凭据、不改RAW head。首次执行可指定-KeyPrefix；再次带同前缀及-ReuseSaved重放已保存候选，验证幂等，避免重复外部查询。默认三股与40自然日日历，仅作为预测输入准备；不发布模型或生成预测。

package-transformer-artifacts.py：仅封装已核验三份本机权重，对照原核验SHA256，安全重载并生成包含配置/缩放/运行实现哈希的不可变候选包；不覆盖旧包、不自动发布。使用既有模型Python运行，输出忽略runtime/model-artifacts供只读挂载。

verify-transformer-artifacts.py：从既有真实历史CSV与行情缓存准备45个回放窗口，加9个MS-02固定输入；在无网络容器对照原225点，核验五日、重复一致与误差容差。历史QA UUID是派生标识，不能冒充正式业务版本；不评估未来准确率。--prepare使用既有研究Python，--verify使用容器/app同构实现。

check-model-artifacts.ps1：通过MS-02取得当前三股FRESH固定窗口，再调用内部候选验收接口，三份权重各输出五点并重复核对；验证清单/检查点/输入/日历回显与五日期，脱敏证据写入忽略runtime/sdd005b/http。仅本机读取服务凭据，不发布模型、不直写库。

模型测试扩展：start-auth-test-db.ps1在同一隔离测试容器增加stock_prediction_test，check-auth-task1.ps1仅注入该库的限定URL/密码并在finally恢复环境，包含MS-03模型登记/事务回归。未接现用stock_prediction。

prepare-model-registrations.py：只读三个原S3_one_year研究报告与sealed catalog，核验seed/epoch/训练截止/实验策略一致，提取2024验证与2025/2026回顾指标为有界十进制字符串并保存原文件SHA256；输出忽略runtime/sdd005c/candidates，不训练或选择新模型。test_prepare_model_registrations.py采用虚构数值仅验证转换/防错，不是实验结果。
register-model-candidates.ps1：通过本机MS-03受认证内部接口导入三份PENDING；逐字段核对评价并重放同一UUID和registrationHash，重启后对照本机摘要，不直写库、不发布、不创建管理员、不打印凭据。首次运行先执行prepare脚本；已被人工审核发布后不继续用本批PENDING专用验收脚本。

初始化管理员.cmd：打开现有init-admin.ps1的交互入口，邮箱与昵称由本机输入，密码使用SecureString隐藏输入；通过临时非Web初始化容器运行，不将凭据写入命令参数或文件；本机可信初始化不属于普通用户邮箱验证码注册流程。初始化工具拒绝已注册邮箱或已经存在管理员；窗口保留受控完成/失败提示。
SDD007A：prepare-comparison-evidence.ps1 -From yyyy-MM-dd -To yyyy-MM-dd；最多3股各一次RAW/后复权/日历采集，使用KeyPrefix在runtime保留无凭据候选，重运行同KeyPrefix复用候选不重复采集，不改行情头。仅内部证据，不代表用户比较页面完成。

## 股票对比离线复核
check-comparison-snapshot.ps1使用已有SDD007A证据运行正式Java计算器，再由verify-comparison-metrics.py以Decimal独立复算三股三个周期的全部指标/曲线。依赖已构建后端JAR、JDK和Python，不调用供应商、不写业务数据库；结果保存到忽略的runtime/sdd007b/audit。

check-comparison-update.ps1：仅对市场/调度模块执行指定测试，读取本机忽略的隔离库配置，不打印密码；禁止配置到现用数据库。

比较持续更新审计：export-comparison-update-evidence.ps1仅接受本轮三股成功COMPARISON_UPDATE任务UUID，只读导出固定证据到runtime/sdd007d/live；check-comparison-snapshot.ps1可指定runtime内OutputDirectory，原B目录默认保留。真实首验未完成前不得把旧样本审计当作新数据验收。


## 五日预测独立复核

verify-forecast-evaluation.py：仅以专属数据库账号SELECT已保存快照、固定验证与同源实际证据，用标准库Decimal80位独立复算5点/8指标，核对交易日及10月5日既有哈希，并运行4项内存防错控制。不取外部行情、不重新预测、不调用Java计算器/MaaS、不写业务库，不输出凭据。当前工具仅验收五点均已核验的完整样例，缺失/部分状态会停止，不能把它当完整场景测试集。

在项目根目录使用已有Python运行：

```text
python scripts/verify-forecast-evaluation.py --prediction-id 19ddcb7c-3fba-4df3-ba4f-f8eebfeb3f14 --output runtime/verification-20261020/new-audit.json
```

输出必须为项目内新的JSON文件，已有文件拒绝覆盖。报告见docs/verification-five-day-20261020.md。工具读取本机Docker已有专属账号环境，仅输出复核摘要，禁止将容器环境或含秘密配置打印出来。

start-system.ps1：日常启动原部署。检查容器项目/服务标签，分阶段等待 MySQL、Nacos（HTTP与gRPC）、五业务服务、网关和前端；总等待默认600秒，Docker引擎启动另限120秒。已退出应用最多重启一次，不创建或重建容器。根目录启动系统.cmd调用本脚本，失败保留窗口；-NoBrowser适合命令行验收。

## 新股票接入与预测适配

首次接入通过指定代码和日期采集历史日线并保存候选，预检整批文件后再入库。已导入股票可加入已有调度名单。预测适配沿用现有同源输入窗口和已登记模型校验。新增只读制品检查接收指定股票和候选目录，核验五个目标日、输入与制品版本。正式预测依赖模型的已审核覆盖范围。

以下示例在 PowerShell 7 中运行。候选目录位于 runtime，预检通过后才执行发布命令。

~~~powershell
$stocks = @('SH600519','SZ000001')
$directory = 'runtime/market-onboarding/batch-01'
./scripts/collect-market-candidates.ps1 -InstrumentIds $stocks -StartDate '2025-01-02' -EndDate '2026-11-13' -CandidateDirectory $directory
./scripts/import-market-candidates.ps1 -InstrumentIds $stocks -CandidateDirectory $directory -ValidateOnly
./scripts/import-market-candidates.ps1 -InstrumentIds $stocks -CandidateDirectory $directory
./scripts/prepare-forecast-input.ps1 -InstrumentIds $stocks
./scripts/check-model-artifacts.ps1 -InstrumentIds $stocks -CatalogPath 'runtime/model-artifacts/catalog.json'
~~~

首次发布后，可把股票加入部署配置的 MARKET_PLAN_INSTRUMENTS。采集默认保存证券代码作为名称，证券名称和上市状态后续根据已核验资料补齐。收集或发布中断后先核对现有结果，使用原候选文件重跑导入可复用操作编号。采集使用 -ReuseSaved 可复用同期间候选文件。

预测准备要求当前行情覆盖通过检查，并形成六特征所需的六十条输入、同源复权因子和五个目标交易日。制品验证读取指定模型目录并核对模型声明范围、输入版本和五点输出。现有 Transformer 制品的模型声明及服务端审核规则仍以原三股为限。要正式支持新股票，还需完成新股票的样本外验证、扩展制品及两端验收规则，再登记新版本审核发布。上述最后一条对现有模型不支持的股票会报适配失败。

### 沪深 A 股搜索目录同步

复用首页股票搜索读取的股票目录。同步范围为 BaoStock 基础资料中状态为上市、代码段属于沪深 A 股的股票。写入仅涉及基础资料，使用同一事务，已登记记录通过重复键保护原资料。验收时分别搜索已有股票和新增股票，核对股票目录、行情版本和预测覆盖范围。

使用装有 BaoStock SDK 的 Python 环境执行。运行 python scripts/sync_stock_directory.py 默认只预检并显示数量，增加 --apply 将目录同步到正在运行的本机 Docker 数据库。容器名称默认 aistock-sdd001-mysql-1，也可用 --container 指定本项目的 MySQL 容器。

## 腾讯控股盘中报价

futu-live-quote.py 在 Windows 本机连接已登录的富途 OpenD（127.0.0.1:11111）。市场服务的 AISTOCK_MARKET_LIVE_PILOT_TOKEN 默认留空，试点关闭。启用本机试点时，通过未提交的本机环境为 Compose 行情服务和采集进程配置同一个随机值，禁止复用其他服务密钥。

装有 futu-api 的 Python 环境可运行 python scripts/futu-live-quote.py。默认把报价和连接状态送往本机行情服务映射端口 18082。可加 --seconds 30 做短时测试。终止采集后页面会标记失联并保留最后报价。首版快照保存在行情进程内存，服务重启后由下一次采集重新填充。行情接口仍要求登录，OpenD 和写入端口用于本机试点。

日常使用「启动系统.cmd」会在所有基础服务就绪后启动或复用采集器。启动脚本使用 aiStock 的 .venv-model 和项目运行目录 runtime/futu-live-sdk，采集日志保存在 runtime/futu-live-collector.log。
