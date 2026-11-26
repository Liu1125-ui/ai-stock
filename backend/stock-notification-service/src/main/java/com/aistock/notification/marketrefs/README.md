# notification行情版本候选引用
作用：仅向可信内部调用方返回本服务保存的候选引用行数。
实现：静态本库白名单注入公共有界扫描器，GET+Token、no-store、无写操作。JSON同一行多列OR只计一次；未知不能表示0，不能作为停用许可。

固定扫描新增alert_evaluation_market_use.raw_version_id/receipt_json，总计4类。
