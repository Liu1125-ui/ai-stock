# 前端工程

Vue 3、TypeScript、Pinia、Vite、Element Plus、ECharts。已实现邮箱注册/登录、个人中心、管理员用户管理、首页证券搜索、本人自选和历史行情分析。分析页显示真实已发布版本及 MA/MACD/RSI；预测入口在模型未接通时禁用，不显示伪造结果。

安装 pnpm install；执行 pnpm test、pnpm typecheck、pnpm build。开发运行 pnpm dev，将 /api 代理至本机网关。历史行情默认三个月，半年/一年可切换；行情出错不会清空本人自选记录。
