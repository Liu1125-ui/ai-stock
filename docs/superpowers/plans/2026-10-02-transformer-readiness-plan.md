# Transformer 接入前核验执行计划（已执行）

> **For agentic workers:** 后续实现使用 superpowers:executing-plans；本清单是只读准备阶段，由本代理直接执行，不委派产品实现。本轮全部核验步骤完成，正式预测业务仍未实现。

**Goal:** 验证现有权重能否复现研究结果，并找出正式五交易日预测的必要输入条件。

**Architecture:** 在已有研究环境重载不可变权重，复算归档真实特征和结果；再独立查询真实源与未来日历，运行研究推理。临时结果留在忽略的 runtime 目录，正式工程仅提交脱敏核验文档。

**Tech Stack:** 现有 Python/PyTorch、NumPy/Pandas、Time-Series-Library Transformer、Baostock；正式 Java/Vue 工程只读检查。

**Spec:** `E:/aiStock/需求与设计/首批详细设计_五日预测纵向切片_V0.1.md`；研究事实以当前训练源码与保存权重为准，不将设计草案当作已实现接口。

## Global Constraints

- 未来 **5 个目标交易日**，禁止自然日替代。
- 业务仍十三项、五服务、管理员与普通用户；跨服务只通过接口。
- 不修改现有权重、第三方源码、业务数据库，不发布未经审核的模型。
- 不把加载成功或重复一致性当作准确率证明。
- 不保存凭据、不安装依赖、不调用训练入口、不创建新任务线程。
- 用户已授权本代理全权推进常规可逆步骤，本轮直接执行只读核验；不重新请求批准。新业务增删与关键行为取舍仍按项目约定澄清。

## Review Focus

1. 权重可加载但特征维度/顺序不符：核对配置、保存统计和真实构造，禁止用 Alpha158 标签描述六维模型。
2. 同一张量可推理但与原 CSV 不符：对预设历史窗口复算五点，固定 1e-6 绝对容差。
3. 未复权行情缺因子：检查真实配对来源及定义，禁止把缺失因子替换为 1。
4. 节假日/未来日历不足：查询连续自然日覆盖，再选起点后的五个开市日；完整日历不等于有未来行情。
5. 研究环境通过但正式容器或发布接口缺失：查 Python 依赖、实际路由与 Java 校验，明确未接通边界。

## Task 1：归档数据与检查点复现

**Files:** 读取研究目录 `联合研究/顺序改进验证/结果/S3_one_year_{42,43,44}_rmse6p2.pt`、对应 2026 CSV、三股 canonical CSV 与 NPZ；临时探针 `runtime/sdd005-model-audit/probe.py`；结果 `checkpoint-audit.json`。

**Interfaces:** 使用 `Model(SimpleNamespace(**ck['config']))`、`load_state_dict(...,strict=True)`，调用 `model(x,None,dec,None)`；不调用正式 infer 接口。

- [x] 读取构造/训练/标签实现，核对价格、成交量与因子公式。
- [x] 重载三份权重，核对 `[1,60,6] → [1,5,1]` 及 mean/std。
- [x] 重建三股历史特征/目标，与 NPZ 对齐；每股每权重均匀选五个历史起点，五点逐一对照 CSV。
- [x] 执行并确认退出码 0、45 个窗口/225 点一致；最大差小于 1e-6。

本机命令：

```powershell
& 'E:\aiStock\.venv-model\Scripts\python.exe' 'E:\aiStock\项目代码\stock-system\runtime\sdd005-model-audit\probe.py'
```

## Task 2：真实源与未来交易日历

**Files:** 临时探针 `runtime/sdd005-model-audit/source_probe.py`，完整来源结果 `live-source-audit.json`。

**Interfaces:** `query_trade_dates(start_date='2026-09-30',end_date='2026-11-11')`；`query_history_k_data_plus(...,adjustflag='3'/'1')`；同一来源逐日配对。父进程以 40 秒超时收集子进程唯一结果标记。

- [x] 查询三股原始/后复权行情，验证字段、日期唯一性/顺序/配对与有效比率。
- [x] 验证 43 个日历条目连续，明确五个目标日。
- [x] 执行并确认源状态 SUCCESS；三股均 86 对行情；五个日期为 10-08/09/12/13/14。

本机命令：

```powershell
& 'E:\aiStock\.venv-model\Scripts\python.exe' 'E:\aiStock\项目代码\stock-system\runtime\sdd005-model-audit\source_probe.py'
```

## Task 3：新来源研究推理与正式接入审查

**Files:** 临时探针 `runtime/sdd005-model-audit/live_inference_probe.py`，结果 `live-inference-audit.json`；读取正式 `app/main.py`、`requirements.txt`、`providers.py`、`CandidateValidator.java` 与 `MarketQueryService.java`。

**Interfaces:** 仅消费 Task 2 源响应及 Task 1 制品；五点结果保存为研究记录，不向发布接口发送请求。

- [x] 重建三股最新 60 行特征，检查来源因子与旧归档重叠一致性。
- [x] 三股乘三份权重运行九次，核对五个有限正比率、相同输入重复一致性及截断比例。
- [x] 执行并确认退出码 0、45 点有效、截断比例 0；不评价未来结果准确性。
- [x] 查正式工程：确认因子缺失、未来日历被拒、模型依赖/发布未接通与草案路由差异。
- [x] 保存核验报告/脱敏证据、更新实施状态/待办，自查后小步提交。

本机命令：

```powershell
& 'E:\aiStock\.venv-model\Scripts\python.exe' 'E:\aiStock\项目代码\stock-system\runtime\sdd005-model-audit\live_inference_probe.py'
git diff --check
```

## 自查结论与交接

本计划五项 Review Focus 均有实际检查与证据，没有把研究推理升级为业务功能。正式实施的下一项是“版本化预测输入”，其具体接口/物理表与测试计划应另行按 SDD 形成；本清单不授权省略模型审核或用临时探针充当产品代码。核验报告：`docs/verification-transformer-readiness-20261002.md`。
