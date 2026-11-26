# SDD-005D1正式推理发布与输入验证 Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL superpowers:executing-plans，作者顺序执行/最后一次独立审查。

Goal：正式infer从MS-03真实发布状态及MS-02固定FRESH输入执行五点，现用PENDING拒绝。
Architecture：MS03publication→Python子进程受控读取canonical模型/输入→既有安全engine→正式五点回显。
Tech Stack：Spring Boot/JDBC/MySQL8，FastAPI/PyTorch/urllib，原Compose。
Spec：docs/superpowers/specs/2026-10-05-sdd005d1-formal-inference-design.md。

## Global Constraints

13业务/5服务/2角色不减；固定aiStock/currentcodex，权重/计算文件不改；SERVICE_TOKEN不输出；正式输入FRESH且仅固定版本；候选不自动发布。1MiB请求、64KiB依赖响应、socket3秒、工作总45秒单槽。任务/快照/UI属D2。

## Review Focus

1. 内部publication路径变体/伪身份不能从网关或普通Cookie绕过服务身份。
2. 调用方伪造window/发布状态/UUID或改hash，不能执行其他模型。
3. 业务响应重定向、超大/深层JSON或停顿，无凭据泄露及失控工作。
4. FRESH输入ID/股票错配或STALE、缺五日，不返回成功曲线。
5. 发布/推荐/下线并发，仅固定descriptor版本生效，不暗换候选；旧候选验收保持。

## Task1：MS-03发布状态只读契约

Files：modelcatalog/ModelControllers.java/ModelRecords.java及README；tests/modelcatalog/ModelHttpTest.java。
Consumes：ModelRepository.locked/find与真实业务状态。Produces：GET /internal/v1/models/{modelId}/publication → PublicationDescriptor(UUID modelId,String artifactId,String manifestHash,String checkpointHash,long publicationRevision)。
- [ ] 写无token401、PENDING409、已发布descriptor/下线409测试RED：
```java
mvc().perform(get("/internal/v1/models/"+v.modelId()+"/publication").header("X-Service-Token",SERVICE)).andExpect(status().isConflict());
```
- [ ] 实现token→锁head→find→PUBLISHED检查→descriptor，错误data=null。
```java
return ok(repo.locked(head->{var v=repo.find(id,false);if(!"PUBLISHED".equals(v.state()))throw conflict("MODEL_NOT_PUBLISHED");return new PublicationDescriptor(v.modelId(),v.artifactId(),v.manifestHash(),v.checkpointHash(),v.revision());}),request);
```
- [ ] 限定stock_prediction_test全后端auth-integration/cli-build GREEN，提交此独立契约。

## Task2：正式Python推理、部署及验收

Files：algorithm/app/inference/publication.py新建、http.py/worker.py/main.py/README；tests/test_formal_inference.py及README；deploy/compose.yaml；docs/evidence/验收/status/pending。
Consumes：Task1 PublicationDescriptor及现有MS02 ApiResponse<Window>。Produces：fetch_publication(body)、fetch_window(body)严格核验；/infer(body)输出AVAILABLE/formalInference=true/validationOnly=false/modelId/publicationRevision/五点。
- [ ] 写真实本机HTTP fixture测试RED：未配置与鉴权保持、伪字段/UUID/尺寸、PENDING与hash错配、canonical窗口STALE/ID错配、重定向/超大/网络失败、有发布fixture五点。
```python
r=client.post('/internal/v1/infer',json=body,headers=HEADERS)
assert r.json()['result']['formalInference'] is True
assert r.json()['result']['points'][0]['referencePrice']=='10.200000'
```
- [ ] 严格request五字段、固定业务URL/拒绝redirect/64KiB+socket3秒、worker总45秒；在子进程读取业务状态和FRESH输入，安全计算与制品哈希不变。
```python
publication=fetch_publication(body);window=fetch_window(body)
result=InferenceEngine(root).validate(body['artifactId'],body['manifestHash'],window)
if result['checkpointHash']!=publication['checkpointHash']:raise InferenceError('MODEL_PUBLICATION_MISMATCH')
result.update(formalInference=True,validationOnly=False,modelId=body['modelId'],publicationRevision=publication['publicationRevision'],instrumentId=body['instrumentId'])
```
- [ ] 完整算法容器GREEN；重建prediction/algorithm，Compose配置两固定authority地址，实际PENDING模型拒绝，无token401/内部网关404/服务健康；制品目录无修改。
- [ ] 单次最终独立审查，重要项一轮RED→GREEN修复；更新准确验收与D2后续，不虚构管理员登录页面成功或现用预测成功；提交。

## Pre-flight与执行记录

基线2b4c988，管理员真实初始化已完成ADMIN/ENABLED；工作树干净。Task1输出字段与Task2字典核验同名；输入Window由已有MS02提供，无新增表/跨库写。沿用spec Rulings；保留计划和运行证据，不删除原文件。

Task1：两项接口先404 RED→GREEN，完整后端179项失败0/错误0/跳过0。Task2：10项正式协议原503 RED→GREEN，完整算法56项通过；生成fixture及模拟业务依赖明确标记，不当真实效果。当前正准备部署和实际未发布拒绝核验。

Task2部署核验：实际配置环境旧测试2项RED，显式隔离未配置状态并增加配置健康声明测试后57项GREEN。三只真实股票当前RAW版本均重新生成60条/5目标日FRESH快照；真实3个PENDING publication409，infer均MODEL_NOT_PUBLISHED/result=null，额外window422，无身份畸形JSON401，网关内部路径404。证据runtime/sdd005d1/http-probes.json。提交c8c7f79。
Ruling: 旧快照不替代新RAW头，使用既有采集脚本补齐新版本输入 — 行情与输入必须同版本 — 代价是每次新RAW头需准备输入，D2自动化处理。
Ruling: 测试显式配置/清理authority环境 — 未配置503契约仍有效，配置后需逐次查发布状态 — 代价是健康声明仅表示检查能力，不表示已有可用模型。

最终独立审查1项Important：同日期RAW换头时旧输入仍FRESH。先补两项测试，旧实现返回AVAILABLE，均RED；正式fetch_window追加当前RAW版本/内容哈希检查，59项全套GREEN。候选历史验收保持。无Critical/Minor，不重复审查。
Ruling: 当前RAW头一致性以开始时读取为准，读取后更新不暗换已固定输入 — 跨服务不作分布式事务，结果保留版本 — 代价是执行中可能出现更新，下一请求需重新准备并核验。

收尾：唯一审查Important已修复，全套59 GREEN；修复后真实三候选均受控拒绝。验收/状态/待办/evidence同步。Ruling: 按用户固定目录与常规自治授权保持当前分支、权重和记录，不重复询问合并 — 无远程且下步继续增量开发 — 代价是远程备份待配置。
