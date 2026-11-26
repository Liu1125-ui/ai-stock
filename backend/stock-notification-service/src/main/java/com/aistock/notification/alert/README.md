# 预警规则与偏好
作用：保存本人日线预警规则、幂等请求和通知偏好。实现：独立MySQL、用户级短事务行锁、revision校验、外部边界调用在事务外。A阶段自动评价与邮件投递未启用。

安全：公开端点由NotificationSecurityFilter解析Cookie会话并校验Origin/CSRF；NotificationAccess通过有限超时的Feign调用当前身份与本人资料，不能信任客户端userId或邮箱验证标志。AlertController严格字段与JSON类型校验，网关只转发精确白名单。

仓储通过jdbc()方法访问，避免直接读取Spring代理字段。NotificationResponseLimit在解码前限制所有内部响应为64KiB（包括未知长度和错误正文），关闭原响应体；超限失败，不降级为已验证身份。

B2基础：AlertAccountClient读取权威账号状态，AlertEvidenceClient核对固定证据及当前边界；AlertRemoteHttp使用服务发现或显式内部地址，限制响应128KiB、连接2秒/整体30秒，禁用重定向。尚未接入自动评价/扫描，邮件未启用。

B2评价内核：内部令牌保护EvaluateRequest与分页扫描，固定证据、实时账号状态在事务外读取，沿用owner guard串行核验规则修订；触发、站内消息与回执同事务保存。四种比较严格大于/小于；规则ID+用户+股票+交易日去重，旧触发不覆盖；来源缺失独立于价格可用性。自动调度、公开消息页尚未接入，未部署V3到业务数据库。

SDD006C基础：AlertMailAccountClient使用独立context/target协议，revision锚定收件人；连接2秒、整体10秒、64KiB、禁止重定向和重复/未知字段；404/409收件人失效与依赖暂不可用区分。只读接口不发送邮件，原AlertAccountClient保持兼容。

Task5C：原评价operation为owner身份，先查固定引用元数据，持久原RAW和市场登记编号，再读取证据。终态与trigger/inbox/mail同事务；锁owner后物理过期，恢复只GET原登记，最多25条。新开关默认false，旧结果与当前边界检查不变。
