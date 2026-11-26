保存训练接单和原始任务身份。调度执行由调度服务承担。此目录当前没有公开训练入口。
预测服务接单接口默认关闭。仅在隔离环境设置 aistock.training.admission-enabled=true 并提供 aistock.training.catalog 时注册路由。目录格式为配置编号到 configurationHash/datasetHash 的 JSON 对象。提交实时核验管理员或当前实验授权，既有回执按当前登录人读取。回执只表示已接单，调度服务接收后才可展示排队。
交接工作器默认关闭。隔离启用 aistock.training.dispatch-enabled 后，固定调度地址按原编号交接，响应未知先回查，匹配全部指纹才确认。
