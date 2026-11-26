> 2026-10-27后续：已随管理员行情重取入口部署，生产内部401/400/404均验证no-store；相关旧“未部署”说明为此前过程状态。见[本轮证据](evidence/sdd010b1-verification.json)。

# 内部行情预检错误响应缓存头修复（2026-10-27）

## 原因与修复

上次记录的Minor：成功响应设置no-store，但参数错误400、资源不存在404、依赖不可用503和未知子路径404经异常处理返回时缺失缓存控制。现已将该资源子树的no-store设置移到原内部认证Filter入口，在认证、Controller及异常处理前统一生效。只针对/internal/v1/market/admin-refresh-context及其子路径；不改变原鉴权、供应商调用、数据发布或数据库结构。

## 验证

先以真实MockMvc链路复现，4项缓存测试中3项失败，原因均为Cache-Control缺失；修复后缓存头与原认证专项共9项通过。再执行公共模块与行情模块clean verify（cli-build配置），公共、行情，零失败/错误，构建成功。依赖的404/503用测试对象模拟，HTTP认证/控制器/公共异常处理实际运行。

本轮Docker Desktop/backend进程未运行，com.docker.service为Stopped；未启动Docker、未执行MySQL标记测试、未替换运行镜像。MySQL测试由原模块配置排除，不能将此次成绩冒充完整数据库集成回归或线上验收。供应商、付费模型和邮件实际调用0。

## 状态

源码已修复、测试及打包通过；运行版本仍待下一次部署后只读复查。因此只关闭源码缺口，不把生产Minor标为已验收，也不改变SDD010B1 Task3—7待实现状态。现有数据库卷和生产数据未操作。测试日志保留在本计划专属工作区的cache-header-red/green/full-verify.log。