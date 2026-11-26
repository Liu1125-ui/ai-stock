# E1 独立审查与一次修复

审查范围 bad62b0..d062505。唯一只读审查：Critical 0、Important 1、Minor 3。

Important：原点上方的下降序列被描述为上行。新增110→106、原点100案例RED，修复后TREND表示五日路径，RELATIVE_ORIGIN独立表示相对原点的位置；百分比变化由服务端BigDecimal计算为R1—R5。

Minor：补相对变化事实；未知POST刷新确认终态后清除原operation，未知或RUNNING保留；增加立即响应头但持续慢正文的超时测试。走势和终态恢复测试RED→GREEN，慢正文截止测试GREEN。

另完整回归发现旧调度测试总以当天为截止，20点前违反现有生产规则。测试采用此前已到期日期，不修改生产截止规则；修正后五项通过。此前并行针对同一隔离库测试导致旧验证并发case超时，改为串行后该case通过。

最后完整回归与部署结果见verification-sdd005e1.md。没有再分派复审。
