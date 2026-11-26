# 预警规则测试
作用：核验独立MySQL中的权限、幂等与修订并发；仅使用13316隔离测试库。

NotificationSecurityTest：匿名、伪造owner、来源/CSRF、未知字段、会话过期和body上限；NotificationAccessTest：当前资料owner、启用状态、故障关闭。

AlertRepositoryProxyTest通过真实Spring异常翻译代理复现并防止仓储字段空指针；NotificationFeignContractTest通过真实HTTP核对会话与完整资料解码；NotificationResponseLimitTest核验正常/错误正文、未知长度超限与资源关闭。

Task5C专项覆盖真实事务、超时/锁等待、错RAW、未知登记、旧证据跳过和回滚；HTTP/本机传输校验内部证明身份及凭据不泄露给用户服务。无真实邮件。
