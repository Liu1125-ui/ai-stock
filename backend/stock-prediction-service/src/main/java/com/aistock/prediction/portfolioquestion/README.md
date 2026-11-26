持仓规则说明与有依据问答模块。

作用：读取本人不可变估值，提供不调用AI的公式/口径说明及显式发送的云端问答。
实现：Facts白名单不含数量/平均成本/身份；专用1—2段输出与引用核验；V11持久操作/缓存/本人历史；共享MaaS单槽，45秒业务期限和锁后时间重查，失败保留旧结果。Controller使用已验证会话身份，拒绝客户端owner/facts与额外JSON字段。
配置：portfolio-question.enabled默认false，不影响免费规则；实际云验证需另行批准额度。容量保护限制提示字符数，不声称等于token预算。公开路由和前端仍按SDD008C后续任务接入，不能把本模块测试通过当已上线。

## MaaS持仓请求与故障分类

持仓问答使用deepseek-v4-flash时显式发送thinking.type=disabled，仅用于保存事实的简短解释；其他模型名及原有预测/行情/比较/单股问答调用参数保持原样。保留HTTP35秒、持久操作45秒、共享租约50秒及不自动重试约束，不放宽事实白名单或输出引用校验。

持仓专用错误区分AI_TIMEOUT、AI_CONNECTION_FAILED、AI_INTERRUPTED；供应商HTTP错误沿原类别映射。日志仅固定category与HTTP状态，不记录问题、事实、接口凭据、响应正文或原始异常消息。普通响应流/大小保护异常不能误报为连接失败。

官方依据：https://support.huaweicloud.com/intl/en-us/model-call-maas/model-call-005.html 与 model-call-019.html：V4默认思考，thinking.type可设disabled，max_tokens仅限制content。参数适配修复不等于已证明历史请求失败的唯一原因，真实云端结果需独立验收。
