# SDD005E3实施计划审核记录

日期：2026-10-09。审核对象：2026-10-07单股问答设计/实施计划；对照当前正式代码，未开发产品功能。

## 结论

方向可行，原计划存在需在编码前修正的执行缺口，已直接修订并同步设计说明。不是业务范围变更，13业务/五微服务/双角色保留。主体审核为文档与代码静态对照，另外实际执行现有Nginx测试验证命令可用；不宣称新增问答自动化或云验收通过。

## 发现与处置

1. 原计划过期GET只返回expired，而页面又禁止pending新发送，可能一直锁住。补显式原POST结束过期请求，只落FAILED/AI_INTERRUPTED不调用模型；新问题要等原终态。
2. 仅用服务器latestAttempt无法覆盖原POST尚未claim时刷新，暂404可能误新建请求。补同标签页固定输入恢复、暂404不清pending、同op恢复并发测试和storage失败处理。
3. 缺预测的ANSWERED按先render会因未知占位符报错，却又要求成功降级，顺序冲突。明确合法OUT_OF_SCOPE成功，错误ANSWERED拒绝，不吞坏结构。
4. 核对frontend/src/api/auth.ts发现401身份失效路径清单无stock-questions。补文件变更和事件回归；原Spring Security/Nginx接入要求保留。
5. 指标定义与状态不匹配也可能满足泛化引用检查；预测可借别的topic绕过限制。补同指标配对、任意PRED引用的原点/验证/口径规则、目标日期价格配对、知识事实白名单。
6. 跨用户op的404与本人冲突409未区分；缓存只写源context时可能错挂analysis。补归属先行、sourceAnalysisId进hash以及同facts异analysis测试。
7. Java strip与JS trim对空白不同；JSON重复字段/尾随数据未明确。补固定空白集合、Unicode码点/代理项、局部严格JSON以及中文转义体积限制测试。
8. nginx.test.js导入Vitest且相对frontend读配置，不能直接node执行；示例Java文本块少一引号，证据日期写死设计日。已改Vitest命令、闭合文本块、实际验收日期命名。

## 对照证据

- MaasOperationGate.lock/lockAndAcquire：到期恢复发生在写操作领取/回放流程；GET只读不会主动收束原状态。
- AnalysisRepository：当前源get仅本人，数据库返回context；不可变源可复用，MySQL JSON重排不能直接重算比对原Java序列hash。
- AnalysisPredictionFacts及ExplanationFacts：预测事实前缀PRED_，需要白名单和元数据口径；具体key执行前再按实际源逐项核对。
- ExplanationTemplate：未知事实/字面数字/方向被拒，不能先render后再承诺无预测资料不足成功；不代表语义完整核验。
- frontend/src/api/auth.ts：401路径清单需新增问答；同op原body重试与身份generation保护复用。
- frontend/nginx.test.js：import {it,expect} from vitest、readFileSync('nginx.conf')，执行必须由Vitest在frontend目录处理。

## 完成范围

已修订实施计划和设计状态/细则，新增本审核记录。尚未获实施计划最终确认、未写产品代码或调用MaaS；现有Nginx回归命令实际执行1文件/1测试通过（退出0），只证明修正的命令可用，不代表未实现的问答路由已通过。下一步按修订计划编码，真实云调用仍待功能可审阅后单独授权。

验证命令：pnpm --dir frontend exec vitest run nginx.test.js；结果1 passed，退出0。git diff --check通过。计划复核补充F同步共享渲染器日志要求及恢复存储清理时机，新增问答测试尚不存在，不填写通过数量。
