# SDD-005B兼容模型制品与容器推理验收

日期：2026-10-03。依据：已核验Transformer权重、SDD-005A固定输入和本批规格/计划。当前交付的是候选模型运行单元，正式模型发布、本人预测任务/五点持久化和前端结果页尚未交付。

## 1. 本批实现

1. 将42/43/44三个种子的既有真实权重封装成不可变CANDIDATE包。清单绑定架构、缩放参数、检查点/运行实现哈希、来源、训练截止、六列特征和五日输出定义。原研究代码及权重未改。真实artifactId带制品/实现联合哈希后缀，保留旧包；catalog仅是候选目录。
2. 提取原Transformer必要类体，调整包内导入，保留MIT、上游提交及来源/提取哈希。使用weights_only安全重载和strict状态匹配；模型为60行六列量价输入、五日输出，并非Alpha158模型。
3. 新增受认证POST /internal/v1/model-artifacts/validate，返回validationOnly=true。输入严格检查数值精度、日期、因子和版本格式；输出绑定输入、RAW、日历及模型版本。inputHash回显MS-02摘要，tensorInputHash另校验转换/标准化后张量及目标日，不声称独立重算数据库摘要。
4. 算法容器实际安装PyTorch2.14.0+cpu、NumPy2.5.3，Python3.13.15，模型目录只读挂载。模型加载在工作子进程中执行，45秒超时，单槽保留至子进程结束，线程池使health可响应。

## 2. 测试及真实证据

1. 制品/推理单元11项经历缺模块RED到GREEN；HTTP边界6项经历路由/模块缺失RED到GREEN。审查修复后最终运行中的新容器完整Python测试44项通过、失败0；只有一项外部Starlette/AnyIO弃用提示。
2. 三股每股五个既有历史起点，三种子共45个真实历史窗口225点，无网络容器中与原CSV最大差5.960504978475001e-08，低于1e-6容差。重复推理完全一致。历史回放元数据的UUID和日历摘要为QA派生标识，不是MS-02业务版本，报告显式标记。
3. 当前中信证券、恒瑞医药、万科A的真实MS-02固定FRESH窗口，各60行；三种子共9组45点。实际HTTP调用每组再重复一次，共18次，结果完全一致，版本/哈希及五日期对应，最大标准化截断比例0。
4. 当前五个目标交易日为2026-10-08、10-09、10-12、10-13、10-14。参考价采用预测起点股权口径：起点RAW价乘预测比率，保存六位、展示两位；人工例10×1.02=10.20以及拆分价/量转换已在单元验证。
5. 实际未认证畸形请求401；认证后超过1MiB请求413；清单哈希错误MODEL_MANIFEST_HASH_MISMATCH且result=null；health为UP/candidateValidation=CONFIGURED/inference=UNAVAILABLE；正式/internal/v1/infer仍503/PUBLISHED_MODEL_NOT_CONFIGURED。
6. Java与前端未修改，本批不重复无关广泛测试，不冒充已完成预测页面验收。旧行情采集/权限/health测试包含在44项算法回归中。

脱敏汇总：[sdd005b-20261003.json](evidence/sdd005b-20261003.json)。完整本机证据保留在忽略runtime/sdd005b/container-replay.json及http目录；制品在忽略runtime/model-artifacts，只读挂载，不提交权重或凭证。

## 3. 复验方法

1. 在固定工程目录用既有研究Python运行scripts/package-transformer-artifacts.py --checkpoint-directory E:/aiStock/实验与测试/联合研究/顺序改进验证/结果。脚本校验既有来源哈希，旧包不覆盖。
2. scripts/verify-transformer-artifacts.py --prepare从既有研究CSV/行情缓存与固定窗口生成回放包；容器--network none，脚本/模型只读挂载，再执行--fixtures /evidence/replay-fixtures.json --output /evidence/container-replay.json核验迁移。明确这些已见历史结果只证明迁移一致性。
3. 运行scripts/check-model-artifacts.ps1取得实际HTTP五点与重复验证，不直写库或发布模型。
4. 完整测试命令：docker compose --env-file .env -f deploy/compose.yaml exec -T algorithm python -m pytest -q -p no:cacheprovider。直接pytest脚本入口会漏/app导入路径，曾产生收集错误；改用Python模块入口后初版43项通过；最终审查修复后44项通过。只读/app不写测试缓存。

## 4. 边界和下一批

1. 本批不评价未来误差，不承诺预测准确率。迁移对照使用旧CSV而非重新挑选最好种子；三个候选均保留，不自动推荐或上线。
2. 后续先实现MS-03模型制品登记/管理员审核发布/版本可用性与回滚，再接本人预测请求、FRESH固定窗口、五点持久化和页面。正式请求不得将候选验收、STALE回放或备用规则混作成功模型预测。
3. 十三项业务、五服务、两个角色、实验单独授权及未来业务扩展规划保持。代码由Codex完成；无Git remote，未push或执行远程CI。
4. 沿用用户授权的固定目录和当前分支、常规实现自主推进；最小上游提取以来源哈希和225点保护兼容。ID哈希后缀保证实现修复时生成新包，代价是后续调用/登记使用完整ID。当前运行单元与正式业务分批验收，总代价为预测入口本批仍不可用。

## 5. 最终独立审查

一次独立只读审查范围f868780..c24234e：Critical0、Important1、Minor0。Important为小于1MiB的深层JSON触发未捕获RecursionError。新增test_deeply_nested_json_is_controlled_before_worker，实际观察RED解析栈溢出，再在解析边界捕获并返回422；GREEN及最终容器44项全部通过。真实HTTP深层请求422/MODEL_REQUEST_INVALID、health UP；重新九组18次真实请求均五点且重复一致。修复提交52fbfa9。无第二次审查、无新增延期Minor；模型计算实现与制品ID/哈希未改。

审查确认固定架构、安全strict加载与哈希、有界数值、单槽生命周期、候选验证/正式发布边界；超时与取消采用代码核对及已有单元，不虚构真实45秒超时压力测试。未来准确率、MS-02数据库摘要独立重算、管理员发布与用户预测均在后续批次验收，本批不对其作通过结论。

完整处置与决策见[sdd005b-review-and-fixes.md](sdd005b-review-and-fixes.md)。
