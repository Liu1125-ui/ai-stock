# 预测入口与CI遗漏修复核验

日期：2026-10-18。依据project-completion-audit-20261017.md的AUDIT-01、AUDIT-02及用户“好的，开始做”。这是现有功能的有界修复，保留固定目录/当前分支；持仓与MyBatis-Plus不混入本批。

## 设计与原因

自选卡共享已发布模型目录的五日/股票兼容判断；符合条件时导航到已有预测表单，不执行预测POST。加载、目录失败、不支持分别说明；失败时行情和移除仍可用，并支持只读重试。预测表单和卡片共用同一显示判断，提交仍由后端重新检查权限、版本及新鲜度。卸载或后续加载使旧响应失效。工程诊断页面不再保留“预测尚未开放”的旧提示。

CI读取完整测试所需的五个隔离库配置；补预测库环境变量和建库语句。完整算法测试导入numpy/torch，原工作流仅安装基础requirements，遗漏推理依赖；同步现有Dockerfile的requirements-inference.txt及固定CPU PyTorch版本，不更换业务算法。

## 复现与验证状态

组件回归初次8项中6项失败、2项原名称展示通过，失败对应支持股票无导航、状态提示缺失及错误重试不可用。修复后卡片、列表和预测页定向12项通过，完整前端155项及严格构建通过；原包体积提示保留。

将原工作流env交给实际ModelCatalogMysqlTest，因PREDICTION_TEST_DB_URL/PASSWORD缺失，在严格隔离库前置校验失败；没有连接生产库。这是CI配置消费者复现，不冒充产品断言RED。当前从修正后的workflow实际解析env、建库SQL与Maven命令，在独立aistock-audit-ci-20261018-mysql、MySQL8.0.46和13316隔离端口运行完整后端；最终669项通过，failures/errors/skipped均0，Maven BUILD SUCCESS。原auth测试容器保持停止，生产数据库未改动。

既有算法容器完整pytest94项通过，1条既有anyio弃用提示；使用替身，不调用行情源、MaaS或发送邮件。不把已有容器测试当作全新GitHub runner从公网安装依赖已成功。

日志和本机消费者脚本位于runtime/audit-fixes-20261018。独立只读审查通过，未发现Critical/Important；审查提出的导航测试缺口已补：实际挂载PredictionPage，目录失败、重试、导航全过程createPrediction均0。新前端已单独部署，未推送远程，仓库无remote。后端计算逻辑和数据库迁移未修改。

## 完整回归发现的测试假设问题

首次完整后端在ComparisonTaskMysqlTest.concurrentCreationAndClaimEachHaveOneOwner失败：两并发claim均暂时为空。claim在一个事务中执行多个FOR UPDATE SKIP LOCKED扫描；这允许本轮因锁竞争跳过，实际工作器会继续定时轮询。未抓取数据库锁轨迹，不能断言复原了此次具体锁顺序。

经独立审查，修正过强的“首轮恰好一名”断言，保留严格不变量：并发Future全部结束且不掩盖异常/超时；首轮最多一名；若为零则原任务必须仍QUEUED、attempt为0、token/lease为空、context恰好一份，并在同一NOW单次无竞争领取成功；最终唯一owner、RUNNING/attempt_count=1/ordinal=1/token一致，之后再领为空且总attempt为1。用例重复10轮；没有修改产品仓储或放宽数据隔离。

本机重建临时测试容器的一轮漏传工作流服务MYSQL_DATABASE，导致stock_user_test不存在，该失败日志保留。随后完整复制服务env重建空库并重跑；它属于本机验收工具错误，不是产品配置修复证据。前端首次构建命令漏指定根.env，未执行构建；补显式--env-file后镜像构建成功，未输出配置内容。所有失败记录均保留。

## 最终交付与限制

完整后端669项通过（增加的9项来自并发用例10轮），完整前端155项/38文件通过，严格构建通过，算法94项通过。独立审查最后无Critical/Important/Minor。原包体积与anyio弃用提示保留。

前端修复e26c9f5、CI修复98d4aef、并发测试85f1ef1分别提交。仅更新aistock-sdd001的frontend容器；原六Java/算法及生产数据库容器未重建。七个健康端点均HTTP200，前端HTTP200；实际WatchlistCards-bs4NYDkq.js和PredictionPage-FAUcaHAE.js与本机构建逐字节一致。算法探针初次误用Java的/actuator/health返回HTTP错误，改为其源码定义的/health后正常，不代表算法曾故障。

旧前端保留标签aistock/frontend:audit-fixes-before-20261018。需要回退时将该标签恢复为aistock/frontend:sdd001，再用根.env、项目名aistock-sdd001、deploy/compose.yaml，仅up -d --no-deps frontend；本轮未实际回退演练。临时CI MySQL核对专用label和tmpfs后已清理，原测试容器/生产卷未清理。

浏览器已打开本机系统，但会话过期停在登录页，已请用户自行登录。此项在用户自行登录后完成，见下方补充验收；先前停在登录页的记录保留以区分验收阶段。远程无remote，未执行GitHub runner，也未验证全新runner公网安装依赖。

本轮人工执行新增真实行情查询0、MaaS调用0、邮件发送0；既有后台定时任务保持原状态。没有新增预测请求或修改历史结果。持仓与MyBatis-Plus仍待下一片设计和实施，不把本次修复视为全系统完成。

## 用户登录后补充验收（2026-10-18）

用户自行登录后，浏览器确认当前角色为ADMIN。通过实际页面导航验证：个人中心→行情首页，首页SH600030自选卡出现可点击“发起预测”；点击后进入/market/SH600030/predict，显示“SH600030 · 五交易日预测”、已发布模型transformer-s3-2023-seed43-9a96167ecb00234d及可用的“发起五日预测”表单按钮。随后进入“我的自选”，同一股票卡入口同样可点击并到达同一表单。

两条真实登录态导航路径验收通过，未点击提交预测按钮、未调用付费AI、未发送邮件；未修改自选列表。页面保留在预测表单。本轮未使用普通用户账号做第二轮浏览器验证，不能把ADMIN实际操作扩大表述为两个角色均已人工验收。
