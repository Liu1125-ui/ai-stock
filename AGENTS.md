# 正式工程开发约定

依据 E:\aiStock\AGENTS.md、需求分析 V1.8 和 SDD-001 工作。先写规格与测试，再实现行为。五个业务服务独立拥有数据，不跨库读写。业务接口统一返回 `{code,message,data,traceId}`；不可将联调样本、基线或静态页面当作真实行情与 Transformer 预测。CodeArts 代码生成/重构需有实际文件差异和测试记录，不能只记录工具启动。
