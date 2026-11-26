# SDD010B3 Task3A内部凭据协议核验

2026-11-04。仅完成Task3前置协议，不代表Task3完整交付。实际证据/发布入口的固定版本门禁仍待Task3B，全部owner接入与管理员页面仍待Task4—8。当前未部署、未生产迁移，不开启停用按钮。

## 实现和边界

新接口/internal/v1/market-version-uses采用独立服务认证过滤器，认证先于正文读取；重复token、超大正文、重复/未知JSON字段及类型错误拒绝，统一no-store。POST只接受登记或请求核对；调用方不能提交“成功证明”释放凭据。

requestId回执和canonical selector通过V17持久化，原请求重放不重新选头；关闭协议后允许读取既有回执；已释放请求不复活。补充原选择用于后续比较evidenceId成员校验，不能只拿相同RAW授权任何证据。

终态证明从固定owner地址查询，所有网络IO在数据库事务外；连接1秒、完整4秒上限、响应64KiB上限、禁止重定向。UNKNOWN、错代次/集合、重复JSON、超时等保留ACTIVE。核对每批至多50个bundle，损坏记录也先记录检查时间，不将前部异常当理由一直饿死后来记录。协议与定时核对默认false；当前构建enforced=true明确拒启动，管理员命令服务始终未就绪。

## 测试

先写HTTP安全测试并观察实现缺失失败，再通过6项HTTP、6项本机HTTP证明client、2项Spring配置、5项MySQL协议/轮转专项，共19项。

完整命令mvn -f backend/pom.xml -Pauth-integration,cli-build -pl stock-market-service -am test成功；本轮新生成报告公共65、行情298，共363项，失败/错误/跳过0。包含Task1/2既有迁移和事务竞争回归，不将19专项重复加总。未改前端，不宣称跑了前端或全系统其他模块。

专项包括：认证前不解析非法正文、重复token/JSON、64KiB、精确字段及类型、调用方伪终态拒绝、回执查询身份；fixed owner路径/服务token、错集合/代次、UNKNOWN、重定向不跟随、停滞响应有界、限体、缺token不发送；默认任务不运行、未齐备enforcement拒启动；原回执/选择保存、门禁关闭重放、RELEASED不复活、旧selector安全补齐；52个任务中50项含损坏记录的首批检查、后来2项得到检查且全部未释放，合法证明可释放。

独立静态审查未发现重要问题，审查人未另跑同库测试以免重置夹具。Compose config --quiet校验通过，未启动Compose或应用配置。

## 本机现有系统保持

只读确认业务stock_market仍V14，market_version_use_request表不存在；只在端口13316隔离测试库应用V17。新proof client网络测试使用本机临时HttpServer和mock，不连接真实业务owner/MaaS/Tushare/Baostock或SMTP。本轮未部署、未发起真实预测、付费AI或邮件；原后台自然活动仍按原配置，不归为本轮测试。

原始日志runtime/lifecycle-task3a-*.log为忽略文件。下一步Task3B必须完成真实证据及发布路径门禁，之后才能把Task3标为完成。
