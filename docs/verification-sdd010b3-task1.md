# SDD010B3 Task1 契约与迁移基础核验

2026-11-04。仅Task1完成，未部署、不开放管理员按钮。Task2本库互锁及Task3—8消费者接入、API、页面和受控上线尚未完成，FR-11继续部分完成。

实现了许可三态的纯决策、cycleId隔离、精确长整数与UUID/用途校验、严格JSON/原因规范化、不可变使用选择和稳定SHA-256指纹。compare selector按股票/证据配对排序，不能替代owner完整业务指纹。

V15新增lifecycle、use_bundle、use、action、action_event五表。owner/用途/代次统一存bundle，使用明细通过同库FK关联，避免重复身份矛盾。仅回填AVAILABLE的baostock/RAW，不改写原行情头、价格和预测输入。测试夹具按新增FK顺序先清理这些表，避免跨测试遗留。

先运行缺失实现的契约/策略RED，再通过11项专项。迁移测试先因仍是V14、表不存在而失败；加入V15后暴露MySQL CHECK错误的Spring类型与预期不同，核对底层错误3819，改为精确vendorCode断言，保留1062唯一键及1451外键检查。不是放宽成任意异常即可通过。

完整执行：mvn -f backend/pom.xml -Pauth-integration,cli-build -pl stock-market-service -am test。本轮新生成报告：公共65项、行情263项，共328项，失败/错误/跳过均0。其中新纯策略5项、契约6项、迁移4项；既有MySQL行情查询、版本审核、比较及其他测试一并通过。未修改前端，不宣称本轮重跑前端或全系统其他业务模块。

迁移4项覆盖：新测试schema全迁移；V14含旧行情/头/日线/预测输入升级、逐行不变、Flyway再次执行0迁移；bundle与use唯一/FK/状态约束；两个真实连接并发登记同业务身份仅一条成功。最后一项只证明数据库唯一约束，不等同于后续acquire/disable事务互锁已经实现。

测试只使用带测试标签的127.0.0.1:13316临时MySQL与固定专用schema stock_market_lifecycle_migration_test。清理Flyway schema前必须验证显式测试URL和数据库名；不接生产库。凭据由忽略的runtime文件进程内注入，未提交或输出。

独立只读审查无重要问题；指纹范围与规格表述小差异已修正并同步README。审查人未独立重跑测试，测试证据来自本轮实际命令。

只读核对本机业务stock_market仍是V14，五张新生命周期表数量为0；没有运行生产迁移、重建服务、修改业务开关、调用付费AI或主动发送邮件。本轮现有后台自然活动不因此统计为零。原日志在忽略目录runtime/lifecycle-task1-*.log。

下一步Task2实现原子登记、动作存储和真正acquire/disable/restore/release竞争测试；Task1基础不能单独作为开放停用按钮的依据。
