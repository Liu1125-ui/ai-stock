# 预测服务（MS-03）

作用：保存模型候选、审核发布状态与推荐指针；预测任务/结果将在下一业务切片实现，当前不生成伪造预测。

实现方法：Flyway在stock_prediction建立独立模型表；内部登记经Python安全inspect核对不可变制品，保存原研究评价与来源SHA；公共管理接口经MS-01会话解析核验ADMIN、Origin、CSRF。状态、head、追加审计由同库事务与revision保护，operationId重放返回原回执。发布与推荐分开；下线保留版本/事件，曾发布版本可回滚。API详情见docs/superpowers/specs/2026-10-03-sdd005c-model-release-design.md。

原GET /internal/v1/diagnostics/instruments/{id}仍为受认证行情联调，依赖故障503。Compose中采用Nacos/Feign服务发现，算法地址为algorithm:8090。算法/internal/v1/infer当前明确503，三份现用候选保持PENDING；注册模型不是正式预测业务验收。

测试仅使用限定stock_prediction_test临时MySQL，运行scripts/check-auth-task1.ps1；真实研究报告不同于合成测试fixture。模型目录只读挂载，所有密钥在忽略.env，不提交密码或Token。
