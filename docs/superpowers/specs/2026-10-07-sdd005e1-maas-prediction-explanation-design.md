# E1 MaaS预测解释设计

范围：FR05已有本人五日成功预测的自动解释/失败重试及保存依据。FR09开放问答、持仓/股票比较尚未实现，后续接入；不删13业务/5服务/2角色。MaaS不改权重、预测值或评价，不给交易指令。

华为云标准V2完整HTTPS Chat URL及model由用户确认：https://api.modelarts-maas.com/v2/chat/completions，deepseek-v4-flash。Authorization Bearer、stream=false，使用choices[0].message.content。不照抄官方示例verify=false，TLS校验保持开启。API Key只经本机隐藏脚本写入忽略.env，绝不前端下发/记录/提交。最多少量真实验收调用，不开启无限重试。

MS03基于可信Actor的ForecastRepository.get与VerificationQueryRepository.list取得本人固定成功快照及current评价（含hidden）。发送白名单事实，去掉用户邮箱、密码、session、用户id、完整输入特征和任意问题。事实包括原点日期/价格、各目标日期/预测价格、服务端计算趋势与相对变化、训练截止/模型制品、验证状态/已核验数与保存误差/局限。不将未来值标作实际行情。

采用三段JSON模板：paragraphs[{kind:overview|readout|risk,template,refs:[factId]}]，恰好三种kind各一项。自然简洁文本，数字与日期只使用{{FACT_ID}}，每个引用存在且与模板占位相符。程序替换事实值，拒绝独立数字/未声明引用/HTML/超长/交易指令与收益保证。不声称字段/词检查完全消除自然语言幻觉，需固定案例核验与真实输出检查；核验不通过不显示内容。

独立记录contextHash、promptVersion、providerModel、预测/input/model版本、current验证版本及事实JSON；新验证产生新解释上下文，旧解释保留并标记已过时。仅GET /api/v1/predictions/{id}/explanation与POST同路径（body operationId UUID），Actor/CSRF/归属先校验，ADMIN仍只读本人。body不接受userid/价格/事实/任意prompt。服务端单全局持久槽，操作50秒租约，HTTP2秒连接/35秒完整截止/128KiB正文/无重定向；外部调用在事务外。并发或重放不重复调用；未知响应用同operationId读取，不盲目重试，失败显式操作新ID才重新生成。禁无限自动重试。

UI独立ExplanationPanel：GET保存状态；已配置且成功预测首次无解释时POST一次，保存成功不反复调用；事实变化标记旧解释并提供重新解释按钮。忙/失败/未配置状态单独显示，数值与验证仍可用。账号/预测切换清空并拒绝旧响应，401沿用全局退出，纯文本渲染，不v-html。

计划两种方案：直接自由文本数字校验容易漏算数口径；全固定文案无法体现AI理解，故选择结构化模板+服务端数值与保存证据。采用当前默认限制，未来业务扩展另加事实提供器。固定目录codex分支，无remote不push；用户已授权常规设计自主审核。
