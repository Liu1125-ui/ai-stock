# 算法与采集组件

作用：内部行情采集、候选检查/迁移验收和受控正式五日推理。采集仅产候选，行情归MS-02、发布归MS-03，算法不写业务库。

实现方法：FastAPI先验证SERVICE_TOKEN并限制请求，阻塞工作在独立线程/有界子进程内完成。正式infer只接受模型/制品/输入编号，经MS-03核验真实PUBLISHED状态，向MS-02读canonical固定FRESH窗口；安全加载原Transformer，输出五点/完整版本/回显。研究validate仍明确validationOnly，正式formalInference不表示未来准确性已证明。

MODEL_ARTIFACT_ROOT只读挂载。PREDICTION_MODEL_AUTHORITY_URL与MARKET_INPUT_AUTHORITY_URL由受信部署配置指定，拒绝重定向，响应64KiB，socket3秒，模型工作总45秒共享单槽。未发布、数据过期或服务异常返回受控失败，不改旧模型/行情。

测试：python -m pytest -q；生成fixture只验证协议/计算，不作为实验效果。真实研究/历史回放保留此前证据。用户任务/数值持久化/预测页面由下一子步完成，原候选未自动发布。代码由Codex生成，无核实CodeArts差异。

SDD006B0新增受保护POST /internal/v1/market/calendar-collect：独立查询日历，日期不依赖股票行情。共享单采集槽及25秒子进程；MS-02负责核验与持久化，尚未接入自动预警。
SDD007A：受保护comparison-collect，独立一年历史协议，RAW与后复权配对，最多370点/367日历，三次SDK查询，无数据库写入。
