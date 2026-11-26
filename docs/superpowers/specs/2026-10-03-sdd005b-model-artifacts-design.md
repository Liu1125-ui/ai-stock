# SDD-005B：兼容Transformer制品与容器推理

日期2026-10-03。依据已有模型核验、SDD-005A固定输入与五日详细设计。用户授权本代理按顺序独立负责常规可逆开发，不重复逐项确认；本批按依赖先完成模型运行单元，正式管理员审核发布、MS-03预测任务/结果与页面在后续切片接入，不把候选检查当作用户预测。

## 目标与方案选择

采用三份既有研究权重的不可变候选制品、最小同构Transformer实现、容器内部验收接口。保留所有种子，不根据新样本挑默认/推荐。复用整个研究目录会引入无关训练依赖与本机路径；重新设计网络会破坏权重兼容。选择保留上游所需类的原始类体、仅调整包内导入和裁剪无关类，保留MIT许可证、提交/来源/提取哈希；原研究仓库不修改，strict重载与历史225点一致性作为门槛。

## 制品契约

每个artifactId为transformer-s3-2023-seed42/43/44；固定目录含manifest.json与checkpoint.pt，权重和运行证据在忽略runtime，摘要进入Git。清单schemaVersion=1、status=CANDIDATE、来源提交、checkpoint哈希/大小、完整模型config、六列mean/std、训练截止/epoch、证券列表三股、window60/label30/horizon5、featureDefinition=baostock-six-price-volume-v1、sourceDefinition=baostock-backward-close-over-raw-v1、targetDefinition=adjusted-close-relative-origin-v1、clip10、实现文件哈希。

使用单独校验manifest文件字节SHA256作为manifestHash。清单未知字段/数值/长度/模式拒绝。只允许固定架构、字段顺序、允许证券和三个受控ID；文件路径不可由请求传入。安全torch.load(weights_only=True,map_location='cpu')，strict=True，配置/mean/std/cutoff/epoch与清单一致且有限合法；checkpoint与运行文件哈希不符拒绝，权重全有限。包目录只读挂载，未加载制品不影响行情采集与health。

## 输入与输出

输入为SDD-005A的window：源/定义/RAW匹配，60行升序原始量价与正因子、originDate/close等于末行，五个目标日递增晚于起点；所有ID/哈希符合格式，证券受支持。字段小数长度与指数受限，禁止缺失值、非有限值、零/负价量额与异常VWAP，不补0。预处理：OHLC*f、amount/volume*f、volume/f，float32六列按已存mean/std标准化，clip[-10,10]；decoder末30行加5行零，无时间marker，与训练一致。

候选输出固定五个有限正比率，十进制字符串24,12 HALF_UP；参考价起点RAW*ratio，保存20,6口径并另给两位显示值，明确为预测起点股权口径参考价。输出回显artifactId/manifestHash/checkpointHash/inputVersionId/inputHash/rawVersionId/calendarHash/五日期，另有tensorInputHash（已转换/标准化张量及目标日摘要），clippedFraction、耗时与validationOnly=true。inputHash由MS-02提供，不谎称算法独立重算其包含后复权close的存储哈希；MS-03后续须通过受信接口读取和复核回显。

## 内部HTTP与失败

新增POST /internal/v1/model-artifacts/validate：仅服务身份，先鉴权后限制1MiB并解析；请求只有artifactId、manifestHash、window。window新鲜度FRESH/STALE/UNKNOWN原样回显，验收允许固定历史回放但不是新用户预测资格；正式业务未来必须FRESH且已审核发布。

单推理槽，在线程池调用45秒工作子进程，超时/忙/坏清单/哈希不符/不兼容输入/无效输出均受控返回状态，无成功points。健康仍inference=UNAVAILABLE（未发布），新增candidateValidation能力摘要；/internal/v1/infer仍503/PUBLISHED_MODEL_NOT_CONFIGURED。不暴露本机路径/凭据/堆栈、不允许动态代码加载或客户端上传权重。

## 部署与验收

容器保持现有Python3.13与HTTP采集依赖，官方CPU轮子torch2.14.0+cpu、numpy2.5.3与研究版本一致；按安装结果验证，禁止未安装便宣称可运行。read-only模型挂载、服务用户10001、固定线程2。旧采集/权限/health契约保留。

三制品哈希匹配；容器安全strict加载；三股各五历史起点、三权重，共45窗口225点与已归档CSV最大差<=1e-6；固定来源3*3当前窗口45点有限正值且重复一致、日期和版本对应。另人工价格/量变换与参考价样例、坏哈希/配置/因子/极端数字/形状/权限/超大/繁忙/超时/慢请求健康回归。未评价未来误差、不宣称长期源可用或全公司行为语义通过，不自动发布模型。

## 实施补充
真实制品ID使用种子前缀加checkpoint与运行实现联合哈希的16位后缀。实现变更产生新包ID，保留旧包、不覆盖；本机catalog仅作当前候选目录，不代表发布推荐。
