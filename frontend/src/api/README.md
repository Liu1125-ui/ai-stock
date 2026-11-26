# API 契约

作用：集中定义工程状态、注册、登录、本人资料和管理员用户接口。
实现方法：解析 `{code,message,data,traceId}`，失败不伪造成功数据；写请求先取CSRF，Cookie由浏览器携带，认证变化后刷新CSRF，不存储登录凭据。

行情批量摘要增量字段displayName（可空）、displayNameVerified、listingStatus用于自选名称一致性。前端允许字段缺省以兼容旧服务，缺省视为未核验，不假装真实名称已确认。

models.ts：定义候选清单、不可变评价、推荐head、审核事件及变更请求，沿用Cookie/CSRF认证，不传客户端角色。

predictions.ts：已发布模型、本人预测请求/详情/分页/隐藏接口。网络未知使用原operationId，身份来自会话，接口不接受用户ID/价格。明确区分未接通的解释/校准/事后验证。401沿用全局退出处理。

认证异步请求绑定本机认证代次：登录/退出/密码变更与profile用户ID变化使旧请求失效；等待CSRF后、响应解析后、403自动重试前均校验，旧CSRF promise不能清掉新的等待对象。用于阻止本页面账号切换后的旧写操作重放；不声称已核验其他浏览器并发改变会话的界面行为。

verifications.ts提供可信会话本人验证列表与版本详情GET，复用auth.request及账号请求世代保护。十进制价格/误差为字符串、无样本指标null；不提供伪造用户ID或绕过身份的参数。
questions.ts：本人保存分析的问答POST/列表/详情/操作GET，沿用会话与CSRF，未确认请求复用原操作号。

alerts.ts：当前用户预警规则与通知偏好API；阈值以十进制字符串传输，requestId用于创建幂等，revision用于并发更新保护。

comparisons.ts：股票比较的固定选源、幂等创建、本人恢复/详情/摘要历史；全部通过现有会话与CSRF request方法。

portfolioQuestions.ts：本人固定估值的规则GET和问答POST/历史/恢复；不携带持仓正文，POST仅操作号、估值号、问题和告知确认。

adminTasks只调用三类GET契约，使用既有认证请求器和安全整数检查。

adminTaskActions.ts：任务能力/操作/原回执/历史；使用新增requestWithoutRetry保留认证代次和401处理，但不沿用403自动重发/CSRF刷新。409保留经绑定校验的data回执；恢复原请求由用户再次确认，传输层每次调用只发一次。原request与其他业务流程不变。

分析操作回执在证据准备前允许contextHash=null；执行代次/修订/contextReady为兼容可选字段，前端不自行生成或修改执行代次。未知响应仍恢复原operation，不能据此重新选行情版本。
