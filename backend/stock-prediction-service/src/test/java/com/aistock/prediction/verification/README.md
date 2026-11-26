# 事后验证测试

作用：检验复权口径、有效样本分母、固定快照、精度与非法输入；隔离MySQL测试验证追加版本、幂等回执、过期与当前指针。HTTP测试校验服务认证在解析前、请求和响应限额、固定版本身份与完整正文超时。

方法：mvn -B -f backend/pom.xml -pl stock-prediction-service -am -Pauth-integration,cli-build verify；或scripts/check-auth-task1.ps1完成五服务回归。MySQL必须stock_prediction_test，禁止在业务库插入模拟预测。纯计算与网络fixture不代表外部实际行情验收。

RealSourceContractProbe为显式手工探针（文件名不在默认自动测试匹配规则内）：配置REALIZATION_PROBE_FILE为脱敏实际证据报告，运行-Dtest=RealSourceContractProbe读取MS-02真实样本并由MS-03复核哈希/协议。仅只读文件，不联网、不写库、不运行或伪造用户预测；正式CI不依赖本机实时文件。

Task4C：VerificationMarketUseMysqlTest在真实隔离库检查新实际证据独立绑定、原预测快照字节不变、未到期不登记、错误RAW拒发布、错代次及许可拒绝；HTTP客户端用例补同一PREDICTION_VERIFICATION头在prepare和读取间的一致性。
