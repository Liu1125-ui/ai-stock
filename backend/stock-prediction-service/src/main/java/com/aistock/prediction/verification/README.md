# 预测事后验证

作用：对本库不可变成功预测获取MS-02固定真实证据，计算五日误差、方向命中及独立朴素基线，保存可追溯验证版本。

实现：内部接口在JSON解析前核对SERVICE_TOKEN，4KiB严格请求、128KiB完整依赖响应、禁重定向、完整正文截止。短事务领取与发布，网络不在事务内；同操作重放、参数冲突、同证据去重。版本与结果只追加，失败/过期保留旧结果；当前指针不允许旧操作或较早截止日回退。隐藏预测和后来下线的模型不影响原成功预测验证资格。无到期点返回SKIPPED_NOT_DUE，不采集或创建评价。

算法：同批RAW/后复权因子换算起点股本口径，比较统一6位，除法18位HALF_UP；归一化分母使用原始P0。输出十进制字符串，包含带符号误差、绝对误差、方向和基线。仅VERIFIED进入分母；零样本指标null。规则origin-share-close-v1。

验证：VerificationCalculatorTest手算、VerificationMysqlTest隔离stock_prediction_test、VerificationHttpTest认证/正文、VerificationMarketClientTest固定证据读取和依赖故障。真实部署/外部源另见docs/verification-sdd005d3a.md；测试fixture不证明真实预测准确度。

阶段：D3A内部采集与存储；MS-04自动调度及本人验证页面归D3B，尚未实现，不能宣称FR-06完整验收。

执行截止从请求开始90秒，HTTP阶段与数据库短事务使用剩余预算，最终发布检查截止；120秒租约用于恢复，不能替代执行预算。读取完整日历并重算calendarHash，来源修改日历必须生成新的固定证据。

D3B1新增VerificationQueryRepository与VerificationHistoryController：可信用户会话只读本人验证列表/详情，ADMIN不扩大普通业务读取权限；隐藏不取消归属。20条分页、固定current指针、未采集点PENDING/WAITING、执行过期只标记，不改库。保存证据和十进制字符串直接返回，跨预测版本404。自动任务仍属D3B2。

D3B2到期发现：内部身份保护的GET /internal/v1/prediction-verifications/due，最多50条UUID keyset；仅成功固定快照，隐藏/下线仍可验证，当前指针已5有效点排除。北京时间20点截止，第一目标未来不入队，证据截止最多原点90日。只查本库，不生成数据。MS02受控来源错误保留到验证回执，RAW不一致不误当网络故障。

## 行情修订影响范围
/internal/v1/prediction-verifications/affected按证券查询成功且首目标已到期的预测，含hidden与current已完整结果；有界UUID分页，仍限制origin+90和截止日。只查询MS03本库，不改写原预测。

SDD010B3 Task4C：新实际证据单独登记PREDICTION_VERIFICATION，沿用原operationId与冻结请求。尚未到期直接SKIPPED，不申请凭据；到期后取固定RAW的实际证据，核对RAW一致再计算。AVAILABLE映射为内部SUCCEEDED证明，结果身份为验证版本；错代次和迟到写入不能改终态。原forecast snapshot_json保持只读，新增验证仍独立保存。
