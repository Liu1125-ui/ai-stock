# E2b 独立终审与一次集中修复

范围03339da..e5e2f72；prediction_reference_final_review只读审查，未执行测试、访问数据库或云端。Critical0、Important1、Minor1。

Important：仅marketVersionId变化时Panel watch清除pending/预测选择，未知结果丢失原操作。RED复现版本变化、当前行情消失均丢失重放；修复将对象重置限定股票/范围/账号变化，版本变化保留原输入，pending可以不依赖当前行情可用性或云配置状态核对已有请求。新生成仍要求有效行情/配置。

Minor：翻到另一候选页，保留的合法选择因不在当前页被禁用。RED复现；本对象保存已查询候选ID，分页不清除，股票/范围/账号重置清除。它只用于交互可用性，新生成服务端仍重新校验本人、股票和hidden状态，不能代替权限判定。

三个问题场景测试RED3/12，runtime/e2b-review-red.log。一次集中修复，完整前端与生产构建结果在验收文件记录。后端代码不受本次修复影响，不发起循环复审。
