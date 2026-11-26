# SDD-005D3A 最终审查与修复

审查范围7c38c96..ea48f8a；唯一独立只读审查verification_persistence_review，不做再次审查。Critical0、Important2、Minor0。实际运行结果另见verification-sdd005d3a.md。

## 已复现并修复

1. 日历本体丢失：新增fixedEvidenceRetainsOriginalCalendarAfterSourceChanges，RED预期calendar数组实际不存在。Evidence保存完整当次日历，读取固定版本保留原日历；MS-03重算calendarHash并检查连续日期/开市布尔值。来源后续修订只能生成新证据。
2. 90秒预算未生效：新增totalBudgetPreventsPublishingBeforeLongerLeaseExpires，RED模拟91秒时仍在120秒租约内，原实现返回AVAILABLE。现在请求开始确定90秒执行截止，短事务使用剩余事务/语句时限，依赖HTTP各阶段取剩余预算与阶段时限较小者，最终写入前后检查。MS-02接收的租约也不超过执行截止。超时不提交评价，原成功预测与验证保留。
3. 补充并发实证：建议补测不同operation相同证据竞争，新增测试在全回归RED，一方误报FAILED。重复读事务在去重插入后仍用旧一致性快照读不到胜出行；最终版本查询改为FOR UPDATE当前读，两个任务均AVAILABLE且只一份版本。此项在同一修复轮解决。

## 附加覆盖

日历篡改拒绝、剩余300毫秒正文等待受限、旧操作同截止晚完成不回退指针、同操作只执行一次、来源失败保留旧结果。附加覆盖不冒称原测试都失败。

日志：runtime/sdd005d3-review-red.log（为一次看两个模块RED，特意忽略失败继续运行，BUILD SUCCESS不代表通过）、sdd005d3-review-green.log、sdd005d3-review-full-final.log（并发RED）、sdd005d3-concurrent-fix.log（22项受影响测试GREEN）。最终完整回归另记录，不用局部GREEN替代。

## 审查未判断内容的处置

- D3B任务/本人接口/页面留到下一切片；不能宣称FR-06全部完成，后续仍需个人数据隔离与UI验收。
- 真实目标从10月8日才到期；本轮真实历史采集不代表未来误差或预测成功率。
- 既有Task2数学主体沿用此前审查和当前完整回归；不能由测试通过保证金融预测质量。
- 实际部署与网关边界必须运行核验；源码审查不作为运行成功证明。

本轮无延期Minor；前轮signedError字段延期已按输出契约补齐。代码来自Codex，未伪称CodeArts执行结果。
