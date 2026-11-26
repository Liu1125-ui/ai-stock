# SDD-005D1 正式推理验证记录

日期：2026-10-05。范围为内部正式推理通路，个人预测任务、结果持久化与页面属于下一子步D2。

## 已完成与实测

1. MS-03提供受服务身份保护的模型发布状态读取；只允许PUBLISHED返回固定制品、清单、权重及发布版本。候选、下线和不存在模型拒绝。
2. Python正式infer仅接受模型与输入版本标识，向MS-03核验发布状态，向MS-02读取固定FRESH快照；不接受用户提交价格或自行声明的发布状态。拒绝重定向，响应64KiB，socket3秒，总工作45秒，共享单槽。
3. 后端完整隔离测试179项通过；配置后的真实算法容器59项通过。部署后旧测试有两项失败，原因是默认未配置服务地址；已显式隔离配置状态并新增健康声明测试，回归通过。第三方Starlette/AnyIO有一条弃用提示。
4. 三股最新RAW头与旧预测输入版本不同，使用既有真实Baostock采集脚本生成对应输入，均60条、FRESH，原点2026-09-30，目标交易日2026-10-08、09、12、13、14。旧快照保留，不冒充新版本。
5. 真实部署三个候选仍PENDING：publication均409，infer均MODEL_NOT_PUBLISHED且result=null。伪造window422；未认证畸形请求401；网关内部路由404。成功计算由生成权重和模拟业务HTTP测试覆盖，未实际发布候选，不作为现用模型成功或准确性证据。
6. 三份清单、权重和数学运行文件哈希全部与原catalog一致；数学模型、特征处理与权重未变。179项Java结果见runtime/sdd005d1-publication-green.log，算法见runtime/sdd005d1-deployed-tests.log，接口记录见runtime/sdd005d1/http-probes.json；正式摘要见[evidence](evidence/sdd005d1-20261005.json)。

## 当前边界与下一步

独立管理员已在实际库核验为ADMIN/ENABLED，未声称已验证管理员网页登录与点击发布。正式发布仍由管理员审阅决定。前端本批未改，用户发起预测入口尚未接通；D2负责当前输入自动准备、本人任务、固定五点保存、历史查看与页面。只有依赖和发布状态均满足才生成新结果，失败保留旧数据，不把基线充当模型预测。

发布核验为计算开始时的一致读取，返回固定publicationRevision；执行中下线不撤销已经开始的纯计算，新调用重新核验。13业务、五服务、两角色不减。代码由Codex完成，无核实CodeArts源码差异；无Git远程，未push或远程CI。

Final review: one Important fixed with two RED regressions; deployed full suite 59 GREEN. Current RAW version/content hash checked for formal inference. See [review and fixes](sdd005d1-review-and-fixes.md).
