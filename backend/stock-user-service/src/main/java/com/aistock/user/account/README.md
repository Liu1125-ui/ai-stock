# 账号与凭据工具

作用：规范化邮箱、校验并加密密码、计算凭据摘要、访问本服务用户表。

实现：EmailAddress 使用ASCII邮箱规则且不合并邮箱别名；PasswordHasher 使用BCrypt成本12并保留密码中的空格。SecretDigests 对验证码使用绑定挑战与邮箱的HMAC，对会话令牌只计算摘要。UserRepository 使用参数化SQL，普通注册固定USER角色，资料更新使用revision防止旧页面覆盖新数据，行锁要求调用方已开启事务。

当前只提供内部组件，尚未开放注册或登录接口。返回的Account不包含密码摘要。
