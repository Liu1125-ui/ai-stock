# 单股问答
作用：围绕本人已保存分析解释指标、预测和验证，不重算或暗换证据。
实现：QuestionInput规范化；QuestionFacts冻结白名单；QuestionAnswerTemplate核验；Repository保存不可变问题与答案，Service编排MaaS，Controller执行本人权限。未知状态只读查询，显式同操作恢复；共用有界付费调用槽。
QuestionPrompt：v2指令单独维护，普通与预测格式示例通过真实核验器测试，禁止抄事实值；失败仅记录固定规则/类别，不保存未经核验的模型原文。
