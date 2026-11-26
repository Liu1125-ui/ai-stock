# SDD-005B兼容模型制品与推理 Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL superpowers:executing-plans。本代理按用户授权直接执行，最终一次独立审查。

Goal：三份既有Transformer候选在正式算法容器读取固定窗口，返回可校验五点，并证明迁移一致。
Architecture：不可变清单/权重+同构网络与预处理+内部验收子进程。不触发模型发布或用户成功预测。
Tech Stack：Python3.13、PyTorch2.14.0+cpu、NumPy2.5.3、FastAPI、Compose。
Spec：docs/superpowers/specs/2026-10-03-sdd005b-model-artifacts-design.md。

## Global Constraints

- 固定E:\aiStock与当前codex分支；原研究源码/权重不改，五服务边界与13项业务不减。
- 三种子均保留CANDIDATE，不自动推荐或发布。
- 权重安全strict加载、哈希与架构匹配、数值有限、源/60/5契约固定。
- 只读挂载；内部身份先于1MiB体积/解析，45秒子进程，单槽线程池。
- /infer仍未发布拒绝；输入hash仅回显MS-02摘要，不冒称独立重算。

## Review Focus

1. 改变清单/权重/实现或路径穿越：拒绝且不执行任意代码。
2. 短极端指数/缺因子/日期错配：特征与哈希前有界拒绝。
3. 检查点架构/缩放参数或输出失效：不产生成功五点。
4. 并发/超时/取消/慢初始化：单槽保留到子进程结束，health不阻塞。
5. 候选验收被误当发布：显式validationOnly，公开列表不扩，/infer仍503。

## Task 1：制品封装与纯推理

Files：app/inference/{artifacts.py,engine.py,contracts.py,README.md}、app/inference/vendor/{model.py,embedding.py,attention.py,encoder_decoder.py,masking.py,LICENSE,README.md}、scripts/package-transformer-artifacts.py；tests/test_model_artifacts.py。
Consumes：三份已核验ck和只读上游类，SDD-005A window。
Produces：ArtifactStore(root).load(artifact_id,manifest_hash)；InferenceEngine(root).validate(artifact_id,manifest_hash,window)返回候选五点；package脚本输出固定三包与脱敏目录摘要。

- [x] 写坏ID/路径/文件hash/配置/缩放/极端输入/实际safe strict重载测试。
```python
def test_existing_checkpoint_can_load_and_has_five_positive_points(bundle,window):
    result=InferenceEngine(bundle).validate('transformer-s3-2023-seed42',hash_of_manifest,window)
    assert result['validationOnly'] is True and len(result['points'])==5
```
- [x] 在现有研究venv pytest观察缺模块RED；再实现有界制品与输入校验、提取同构类、公式和五点十进制换算。
- [x] 固定源sha与MIT，执行安全封装三包，测试GREEN及三股真实输入直连engine；小步提交。
Expected：无假数据替代真实推理，负路径拒绝。

## Task 2：HTTP验收与容器

Files：app/inference/{http.py,worker.py}；app/main.py、requirements-inference.txt、Dockerfile；deploy/compose.yaml；tests/test_inference_http.py。
Consumes：Task1 engine与清单ID；本机runtime/model-artifacts只读挂载。
Produces：POST /internal/v1/model-artifacts/validate，candidateValidation能力；旧/infer继续未发布拒绝。

- [x] 写认证前畸形/1MiB/超时/单槽/不阻塞health测试，观察404或缺模块RED。
```python
assert client.post('/internal/v1/model-artifacts/validate',content='{').status_code==401
```
- [x] 实现线程池+45秒子进程、唯一结果标记、受控状态解析；不记录凭据。
- [x] 安装同版本官方torch轮子，构建镜像并运行全Python测试，容器三制品读取/真实验证；小步提交。
Expected：旧业务采集与契约回归通过，坏清单无points，正式infer503。

## Task 3：迁移一致性验收与审查

Files：scripts/verify-transformer-artifacts.py、scripts/check-model-artifacts.ps1、docs/verification-sdd005b.md、docs/evidence/sdd005b-20261003.json、status/pending。
Consumes：归档CSV/原始行情，真实固定三股输入快照，Task2受认证验收接口。
Produces：45历史窗口225点的容器迁移证据及9次当前输入45点，hash/重复/五日期核对。

- [x] 45个既有历史窗口按预设均匀5起点挑样，不新增准确率评估；对照CSV最大差<=1e-6。当前3*3真实输入每次五点/重复一致，换算人工10*1.02=10.20，拆分价/量转换手算样例。
- [x] 一次独立审查，重要问题RED→GREEN一轮修复；完整Python回归，既有全后端仅在受影响时跑，不做无关前端测试。
- [x] 更新实现状态明确候选推理/正式预测未发布，保存真实证据；小步提交，保留当前分支与runtime证据。
Expected：所有字段/版本/来源真实对应、无未来准确率承诺，无依赖本机研究Python路径的容器运行。

## 执行记录

基线f868780、工作树干净。三任务顺序依赖，Task1接口供Task2，Task3用同一窗与输出字段。Ruling：用户已授权逐步自治开发，沿用固定目录/分支并由本代理执行，先交付兼容模型运行单元再正式业务；若取舍不合适可回滚本批提交调整。最小上游类提取不改研究原文件，以许可证/来源哈希/严格重载和225点对照保护兼容。已在线核验官方torch仓库存在LinuxPython3.13的2.14.0+cpu，不凭版本猜安装成功。
Task1 Ruling：固定种子ID不能容纳后续实现修复而保持包不可变；真实ID增加制品/实现联合哈希后缀，旧包保留，catalog指当前候选。代价为后续登记/调用需使用完整ID。
Task1 complete：11项缺模块RED→11项GREEN，三份真实权重安全strict加载；固定三股实际MS-02快照9次推理45点，重复完全一致，最大截断比例0；候选包已封装且原研究源码/权重不改。
Task2 complete：HTTP5项RED（路由/模块缺失）→6项GREEN；新容器全Python43/43，外部弃用提示1。依赖实际安装与镜像构建成功；容器无网络回放45历史窗口225点+9当前窗45点，最大CSV差5.960504978475001e-08、当前截断0，重复一致。

Task3 complete：45既有窗口225点无网络容器迁移对照通过，最大差5.960504978475001e-08；三股真实FRESH固定窗口3*3组45点，HTTP各重复一次共18调用，模型/输入/日历回显及五日期一致。安全实际401/413/坏hash失败null/正式infer503通过。
Final review：一次独立审查f868780..c24234e，Critical0、Important1、Minor0。Final: fixed 深层JSON未捕获异常 — test_deeply_nested_json_is_controlled_before_worker RED→GREEN，最终已部署容器suite44/44，修复52fbfa9；实际深层HTTP422、health UP，九组18调用重新验证。
Final: Ruling: 未来准确性、MS-02存储摘要独立重算、管理员发布及用户预测不属于本批候选运行验收 — inputHash明示回显，已有张量摘要；后续按业务/实验分别验收 — 若边界理解错需补该批规格/验证，当前不得承诺准确率或开放正式预测。
Final minor (deferred)：本批无新增。原SDD-005A SDK退出诊断维持既有待办。
Finish：根据用户固定目录与自治授权保持当前codex分支、原研究与runtime证据，正常checkout未建临时worktree，不删除工作目录或重要记录；无远程，不merge/push/PR。
