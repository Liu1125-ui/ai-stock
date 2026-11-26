# 后台预警账号状态
作用：为通知服务提供账号当前启用/禁用状态，不暴露邮箱、密码或会话。
实现：先核验内部服务令牌，再以本库UserRepository只读查询，返回检查时间；依赖失败受控503。此接口不创建预警或发送邮件。

SDD006C：alert-context只返回账号状态、revision、验证标志与核验时间；alert-mail-target须匹配revision才返回当前验证邮箱。两者仅内部服务令牌访问，响应no-store，公开网关不透传。原alert-status三字段协议保持。
