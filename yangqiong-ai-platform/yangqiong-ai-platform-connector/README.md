# yangqiong-ai-platform-connector 连接器生态模块

## 一、模块定位

本模块是平台与外部世界的**能力接入层**，将钉钉、企业微信、飞书、外部数据库、文档解析等外部系统能力**成品化**为：

- **Agent语义化工具**：自动装配进Agent工具箱，让Agent具备发消息、查用户、查库等能力
- **入站回调网关**：接收钉钉/企微/飞书推送的消息，验签、幂等、异步驱动Agent执行并回复
- **管理面API**：提供商目录、凭证托管（AES加密）、实例管理、文档解析分块预览

设计原则：框架零侵入（通过`ToolExtraContributor`缝隙装配）、第三方SDK隔离（纯HTTP客户端实现，不引SDK）、实例化模型（Provider→Instance→Tools）、凭证托管（P1租户密钥加密组件）。

与 integration 模块的分工：integration 负责"平台→外部"的通知/告警分发（群机器人webhook），connector 负责"Agent↔外部"的双向能力接入（应用API+入站网关），connector 单向依赖 integration 复用 `IntegrationRecord` 幂等落库与 `WebhookRetryExecutor` 状态常量。

## 二、模块结构

```
com.yangqiongai.ai.platform.connector
├── spi/                  # 提供商SPI契约
│   ├── ConnectorProvider         # 提供商接口（编码/描述/工具/入站网关/回复）
│   ├── ConnectorDescriptor       # 提供商描述（凭证字段/配置字段/工具定义）
│   ├── ConnectorField            # 字段声明（必填/密钥/占位符）
│   ├── ConnectorCredentialView   # 凭证解密后一次性只读视图（按声明校验必填）
│   ├── ConnectorInboundGateway   # 入站网关（验签/ack/标准化消息）
│   ├── ConnectorCallbackRequest  # 入站请求抽象（method/headers/query/body）
│   ├── ConnectorCallbackReceipt  # 入站处理结果（ack载荷+异步消息）
│   ├── ConnectorInboundMessage   # 标准化入站消息（messageId/senderId/text）
│   └── ConnectorToolDefinition   # 工具定义（名称/描述/参数JSON Schema）
├── dingtalk/             # 钉钉提供商（应用API+机器人回调）
├── wecom/                # 企微提供商（自建应用+AES回调）
├── feishu/               # 飞书提供商（自建应用+事件订阅）
├── database/             # 数据库提供商（只读查询+表结构）
├── docparser/            # 文档解析（管理面API，非Agent工具）
├── entity/               # ConnectorCredential / ConnectorInstance
├── mapper/               # 凭证与实例Mapper
├── service/              # 注册中心/凭证/实例/网关/工具装配/生命周期
├── web/                  # 管理面与开放回调Controller
└── config/               # 自动装配与配置属性
```

## 三、启用与配置

模块总开关默认关闭，在业务方 `application.yml` 中开启：

```yaml
ai:
  connector:
    enabled: true          # 总开关（默认false）
    tool-cache-ms: 60000   # 实例工具装配缓存TTL
    gateway:
      async-workers: 4     # 入站消息异步处理线程数
    database:
      max-rows: 200        # 查询默认行数截断
      timeout-seconds: 10  # 执行超时
    docparser:
      ocr-enabled: true    # 扫描件OCR兜底（需存在OcrEngine实现Bean）
      chunk-size: 500      # 分块尺寸（字符）
      chunk-overlap: 50    # 分块重叠（字符）
      max-file-size-mb: 20 # 最大文件大小
```

默认参数见模块内 `src/main/resources/default-config/application.yml`，业务配置可覆盖。

数据库表由 `yangqiong-ai-platform-server` 的 `V23__agent_connector.sql` 迁移脚本创建（凭证表、实例表，均含 `scope_id` 隔离列）。

## 四、管理面API

### 1. 提供商与凭证/实例管理（`ConnectorController`，前缀 `/api/agent/connector`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/providers` | 提供商目录（含声明字段与工具定义） |
| GET | `/credentials` | 凭证列表（密钥脱敏） |
| POST | `/credentials` | 创建凭证（明文字段AES加密入库） |
| DELETE | `/credentials/{id}` | 删除凭证 |
| POST | `/credentials/{id}/test` | 凭证连通性测试 |
| GET | `/instances` | 实例列表 |
| POST | `/instances` | 创建实例（绑定providerCode+credentialId+agentCode） |
| DELETE | `/instances/{id}` | 删除实例（联动清理连接池/缓存） |
| PUT | `/instances/{id}/status` | 启用/停用实例 |

### 2. 入站回调网关（`ConnectorGatewayController`，开放路径免登录）

```
GET/POST  /open/connector/{instanceCode}/callback
```

按实例编码路由到对应提供商网关：验签（钉钉sign/企微msg_signature/飞书encrypt）→ 幂等（`IntegrationRecord`唯一键）→ 异步执行Agent → 回复消息。未知实例返回安全空ack不泄露信息。

各平台回调配置地址即此URL（替换`{instanceCode}`为实例编码）。

### 3. 文档解析（`DocParserController`）

```
POST  /api/agent/connector/docparser/parse    # multipart上传file
```

返回标准分块结构（`DocParseResult`：chunks[index/type/text/metadata]），type为 text/table/sheet，metadata携带page/sheetName/rows。调用方据此走既有 KbDocument 摄取链路，本API不替代摄取管线。

- PDF：文本层优先（按位置排序），扫描页OCR兜底（SPI，见下）
- Word(docx)：段落+表格线性化（`单元格1 | 单元格2`）
- Excel(xlsx/xls)：按sheet拆分，首个非空行为表头，每50行一块并携带表头
- 纯文本：txt/md/csv/json
- 统一页眉页脚清洗（≥60%页数重复判定）、分块尺寸/重叠可配

## 五、Agent工具集成

`ConnectorToolkitContributor` 实现 `ToolExtraContributor` SPI，在Agent工具装配时：

1. 按实例绑定的 `agent_codes` 过滤启用实例
2. 调用 `Provider.createTools(instance, credentialView)` 产出语义化工具（带60秒本地缓存）
3. 凭证校验失败/实例停用时跳过该实例不影响其他实例

内置工具清单：

| 提供商 | 工具 |
|---|---|
| dingtalk | send_work_notice、send_group_message、get_user_by_mobile、create_todo |
| wecom | send_work_message、send_group_message、get_user_by_mobile |
| feishu | send_message、get_user_by_mobile、create_task |
| database | query_database（只读）、describe_tables |

数据库提供商工具由 `SqlSafetyValidator` 强制安全：只读语句白名单（SELECT/SHOW/DESC/EXPLAIN/WITH）、拒绝DML/DDL/多语句/`INTO OUTFILE`（注释剥离+字面量遮蔽后检测）、表白名单校验；连接池为独立HikariCP小池，实例删除联动关闭。

## 六、OCR扩展

`docparser.OcrEngine` 为SPI接口，平台默认无实现（PDF扫描页跳过并计数`ocrPageCount`）。接入Tess4J等引擎：

```java
@Component
public class TessOcrEngine implements OcrEngine {
    @Override
    public String recognize(byte[] imageBytes) {
        // 实现PNG图片文字识别
    }
}
```

注册Bean且 `ai.connector.docparser.ocr-enabled=true` 时自动生效。

## 七、扩展新提供商

1. 新建包实现 `ConnectorProvider`（`@Component`注册）：
   - `providerCode()`：唯一编码
   - `descriptor()`：声明凭证字段（`.secret()`标记密钥）、配置字段、工具定义
   - `createTools(instance, credential)`：按凭证+实例配置产出 `AgentTool` 清单（阻塞HTTP调用统一调度至弹性线程）
   - 需要入站消息时实现 `createGateway()` 与 `reply()`
2. HTTP客户端封装在提供商包内，token缓存用 `ConcurrentHashMap` 并预留过期余量
3. 回调涉及XML时必须禁用外部实体（参考 `WeComGateway` 的 `disallow-doctype-decl`）
4. 补充单测：token缓存、验签失败、ack语义、幂等、工具参数边界

## 八、测试

```bash
mvn clean test -pl yangqiong-ai-platform-connector -o
```

177个单元测试覆盖：凭证校验/脱敏、token缓存、验签失败路径、SQL注入绕过（注释/字面量/OUTFILE）、幂等竞态、ack语义、分块边界、OCR兜底开关等。

> 注意：本模块部分历史修复曾出现"编辑未落盘导致假绿"问题，验证一律使用 `mvn clean test`。
