# Docker启动恢复记录（2026-10-25）

## 现象与证据

Docker Desktop启动失败，最新backend日志报告Secrets Engine移除本机engine.sock时出现文件不可访问/路径语法错误；Docker API不可用。Docker/run中另有dockerInference残留套接字。两个目录均已核对仅一个零字节ReparsePoint对象，没有普通文件内容。

## 实际处理

停止本次失败的Docker Desktop及backend进程；将原目录同目录改名保留为docker-secrets-engine.stale-20261025-131529-sdd010b1与Docker/run.stale-20261025-131529-sdd010b1。随后启动原Docker Desktop。没有删除文件、factory reset、注销WSL、迁移数据磁盘、更改原数据目录或重建业务镜像。

Docker version实际返回Server Docker Desktop4.85.0、Engine29.6.2，退出0。原11个容器和aistock-sdd001_mysql-data卷存在；它们原先已停止约36小时。先启动原MySQL/Nacos，检查healthy后启动原八个应用容器；专用测试MySQL经原脚本验证标签后启动。前端HTTP200。没有用新开发代码部署替换旧镜像。

## 限制与后续

本次恢复启动，不代表根治：此前2026-10-20也发生同类残留套接字故障。为什么Docker退出后遗留且下次启动无法清理仍需进一步查证；不得凭本次恢复宣称升级/设置已解决长期问题。两个保留目录不自动删除。

容器/卷存在与健康检查不是全量数据库一致性或备份恢复演练。未执行会读取.env并提交内部推理POST的旧check-compose脚本；只做公开健康GET，不额外触发供应商、付费模型或邮件。原有容器后台任务维持原配置，自然活动须与本次人工验收区分。

## 开发连续性

SDD010B1 Task1已提交7353b91，公共模块完整43测试通过。Task2开始时遇到此环境问题，恢复后继续，不能把Docker恢复视为Task2代码完成。