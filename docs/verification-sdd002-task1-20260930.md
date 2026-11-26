# SDD-002 Task1验收记录

日期：2026-09-30。基线：19083db。范围：用户数据库、安全工具和注册挑战/限频基础组件；不是完整账号业务验收。

## 已实现与验证

实际Flyway迁移在独立MySQL8.0.46的stock_user_test执行。邮箱唯一、有效实验授权唯一、未验证邮箱不可置空、非法角色与状态拒绝均通过数据库测试。验证挑战可以先于账号存在，过期边界拒绝、错误上限拒绝、两个并发消费者只能一个成功。

密码使用BCrypt成本12，随机盐；保留首尾空格，验证字符及UTF8字节限制。公开Account不含密码摘要。HMAC绑定挑战与邮箱，限频桶键隐藏原始邮箱/来源。

同邮箱冷却、小时发送上限、来源上限、窗口结束恢复、拒绝后不消耗其他邮箱额度、登录失败双桶上限均验证。非法邮箱只计来源额度，即使调用事务回滚仍保留计数。新服务实例读取相同数据库保留限频，不声称tmpfs容器停止后保留数据。

用户和挑战FOR UPDATE必须在活跃事务中执行。昵称2—30个Unicode码点，按存储值校验，支持Unicode且拒绝控制字符；revision拒绝旧页面覆盖。

## 命令与结果

- scripts/check-auth-task1.ps1注入测试地址与忽略文件中的临时凭据，执行整个后端`mvn -B -Pauth-integration clean verify`。最终BUILD SUCCESS，39测试、0失败、0错误、0跳过。
- 不提供测试数据库凭据时，标准`mvn -B clean verify`通过；默认排除mysql标签。这不代表MySQL业务验收。
- UserRepositoryTest与ChallengeRepositoryTest分别以`-DargLine=-Duser.timezone=Asia/Shanghai`、`-DargLine=-Duser.timezone=UTC`执行，均通过；验证原始DATETIME保存UTC及截止时间读回一致。
- git diff --check无空白错误。所有新建代码目录附README。

运行日志存于忽略的runtime/auth-task1-final.log和auth-timezone-*.log；不提交凭据或测试生成文件。生产容器未重建，业务数据库未执行本次迁移。

## 独立审查

发现昵称长度不符合规格、非法邮箱绕过来源额度两项Important问题。新增测试先确认失败，再修改实现，最终整个后端回归通过。时区疑虑经两种JVM时区的真实数据库测试排除。

次要待办：RateBucketRepository.lock本身尚无事务断言，当前唯一调用方RateLimitService已保证事务；未来新增调用方前补保护。这不影响已验证调用路径。

## 实施取舍与限制

使用Docker CLI隔离MySQL而非Testcontainers，降低新依赖数量；CI需明确启动测试数据库。普通构建与数据库配置分开，CI需显式执行auth-integration才能验证数据库约束。

根据用户授权由Codex实现，未把客户端打开或退出码0当作CodeArts代码生成证据。

真实邮件、HTTP注册、可撤销会话、浏览器安全、管理员和前端属于后续Task2—7。真实SMTP配置未取得，尚无真实邮件送达验收。
