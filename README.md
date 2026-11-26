# 基于 Transformer 的股票时序分析与辅助决策系统

这是毕业设计的持续开发工程，包含五个业务微服务、网关、算法服务、前端、MySQL 和 Nacos。当前功能与待办以 [实施状态](docs/implementation-status.md) 为准；历史验收记录不代表所有规划业务已完成。

## 本机使用

日常运行请双击根目录的 **启动系统.cmd**。脚本先启动 Docker Desktop（如有需要），检查原有十个容器，等待数据库、注册中心和应用依次就绪，成功后打开 http://127.0.0.1:15173/ 。需要已验证的邮箱账号登录。

脚本复用原容器和配置，不重新编译、不拉取镜像、不创建数据库或更改后台任务开关。重复运行不会重启已运行的服务。启动失败时窗口保留错误提示；缺少原容器时会停止，不能用重置 Docker 或新建空卷代替恢复原部署。

命令行可运行 `pwsh -File scripts/start-system.ps1 -NoBrowser`。开发构建与部署仍使用 `scripts/start-local.ps1`，它会编译和构建，不是日常启动入口。停止使用 `pwsh -File scripts/stop-local.ps1`，保留数据库卷。

已在本机启用腾讯控股实时报价时，启动脚本会在行情服务就绪后启动或复用富途采集器。OpenD 需保持登录，采集器故障会单独提示，系统仍可使用。

## 项目结构

- backend/：Java 17 / Spring Boot 多模块；用户、自选、行情发布和查询等服务。
- backend/algorithm/：FastAPI 采集入口与未发布模型时的受控拒绝。
- frontend/：Vue 3、TypeScript、Pinia、Element Plus、ECharts。
- deploy/：Compose 与本机配置模板。
- scripts/：本机启动、验证与受控数据导入。
- docs/：SDD 规格、计划、执行与验收记录。

## 验证与边界

早期 SDD-003 验收记录见 docs/verification-sdd003.md；最新功能和验证记录见 docs/implementation-status.md。日常启动修复的验证见 docs/startup-reliability-20261104.md。已有 GitHub Actions 配置尚未在远程执行；当前无 Git 远程。CodeArts 生成代码要求尚无可核实源码证据，不能将现有 Codex 实现归为 CodeArts 产出。
