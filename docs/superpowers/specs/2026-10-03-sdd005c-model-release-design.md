# SDD-005C 模型登记与审核发布设计

日期2026-10-03。目标：在MS-03独立库登记三份既有候选，管理员可查看真实评价、发布/未采用/下线、推荐切换和回滚，普通用户只读已发布版本。本批不接用户预测任务，不改变原权重、不保证准确率；/infer仍未接业务执行。依据需求FR-12/AC-12及用户全权负责、先计划检查再做的授权，按architectural路径自主设计与执行。

## 方案与边界

1. 选择MS-03保存不可变制品元数据/评价和可变发布状态，Python仅检查本机只读制品。直接改文件status会破坏清单哈希，跨库联表读取用户会破坏服务边界；均不采用。Python新增受认证inspect，安全strict重载后返回原manifest，与五点验收共用有界工作槽。不变更计算实现/既有包ID。
2. 初版仍三股/60行/六列/五交易日；制品自述为CANDIDATE，业务状态为PENDING/PUBLISHED/REJECTED/OFFLINE，两者独立。支持集合/周期由manifest展示，扩展时登记相容新制品，不能改名兼容。
3. trusted internal登记只接受artifactId、manifestHash及结构化评价；请求<=64KiB。评价包含源报告SHA256、种子、2024验证MAE/基线、2025/2026回顾MAE和2026方向率，均有限非负受限十进制字符串。导入脚本从原JSON提取且保留源hash；这是可信服务导入的溯源，不宣称服务独立重算原研究报告。原评价为回顾性开发证据，不是未来盲测。

## 存储与事务

1. MS-03 Flyway V1仅在stock_prediction建model_version/model_release_head/model_release_event。模型UUID独立于artifactId，artifactId唯一；清单/评价/registrationHash不可改，重复相同登记复用，不同内容冲突。head固定初版兼容组，初始化revision0/currentModelId=null。无跨服务FK或联表。
2. 所有写入先锁head，再锁/读模型；同库TransactionTemplate保存状态、head和审计。注册登记为服务事件。管理员操作绑定session解析出的actorUUID、operationId、expectedRevision、expectedHeadRevision、10—500字符理由和researchLimitationsAcknowledged。相同操作及actor/内容重放返回原回执，不同actor/内容冲突；过期revision409。
3. publish仅PENDING/OFFLINE进入PUBLISHED；reject仅PENDING；offline仅PUBLISHED且若当前推荐则清空head；recommend仅已发布；rollback仅曾经发布版本，允许重新激活OFFLINE。publish本身不自动推荐，recommend/rollback显式切head。发布/推荐/回滚前重新inspect，缺权重/哈希不符/服务失败不改DB。每次操作追加事件并递增model/head revision，旧事件不改。
4. 本批不预置管理员、不改变现有普通账号、不自动发布三个候选；管理员页面与隔离测试可验收，现用发布需本人审核账号和评价后执行。已发布清单不意味着用户预测业务已接通。

## 身份与接口

1. MS-03新增Spring Security无服务器会话，Cookie到MS-01内部resolve经Feign验证；仅返回可信身份，忽略客户端X-Role/X-User-Id。普通GET /api/v1/models只返回PUBLISHED摘要。GET /api/v1/admin/models含head/候选；GET /api/v1/admin/models/{uuid}/events审计；POST /api/v1/admin/models/{uuid}/{publish|reject|offline|recommend|rollback}。
2. 内部POST /internal/v1/models/candidates以SERVICE_TOKEN认证先于有界JSON；公共管理写要求ADMIN、允许Origin、Cookie/X-XSRF-TOKEN，与现有账号CSRF契约一致。内部接口仍逐请求鉴权，标准API{code,message,data,traceId}，异常受控。CSRF与安全拒绝no-store。规范化路径与controller身份检查双层防变体绕过。
3. 网关具体model路由优先于现有admin/**到用户服务的泛匹配，白名单只允许上述方法与UUID动作；内部检查/登记不得代理。Feign转发允许cookie/origin/csrf/trace，丢弃客户端身份及服务凭据，失败返回PREDICTION_SERVICE_UNAVAILABLE。
4. 管理页/admin/models仅ADMIN导航/守卫。卡片显示候选状态、支持股票/特征/周期、训练截止与真实误差/基线、回顾性说明；动作对话框要求理由、局限确认并显示revision，冲突重载后提示，网络失败保留当前数据。推荐head为null时显示尚未设置；无成功预测曲线。

## 验收

真实MySQL隔离stock_prediction_test：登记幂等/改清单冲突/事务失败无状态与head更改/操作重放/并发CAS/发布下线回滚及推荐不串版/元数据不变/旧事件保留。HTTP：无Cookie401、普通用户403、伪身份忽略、CSRF/Origin403、内部无token401/变体/超大413；依赖失败503不保存发布。Python inspect安全重载、鉴权、错误与五点旧路由回归；前端动作与局限确认/状态展示及类型构建。现用仅导入三个PENDING，服务重启持久化核验，无管理员正式发布。一次最终独立审查，重要问题一轮TDD修复。

## 执行决定

Ruling：遵循用户固定目录和常规自治授权，保持当前codex分支，作者顺序实现、一名最终只读审查者；不重复规格/计划确认。若拆分不合意可回滚本批，用户预测另批接通。Ruling：评价采用可信内部导入的原报告摘要与hash，并标记回顾性；代价为后续科学性审查仍须原始实验/独立盲测，不能仅凭摘要宣称准确。Ruling：不自动初始化管理员或发布候选；审核页面与隔离测试完成后，再由用户选择账号和发布，代价是现用模型列表本批为空。
