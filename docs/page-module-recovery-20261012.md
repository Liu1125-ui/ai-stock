# 部署后个人中心模块加载失败修复

## 直接证据

用户截图显示Failed to fetch dynamically imported module，路径/assets/ProfilePage-D_iynndP.js。部署前请求该旧路径返回HTTP200、Content-Type text/html；当时新文件为ProfilePage-Cx2QtEo_.js。Nginx原先把不存在的assets回退为index.html，旧浏览器路由表请求已替换的哈希资源后无法执行。当前权威用户服务健康UP，未登录GET /api/v1/users/me正确返回401，CSRF端点200；不能将资源错误断言成密码错误或当前账号服务故障。

## 修复与回归

- /assets/缺失文件真实返回404，不回退SPA；HTML入口与前端路由响应no-store，已存在的哈希资源可长期缓存。
- 路由模块/CSS加载失败时，只对同源相对路由硬导航；会话一分钟最多一次，sessionStorage不可用时停止自动恢复并提示手动刷新。不重放登录/业务写请求，不处理普通业务异常为更新错误。
- 登录页面用易懂的页面资源提示替代原始模块报错；已知健康匿名状态不再保留旧unavailable警告；恢复权威身份后清除启动时残留的账号服务错误。
- RED确认缺失恢复模块和两个残留警告断言失败；针对性6项通过，新增健康匿名与手动提示覆盖后，完整前端31文件115项通过，严格类型与生产构建通过。保留既有大包提示。

日志runtime/page-module-recovery-red.log、green.log、full.log、build.log（均带page-module-recovery-前缀）。仅前端变动，后端与算法未改，未重复运行其完整测试；此前536/76项是上一轮结果。

## 部署核验

已保存并核验前端回滚标签aistock/frontend:before-page-recovery-20261012，仅更新frontend容器，nginx -t通过。实际HTTP核验：截图旧模块404、当前模块200且JavaScript类型；首页与/profile为200且no-store；匿名本人接口401、CSRF200。没有更改账号、密码、规则、偏好，没有发送邮件或调用MaaS。

用户在修复部署前反馈“已登录”，这证实用户可进入个人中心，不等同本次新恢复逻辑或邮件偏好已经人工验收。浏览器自动化工具内核路径错误仍存在，无法代替用户视觉检查。旧标签页尚未运行新恢复代码，首次请Ctrl+F5刷新；以后加载新入口后才具备自动恢复能力。真实预警详情、外发和长期运行继续按原待办执行。

机器证据：evidence/page-module-recovery-20261012.json。
