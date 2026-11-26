# SDD-003 GitHub案例核验与采用记录

日期：2026-09-30。用途：自选股与行情业务实施参考；不作为股票预测准确性或数据源持续可用的证明。

GitHub CLI未登录，本次未索取账号或Token；使用公开GitHub API与固定提交raw源码完成只读检查，未运行下载代码。参考文件仅在忽略提交的runtime/github-reference缓存。实际提交SHA由commits API复核，许可证文本也已读取。

## 1. Apache ECharts官方K线示例

仓库：[apache/echarts-examples](https://github.com/apache/echarts-examples)。固定提交：88ca004030e999073a15303e5fe32462b8fefae2，Apache-2.0。

实际读取：[candlestick-brush.ts](https://github.com/apache/echarts-examples/blob/88ca004030e999073a15303e5fe32462b8fefae2/public/examples/ts/candlestick-brush.ts)。采用多面板、横轴联动、时间缩放和K线数据顺序的思路，按本系统API组织图表。

不采用示例数据或浏览器均线函数。该函数把首个有效均线推迟一个样本，且只保留三位；本系统后端按明确公式计算，前端只展示。splitData会修改输入数组，本系统映射保持原响应不变。UI颜色、标签与空状态独立设计。

文件SHA-256：B555C035C5856508D29F8C660DDF39ADEE957325DB14C23AE40E6639EC6EB9AD。

## 2. Vue与Pinia自选股案例

仓库：[mhightower/My-Financial-AI-App](https://github.com/mhightower/My-Financial-AI-App)。固定提交：df5362abca59e7496cea04adc42d0723f32b9626，MIT。

实际读取：[watchlists.py](https://github.com/mhightower/My-Financial-AI-App/blob/df5362abca59e7496cea04adc42d0723f32b9626/backend/app/routers/watchlists.py)及[src/stores/watchlists.js](https://github.com/mhightower/My-Financial-AI-App/blob/df5362abca59e7496cea04adc42d0723f32b9626/src/stores/watchlists.js)。参考列表与行情分开加载、成功后更新列表和重复约束处理。

不沿用其客户端user_id身份、15股固定上限、多分组或买卖计划；本系统归属取有效会话、数据库唯一约束负责并发去重、重复添加返回原记录。行情失败显式提示，不能沿用静默忽略报价错误的体验。

文件SHA-256：router为65BA9BD14849B633A71F71920E77C7146A1CD21572A40945210D76953BF03801，store为CCFCA6E0349CB02341256F9C82FB9D4AC88AC56DD61EC9B5D8F7EE700E2A5943。

## 3. Baostock适配与测试案例

仓库：[wenhq/binwh.quant-pilot](https://github.com/wenhq/binwh.quant-pilot)。固定提交：6665ebb5421fd2609acb2ae0c75752daf6dd9fe4，CC0-1.0。

实际读取：[baostock_source.py](https://github.com/wenhq/binwh.quant-pilot/blob/6665ebb5421fd2609acb2ae0c75752daf6dd9fe4/backend/app/services/data/baostock_source.py)及对应[test_baostock_source.py](https://github.com/wenhq/binwh.quant-pilot/blob/6665ebb5421fd2609acb2ae0c75752daf6dd9fe4/backend/tests/test_baostock_source.py)。参考SDK全局会话串行保护、源代码映射和假SDK边界测试。

不沿用源码中的复权标志注释、查询错误返回空表、缺失成交量填零或北京市场映射上海的做法。本系统明确限制已验证沪深代码、正常空结果与失败分开、Decimal解析、保留停牌/缺失状态；在线成功另行验证。

文件SHA-256：source为E82356987D6E7928504161FD4B428BB88FB4714BFF071D35CDFDB32761E08150，test为DD5C9C547BA7E7DE220E75E10FCEE2C765BC3CED6F31D1F278FB1985C1861F92。

## 4. 采用与许可证边界

以上是已读取源码的实现经验。本批按思路独立编写代码，没有复制参考仓库生产文件。ECharts 以 npm 依赖接入，package.json/lockfile 记录依赖版本；其项目采用 Apache-2.0，发布分发时应保留适用许可证与 NOTICE。若后续复制或改编参考源码，应在文件注明原仓库、固定提交和修改内容，并核对相应版权/许可证。

未证明任何参考仓库完整通过审计、能直接满足本科毕设或可稳定取得最新行情。性能宣传、README功能清单和测试mock不能替代本系统真实验收。

## 5. 对本批设计的影响

不改变已定业务范围、五服务、两角色与真实数据来源。补强三项验收：K线顺序为open/close/low/high；图表不能更改API输入或重新计算指标；采集错误不得发布合法空结果。前端参考体验，后端按本系统身份、版本、幂等和错误契约重新实现。
