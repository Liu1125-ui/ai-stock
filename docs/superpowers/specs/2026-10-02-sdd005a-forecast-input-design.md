# SDD-005A：版本化预测输入

日期：2026-10-02。依据五日切片设计与模型接入前核验。范围仅行情服务和 Python 采集候选；本批不发布模型、不开放用户预测、不改十三项业务和五服务边界。

## 目标与方案

为现有六维 Transformer 提供不可变输入快照，绑定原始行情版本、同来源后复权转换与未来日历。选用独立预测快照而不是扩大原始行情日期限制或一次性给当前 head 补因子：原始行情仍禁止未来实际日线，日常更新也不会覆盖旧预测输入。

用户已授权本代理负责常规可逆步骤并连续推进。本批在固定 stock-system 工作目录及现有 codex 分支实施，保留之前成果，不创建额外工作树；具体可逆接口细节自行决定并留证，不重复询问范围/执行方式。

## 候选契约

Python `POST /internal/v1/market/forecast-collect` 请求：operationId、instrumentId、rawVersionId、start、end、calendarThrough。每次仅一只已支持证券；start/end 为原始版本最后 60 行的首末日，calendarThrough 至少 end 后 5 日且最多后 90 日。operationId 1—128 位字母数字横线下划线，证券 `(SH|SZ)六位数字`，rawVersionId UUID。Python 请求必须先认证、限制 2 MiB 后解析；采集与现有行情共用单槽，子进程 25 秒超时，SDK登录/读/退出均检查失败。

成功候选：operationId、instrumentId、rawVersionId、provider=baostock、definition=baostock-backward-close-over-raw-v1、fetchedAt、sourcePayloadHash、bars、calendar。

bars 为按日期排序的一一配对记录：`{raw:RawBar, backwardClose:十进制字符串}`，raw 包括 date/OHLC/volumeShares/amountYuan/tradeStatus/sourceAdjustmentRatio=null。原始与后复权按相同代码/频率分别 query，字段/日期/代码/adjustflag必须匹配。calendar 为 start 至 calendarThrough 的每个自然日与开闭标记，绝不能用星期数代替源安排。候选失败只返回状态与受控错误，不能提供成功快照。

## MS-02 保存与查询

`POST /internal/v1/market/forecast-inputs` 受内部身份保护，接收上述候选。服务端核对原始 AVAILABLE 版本属于同一证券/Baostock/RAW，候选恰好匹配最后 60 行，日期及全部原始数值一致；禁止未来实际日线，禁止缺失/停牌/非正值/异常VWAP窗口。

采用独立表：forecast_input_version、forecast_input_adjustment、forecast_input_calendar、forecast_input_operation。它们均属于行情库，仅在该库建立 FK。原始版本与 head 不改写。快照包含定义、内容哈希、日历哈希、来源响应哈希与捕获时间。比率由 Java 用 backwardClose/rawClose 得出，正值 DECIMAL(24,12)，HALF_UP 舍入；原始价保留 RAW，用于六列转换。来源后复权收盘价精度核对 DECIMAL(28,12)。保存与操作回执在同一本地事务完成；同操作不同内容冲突，同内容重用快照；失败不影响旧快照/head。

完整日历必须覆盖60行输入全部日期，历史开市日期与这60行逐一相同；起点之后至少五个开市日。未来日历是预告安排，可至捕获日之后，但不代表存在未来价格。日历与转换均随 inputVersionId 冻结，calendarHash 是该日历清单版本标识；不把原始行情的历史日历字段扩展为未来行情。

`GET /internal/v1/market/forecast-window?instrumentId=&inputVersionId=&window=60&horizon=5` 返回原始版本/内容哈希、inputVersionId/inputHash、calendarHash、定义/来源/口径、截止日/原始起点价、60行附带因子的输入、五个目标日和新鲜度。未指定 inputVersionId 时只选当前RAW head的已保存快照并要求新鲜；指定版本用于固定输入/重试，可读历史快照且明确新鲜度。当前策略只支持60/5，后续通过新兼容定义扩展，不全局锁死业务。不存在、版本失效、错证券、缺日历或窗口均明确失败。

新鲜度沿用上海20时更新切点，以快照日历核对最近应有开市日。日历不足为UNKNOWN；默认新请求不能把STALE/UNKNOWN快照当作可用输入。固定版本不会静默换新版本。MS-03后续创建新预测仍须核对兼容、状态和时点。

## 验收

真实三股来源配对和未来日历入库；60行/五点日期正确；因子缺失/错日期/RAW数值差异/未来价格/缺日历/停牌窗口拒绝；并发同内容去重、同操作冲突、原始head切换仍可读固定旧快照；内部未认证/畸形/超大请求受控拒绝；既有全部回归通过。只保存候选与输入，不声称正式模型已发布、未来准确性已评价或公司行为点时语义全部通过。

## 实施补充（2026-10-03）
内部准备端增加GET /internal/v1/market/forecast-raw-window?instrumentId=，认证后读取当前Baostock RAW版本末60行与版本/哈希，返回不含技术指标的原始数字字符串。既有/api日线仍须Cookie登录。
