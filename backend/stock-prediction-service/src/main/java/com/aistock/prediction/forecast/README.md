# 本人五日预测
作用：同步预测编排、原始模型与输入快照、本人历史及隐藏。实现：本库guard事务保证单用户并发与请求重放，180秒期限和终态CAS防止旧执行写回；外部调用在事务外。成功不可覆盖，不承担后台训练调度。

SDD010B3 Task4C：以predictionId（全局唯一，区别于本人范围operationId）作为使用身份，先提交原预测操作，再登记CURRENT_HEADS固定RAW。prepare/infer全程传同一凭据，推理前核对窗口rawVersionId及本地执行状态。成功/失败/到期按generation+state条件提交并递增fencingRevision，通知只发生在提交后；旧五点快照不改写。新开关默认false。
