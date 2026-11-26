五日 Transformer 离线工作器入口。核对固定样本指纹，使用 2023 训练、2024 验证选轮次和 2025 留出股票评价。输出权重与损失指标，发布仍由模型管理负责。

训练执行接口默认关闭。启用时，算法进程需要持久可写的 TRAINING_EXECUTION_ROOT，及只读的 TRAINING_DATA_ROOT、TRAINING_DATA_MANIFEST。预设配置二选一：TRAINING_PRESET_FILE 指向原单文件，或 TRAINING_PRESET_DIRECTORY 指向多预设目录。目录内的文件以内容 SHA-256 加 .json 命名；训练请求必须同时匹配内容指纹、预设编号与数据指纹。调度进程按原任务编号查询并核对执行令牌及两个指纹。算法子进程继承独占锁，进程退出后才能为中断任务确定终态。无法确认结果时，调度继续保留执行槽。

候选登记需要配置 TRAINING_CANDIDATE_ROOT。训练结果经过已有 ArtifactStore 严格加载，候选目录供算法服务的 MODEL_ARTIFACT_ROOT 读取。隔离配置中两者指向同一个专用目录。调度将候选交给预测服务现有内部登记入口，模型状态为 PENDING，发布仍走管理员审核。

## 同批留出样本融合对照

运行 python -m app.training.fusion_replay，提供只读制品根目录 --artifacts、冻结特征目录 --source、数据清单 --manifest 及其 --dataset-sha256。重复提供二至三个 --member artifact-id:manifest-sha256:weight-bps，权重基点合计 10000。程序严格加载各制品，在同一批留出窗口上计算单模型、加权模型与相对价格恒等于 1 的朴素基线 MAE。输出记录数据与模型指纹，可用于复现实验。权重需在查看留出结果前确定。

## 滚动训练离线制品

python -m app.training.rolling_train 读取冻结特征、清单及有明确训练、验证和留出日期的元数据。元数据提供 datasetHash、seed、train、validation、holdout，各日期区间使用 from 和 through。输出包含 v2 权重、清单与独立评价。训练完成后先通过同一推理加载器检查，再原子写出目录。当前产物用于隔离复现，正式登记仍需接入周期模型的评价契约。

## 周期训练快照就绪检查

python -m app.training.readiness 复用滚动训练输入并额外接收 --expected-session 和可选的 --previous-dataset-hash、--previous-session。目标日期由交易日历提供。默认训练组和留出组各需 80% 的股票在目标日有有效特征，可按数据策略配置。输出 READY、STALE、INSUFFICIENT_COVERAGE 或 ALREADY_PROCESSED，以及各组覆盖、异常股票和有效窗口数。校验失败时命令报错。就绪检查只读，不入队。

## 从 Baostock 归档冻结训练快照

运行 python -m app.training.snapshot，提供 canonical CSV 目录 --source、既有股票分组清单 --cohort 及 --cohort-sha256、归档核验索引 --source-index 及 --source-index-sha256、截止日期 --as-of 和新目录 --output。输出包含 manifest.json、六维量价特征、原始 CSV、股票分组与核验索引副本。归档版本采用原始文件 SHA-256；读取训练样本时会再校验原始行情与特征指纹。用输出的 datasetHash 设置滚动训练参数，并以输出目录作为训练 source。归档指纹与正式行情库的版本 ID 分别记录。

## 合并增量行情

python -m app.training.increment 接收 --base、--base-index、--base-sha256、--delta、--delta-index、--delta-sha256、--calendar、--calendar-sha256、--as-of、--output。增量索引包含 provider=baostock、basis=baostock-backward-close-over-raw-v1、asOf 及 features（每项 stock 和 raw_sha256）。交易日历 JSON 提供已核验并升序排列的 sessions。增量包含截止日和至少三个可比的重叠交易日。重叠原始量价与交易状态必须一致，复权因子允许 1e-9 相对误差。未提供增量的股票原样保留，交给快照就绪检查计算覆盖率。输出索引可作为快照生成命令的 --source-index。

滚动执行预设使用 schemaVersion=2，并增加 trainingData（日期、数据指纹和种子）。预设按配置 SHA-256 定位；TRAINING_ROLLING_SNAPSHOT_ROOT 下以数据 SHA-256 命名目录，内部使用 manifest.json。滚动快照和旧固定快照可同时配置。工作器通过原训练任务编号读写执行回执，训练成功后以同一登记载荷向候选目录导出制品。运行耗时不参与登记指纹。

用于定时扫描时，将原参数 `--training-data` 换为 `--preset`，输入滚动训练器实际使用的 v2 预设。报告额外包含 configurationId 和配置文件原始字节的 SHA-256。只有检查成功且快照、预设均已就位后，发布方才在同一目录用原子替换更新报告。旧参数仍可用于离线数据检查。

## 采集近期训练行情

`python -m app.training.collect_increment --base <旧归档> --index <旧索引> --index-sha256 <指纹> --start YYYY-MM-DD --through YYYY-MM-DD --output <新目录>` 使用一次 Baostock 会话读取日历和现有股票池的不复权、后复权日线。起始日应覆盖旧归档结尾至少三个有有效成交的交易日，结束日期不得晚于上海时间已完成的行情截止日。单次支持 121 个自然日。

输出包含 `index.json`、`calendar.json` 和逐股 CSV，可直接传给 `app.training.increment`。每只纳入的股票须通过日期、量价和复权重叠复核。有缺口的股票列入 `omittedStocks`，合并保留其原档。两种复权口径对不上、重叠发生冲突或上游请求失败时，不发布本批。用输出的日历截止日期和最新开市日分别核对周扫描与训练样本。

## 五日 DLinear 对照

运行 app.training.dlinear_study，使用与已发布 Transformer 相同的六列量价特征、60 日输入和五个交易日相对原点收盘价标签。线性对照采用 DLinear 的趋势与残差分解，六列特征共同进入带岭惩罚的五输出回归。训练归一化只使用 2023 年训练股票，岭惩罚只按 2024 年验证 MAE 选择。价格不变基线的输出固定为 1。DLinear 方法来自 https://github.com/cure-lab/LTSF-Linear。

    $env:PYTHONPATH = (Resolve-Path backend/algorithm).Path
    $source = 'runtime/training/weekly-live-20261114/snapshot'
    python -m app.training.dlinear_study --source $source --manifest "$source/manifest.json" --dataset-sha256 94017bcf98d368f773d77de2d814abc073937b82abf8140261b09c309fe670fa --artifacts runtime/model-artifacts --artifact-id transformer-s3-2023-seed43-9a96167ecb00234d --manifest-hash 6894c7d52ba9a3968ef1f85cd0923b3d249a376281f79d752a0fe460d09f97d6

本轮两个时期使用同一批 10 只留出股票，与原 Transformer 训练股票无重叠。这些年份此前已用于项目回顾性实验。整体 MAE 按价格不变、DLinear 岭回归、Transformer 顺序，2025 年为 1.8275、1.8502、1.8427 个百分点（478 窗口），2026-01-01 至 2026-11-13 为 2.3750、2.3880、2.3800 个百分点（403 窗口）。两个时期的第五日 MAE 均以价格不变基线最低。逐日误差及方向指标保存于 runtime/dlinear-five-day-study-20261120/report.json。

## 沪深 300 特征消融

保持原六列量价输入、60 日窗口、五个目标日、训练验证切分和训练配置，分别训练六特征模型及八特征模型。八特征组新增沪深 300 同日收盘收益率和截至同日的 20 个交易日收益率标准差。两组按完全相同的窗口和标签训练，每组固定随机种子 42、43、44，均最多训练 6 个周期并按验证集选权重。指数缺少精确日期时停止实验，预测原点后信息不参与特征计算。

    $env:PYTHONPATH = (Resolve-Path backend/algorithm).Path
    $source = 'runtime/training/weekly-live-20261114/snapshot'
    python -m app.training.market_feature_study --source $source --manifest "$source/manifest.json" --dataset-sha256 94017bcf98d368f773d77de2d814abc073937b82abf8140261b09c309fe670fa --index runtime/market-ablation-20261120/csi300.json --through 2026-11-13

本轮 1,353 个训练窗口、1,292 个验证窗口、881 个留出窗口。三次重复的平均绝对误差按百分点评价，2025 年六特征、八特征整体 MAE 为 1.8379、1.8659，第 5 日为 2.4028、2.4646。2026 年至 11 月 13 日整体 MAE 为 2.3935、2.4240，第 5 日为 3.1340、3.2180。两期的价格不变基线整体与第 5 日 MAE 均最低。两个新增特征按组合检验，本次不推断两者各自的效果。详细报告及原指数数据位于 runtime/market-ablation-20261120。

## 第五日损失加权对照

在原六特征 Transformer 上比较等权训练与第 5 日权重加倍。权重由 1、1、1、1、2 归一化为均值 1，避免单纯增大损失尺度。两组使用相同的三组随机种子、模型结构、数据及优化配置，均按验证集五日整体 RMSE 选择权重。逐日计算留出集 MAE，并报告前四日整体变化。该参数仅在离线实验显式传入，现有训练调用默认保持等权。

    $env:PYTHONPATH = (Resolve-Path backend/algorithm).Path
    $source = 'runtime/training/weekly-live-20261114/snapshot'
    python -m app.training.horizon_weight_study --source $source --manifest "$source/manifest.json" --dataset-sha256 94017bcf98d368f773d77de2d814abc073937b82abf8140261b09c309fe670fa --through 2026-11-13

详细报告保存在 runtime/horizon-weight-study-20261120/report.json。

真实数据结果按百分点计，2025 年等权与加权整体 MAE 为 1.8379、1.8349，第 5 日为 2.4028、2.4002。2026 年至 11 月 13 日整体为 2.3935、2.3908，第 5 日为 3.1340、3.1302。两组前四日平均误差没有因加权增加。两个时期价格不变基线在整体和第 5 日上的误差仍更低。结果来自项目曾用于历史对照的留出区间，模型参数未部署。
