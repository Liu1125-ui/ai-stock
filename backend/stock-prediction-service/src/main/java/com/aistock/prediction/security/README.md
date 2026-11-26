# 模型接口身份与请求保护

作用：将MS-01验证后的会话身份用于MS-03模型权限。方法：Feign解析Cookie、不信任客户端角色；Spring Security验证ADMIN、Origin与CSRF，内部模型路径独立服务凭据校验，JSON在控制器中有界读取。公共写与内部登记使用不同身份边界。
