# SDD-003 自选股与行情查询实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. 当前会话顺序实施，原生方式执行；最终按requesting-code-review进行独立只读审核，不并行修改共享认证与行情契约。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 已登录用户从真实证券目录搜索并维护本人自选股，查看带日期、来源和版本的日线及MA/MACD/RSI。

**Architecture:** MS-01保存个人引用；MS-02拥有行情版本、校验、发布与指标；Python执行唯一一套外部SDK采集；网关按接口白名单代理。查询读已发布MySQL版本，外部采集故障不覆盖旧版本。采集时机后续接MS-04统一调度，不新增独立业务队列。

**Tech Stack:** 当前Java17、Spring Boot3.2.5、JdbcTemplate/Flyway/MySQL8、Feign/Nacos、Spring Security；Python/FastAPI/Decimal/Baostock；Vue3/TypeScript/Pinia/Vite/Element Plus与按需ECharts。保留现有Java持久化方式，不在本批同时重写已验收账号模块；后端总体MyBatis-Plus要求仍须后续明确接入与验证，不宣称已实现。

**Spec:** [详细设计](../specs/2026-09-30-sdd003-watchlist-market-design.md)。[实际GitHub源码核验](../../sdd003-github-references.md)。

状态：用户已确认，Task1—6已实施并完成本机验证及独立审查；实际证据见执行台账与 verification-sdd003.md。

以下检查项保留最初计划原文；实际执行结果、调整与证据以执行台账和验收记录为准。

## Global Constraints

- 五个业务服务独立库与访问账号，不跨库查询或建立跨库外键。
- 仅USER/ADMIN；自选归属来自当前会话，客户端不得指定userId。
- 浏览器写请求均校验会话、CSRF和Origin；行情服务直接端口也核验会话，不能仅依赖网关。
- 统一ApiResponse与traceId；故障不含成功数据，源码不提交服务凭据、会话Cookie或Token。
- 初始查询三只已有真实样本：SH600030、SH600276、SZ000002；数据表不写死三只，DEMO不进入公开搜索和个人自选。
- 当前basis=RAW，原始日线独立保存；来源因子不可跨源混用，复权选择待独立核验后开放。
- 小数API使用字符串，Java BigDecimal、Python Decimal；候选建议精度沿规格第10节，正式发布拒绝溢出或丢失精度。
- 默认三个月，查询选项半年/一年；跨度一年为配置默认，Asia/Shanghai日历和20:00更新策略明确记录。
- MA5/10/20、MACD12/26/9、RSI14公式与播种按规格tech-v1，不用前端库重新计算。null不变成0。
- 新建实际代码目录附README，不建空壳目录；保留原Flyway V1/V2及工程联调功能。
- 初始管理员不自动创建；受控导入工具使用服务凭据，不通过直写业务库绕过校验。
- GitHub代码不整仓搬运；改编时保留许可证/版权和来源。不能把案例测试或宣传当本系统验收。
- 当前Baostock联网探测超时，旧数据截止2026-09-24；存量读取成功不能宣称在线更新成功。

## Review Focus

1. 用户A通过B的记录/伪造身份头修改自选：Task3 HTTP归属与双用户MySQL测试；会话失效拒绝，行情故障不删除个人记录。
2. Python查询异常和空结果混淆：Task4假SDK分别验证成功空、错误码、超时；Task1失败不切换head、候选非法不发布。
3. 图表数组顺序/原地修改与指标范围漂移：Task5验证open/close/low/high、输入freeze及null，Task2验证不同范围重叠日期指标相同。
4. 两个发布请求把旧候选覆盖较新head：Task1锁定单槽、revision及受控expectedRevision，过期请求409；同operationId重试复用结果。
5. 日历不足、停牌或公司行为造成“看起来最新”：Task2核验UNKNOWN/STALE/FRESH独立状态、停牌指标为空和未复权标签，不能用抓取时间替代最后行情日。

## 共享数据与接口

Task1定义以下Java数据类型，后续任务不得自行另造同名不同字段：

```java
public record RawBar(LocalDate date, BigDecimal open, BigDecimal high,
    BigDecimal low, BigDecimal close, BigDecimal volumeShares,
    BigDecimal amountYuan, int tradeStatus, BigDecimal sourceAdjustmentRatio) {}
public record TradingDay(LocalDate date, boolean open) {}
public record MarketCandidate(String operationId, String instrumentId,
    String ticker, String displayName, boolean displayNameVerified,
    String listingStatus, String provider, String priceBasis,
    Instant fetchedAt, String sourceVersionId, String sourcePayloadHash,
    List<RawBar> bars, List<TradingDay> calendar, Long expectedRevision) {}
public record Publication(String operationId, String versionId,
    String state, boolean replayed, long headRevision) {}
public record IndicatorPoint(LocalDate date, BigDecimal ma5, BigDecimal ma10,
    BigDecimal ma20, BigDecimal dif, BigDecimal dea, BigDecimal macd,
    BigDecimal rsi14) {}
```

公开DTO由这些值投影，小数字段转String；BigDecimal实体不能靠默认Jackson输出数字。候选未知字段拒绝。正常交易缺量额拒绝、停牌允许null；参数列表可为空但不能为null。来源和证券代码验证后才计算规范哈希，标记是否缺少日历。

MS-01个人记录DTO：WatchlistItem(String instrumentId,Instant createdAt)，WatchlistPage(List<WatchlistItem> items,int page,int size,long total)。身份复用现有com.aistock.user.session.Identity，其userId为UUID；WatchlistRepository.add/delete/count/list均接收UUID userId，不能自行改成可注入的客户端字符串。证券姓名与报价通过MS-02批量摘要，行情失败不改MS-01引用。

---

## Task1：行情版本发布、真实校验与指标核心

**Files:**
- Create: `backend/stock-market-service/src/main/resources/db/migration/V3__market_versions.sql`。
- Create: `backend/stock-market-service/src/main/java/com/aistock/market/quotation/{RawBar,TradingDay,IndicatorPoint}.java`。
- Create: `backend/stock-market-service/src/main/java/com/aistock/market/ingestion/{MarketCandidate,Publication,CandidateValidator,PublicationService,PublicationController}.java`。
- Create: `backend/stock-market-service/src/main/java/com/aistock/market/version/MarketVersionRepository.java`、`backend/stock-market-service/src/main/java/com/aistock/market/indicator/TechnicalIndicators.java`及各目录README。
- Test: 对应`CandidateValidatorTest`、`TechnicalIndicatorsTest`、`PublicationMysqlTest`。
- Modify: 行情pom的隔离MySQL测试配置、`scripts/start-auth-test-db.ps1`、`scripts/check-auth-task1.ps1`，新增stock_market_test及专用环境变量；保持现用库不清理。

**Interfaces:** `TechnicalIndicators.calculate(List<RawBar>): List<IndicatorPoint>`；`CandidateValidator.validate(MarketCandidate): void`；`PublicationService.publish(MarketCandidate): Publication`；内部POST `/internal/v1/market/versions`使用服务凭据。Repository只持有stock_market datasource。

- [ ] RED：恒价40个有效交易样本、MA第5条、RSI第15条、DEA第34条、停牌、非法OHLC与精度测试先失败。

```java
@Test void fifthBarHasMa5AndFlatRsiStartsAtFifteenth() {
    var points = new TechnicalIndicators().calculate(constantBars(40,"10"));
    assertNull(points.get(3).ma5());
    assertEquals(0,points.get(4).ma5().compareTo(new BigDecimal("10")));
    assertNull(points.get(13).rsi14());
    assertEquals(0,points.get(14).rsi14().compareTo(new BigDecimal("50")));
    assertNull(points.get(32).dea());
    assertEquals(0,points.get(33).macd().compareTo(BigDecimal.ZERO));
}
```

constantBars测试夹具用开始日期2025-01-02加自然日生成纯公式序列，明确不是交易日历或生产数据；tradeStatus=1、量1000、额10000、OHLC同值，ratio为null。

- [ ] 执行单测，保存RED；实现规格公式，正常样本推进MA/EMA/Wilder，停牌输出null且不推进。中间12位HALF_UP，无随机、无前端重算。
- [ ] 新迁移扩展instrument：listing_status、name_verified、metadata_source、metadata_checked_at；建source_code_map、market_data_version、market_daily_bar、market_calendar_day、instrument_data_head、market_ingestion_operation。source_code_map唯一provider/源代码，真实代码映射验证；已有DEMO记录兼容默认但不能公开。
- [ ] version含证券、来源、口径、候选/规范哈希、fetchedAt、coverage、quality、STAGING/AVAILABLE/REJECTED/UNAVAILABLE；daily/calendar只本库version外键；head主键证券/来源/口径且revision。操作记录唯一operationId/参数哈希，错误摘要脱敏。
- [ ] 使用MySQL独立库测试：相同内容复用版本、相同操作不同参数409、验证失败记录REJECTED且旧head不变、两发布冲突仅匹配expectedRevision者切换、旧版本日线仍原值。事务边界在本服务，发布远程调用不放事务内。
- [ ] 内部Controller先核验服务凭据和请求大小、规范格式；API错误BusinessException，有限体积：最多1500条日线/3000条日历/2MB候选为配置初值；超出413。数据库CHECK或事务校验拒绝非法价格及非RAW口径。
- [ ] 运行整个后端专用配置clean verify并确认账号81项未回归；记录实际新增数量，审核迁移与范围后只提交该功能。

## Task2：受保护证券检索、批量摘要和日线API

**Files:**
- Create: 行情`instrument/InstrumentQueryService.java`、`quotation/{MarketQueryService,MarketQueryController,MarketResponseMapper}.java`。
- Create: 行情`security/{SessionIdentityClient,MarketAccessFilter,MarketSecurityConfiguration}.java`；依赖Feign/Nacos+负载均衡+安全现有BOM。
- Create: 网关`MarketPublicClient.java`、`MarketApiProxyController.java`及测试。
- Modify: 行情pom/application配置、现有JdbcInstrumentRepository兼容真实字段、网关ProxyHeaderPolicy、auth错误路径识别。
- Test: `MarketQueryTest`、`MarketHttpSecurityTest`、`MarketQueryMysqlTest`、`MarketApiProxyControllerTest`。

**Interfaces:** `MarketQueryService.search(q,page,size)`、`instrument(id)`、`summaries(List<String>)`、`daily(id,from,to,basis,versionId)`；API路径按规格13节。市场会话解析POST `/internal/v1/sessions/resolve`请求token，服务凭据由服务器配置，失败关闭。

- [ ] 先写安全RED：未登录401、禁用/撤销拒绝、伪造X-User-Id无效、内部服务凭据缺失401；公开端口和网关入口都覆盖。
- [ ] 先写检索/查询RED：DEMO排除、%/_按字面、未知证券404、跨证券版本404、非法日期400、无head为NO_DATA、RAW之外400、最大片段一年配置、batch限100且单项无数据不影响其他。
- [ ] 实现数字字符串ResponseMapper、稳定排序和参数绑定；空目录为空列表而非返回DEMO。summary只取一次固定head；daily读取一次head后固定version，不在返回中混用新旧指针。
- [ ] 指标读取完整version计算再截取。重叠日期一致性测试：同版本三个月和半年请求的MA/RSI/DIF完全一致。freshness按有覆盖的日历及时区求FRESH/STALE，未知日历UNKNOWN，单独显示lastUpdateError。
- [ ] 网关GET白名单仅允许instruments及其detail/daily/summaries；不要用无限通配公开内部路径。MS-02安全配置只放行health、诊断及受凭据保护的internal，不配置默认口令。网关保留当前Cookie、禁止共享jar和外部身份头。
- [ ] 真实HTTP与MySQL边界回归通过后，提交查询功能。未配置行情不伪造价格供UI测试。

## Task3：本人自选股持久化与幂等操作

**Files:**
- Create: 用户迁移`backend/stock-user-service/src/main/resources/db/migration/V2__watchlist.sql`、watchlist目录README及`{WatchlistItem,WatchlistPage,WatchlistRepository,WatchlistService,WatchlistController,MarketIdentityClient}.java`。
- Modify: UserApplication已启用Feign，保留该配置并添加本服务客户端；网关ProxyHeaderPolicy与UserPublicClient/UserApiProxyController新增DELETE；对已有会话与CSRF链不另建认证。
- Test: `WatchlistMysqlTest`、`WatchlistServiceTest`、`WatchlistHttpTest`及代理DELETE测试。

**Interfaces:** `WatchlistService.list(Identity,int,int)`、`add(Identity,String)`、`delete(Identity,String)`；Repository所有SQL带userId；MarketIdentityClient核验内部真实证券返回InstrumentView，不直接访问stock_market。

- [ ] RED：A/B同证券各一条、A删除不影响B、同用户双线程添加返回同一记录、重复删除安全、DEMO/未知证券拒绝、市场503不写入。

```java
@Test void deletingOwnInstrumentDoesNotDeleteAnotherUsersRow() {
    repository.add(userA,"SH600030");
    repository.add(userB,"SH600030");
    repository.delete(userA,"SH600030");
    assertEquals(0,repository.count(userA));
    assertEquals(1,repository.count(userB));
}
```

fixture建立隔离库已验证两个账号；repository.add/delete本地事务，count查询本人记录，数据库连接严格断言stock_user_test。

- [ ] 迁移watchlist主键user_id/instrument_id，user_id仅本库user_account外键；instrument为REF，无跨库FK。默认createdAt由服务器Clock，不能让重复添加重写。
- [ ] 先在事务外核验真实证券，再执行唯一插入；重复键只处理该唯一约束，返回既有记录，不能吞掉其他SQL错误。删除只按当前userId+instrumentId，无需依赖行情在线，保证依赖失败时仍可移除本人引用。
- [ ] HTTP路由users/me/watchlist复用Spring Security登录和CSRF；Controller取Authentication内Identity，拒绝未知JSON。DELETE支持Origin、CSRF、Cookie代理，不把方法转换成GET。
- [ ] HTTP回归验证401/403、客户端userId注入400、分页、禁用账号、跨用户归属；真实MySQL并发测试通过后只提交此业务。

## Task4：Python采集适配与有来源的初始化工具

**Files:**
- Create: `backend/algorithm/app/market/{contracts,providers,collector}.py`与README；`backend/algorithm/tests/test_market_provider.py`、`test_market_contract.py`。
- Create: `scripts/prepare-market-candidates.py`、`scripts/import-market-candidates.ps1`、`scripts/check-market-source.ps1`及说明。
- Modify: `app/main.py`仅追加受凭据保护采集入口；requirements固定已验证Baostock版本范围/实际版本，Docker依赖；保持未发布模型infer503。

**Interfaces:** `BaostockProvider.collect(instrument_id,start,end)->dict`；`collector.run(operation_id,provider,instrument_id,start,end)->CandidateResult`；CandidateResult定义为operationId:String、state:String、candidate:MarketCandidate或null、errorCode:String或null、retryAt:ISO时间或null；正常空结果LEGITIMATE_EMPTY，错误返回SOURCE_TIMEOUT/SOURCE_INVALID等，候选数字字符串。Java发布契约沿Task1。

- [ ] 先用假SDK写RED，分别检查沪深显式映射、非法代码、login失败、query错误、正常空、字符串Decimal、停牌、finally登出，以及两个并发请求SDK临界区不重叠。

```python
def test_query_error_is_not_legitimate_empty(fake_sdk):
    fake_sdk.query_result.error_code = '10004006'
    result = collect_with_sdk(fake_sdk, 'SH600030', '2026-06-01', '2026-09-24')
    assert result['state'] == 'SOURCE_INVALID'
    assert result['candidate'] is None
```

collect_with_sdk为测试帮助函数：创建可注入SDK的BaostockProvider并捕获其受控结果，禁止调用真实网络；SDK结果字段、状态和迭代由fake_sdk fixture提供。

- [ ] 实现单一SDK边界与timeout；SDK进程全局会话锁覆盖login/query/logout，FastAPI用同步端点/线程池承接，不阻塞异步事件循环。进程整体deadline由受控短命采集进程执行，避免SDK无限内部重试让HTTP长期占用；超时终止仅本次子进程，不能杀其他Python。
- [ ] 来源代码SH/SZ直接映射，不猜裸代码的市场；adjustflag当前固定3，若读取比值需另行核验1/2语义。错误消息脱敏，不fillna(0)，不errors=coerce静默接受非法样本。
- [ ] prepare工具只读既有SQLite一致快照与三股版本，校验旧hash并导出新候选字符串/原版本ID/hash；无基本资料以代码标签/displayNameVerified=false；fetchedAt沿原versions.created转换Instant，不以导入时刻充当原获取时刻。研究浮点复权比值未经规范精度验证时RAW候选sourceAdjustmentRatio=null，保留原载荷哈希，不强行截断或宣称复权可用。证据注明2026-09-24截止、浮点研究载荷来源和日历未知，不生成假新行情。
- [ ] import工具经本机隐藏/忽略配置取内部服务凭据，HTTP提交候选；不打印凭据、不写业务DB。候选文件上限与hash验证通过才提交，重复操作ID稳定，失败记录保留。
- [ ] pytest通过后，真实网络探测单独运行有限三股样本并保留success/failure证据；失败不称已接通，旧版本可读独立验收。提交适配与工具。

## Task5：首页、自选列表与分析图表

**Files:**
- Create: `frontend/src/api/market.ts`、`watchlist.ts`；stores/watchlist.ts与测试。
- Create: pages/market/{MarketHomePage,MarketAnalysisPage}.vue，pages/watchlist/WatchlistPage.vue及目录README。
- Create: components/market/{WatchlistCards,MarketChart}.vue、chartOptions.ts及README/测试。
- Modify: router/index.ts、App.vue导航、EngineeringPage诊断路由；api/auth.ts处理新增受保护market路径401；package/pnpm锁记录实际ECharts依赖。

**Interfaces:** API使用现有request，不存cookie/token；watchlistStore.load(page)/add(id)/remove(id)；buildMarketChartOption(DailyResponse)->EChartsOption；响应类型与Task2String小数/状态/版本同名。

- [ ] RED：报价加载失败仍保留个人记录，添加503不插入/删除503不丢失，身份退出清状态；响应乱序不覆盖当前股票；图表原数组保持不变且OHLC顺序正确。

```ts
it('maps candle order without recalculating or changing API data', () => {
  const response = Object.freeze({ bars: Object.freeze([
    Object.freeze({ date:'2026-09-24', open:'10', high:'12', low:'9',
      close:'11', volumeShares:'1000', ma5:null, ma10:null, ma20:null,
      dif:null, dea:null, macd:null, rsi14:null, tradeStatus:1 })
  ]) })
  const option = buildMarketChartOption(response as DailyResponse)
  expect(getCandles(option)).toEqual([[10,11,9,12]])
  expect(response.bars[0].ma5).toBeNull()
})
```

该样例只测图形映射，完整DailyResponse由测试builder补固定元数据；getCandles只读取返回option的candlestick series，不实现重复映射逻辑。

- [ ] 按已核验ECharts官方思路组织K线+成交量+MACD+RSI四面板共享时间缩放，tooltip取同一API值且HTML转义；不导入示例MA函数、不平滑K线、不把null画0。组件mounted初始化，ResizeObserver resize，unmount dispose和断开监听。
- [ ] 首页导航为行情/自选股/个人中心，admin入口沿当前角色；搜索防抖和取消，键盘可操作，代码标签名待核验透明显示。当前无指数/预测/预警数据不摆虚构统计；工程检查移/diagnostics仍可访问。
- [ ] 每行查看分析有效；发起预测禁用并给出明确理由，不用假的结果页。分析页默认三个月、半年/一年，价格口径和未复权公司行为提示、数据日期及状态可见。手机布局不横向溢出，页面错误可重试。
- [ ] pnpm test/typecheck/build通过，实际数量与包体记录；账号10测试不回归。提交前端功能及必要包许可记录。

## Task6：部署、真实浏览器验收与独立审核

**Files:**
- Create: `tests/check-market-business.ps1`、`docs/verification-sdd003.md`、`docs/sdd003-execution-ledger.md`。
- Modify: compose.yaml/README、CI MySQL隔离库与Python测试、implementation-status.md、相关README及GitHub参考记录（实际是否改编源码）。

**Interfaces:** check-market脚本使用独立测试账号和隐藏密码或隔离自动fixtures；禁止读取当前用户密码/验证码或导出Cookie。故障脚本只有指定服务且finally恢复，不清现用卷。

- [ ] 后端全配置clean verify、pytest、前端test/typecheck/build通过；CI建立stock_user_test/stock_notification_test/stock_market_test但不使用生产SMTP。CI文件存在不宣称平台已执行。
- [ ] 保留已有卷部署迁移并检查Nacos/Feign；通过服务工具导入三只真实候选，核对三股行数与版本hash/date、重复导入不重复日线。公开目录不能DEMO；日期不能显示成今天。
- [ ] 浏览器专用测试账号完成登录→搜索→添加→查看分析→删除，真实账号可由本人操作；未授权不修改本人已有自选。测试账号重启用户服务后列表保持，图片可留作UI证据，但不能含密码/验证码。
- [ ] 停市场服务：自选引用可读且可删，行情503不出假图；停Python：已发布日线仍可查询，更新失败；停用户服务：受保护行情失败关闭。每次finally恢复并检查用户现用登录仍有效。
- [ ] 独立审查规格、接口、权限、事务、公式、SDK、源码许可证及真实/模拟界限；具体问题先复现再修复和针对性复测。未解决重要问题不交付。
- [ ] 更新台账、实际测试统计、在线数据源结果和部署限制，按功能小步提交。Git仍无远程则不推送，不伪称CodeArts生成。

## 覆盖自查与执行约定

AC-003-01/02→Task2/3；03→Task2；04→Task1/4；05→Task1/2/5；06→Task2/4/5/6；07→各任务及Task6；08→Task5/6。Review Focus五项均有所属回归。任何“完成”必须有对应实际运行证据。

Java新增组件根明确为backend/stock-market-service/src/main/java/com/aistock/market和backend/stock-user-service/src/main/java/com/aistock/user；测试根分别为相同模块的src/test/java下对应包。Files中的目录和花括号列举为明确组件清单，编写时展开成单独文件。测试帮助函数必须随所在测试文件实现，示例不能直接成为生产数据。公开视图小数字符串与内部BigDecimal明确定义，官方指标示例不替换规格公式。

先原生逐项执行，每步先RED再GREEN。模型发布、预测、复权切换、全量后台与周期调度未因本批暂不实施而从总范围删除。计划审核后实施，具体模块不得以新建文件夹代替完成。

## 前端视图定义补充

DailyResponse位于frontend/src/api/market.ts：instrumentId:String、versionId:String、provider:String、priceBasis:RAW、lastTradeDate:String或null、fetchedAt:String、checkedThrough:String或null、requestedRange:{from,to}、actualRange:{from,to}或null、quality:String、freshness:FRESH/STALE/UNKNOWN、lastUpdateError:String或null、bars:ReadonlyArray<DailyPoint>。DailyPoint包含date:String、open/high/low/close:String、volumeShares/amountYuan:String或null、tradeStatus:Number、ma5/ma10/ma20/dif/dea/macd/rsi14:String或null。所有API返回数字显示字段均沿此定义投影，前端仅进行图表数值转换。

Task5测试夹具补上述元数据后再freeze；getCandles从option.series中选择type=candlestick的数据，使用真实buildMarketChartOption结果，不另写测试专用生产替代实现。组件图表不能把来源数据的停牌null转成价格0。