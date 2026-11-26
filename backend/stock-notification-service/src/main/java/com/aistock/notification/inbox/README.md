# 本人站内消息

## 作用
提供消息分页、未读数、已读操作和规则触发历史。所有owner仅取自MS-01已验证会话，管理员同样只可读取自己的记录；不接受客户端传入owner。

## 实现方法
同库消息与触发快照联表，分页列表、total和unreadCount在REPEATABLE_READ事务内一致读取。消息详情只解析创建时的Snapshot，不重新读取当前行情或规则阈值。批量已读先按消息ID排序加行锁，整批归属通过后再更新，coalesce保留首次readAt；混入不存在或他人ID统一404并回滚。

## 公开接口
- GET /api/v1/messages：page默认1、size默认20且最大50、unreadOnly只能true或false；返回items/page/size/total/unreadCount。
- GET /api/v1/messages/unread-count：返回unreadCount。
- PATCH /api/v1/messages/{id}/read：请求体必须为{}，返回固定消息及首次readAt。
- POST /api/v1/messages/read：请求体仅包含ids，1至50个唯一完整UUID；返回items。不提供无限制全部已读。
- GET /api/v1/alerts/{id}/triggers：先验证本人规则，按创建时间和ID降序返回items/page/size/total。

分页最多100000页。新接口拒绝未知或重复查询参数，写请求继承Origin、会话及CSRF验证，体积上限4096字节；路径及HTTP方法精确匹配。响应沿用统一ApiResponse，快照金额及变化计算源值为字符串、日期和时间为ISO格式。未读计数允许在下一次请求因并发更新而变化。

## 边界
不创建消息、不发送邮件、不调用模型；创建逻辑仍在预警评价事务中。evaluationAvailability由ALERT_EVALUATION_ENABLED控制，默认false，须与已部署的planner/worker一起启用；email deliveryAvailability仍为NOT_ENABLED。NotificationResponseLimit只限制通知服务的内部身份响应，保持原64KiB保护，不用它处理公开消息分页。
