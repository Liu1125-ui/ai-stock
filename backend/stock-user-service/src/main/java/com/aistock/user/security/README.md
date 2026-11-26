# 浏览器安全边界

作用：可撤销会话身份、角色授权、Cookie、CSRF与请求来源保护。

实现：Spring Security无状态链每次从HttpOnly会话Cookie解析当前身份，不信任客户端用户ID或角色头。写接口要求白名单Origin与CSRF，内部接口独立服务凭据且不经网关公开。认证/权限失败输出统一错误。登录和退出清除旧CSRF，前端必须重新获取；生产HTTPS配置cookie-secure=true。
