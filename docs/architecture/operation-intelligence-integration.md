# Operation Intelligence 对接说明

## 1. 目标与边界

`operation-intelligence` 是 Ops Factory 中独立的 QoS 健康曲线数据采集、计算与查询服务，负责：

- 定时从 DV 系统采集性能指标与告警数据
- 对原始数据进行归一化处理
- 按分段策略计算各维度评分
- 计算加权综合健康评分
- 提供健康指标、明细数据与贡献度分析查询 API

适用范围：

- 前端需要展示 QoS 健康曲线与指标明细
- 其他后端服务需要获取运维健康评分数据
- 管理台需要查看贡献度分析与配置规则

非边界：

- 不负责用户鉴权、多租户鉴权策略。本服务当前接口本身未定义鉴权头，接入方需要在上层网关统一收口。
- 不负责 DV 系统的数据生产与管理。
- 不负责前端路由与页面渲染逻辑。

## 2. 运行与配置加载

### 2.1 配置来源

服务启动时会加载：

- `operation-intelligence/src/main/resources/application.yaml`
- 运行目录下的 `./config.yaml`（`spring.config.import: optional:file:./config.yaml`）

环境变量 `OI_CONFIG_PATH` 可用于显式指定配置文件路径，优先级高于默认的 `./config.yaml`。

### 2.2 本地启动

在服务目录下执行：

```bash
cd operation-intelligence
mvn spring-boot:run
```

打包运行：

```bash
cd operation-intelligence
mvn package
java -jar target/operation-intelligence.jar
```

### 2.3 运行目录

服务默认运行目录为 `./data`，通常包含：

- `raw/<environment>/`：原始采集数据 JSON 文件
- `normalize/<environment>/`：归一化数据 JSON 文件
- `detail/<environment>/`：评分明细数据 JSON 文件

建议：

- 生产环境使用独立磁盘路径，不要和代码目录混放
- 把数据目录指到可持久化目录
- 上层部署时提前评估磁盘容量，长期运行会累积 JSON 文件

## 3. 配置项说明

示例文件见 `operation-intelligence/config.yaml.example`。

### 3.1 运行时配置

```yaml
operation-intelligence:
  cors-origin: "*"
  qos:
    enabled: false
    collection-interval-ms: 300000
    rotation-interval-ms: 3600000
    raw-data-retention-days: 7
    detail-data-retention-days: 30
    normalize-data-retention-days: 90
    weights:
      availability: 0.4
      performance: 0.4
      resource: 0.2
    dv-environments: []
```

#### 服务端口

- 含义：服务监听端口，使用标准 Spring Boot `server.port` 配置
- 默认值：`8096`

### 3.2 QoS 配置

#### `operation-intelligence.qos.enabled`

- 含义：是否启用 QoS 数据采集与计算
- 默认值：`true`
- 说明：设为 `false` 时服务仅提供查询 API，不执行定时采集任务

#### `operation-intelligence.qos.collection-interval-ms`

- 含义：数据采集调度间隔，单位毫秒
- 默认值：`300000`（5 分钟）
- 说明：调度器按此间隔定时执行采集任务

#### `operation-intelligence.qos.rotation-interval-ms`

- 含义：数据轮转间隔，单位毫秒
- 默认值：`3600000`（1 小时）

#### `operation-intelligence.qos.raw-data-retention-days`

- 含义：原始数据保留天数
- 默认值：`7`

#### `operation-intelligence.qos.detail-data-retention-days`

- 含义：明细数据保留天数
- 默认值：`30`

#### `operation-intelligence.qos.normalize-data-retention-days`

- 含义：归一化数据保留天数
- 默认值：`90`

### 3.3 评分权重配置

```yaml
operation-intelligence:
  qos:
    weights:
      availability: 0.4
      performance: 0.4
      resource: 0.2
```

#### `operation-intelligence.qos.weights.availability`

- 含义：可用性维度权重
- 默认值：`0.4`
- 约束：三个权重之和必须为 `1.0`

#### `operation-intelligence.qos.weights.performance`

- 含义：性能维度权重
- 默认值：`0.4`

#### `operation-intelligence.qos.weights.resource`

- 含义：资源/告警维度权重
- 默认值：`0.2`

### 3.4 评分阈值配置

当前评分阈值通过 `PerformanceIndicatorScope` 配置文件和 `ProductConfigRule` 管理，存储在 `operation-intelligence/data/qos/config/` 目录下的 JSON 文件中，不通过 `config.yaml` 配置。

### 3.5 DV 环境配置

```yaml
operation-intelligence:
  qos:
    dv-environments:
      - env-code: "DigitalCRM.sit"
        env-name: "DigitalCRM SIT"
        agent-solution-type: "DigitalCRM"
        product-type-name: "DigitalCRM"
        server-url: "https://10.44.212.216:26335"
        utm-user: "admin"
        utm-password: "changeit"
        crt-content: ""
        crt-file-name: "client.jks"
        keystore-password: ""
        strict-ssl: false
```

#### `operation-intelligence.qos.dv-environments[].env-code`

- 含义：环境编码，用于查询接口中指定目标环境
- 说明：全局唯一

#### `operation-intelligence.qos.dv-environments[].env-name`

- 含义：环境显示名称
- 说明：用于前端展示

#### `operation-intelligence.qos.dv-environments[].agent-solution-type`

- 含义：Agent 解决方案类型

#### `operation-intelligence.qos.dv-environments[].product-type-name`

- 含义：产品类型名称

#### `operation-intelligence.qos.dv-environments[].server-url`

- 含义：DV 系统 API 地址
- 说明：必须包含协议前缀 `https://` 或 `http://`

#### `operation-intelligence.qos.dv-environments[].utm-user`

- 含义：UTM 认证用户名

#### `operation-intelligence.qos.dv-environments[].utm-password`

- 含义：UTM 认证密码

#### `operation-intelligence.qos.dv-environments[].crt-content`

- 含义：Base64 编码的客户端证书内容

#### `operation-intelligence.qos.dv-environments[].crt-file-name`

- 含义：客户端证书文件名

#### `operation-intelligence.qos.dv-environments[].keystore-password`

- 含义：keystore 密码
- 默认值：空

#### `operation-intelligence.qos.dv-environments[].strict-ssl`

- 含义：是否严格验证 SSL 证书
- 默认值：`true`
- 说明：生产环境应保持 `true`，仅在测试环境可设为 `false`

## 4. API 规范

### 4.1 基础约定

- Base Path：`/operation-intelligence/qos`
- 数据格式：`application/json`
- 通用请求结构：

```json
{
  "envCode": "production",
  "startTime": 1746057600000,
  "endTime": 1746662400000
}
```

### 4.2 错误返回

当前统一错误格式：

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "No data found for environment: staging"
}
```

已实现错误码：

- `RESOURCE_NOT_FOUND`
  - HTTP `404`
  - 典型场景：指定环境不存在、时间范围内无数据
- `REQUEST_FAILED`
  - HTTP `400`
  - 典型场景：参数非法、时间范围格式错误
- `DV_CONNECTION_ERROR`
  - HTTP `502`
  - 典型场景：DV 系统连接失败、认证失败、超时

### 4.3 接口明细

#### 4.3.1 QoS 健康指标接口

#### `POST /operation-intelligence/qos/getHealthIndicator`

用途：获取综合健康评分与各维度评分概要。

请求体：

```json
{
  "envCode": "production",
  "startTime": 1746057600000,
  "endTime": 1746662400000
}
```

返回示例：

```json
{
  "healthScore": 85.6,
  "availability": { "score": 90, "weight": 0.4 },
  "performance": { "score": 82, "weight": 0.4 },
  "resource": { "score": 84, "weight": 0.2 },
  "timestamp": 1746662400000
}
```

#### `POST /operation-intelligence/qos/getAvailableIndicatorDetail`

用途：获取可用性维度明细数据。

请求体：

```json
{
  "envCode": "production",
  "startTime": 1746057600000,
  "endTime": 1746662400000
}
```

返回示例：

```json
{
  "availabilityRate": 99.95,
  "faultCount": 2,
  "mttr": 15.3,
  "score": 90,
  "details": []
}
```

#### `POST /operation-intelligence/qos/getPerformanceIndicatorDetail`

用途：获取性能维度明细数据。

请求体：

```json
{
  "envCode": "production",
  "startTime": 1746057600000,
  "endTime": 1746662400000
}
```

返回示例：

```json
{
  "p50": 120,
  "p90": 350,
  "p95": 800,
  "p99": 2500,
  "throughput": 1500,
  "score": 82,
  "details": []
}
```

#### `POST /operation-intelligence/qos/getResourceIndicatorDetail`

用途：获取资源维度明细数据。

请求体：

```json
{
  "envCode": "production",
  "startTime": 1746057600000,
  "endTime": 1746662400000
}
```

返回示例：

```json
{
  "cpuUsage": 65.2,
  "memoryUsage": 72.8,
  "diskUsage": 58.1,
  "score": 84,
  "details": []
}
```

#### `POST /operation-intelligence/qos/getAlarmIndicatorDetail`

用途：获取告警维度明细数据。

请求体：

```json
{
  "envCode": "production",
  "startTime": 1746057600000,
  "endTime": 1746662400000
}
```

返回示例：

```json
{
  "alarmCount": 12,
  "criticalCount": 1,
  "warningCount": 11,
  "score": 84,
  "distribution": []
}
```

#### `POST /operation-intelligence/qos/getContributionData`

用途：获取各子指标对总评分的贡献度分析。

请求体：

```json
{
  "envCode": "production",
  "startTime": 1746057600000,
  "endTime": 1746662400000
}
```

返回示例：

```json
{
  "contributions": [
    { "name": "availabilityRate", "weight": 0.4, "value": 90, "contribution": 36.0 },
    { "name": "responseTime", "weight": 0.4, "value": 82, "contribution": 32.8 },
    { "name": "alarmDensity", "weight": 0.2, "value": 84, "contribution": 16.8 }
  ],
  "totalScore": 85.6
}
```

#### `POST /operation-intelligence/qos/getProductConfigRule`

用途：获取当前生效的产品配置规则。

请求体：

```json
{
  "envCode": "production"
}
```

返回示例：

```json
{
  "weights": { "availability": 0.4, "performance": 0.4, "resource": 0.2 }
}
```

#### `GET /operation-intelligence/qos/getEnvironments`

用途：获取已配置的 DV 环境列表。

返回示例：

```json
{
  "environments": [
    { "name": "production", "displayName": "生产环境" },
    { "name": "staging", "displayName": "预发环境" }
  ]
}
```

#### 4.3.2 调用链接口

##### `POST /operation-intelligence/call-chain/query`

用途：查询调用链树结构。

请求体：

```json
{
  "solutionType": "DigitalCRM.sit",
  "condition": [
    { "conditionKey": "menuId", "conditionValue": "604015020" }
  ],
  "startTime": 1746057600000,
  "endTime": 1746662400000
}
```

参数说明：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| solutionType | string | 是 | DV 环境标识（envCode） |
| condition | array | 是 | 条件列表，每个条件包含 conditionKey 和 conditionValue |
| startTime | long | 是 | 开始时间（毫秒时间戳） |
| endTime | long | 是 | 结束时间（毫秒时间戳） |

返回示例：

```json
{
  "chainType": "BES",
  "conditions": [
    { "conditionKey": "menuId", "conditionValue": "604015020" }
  ],
  "totalCount": 100,
  "queryTimeRange": {
    "startTime": "2025-01-01 00:00:00",
    "endTime": "2025-01-02 00:00:00"
  },
  "flows": [
    {
      "flowId": "flow_abc12345",
      "callCount": 80,
      "callRatio": 80.0,
      "nodes": [
        {
          "seqNo": "1",
          "url": "/api/v1/test",
          "serviceName": null,
          "operationName": null,
          "topic": null,
          "eventName": null,
          "busiCode": null,
          "jobDefinedId": null,
          "cost": 100,
          "logMessage": "ER",
          "ipStats": [
            { "ip": "10.0.0.1", "cluster": "ClusterA", "count": 40, "ratio": 50.0 },
            { "ip": "10.0.0.2", "cluster": "ClusterA", "count": 40, "ratio": 50.0 }
          ],
          "clusterStats": [
            { "cluster": "ClusterA", "count": 80, "ratio": 100.0 }
          ],
          "costStats": {
            "p50": 95, "p90": 110, "p95": 120, "p99": 150,
            "avg": 100, "min": 50, "max": 200
          }
        }
      ],
      "ipStats": [],
      "clusterStats": []
    }
  ]
}
```

##### `POST /operation-intelligence/call-chain/config/menu-mapping`

用途：导入 menuId 映射配置。

请求体（multipart/form-data）：

```
Content-Type: multipart/form-data

file: <上传的文本文件>
```

文件格式（pipe 分隔）：

```
menuId|url
604015020|/api/v1/test
604015021|/api/v1/test2
```

返回示例：

```json
{
  "message": "MenuId mappings imported successfully",
  "count": 2
}
```

##### `POST /operation-intelligence/call-chain/config/chain-type`

用途：导入链类型配置。

请求体（multipart/form-data）：

```
Content-Type: multipart/form-data

file: <上传的文本文件>
```

文件格式（pipe 分隔）：

```
chainType|description|conditionKey|extractFields|classifyField|subClassifyField
BES|Business Execution System|menuId|url|null|null
API|External Interface|serviceName|url,serviceName|null|null
BPM|Business Process Management|AppendInfo|busiCode,processName|processName|null
JOB|Scheduled Job|jobDefinedId|jobDefinedId|null|null
```

返回示例：

```json
{
  "message": "Chain type configs imported successfully",
  "count": 4
}
```

##### `GET /operation-intelligence/call-chain/environments`

用途：获取已配置的 DV 环境列表（用于调用链查询）。

返回示例：

```json
{
  "environments": [
    { "name": "production", "displayName": "生产环境" },
    { "name": "staging", "displayName": "预发环境" }
  ]
}
```

## 5. 集成建议

### 5.1 如果你是前端管理台

推荐接入：

1. 页面初始化先调 `getEnvironments` 获取环境列表
2. 调 `getProductConfigRule` 获取当前评分规则
3. 健康曲线主页调 `getHealthIndicator` 展示综合评分
4. 各维度详情页按需调对应的 `getXXXIndicatorDetail`
5. 贡献度分析调 `getContributionData`
6. 调用链页面调 `/call-chain/query` 展示调用链树结构
7. 配置页面提供 menuId 映射和链类型配置导入功能

调用链查询推荐流程：

1. 用户选择 DV 环境和查询条件（如 menuId）
2. 选择时间范围（最长 30 分钟）
3. 调用 `/call-chain/query` 接口
4. 按流程展示调用链树，每个流程可展开查看节点详情
5. 点击节点显示该节点的统计信息（IP 分布、集群分布、耗时分布）

### 5.2 如果你是网关

建议：

- 由网关统一暴露外部稳定接口和鉴权
- 网关可按业务侧需要再封装环境隔离、调用审计、限流和租户维度控制
- 当前版本前端直接访问 `operation-intelligence`（独立服务部署模式），不经过网关。如后续需要统一鉴权或限流，可在网关层增加代理路由

### 5.3 如果你是其他后端服务

推荐接入：

1. 需要获取运维健康数据时，直接调用查询接口
2. 不要依赖 JSON 文件存储结构，只通过 API 获取数据
3. 大批量查询时注意时间范围，避免单次请求过重

## 6. cURL 示例

### 6.1 获取环境列表

```bash
curl http://127.0.0.1:8096/operation-intelligence/qos/getEnvironments
```

### 6.2 获取综合健康评分

```bash
curl -X POST http://127.0.0.1:8096/operation-intelligence/qos/getHealthIndicator \
  -H 'Content-Type: application/json' \
  -d '{
    "envCode": "production",
    "startTime": 1746057600000,
    "endTime": 1746662400000
  }'
```

### 6.3 获取可用性明细

```bash
curl -X POST http://127.0.0.1:8096/operation-intelligence/qos/getAvailableIndicatorDetail \
  -H 'Content-Type: application/json' \
  -d '{
    "envCode": "production",
    "startTime": 1746057600000,
    "endTime": 1746662400000
  }'
```

### 6.4 获取性能明细

```bash
curl -X POST http://127.0.0.1:8096/operation-intelligence/qos/getPerformanceIndicatorDetail \
  -H 'Content-Type: application/json' \
  -d '{
    "envCode": "production",
    "startTime": 1746057600000,
    "endTime": 1746662400000
  }'
```

### 6.5 获取资源明细

```bash
curl -X POST http://127.0.0.1:8096/operation-intelligence/qos/getResourceIndicatorDetail \
  -H 'Content-Type: application/json' \
  -d '{
    "envCode": "production",
    "startTime": 1746057600000,
    "endTime": 1746662400000
  }'
```

### 6.6 获取告警明细

```bash
curl -X POST http://127.0.0.1:8096/operation-intelligence/qos/getAlarmIndicatorDetail \
  -H 'Content-Type: application/json' \
  -d '{
    "envCode": "production",
    "startTime": 1746057600000,
    "endTime": 1746662400000
  }'
```

### 6.7 获取贡献度分析

```bash
curl -X POST http://127.0.0.1:8096/operation-intelligence/qos/getContributionData \
  -H 'Content-Type: application/json' \
  -d '{
    "envCode": "production",
    "startTime": 1746057600000,
    "endTime": 1746662400000
  }'
```

### 6.8 获取配置规则

```bash
curl -X POST http://127.0.0.1:8096/operation-intelligence/qos/getProductConfigRule \
  -H 'Content-Type: application/json' \
  -d '{
    "envCode": "production"
  }'
```

### 6.9 调用链查询

```bash
curl -X POST http://127.0.0.1:8096/operation-intelligence/call-chain/query \
  -H 'Content-Type: application/json' \
  -d '{
    "solutionType": "DigitalCRM.sit",
    "condition": [
      { "conditionKey": "menuId", "conditionValue": "604015020" }
    ],
    "startTime": 1746057600000,
    "endTime": 1746662400000
  }'
```

### 6.10 导入 menuId 映射

```bash
curl -X POST http://127.0.0.1:8096/operation-intelligence/call-chain/config/menu-mapping \
  -F 'file=@menu_mapping.txt'
```

### 6.11 导入链类型配置

```bash
curl -X POST http://127.0.0.1:8096/operation-intelligence/call-chain/config/chain-type \
  -F 'file=@chain_type_config.txt'
```

### 6.12 获取调用链环境列表

```bash
curl http://127.0.0.1:8096/operation-intelligence/call-chain/environments
```

## 7. 当前实现限制

接入方需要明确以下现状：

- 当前仅支持 JSON 文件存储，不支持数据库持久化
- 采集任务依赖定时调度，不支持实时推送
- DV 系统集成当前仅支持 UTM 用户名/密码认证方式
- 评分算法的权重与阈值通过配置文件管理，不支持运行时动态调整
- 前端当前直接访问 `operation-intelligence`，与独立服务部署模式一致
- 调用链查询依赖 DV TraceLog API，单次查询最多返回 10000 条记录
- 调用链低频流程过滤阈值为 3%，当前不可配置
- 调用链树结构仅支持 4 种预定义链类型（BES/API/BPM/JOB）
- 调用链查询时间范围最长支持 30 分钟

如果其他服务要基于这些能力做稳定集成，建议只依赖当前已经被测试覆盖的闭环：

**QoS 相关**：
- `getHealthIndicator` 综合健康评分查询
- `getAvailableIndicatorDetail` / `getPerformanceIndicatorDetail` / `getResourceIndicatorDetail` / `getAlarmIndicatorDetail` 各维度明细查询
- `getContributionData` 贡献度分析
- `getEnvironments` 环境列表查询
- `getProductConfigRule` 配置规则查询

**调用链相关**：
- `/call-chain/query` 调用链查询
- `/call-chain/environments` 环境列表查询
- `/call-chain/config/menu-mapping` menuId 映射配置导入
- `/call-chain/config/chain-type` 链类型配置导入
