# CodeArts SDD-002 Task1生成请求

在E:\aiStock\项目代码\stock-system工作。请读取docs/superpowers/specs/2026-09-30-sdd002-user-account-design.md、2026-09-30-sdd002-database-design.md和docs/superpowers/plans/2026-09-30-sdd002-user-account-plan.md，仅实施Task1。先读取新增测试，生成用户服务V1数据库迁移、EmailAddress、SecretDigests、UserRepository、ChallengeRepository、RateLimitService、RateBucketRepository。使用现有JdbcTemplate、BusinessException和Java17，数据库与限频约束以规格为准，每个新业务目录附README。不要修改现用MySQL数据、不要读取或输出.env，不要改其他服务，不提交或推送Git。完成后在代码中保留真实修改，明确报告生成文件及测试结果。不要把文档或客户端打开当作功能完成。

当前测试约定：EmailAddress.normalize为静态方法；SecretDigests(byte[] key)提供code(UUID,String,String)和session(String)；RateLimitService(JdbcTemplate,byte[] key)提供reserveRegistration(String,String,Instant)。测试使用scripts/start-auth-test-db.ps1建立独立MySQL，再用scripts/check-auth-task1.ps1运行。AuthDatabaseFixture通过Flyway仅迁移stock_user_test，禁止迁移现用数据库。
