# D3B3 行情修订重核设计

目标：已完成评价也能在RAW发布头变化后重新核验；同RAW因子修订可受控发起独立重核。保持原预测/评价不可变与真实数据边界，13业务/五服务/双角色不变。

采用持久发布事件+MS04轮询，不新增队列、跨库联表或仅内存通知。MS02发布事务内取得全局序号锁并保存head变化事件（baostock RAW）；相同head/重放无新事件。全局锁保证序号与提交可见顺序一致，避免普通自增ID后提交造成跳过。V8迁移回填现存head作为初始化事件。

MS02 GET /internal/v1/market/revision-events?after=非负long&limit=1..50，返回items[{eventId,instrumentId,rawVersionId}]。MS03 GET /internal/v1/prediction-verifications/affected?instrumentId&dueThrough&after=UUID&limit=1..50，只取本证券成功且首目标到期的预测，含hidden/downline/current完整者，dueThrough=min(截止日,origin+90)。使用固定cutoffDate与有序UUID分页。

MS04持久一个active事件、其股票/RAW、当前持久轮次cutoff、after预测和revision；获取事件/预测HTTP在事务外，begin/publish用短事务CAS；发布本页任务与游标原子提交。事件最后页完成才推进completed_event_id，失败不推进，重启可续。任务沿用PREDICTION_VERIFY、租约/回执恢复；key=raw-SHA256(predictionId,eventId,rawVersionId,rule,实际dueThrough)，不与每日普通任务冲突。哈希身份包含eventId以处理RAW A→B→A回切，重复同事件去重。事件处理时采集最新有效RAW而不是将旧事件当作当前head；实际证据保存真正RAW id/hash。较新head另有事件，会再次核验。

同RAW因子重核：仅内部POST /internal/v1/tasks/verification-rechecks，body predictionId,instrumentId,dueThrough,requestId(UUID)。独立key=recheck-predictionId-requestId-rule，参数冲突409，重复请求同task。内部认证前置，不给普通用户触发权，不冒称已有管理员按钮。新operation重新采集，原证据相同内容由现有MS02/MS03去重；原预测不改写。

独立PREDICTION_REVISION_PLAN_ENABLED默认false，完成运行验收后启用；共享MARKET_WORKER_ENABLED必须true。分钟轮询每次最多一事件的一页50预测，HTTP2s连接/10s完整响应/128KiB限长禁止重定向。无未来数据，20点cutoff沿用本阶段默认。真实预测首目标10月8日，当前只能验空范围、SKIPPED与保留，不证明未来准确率。

备选：仅完成行情任务后内存通知会丢重启和手工发布；逐预测轮询head易重复/难追踪，故用持久事件与分页。用户已授权常规自主细节与下一步，固定目录不新增worktree，不push/merge。自查：事件序号提交顺序、任务去重与页游标同事务、current完整包含、回切重复事件以及每轮截止及跨日前进均明确。

## 跨日修复决定
active事件每个持久轮次固定cutoff；跨北京时间20点或跨日后仅前进cutoff，清空after并增加revision，重新扫描当前到期范围。提交时再次检查cutoff，旧页不得完成事件。事件HTTP返回后重新读取clock。任务key改为raw-加SHA256(predictionId,eventId,rawVersionId,ruleVersion,实际dueThrough)，同轮重复去重，新截止不冲突，长度68。保留MS03的due不回退保护；未完成事件不会只核对过时前缀而永久完成。
