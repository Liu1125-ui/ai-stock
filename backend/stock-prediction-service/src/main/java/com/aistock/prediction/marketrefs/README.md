# prediction行情版本候选引用
作用：仅向可信内部调用方返回本服务保存的候选引用行数。
实现：静态本库白名单注入公共有界扫描器，GET+Token、no-store、无写操作。JSON同一行多列OR只计一次；未知不能表示0，不能作为停用许可。

V13增加固定白名单analysis_market_uses，扫描raw_version_id，共16组；凭据引用行数仍是候选引用信息，不代替市场在途使用或终态证明，不据此执行停用。

V14再补prediction_market_uses固定白名单(raw_version_id)，共17组；尚未收到绑定时RAW为空，不将未知视作可停用证明。

V15纳入comparison_market_uses.receipt_json固定引用候选，共18组。多RAW收据是历史引用候选，不是当前许可或在途计数；未知不能当0。

固定引用扫描新增portfolio_market_use.receipt_json，持仓在途及历史绑定均不遗漏。总计19类本地固定引用。
