# 管理员行情查询代理
作用：将三类只读行情查询转发至行情服务。实现：共享严格参数与角色校验、服务发现、禁止缓存和安全错误映射，解码响应上限256KiB。

行情重取四资源明确转调度服务，旧版本查询仍转行情服务；POST只发一次，保存的409回执保留，响应上限256KiB。

固定版本/references由独立MarketReferenceProxyController和market-reference-public Feign上下文转行情服务，18秒/64KiB/no-retry/no-redirect，旧三资源与SDD010B1调度路径不变；结果经公共Report严格校验后重建安全包络。

MarketReviewProxyController提供固定版本审核读写与回执核对，独立有界无重试传输，双层ADMIN及Origin约束。
行情版本生命周期的状态与历史沿用管理员只读代理。网关检查会话、路径、分页、响应字段和版本归属，再生成无缓存响应。
