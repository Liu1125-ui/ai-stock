# SDD010B3 Task3B证据读取与发布门禁核验

2026-11-07。Task3市场服务侧协议、读取和发布基础已完成；Task4—8消费者、管理员操作和上线仍待实施。FR-11不关闭，本轮未部署、未开启生产保护。

## 实现范围

受保护内部计算入口使用五项X-Market-Use凭据（Bundle、Owner、Purpose、Operation、Generation），认证过滤器先执行，再校验使用身份及用途。请求结束清理上下文；回执、选项和不可变历史展示不视为新计算入口。

预测输入、分析、比较、持仓报价、预警证据和实际行情验证按凭据固定RAW，不随当前头变化重新选版本。比较还检查原选evidenceId与股票，不能用同一RAW下另一证据冒充。局部SQL读取/同库派生发布在短事务内重新校验ACTIVE及RAW许可，原在途任务可在DRAINING继续；RELEASED或DISABLED拒绝。外部行情采集在事务外，不跨服务持锁。

新RAW发布同事务建立ENABLED许可；内容去重不能将DRAINING/DISABLED旧版本重新挂头。既有已提交操作回执重放只返回历史，不改变当前头。真实连接测试覆盖重发先于停用及停用先于重发两个顺序。

## 审查与验证

独立审查发现一项重要遗漏：预警准备阶段读取本地行情时缺少二次凭据校验。先新增“打开上下文后释放，再准备”的反例并观察失败，再把准备回执与本地快照读取移入短事务；回归确认不读取RAW、不创建准备回执、不调用采集器。采集器调用前检查放在通用网络异常捕获之外，保留门禁错误语义；比较更新同步保留该边界。

完整命令：

```powershell
mvn.cmd -f backend/pom.xml '-Pauth-integration,cli-build' -pl stock-market-service -am verify
```

2026-11-07 16:57完成，退出码0、BUILD SUCCESS。按本次日志创建时间筛选新报告：公共65项、行情312项，共377项；失败、错误、跳过均0。新增HTTP4项、MySQL10项，已包含于总数，不重复加总。覆盖认证顺序、缺失/错身份、上下文清理、固定RAW、旧输入拒绝、原在途DRAINING、释放后拒绝、股票集合/比较证据成员、外部采集无事务、读取与停用竞争、RAW去重双向竞争及预警准备回归。

相关模块打包通过；git diff --check与Compose config --quiet通过。未改前端，不宣称前端或全系统其他模块已重测。公共网关无内部证据转发路由仅作静态核对，生产运行时代理验收留Task8。

## 当前运行边界与下一步

MARKET_VERSION_EVIDENCE_GATES_ENABLED默认false；协议、核对开关继续默认关闭，未完成owner接入时本构建仍禁止全局enforced开启，管理员动作未就绪。真实应用数据、配置未修改，本轮未执行生产迁移或部署。仅隔离测试库13316运行并使用V17；业务容器当前停止，业务库V14是此前已核验记录，本轮没有新查询生产Schema。

无真实Tushare/Baostock取数、MaaS、SMTP调用。测试使用隔离夹具，不作为预测效果或真实行情证据。

下一步Task4：预测、分析、实际验证的owner代次/CAS/终态证明，并贯通Java到Python算法服务的使用凭据转发；Python inference/publication.py的推理前行情复核必须使用同一凭据。owner就绪检查须确认实际证据门禁状态，不能把协议可用等同于读取保护生效。全部消费者完成前不启用全局保护或停用操作。

本地忽略日志：runtime/lifecycle-task3b-final-confirm.log；审查回归RED/GREEN见runtime/lifecycle-task3b-alert-{red,green}.log。
