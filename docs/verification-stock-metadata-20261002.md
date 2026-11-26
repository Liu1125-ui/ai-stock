# 股票基础资料核验记录

日期：2026-10-02；对象为现有三只股票；股票池未扩充。已完成本次基础资料核验与接入，不等于正式预测业务完成。

## 真实接口结果
使用项目固定 Baostock SDK，调用 query_stock_basic，以股票代码精确查询；两次三股查询均成功，代码、唯一记录与字段一致。最后核验时点为 2026-10-02 22:38:05 +08:00（库内UTC 14:38:05.541）。

1. SH600030：来源代码 sh.600030；名称中信证券；上市日期 2003-01-06；type=1，status=1，退市日期为空。
2. SH600276：来源代码 sh.600276；名称恒瑞医药；上市日期 2000-10-18；type=1，status=1，退市日期为空。
3. SZ000002：来源代码 sz.000002；名称万科A；上市日期 1991-01-29；type=1，status=1，退市日期为空。

[Baostock官方接口说明](https://baostock.com/mainContent?file=pythonAPI.md) 将 type=1 定义为股票、status=1为上市、0为退市。agent-reach的Exa搜索返回该官方章节摘录；Jina Reader及网页工具未能获取全文，未声称通读网页。已安装SDK源码和真实返回共同核对参数及字段。上市状态不等于日线tradestatus的交易/停牌状态。

本次为同来源基础资料核验，不是跨交易所交叉核验，不证明日线全无误差或预测准确。完整原始响应保存于本机忽略目录，名称来自真实接口，没有按常识硬编码。

## 实际页面验收
已登录浏览器首页显示三股真实名称及“名称已核验”；原SH600030自选卡片显示中信证券及核验标志，个人引用保留。输入“恒瑞”仅返回恒瑞医药；打开分析页显示恒瑞医药、ACTIVE及已核验近期覆盖，行情截至2026-09-30；预测入口继续明确未接通。验收后恢复用户原行情首页，未添加或删除个人自选。

额外真实回归：从Baostock重新采一份仅含价格、名称未核验/状态UNKNOWN的中信证券候选，通过现有发布API后，三股已确认资料及核验时间完全一致，中信版本和revision2不变。证据price-only-source-regression.json、price-only-publish-regression.json及price-only-metadata-preserved.tsv。

## 数据校验与接入
在业务写入前核对六字段顺序、错误码0、每股一条、来源代码与系统ID一致、类型为股票、合法名称与日期，上市/退市日期及状态不矛盾。另采当前完整 RAW 日线与日历，将基础资料与价格源摘要组成证据哈希，通过已有受认证 MS-02 发布接口提交；未直写业务库或新增公开路由。

三股存储的名称已核验、listing_status=ACTIVE、metadata_source=baostock、metadata_checked_at为真实核验时点。IPO/退市日期用于本次校验及证据记录，当前instrument表没有这两个业务字段，不冒充已保存为可查询业务字段。

三股行情仍各424条，三个versionId与headRevision=2保持不变，总版本仍6个；重复同一operation返回replayed=true。已有自选仍引用SH600030，用户服务只保存证券引用，没有修改自选实体或跨库关联。

操作检查最初误用revision/reused断言，实际契约为headRevision/replayed；保存响应核对后按同一operation继续，重复调用安全复用，未重复建版本。MySQL命令行最初未设UTF8导致中文输出为问号，显式utf8mb4后确认真实存储正常；不是数据内容损坏。

## 正确性修复
1. 原发布SQL会在仅采价格时推进资料核验时间。新增三项MySQL测试均RED后修复：只有名称已核验或上市状态明确才记录来源/时间；NULL旧时间允许首次确认，较新未核验行情保留旧资料及核验时间，不重复建立日线版本。
2. 实际自选卡片原将“名称待核验”写死。批量行情摘要增量返回displayName/displayNameVerified/listingStatus，自选卡片读取该资料，缺失时继续待核验。资料归属仍在行情服务，用户服务和自选引用不改。兼容旧摘要时前端字段可缺省。
3. 新MySQL查询测试先RED，覆盖有行情/无行情的核验名称以及未知证券默认值；Vue测试先RED，覆盖已核验资料展示与缺失摘要。前后端输出使用同一行情资料来源。

## 测试与审查
最终全后端 auth-integration,cli-build clean verify：144项、失败0/错误0/跳过0；未知摘要三字段补充断言之后，6项查询MySQL测试再次通过。前端20项、严格TypeScript与生产构建通过。Python未修改，沿用上一阶段18项已通过证据，本次没有重复运行它们。

独立只读审查分别检查资料SQL和后续新增API/卡片差异，未发现Critical或Important；一项Minor建议补充未知摘要资料默认值断言，已落实并验证。MS-02与前端镜像已重建部署。包体警告仍属TODO-03。

审查未判断的事项由本会话处理：外部真实性由真实响应佐证，实际发布由API与数据库只读核对佐证，页面由实际登录浏览器核对；历史已错误推进的时间对现有三股通过真实重新核验覆盖，没有对全库实施假定迁移。现有模型只有共享资料核验时间，本次两字段同时核验，不新增按字段独立核验时间。卡片本次增加名称展示，不增加上市状态标签。全套结果来自本会话实际执行日志，未拿审查结论代替测试。

## 证据
忽略runtime/sdd005：stock-basic-raw.json、stock-basic-final.json、verified-candidates.json、publish-*.json、replay-*.json、metadata-and-heads-before/after.tsv、watchlist-references-after.tsv、metadata-red/green.log、summary-red.log、cards-red.log、final-backend.log、final-test-totals.json、summary-final-green.log、frontend-tests/build.log、final-images-build/start.log。SDK探测脚本是一次性核验工具，不作为自动资料更新服务。

## 后续边界
TODO-01中现有三股资料已核验，扩充股票池仍待实施；本次未增加新股票、行业业务或额外权限。下一步可以核对既有Transformer实验实现、权重和特征，制定正式五交易日预测接入切片。无Git remote，未声称已push或远程CI，代码由Codex实现。
