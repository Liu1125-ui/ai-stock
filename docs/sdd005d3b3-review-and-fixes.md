# D3B3 唯一审查与修复

只读范围fdbe261..6ec76fd，Critical0/Important1/Minor0，未再审查。

Important：事件请求前捕获时间，跨20点或长故障后仍使用旧cutoff；当普通验证已保存更高due完整current时，修订重核只保存历史而无法更新current，后续日扫描排除完整记录。最小选择：未完成事件跨截止日前进并从首条重新分页，发布页时再次检查；event HTTP后读新clock。任务key的SHA256加入实际due，旧页CAS拒绝而新轮不发生参数冲突。MS03原due/sequence不回退保护保留。

跨日fixture测试RED为expected2026-10-07 actual2026-10-05，修复后GREEN；额外覆盖请求跨20点、页提交跨日拒绝和已完整current新证据追加更新。全部为隔离数据，不能代表真实未来行情效果。事件顺序/回滚、完整hidden资格、RAW回切、原子游标、内部认证与专用地址没有其他阻断问题。
