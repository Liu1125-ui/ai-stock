# 工程入口 Java 包

应用入口与工程状态接口。后续按业务切片增加代码，不为目录图生成空类。使用 Actuator `/actuator/health` 只检查进程。

SDD-005C：模型路由使用具体白名单转发至MS-03，保留Cookie/Origin/CSRF并去除客户端角色及内部凭据；POST在读取时限制64KiB，依赖异常按统一错误返回。

用户预测：ForecastPublicClient使用独立Feign子上下文185秒读期限，只有predictions精确GET/POST/OPTIONS路由；过滤所有客户端服务/角色头，仅page查询透传。其他业务仍保留既有短超时。前端Nginx预测路由190秒，配合内部有限步骤，重复POST由MS03请求键去重。

本人事后验证：仅新增GET /api/v1/predictions/{UUID}/verifications及其单个UUID版本后缀白名单，OPTIONS沿用既有策略；不公开内部执行或写方法，仍只传会话等浏览器头和page，过滤伪造角色/服务头。预测服务再次检查本人归属，ADMIN普通路由也不越界。

持仓问答：精确portfolio-questions路由，独立2秒连接/65秒读取，无重试，4KiB POST；只转发会话/Origin/XSRF等白名单，内部接口仍不开放。

管理员任务接口由专用调度代理负责，只允许三种GET契约和受控预检；保留重复查询参数供下游严格验证，不注入浏览器提供的身份。

管理员任务POST仅retry/cancel精确路径，Origin/固定动作头/JSON大小再次核对；409回执绑定原task/op/action，文案本地映射。SchedulerPublicFeignConfiguration仅用于调度Feign子上下文，关闭HTTP/Feign自动重试、重定向及共享Cookie，并限制成功/错误响应262144字节；其他客户端配置保持原行为。
