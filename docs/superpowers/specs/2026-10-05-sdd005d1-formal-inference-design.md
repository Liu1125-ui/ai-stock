# SDD-005D1 正式推理的发布与输入验证通路

目标：完成SDD-005D预测纵向链路的第一子步；内部正式infer仅使用MS-03真实PUBLISHED模型及MS-02固定FRESH输入，输出五个目标交易日和完整版本回显。下一子步D2接本人任务、不可改写快照与用户页面。依据FR-05/AC-05、版本追溯与用户先计划检查再做/常规自治/一步一步授权。

## 方案与接口

1. 选择算法独立向业务服务核验。只相信调用方传published=true会允许绕过审核；把权重文件改PUBLISHED会破坏hash；新建签名密钥增加不必要配置。新增MS-03 GET /internal/v1/models/{uuid}/publication，校验SERVICE_TOKEN后同库一致读取，仅PUBLISHED返回{modelId,artifactId,manifestHash,checkpointHash,publicationRevision}；其他状态409 MODEL_NOT_PUBLISHED，无记录404。网关不代理此内部路由。
2. POST /internal/v1/infer严格接受modelId/artifactId/manifestHash/instrumentId/inputVersionId五字段，不接受原始价格、window或自行声明发布状态。先鉴权，UUID规范化、股票/制品/hash合法、1MiB限制；缺少业务验证地址503 PUBLISHED_MODEL_NOT_CONFIGURED。Python子进程先读取MS-03 publication并核对请求身份，再向固定MS-02地址GET forecast-window?instrumentId=&inputVersionId=读取canonical快照；拒绝STALE/UNKNOWN、ID错配。普通用户不能调用内部接口。
3. 两个地址仅取受信本机环境，不取请求内容；拒绝HTTP重定向，单次socket3秒和响应64KiB上限，整个验证/模型工作共享已有单槽及45秒子进程期限。依赖失败、未发布、错配返回受控state/result=null，不换模型、不用朴素基线填补。
4. 原InferenceEngine安全加载与计算不改，原权重/制品ID保留。在worker封装层将validationOnly改false、增加formalInference/modelId/publicationRevision/instrumentId，并核对权重hash；父进程检查五点、版本回显、递增目标日及正式标识。历史候选验收仍validationOnly=true，可继续验收STALE历史样本；正式推理只用FRESH。
5. 发布核验与实际推理不组成跨服务事务。授权以工作开始时MS-03返回的版本为准，下线后已开始的纯计算允许完成，返回固定publicationRevision；后续发起重新核验。D2保存快照时仍以已固定模型/输入为准，不能暗换推荐版本。

## 验收与边界

MS-03真实隔离MySQL：服务凭据、不存在/候选/下线拒绝、发布后descriptor正确、推荐变化不改制品；内部无法从网关访问。Python：未认证优先401，字段/UUID/window伪造422，超大413；mock业务HTTP返回真实生成的同构fixture可得到五点10.20例，PENDING/OFFLINE/错hash/重定向/失联/超大体及STALE输入拒绝；单槽/worker超时和候选回归保留。mock是协议测试，不是预测效果或现用发布证据。

现用仅核验三候选PENDING时正式infer受控拒绝、管理员账号ADMIN/ENABLED存在；不自动发布或伪装用户审阅。正式用户任务/持久化/页面由D2完成，验证与MaaS独立后续切片；不宣称五日用户业务已经完成。13业务/5服务/2角色不减。

## Rulings

Ruling：沿用用户固定目录、自治授权及当前codex分支，作者顺序实现/最终一次独立审查，不反复规格/计划确认；代价为若拆步不合意需调整本批。
Ruling：D拆为D1验证执行通路与D2本人任务快照页面，满足用户一步一步来；代价是本批内部推理就绪并不等于用户可点击预测。
Ruling：依赖失败采用关闭拒绝，读取canonical输入而非客户端window；代价是依赖不可用时不能生成新预测，原行情及未来旧快照仍可读。
Ruling：不自动发布真实候选，发布起始状态由MS-03核验且保留revision；代价为现用成功推理须管理员先实际审核，未审核只验证拒绝。
