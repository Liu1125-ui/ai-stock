# SDD007A 独立审查与修复（2026-10-12）

独立评审范围76fa52e..ba6d6c4；评审者只读，未执行Maven/真实外部调用。无Critical，2 Important；执行者根据实际影响将另外2 Minor重评为Important，一次修复。

1. 子进程capture_output先缓冲再限长：改用Linux容器selectors流式读取，stdout+stderr累计2MiB，超限/25秒超时立即kill并wait回收。真实本地无限stdout/无限stderr/静默超时子进程回归，不访问供应商。
2. RAW/BACKWARD状态不一致被误标VERIFIED：比较采集的读边界逐日核对tradestatus，不改既有预测契约。fakeSDK改复权状态0、RAW1时先复现AVAILABLE错误，再修复为SOURCE_INVALID。
3. RAW-window金融数值JSON数字：重评Important，防PowerShell基线经浮点损失。新增RawPoint字符串DTO，先复现isTextual=false，再改为字符串。
4. 同KeyPrefix并发重复采集：重评Important，避免额外供应商调用。文件独占锁覆盖整个批次、JSON原子保存、每股一次attempt reservation；失败/不确定无自动重试，已保存候选仍可恢复发布。测试重复锁和重复attempt均拒绝。

真实三股覆盖、部署保全不在静态审查结论中，由验收记录单列。没有把代码测试当真实数据或用户页面验收。当前尚未实现股票对比页面与AI解释。
