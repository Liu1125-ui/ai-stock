# SDD010B3 Task4C预测、实际验证与Python凭据核验

2026-11-08。Task4三条owner链路现已接入：分析（Task4A/B）、五日预测与新实际验证（本片）。Task5比较/持仓/预警/本地派生和Task6—8管理员与上线仍待实施；未部署、未开启全局门禁，FR-11不关闭。

## 实现与契约

FORECAST以全局predictionId为使用operation，避免用户范围公开operationId跨用户冲突；新实际验证沿用原全局operationId。两者先提交原操作，再持久保存原requestId和单股CURRENT_HEADS选择，登记后保存原完整RAW集合。响应未知只GET原登记，不换头、不重新POST或启动模型。

V14新增执行代次、fencing修订及本库prediction_market_use。首版每个操作代次1且不在原编号重启。预测成功/失败/到期与验证AVAILABLE/FAILED/SKIPPED提交均形成不可再写终态，再提供证明。老预测新增finished_at保持空，不以创建时间伪造完成时刻；新绑定只允许实际RUNNING。原五点snapshot_json不更新，验证结果仍独立追加。

Java将同一凭据传入准备、采集、输入发布、推理及实际验证读写。窗口/实际证据RAW须与原回执一致；推理前检查本地操作仍可执行。未到验证日期直接SKIPPED，不申请使用，不取源。

Python正式推理先服务认证，原五字段正文仍不得传window或私有_marketUse。路由检查单值、规范UUID、正代次及prediction/FORECAST身份，内部生成私有子进程凭据。两个市场读取都转发同一份头，从市场拿绑定RAW；模型发布查询不带使用头。MARKET_VERSION_USE_REQUIRED开启时，缺凭据在worker启动前拒绝。离线inspect/validate不要求业务凭据，不能冒充正式预测。

证明只返回同代次实际终态、结果身份和版本集合；原绑定即使停用新登记仍可核对。预测owner能力列三个已实现目的，仍PARTIAL（比较/持仓未接），管理员actions=false。新增预测/验证/算法required三个模板开关全部false。固定引用扫描增加prediction_market_uses，共17组，不将行数当许可。

## 测试与审查

Python先观察7项缺少校验/转发的反例失败（1项离线边界已通过）；修复后正式推理/候选/传递专项29项通过。新增Java owner/客户端测试在缺实现时编译失败，实施后专项通过：固定RAW与五点结构、许可拒绝零源/模型调用、错误RAW禁止推理/验证发布、错误代次、到期形成真实失败后拒迟到成功、原请求未知登记恢复、未到期不登记、原预测快照不变。增强已有真实HTTP测试，逐请求检查准备/采集/发布/重读及实际证据prepare/read的相同身份。

独立只读审查无Important/Critical。防御性Minor记录保留：手工将通用表owner_operation_id改成非UUID可能使批次解析提前退出；正常写入仅接受UUID，本片未承诺任意手工破坏主键后的自动修复。坏receipt本身仍先轮转再解码。审查人未改代码或操作共享测试库。

完整Java命令：

```powershell
mvn.cmd -f backend/pom.xml '-Pauth-integration,cli-build' -pl 'stock-market-service,stock-prediction-service' -am verify
```

20:06完成，退出码0、BUILD SUCCESS；本次新报告公共65/行情313/预测298，共676项，失败/错误/跳过0，两个服务打包通过。

Python使用已有aistock/algorithm:sdd001镜像，network none、源码只读挂载、PYTHONDONTWRITEBYTECODE和pytest缓存关闭，执行全部tests：102项通过。主机模型venv缺FastAPI，因此复用已有镜像，没有安装新依赖。测试仅本机HTTP/伪模型和合成小权重，未挂载真实业务模型或访问外网，不作准确率证据。

Compose config --quiet、git diff --check通过。生产数据库/镜像/实际配置未修改，仅隔离测试库预测V14/市场V17；无真实数据源、MaaS或SMTP调用。前端未改，不声称重跑前端。

## 后续

按用户授权连续进入Task5，先核对比较已有租约UUID/过期重领与协议代次的映射，不能覆盖旧在途证明；持仓原快照选股后原子登记多RAW，空仓不登记；预警仅同库触发/消息/outbox真实终态后证明，不补发旧邮件。随后补全部入口和worker就绪检查，再管理员接口/页面，最后统一受控部署。

忽略日志：runtime/lifecycle-task4c-python-red.log、python-green.log、python-full.log、client-red.log、owner-red.log、owner-green.log、final-verify.log（均同lifecycle-task4c-前缀）。
