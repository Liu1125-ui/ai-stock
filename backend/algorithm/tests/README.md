# 算法与采集契约测试

执行 python -m pytest tests -q -p no:cacheprovider。测试覆盖无已审核模型时拒绝伪造预测、Baostock 正常/空数据/错误/超时和只读候选准备边界；假 SDK 测试不证明在线源持续可用。实际联网探测另见 docs/verification-sdd003.md。

test_formal_inference.py：生成同构合成权重+本机模拟MS-03/MS-02 HTTP，验证正式infer发布状态/固定输入/哈希/FRESH/重定向/尺寸/失联与五点协议；固定10.20样例仅验证换算，不能充当真实预测效果或现用已发布模型证据。

独立日历测试test_calendar_source.py核验连续休市日、来源错误、范围、鉴权、共享槽及超时。合成日期只是夹具，不作为真实节假日依据。
