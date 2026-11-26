# SDD006B1 审查与修复

唯一独立审查：Critical 0、Important 3、Minor 0；对照实际代码逐项复现后一次修复，最终完整后端389项/算法76项通过。没有第二轮代理审查。

## I1 JSON数字精度

原树解析经过浮点数，100.0000000000000000001被解析为100.0。真实HTTP测试用RawValue发出确切JSON数字并断言传输内容，RED见runtime/sdd006b1-numeric-exact-red.log。启用BigDecimal及精确JsonNodeFactory，精度超界保持原文后交给严格校验拒绝，合法28位边界通过。初次替换字符串夹具没有替换掉科学计数表示，已纠正；不将那次失败算作正确复现。

## I2 完整日历回退

独立日历覆盖当日但缺前一交易日时，原服务没有采用已有完整RAW日历。合法RAW日历夹具RED见runtime/sdd006b1-calendar-fallback-red.log。现回退要求完整覆盖、应到期日一致、重叠日期开市状态一致，并固定实际使用的整份日历及哈希。初版夹具包含未发布未来日期被发布校验拒绝，修正为合法夹具后才复现目标问题。

## I3 响应体总时限

仅HttpRequest.timeout不能保证收到头后的响应体读取期限。真实HTTP服务返回头及一个字符后停滞40秒，旧实现32秒仍未退出；RED见runtime/sdd006b1-review-red.log。改用异步future.get的30秒整体期限，在finally取消future与订阅者，超时返回SOURCE_TIMEOUT。最终真实停滞测试约30秒通过。

## 审查边界

B2调度/通知与C邮件未包含；实际持续可用与真实除权样本不由代码审查证明。本机三股、重启读回、安全及保留性由实施者另行执行，见verification-sdd006b1.md。既有B0日历GET优化仍待办。
