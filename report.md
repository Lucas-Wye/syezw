# 安全与逻辑检查报告（App + Backend）

时间：2026-05-28

范围说明：本次基于代码静态审查（App/Backend）输出可疑逻辑与安全风险点，并给出改进建议。未执行渗透或运行时验证。

## 关键风险（High）

1. **API Key 为空时，后端关闭认证**
   - 位置：`backend/src/lib.rs` 中 `check_api_key`，当 `API_KEY` 为空直接 `return None`，导致所有接口免认证。
   - 风险：误配置后直接暴露所有同步与图片接口。
   - 建议：启动时强制校验 API_KEY 非空；或在未配置时拒绝启动。

2. **数据库默认凭据为 postgres/postgres**
   - 位置：`backend/src/db.rs` 默认 `PG_USER=postgres`，`PG_PASSWORD=postgres`。
   - 风险：生产环境若未配置环境变量，数据库易被猜解。
   - 建议：启动时要求显式配置或至少在非本地环境拒绝默认值。

3. **App 支持明文 HTTP，API Key 明文传输**
   - 位置：`app/src/main/java/org/syezw/model/SettingsViewModel.kt` 中所有请求均基于用户填写的 `apiBaseUrl`；未限制 `https://`。
   - 风险：若用户配置 `http://`，API Key 与密文 payload 可被中间人获取/篡改。
   - 建议：默认要求 HTTPS；若允许 HTTP，需显式二次确认并在 UI 明示风险。

## 中等风险（Medium）

4. **图片下载保存到公开目录（隐私泄露风险）**
   - 位置：`SettingsViewModel.kt` 的 `syncImageDownloads` 写入 `Downloads/syezw_diary_images`。
   - 风险：日记图片落地到公开下载目录，其他应用可读（受系统权限影响）。
   - 建议：改存应用私有目录，或提供可配置选项并提示风险。

5. **后端接口缺少限流与上传大小限制**
   - 位置：`backend/src/lib.rs` 的 `sync_upload`、`image_upload`、`image_refs_upsert`。
   - 风险：可被大请求/高频请求压垮（DoS）。
   - 建议：限制单次请求大小、批量条数、并添加 IP/Key 级别限流。

6. **身份模型过于简化**
   - 位置：后端仅校验 API Key；payload 中 `author` 可由客户端任意传入。
   - 风险：多用户/多设备场景易被伪造作者字段导致数据混淆。
   - 建议：服务端绑定 API Key 与 author，或引入更强的鉴权策略。

## App 逻辑问题（补充）

2. **系统定位关闭后，服务只停止不自动恢复**
   - 位置：`LocationService.startGpsStatusCheck()`。
   - 现象：检测到系统定位关闭会 `stopSelf()`，但没有监听系统定位恢复事件后自动重启。
   - 影响：用户重新打开定位后不会自动恢复记录，需要手动重启 GPS。
   - 建议：订阅定位开关广播或在 UI 侧提示并提供“一键恢复”。

3. **下载同步不包含 GPS 数据**
   - 位置：`SettingsViewModel.syncDownload()` 仅处理 diary/todo/period 与图片。
   - 影响：多设备场景下无法恢复 GPS 轨迹，数据不完整。
   - 建议：若需要跨设备完整同步，应补充 GPS 下载与落库逻辑。

## 低风险/可改进（Low）

7. **下载解密失败仅提示用户，不进行隔离或重试策略**
   - 位置：`SettingsViewModel.kt` 中 `applyDownloadedData` 与 `decryptOk`。
   - 风险：用户容易忽略部分失败导致数据不一致。
   - 建议：记录失败条目并提供重试或失败清单提示。

8. **错误信息直接回显到客户端**
   - 位置：`backend/src/lib.rs` 多处 `message: format!("... {}", e)`。
   - 风险：可能暴露内部错误细节（表结构、SQL 细节等）。
   - 建议：日志保留详细错误，对外返回通用错误码与信息。

## 已观察到的正向做法

1. **API Key 常量时间比较**
   - 位置：`backend/src/lib.rs` `constant_time_eq`。
   - 价值：减少时间侧信道攻击风险。

2. **图片上传/引用分离 + 去重（hash）**
   - 位置：`image_upload` / `image_refs_upsert` 逻辑。
   - 价值：节省带宽，降低重复上传风险。

## 建议优先级

1. **强制 API_KEY 非空**（High）
2. **生产环境拒绝默认数据库密码**（High）
3. **App 强制 HTTPS 或显式风险提示**（High）
4. **后端限流与请求大小限制**（Medium）
5. **图片落地目录改为私有目录**（Medium）

--- 

如需，我可以按优先级逐项修复并补充测试。
