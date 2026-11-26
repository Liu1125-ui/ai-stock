# 2026-10-05 模型发布失败排查

## 实际证据

用户seed43发布后仍显示待审核。Nginx已记录该发布路由409、403、409，不能将点击按钮当成发布成功。数据库三个模型PENDING，正式publication接口409/MODEL_NOT_PUBLISHED；制品inspect接口200/AVAILABLE。

已登记MySQL JSON与哈希核验后的制品清单只有两个mean浮点值不同：mean[2] 235.0060882568359 对235.00608825683594；mean[3] 237.90721130371097 对237.90721130371094，均相差一个double ULP。既有JsonNode.equals严格比较导致MODEL_ARTIFACT_CHANGED，返回通用“状态已变化”提示，普通fixture没有实际浮点数组，未覆盖这个数据库往返边界。

用户截图另显示账号服务无法连接。当前账号与网关health UP、经前端CSRF接口200/OK；本次未复现该瞬时连接故障，不能据此宣称其根因已修复。403可能由Origin/CSRF/会话权限造成，仅日志状态不足以断言具体原因。若仍复发继续核对实际错误码与服务发现。

## 修复边界

仅在制品inspect已按固定manifestHash核验之后，对元数据浮点叶子允许一个ULP的JSON存储往返差异。结构、字段、整数与字符串保持严格一致；更大的数值改变仍拒绝。没有改权重、制品清单、数据库记录、哈希认证、审核权限或自动发布模型。

## 测试与部署

隔离stock_prediction_test复现真实MySQL JSON差异；新增两个回归覆盖合法往返发布及真实mean改变拒绝。RED阶段8项中1项因旧比较MODEL_ARTIFACT_CHANGED失败，另外7项通过，错误出现在预期发布检查处。GREEN、构建部署与实际边界核验以本次执行记录为准；用户登录后的真实发布仍需重新操作并核对数据库及publication接口。

运行日志为忽略目录runtime/model-publish-roundtrip-red.log与runtime/model-publish-roundtrip-green.log，不提交含测试环境变量的Surefire XML。此修复不表示预测正向联调或FR-06事后验证已完成。

## 本次实际结果

预测服务完整39项测试与common2项通过，Maven verify/打包成功；只重建预测服务容器。17:08部署后预测与账号health均UP，前端CSRF200/OK，未登录管理模型401/AUTH_REQUIRED。已生成runtime/model-publish-roundtrip-http.json。用户真实发布需要关闭原弹窗、刷新状态、重新填写并确认，成功状态须再次核对；本次未代替用户发布。
