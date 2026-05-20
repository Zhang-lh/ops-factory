# Operation Intelligence 技术架构文档

## 1. 文档目标

本文档说明 `operation-intelligence` 在 Ops Factory 中的实际技术架构，覆盖：

- QoS 健康曲线数据采集、计算、评分、存储与查询的完整路径
- 调用链挖掘与统计分析的数据处理流程
- 后端服务分层、数据模型与存储策略
- 健康评分算法与各维度的分段评分策略
- 调用链树构建与节点统计计算
- 对外接口列表与推荐使用方式
- 当前实现限制

本文以仓库当前实现为准。凡是"接口已经暴露但运行时尚未完全消费"的能力，会单独标注。

## 2. 定位与边界

`operation-intelligence` 是独立的 QoS 健康曲线与调用链挖掘服务，负责：

- 从外部 DV（Data Vault）系统定时采集性能指标与告警数据
- 对原始指标进行归一化、分段评分与加权健康评分计算
- 从 DV 系统 TraceLog API 查询调用链原始数据
- 构建调用链树结构并计算各节点统计信息
- 将原始数据与评分结果持久化到 JSON 文件存储
- 提供健康指标、明细数据、贡献度分析等查询 API
- 提供调用链查询、配置导入等 API

不负责的内容：

- 用户鉴权、租户隔离与访问控制策略
- Agent 管理与运行时生命周期
- DV 系统本身的数据生产与管理
- 前端路由与页面渲染逻辑

## 3. 总体架构

### 3.0 架构总览图

```text
+------------------------+          +---------------------------+
| web-app                |          | DV 系统                    |
| - 健康曲线页面          |          | - 性能指标                  |
| - 指标详情页面          |          | - 告警数据                  |
| - 贡献度分析页面        |          | - TraceLog API              |
| - 调用链挖掘页面        |          +-------------+-------------+
+-----------+------------+                        |
            | HTTP                                     HTTPS
            v                                          v
                +-----------------------------------+
                | operation-intelligence             |
                |-----------------------------------|
                | Controller                        |
                | QosService / QosCalculationService|
                | CallChainService / CallChainBuilder|
                | CallChainStatistics               |
                | QosDataScheduler                  |
                | DvClient / DvAuthService           |
                | JsonFileStore                      |
                | ChainTypeConfigStore               |
                +---------+---------------+---------+
                          |
              +-----------+---+
              | JSON File     |
              | Store         |
              | - qos/raw/    |
              | - qos/normalize/|
              | - qos/detail/ |
              | - call-chain/ |
              |   normalize/ |
              |   config/    |
              +---------------+
```

### 3.1 架构分层

1. 前端 `web-app`
   - 健康曲线页面：展示整体健康评分与趋势
   - 指标详情页面：按可用性、性能、资源/告警维度展示明细
   - 贡献度分析页面：展示各子指标对总分的贡献数据
   - 调用链挖掘页面：展示调用链树结构和节点统计

2. 后端控制层
   - `QosController`
     - 接收前端请求，委托给 `QosService` 处理
   - `CallChainController`
     - 接收调用链查询请求，委托给 `CallChainService` 处理
     - 处理配置导入请求

3. 后端业务层
   - `QosService`
     - 负责查询归一化后数据、组装接口响应
   - `QosCalculationService`
     - 负责健康评分计算，包括各维度分段评分与加权汇总
   - `CallChainService`
     - 负责调用链查询编排、时间范围拆分、TraceID 提取
     - 处理调用链配置导入（menuId 映射、chain type 配置）
   - `CallChainBuilder`
     - 负责从 TraceLogRecord 构建调用链树结构
     - 按序列签名分组、过滤低频流程
   - `CallChainStatistics`
     - 负责调用链各节点统计数据计算（IP 分布、集群分布、耗时统计）

4. 定时调度层
   - `QosDataScheduler`
   - 按固定间隔定时触发数据采集任务
   - 调用 `DvClient` 从 DV 系统拉取原始数据
   - 触发归一化与评分计算
   - 将结果写入 `JsonFileStore`

5. DV 集成层
   - `DvClient`
     - 封装与 DV 系统的 HTTP 交互，包括指标查询与告警查询
     - 封装 TraceLog API 调用，支持时间范围拆分和重试机制
   - `DvAuthService`
     - 负责 DV 系统的认证与 token 管理

6. 持久化层
   - `JsonFileStore`
     - 基于文件系统的 JSON 存储
     - 按轮转间隔管理数据文件
     - 按保留天数清理过期数据
   - `ChainTypeConfigStore`
     - 存储调用链类型配置和 menuId 映射配置
   - `CallChainStore`
     - 存储构建完成的调用链树结构

### 3.2 核心对象

#### QoS 相关对象

- `HealthIndicator`：综合健康评分结果，包含总分与各维度分数
- `IndicatorDetail`：单个维度的明细数据，包含子指标值与分段评分
- `ContributionData`：各子指标对总分的贡献度分析结果
- `RawData`：从 DV 系统采集的原始性能与告警数据
- `NormalizedData`：经过归一化处理后的标准化指标数据

#### 调用链相关对象

- `CallChainTree`：调用链树结构，包含链类型、条件、查询时间范围、流程列表、总数量
- `CallFlow`：调用流程，包含流程 ID、调用次数、调用比例、节点列表、统计数据
- `FlowNode`：调用节点，包含 URL/服务名/操作名/消息主题、耗时、IP、集群、日志消息等
- `TraceLogRecord`：DV TraceLog 原始记录，包含 TraceID、seqNo、时间、IP、集群、AppendInfo 等
- `ChainTypeConfig`：调用链类型配置，包含链类型、描述、条件键、提取字段、分类字段等
- `MenuIdMapping`：menuId 映射配置

## 4. 数据流

### 4.1 采集流

```text
QosDataScheduler         DvClient           DV 系统          JsonFileStore
     |                      |                  |                  |
     | trigger (fixedDelay) |                  |                  |
     |--------------------->|                  |                  |
     |                      | fetch metrics    |                  |
     |                      |----------------->|                  |
     |                      |<-----------------|                  |
     |                      | fetch alarms     |                  |
     |                      |----------------->|                  |
     |                      |<-----------------|                  |
     |                      |                  |                  |
     | raw data             |                  |                  |
     |<---------------------|                  |                  |
     | write raw                                              |
     |------------------------------------------------------->|
     | normalize + calculate score                            |
     |------------------------------------------------------->|
     | write normalize + detail                               |
     |------------------------------------------------------->|
```

采集流程说明：

1. `QosDataScheduler` 按固定间隔定时触发
2. `DvClient` 向 DV 系统发起性能指标与告警数据查询请求
3. `DvAuthService` 自动处理认证与 token 刷新
4. 原始数据写入 `JsonFileStore` 的 `raw/` 目录
5. 对原始数据执行归一化处理，生成标准化指标
6. 归一化数据写入 `normalize/` 目录
7. 计算各维度分段评分与综合健康评分
8. 评分明细写入 `detail/` 目录

### 4.2 查询流

```text
web-app              QosController       QosService         JsonFileStore
  |                       |                   |                    |
  | POST /getHealth...    |                   |                    |
  |---------------------->|                   |                    |
  |                       | query indicator   |                    |
  |                       |------------------>|                    |
  |                       |                   | read normalize     |
  |                       |                   |------------------->|
  |                       |                   |<-------------------|
  |                       |                   | calculate score    |
  |                       |<------------------|                    |
  |<----------------------|                   |                    |
```

查询流程说明：

1. 前端发起 HTTP 请求到 `QosController`
2. `QosController` 委托给 `QosService` 处理
3. `QosService` 从 `JsonFileStore` 读取归一化数据
4. 如需实时评分，调用 `QosCalculationService` 计算
5. 组装响应结构返回给前端

### 4.3 调用链查询流

```text
web-app      CallChainController    CallChainService     DvClient      DV System    CallChainStore
  |                |                     |                |               |                |
  | POST /query    |                     |                |               |                |
  |--------------->|                     |                |               |                |
  |                | determine chainType |                |               |                |
  |                |--------------------->|                |               |                |
  |                |                     | fetch entry    |               |                |
  |                |                     | logs           |               |                |
  |                |                     |--------------->|               |                |
  |                |                     |<---------------|               |                |
  |                |                     | extract TraceIDs               |                |
  |                |                     |-------------------------------->|                |
  |                |                     | fetch complete |               |                |
  |                |                     | chains         |               |                |
  |                |                     |--------------->|               |                |
  |                |                     |<---------------|               |                |
  |                |                     | build tree                     |                |
  |                |                     |------------------------------------------------->|
  |                |                     | calculate statistics           |                |
  |                |                     |------------------------------------------------->|
  |                |                     | save tree      |               |                |
  |                |                     |------------------------------------------------->|
  |                |                     |<---------------|               |                |
  |                |<--------------------|                |               |                |
  |<---------------|                     |                |               |                |
```

调用链查询流程说明：

1. 前端发起 HTTP 请求到 `CallChainController`，包含条件、时间范围等参数
2. `CallChainController` 委托给 `CallChainService` 处理
3. `CallChainService` 根据 conditionKey 匹配确定链类型（BES/API/BPM/JOB）
4. 查询 DV TraceLog API 获取入口日志（seqNo=1），如数据量过大则应用时间范围拆分策略
5. 从入口日志中提取唯一 TraceID 列表
6. 对每个 TraceID 调用 DV API 获取完整调用链日志
7. `CallChainBuilder` 按 TraceID 分组、按 seqNo 序列签名分组，构建 CallFlow 列表
8. `CallChainStatistics` 计算每个流程和节点的统计信息（IP 分布、集群分布、耗时等）
9. 过滤低频流程（调用比例 < 3%）
10. 将构建完成的 `CallChainTree` 保存到 `CallChainStore`
11. 返回调用链树结构给前端

时间范围拆分策略：

- 初始查询时间范围：15 分钟
- 若单次返回结果达到 10000 条上限，触发时间范围拆分
- 拆分粒度：10 分钟 → 5 分钟（可配置）

## 5. 健康评分算法

### 5.1 综合评分公式

健康评分（Health Score）采用加权求和模型：

```
HS = wA × A + wP × P + wR × R
```

其中：

- `HS`：综合健康评分，取值范围 `[0, 100]`
- `A`：可用性维度评分，取值范围 `[0, 100]`
- `P`：性能维度评分，取值范围 `[0, 100]`
- `R`：资源/告警维度评分，取值范围 `[0, 100]`
- `wA`：可用性权重，默认 `0.4`
- `wP`：性能权重，默认 `0.4`
- `wR`：资源/告警权重，默认 `0.2`

权重约束：`wA + wP + wR = 1.0`

### 5.2 各维度分段评分策略

#### 可用性（A）

| 可用率区间 | 评分 |
|-----------|------|
| ≥ 99.99%  | 100  |
| 99.9% ~ 99.99% | 90   |
| 99% ~ 99.9% | 70   |
| 95% ~ 99% | 50   |
| < 95%     | 30   |

#### 性能（P）

| 响应时间区间 | 评分 |
|-------------|------|
| ≤ P50 阈值  | 100  |
| P50 ~ P90 阈值 | 80   |
| P90 ~ P95 阈值 | 60   |
| P95 ~ P99 阈值 | 40   |
| > P99 阈值  | 20   |

#### 资源/告警（R）

| 告警密度区间 | 评分 |
|-------------|------|
| 无告警      | 100  |
| 低密度告警（低于阈值 1） | 80   |
| 中密度告警（阈值 1 ~ 阈值 2） | 60   |
| 高密度告警（高于阈值 2） | 40   |
| 严重告警    | 20   |

### 5.3 评分计算流程图

```text
raw metrics + alarms
        |
        v
  per-dimension scoring
        |
   +----+----+----+
   |         |         |
   v         v         v
  A score  P score  R score
   |         |         |
   +----+----+----+
        |
        v
  HS = wA×A + wP×P + wR×R
        |
        v
  Health Indicator (0~100)
```

## 6. 存储策略

### 6.1 JSON 文件存储

`operation-intelligence` 使用基于文件系统的 JSON 存储，不依赖外部数据库。

存储目录结构：

```text
operation-intelligence/data/
  qos/
    raw/                     # 原始采集数据
      <environment>/         # 按环境隔离
        <timestamp>.json
    normalize/               # 归一化数据
      <environment>/
        <timestamp>.json
    detail/                  # 评分明细数据
      <environment>/
        <timestamp>.json
  call-chain/
    normalize/               # 调用链树结构
      <timestamp>.json
    config/                  # 调用链配置
      menu_mapping.json      # menuId 映射配置
      chain_type_config.json # 链类型配置
```

### 6.2 轮转间隔

数据按可配置的轮转间隔进行写入。每次采集任务完成后，生成一个新的 JSON 文件。轮转间隔通过 `config.yaml` 中的 `operation-intelligence.qos.rotation-interval-ms` 配置。

### 6.3 数据保留天数

过期数据按保留天数自动清理。保留天数通过以下配置项分别管理：

- `operation-intelligence.qos.raw-data-retention-days`（默认 7 天）
- `operation-intelligence.qos.detail-data-retention-days`（默认 30 天）
- `operation-intelligence.qos.normalize-data-retention-days`（默认 90 天）

超出保留天数的 JSON 文件会在定时清理任务执行时被删除。

## 7. 调用链构建算法

### 7.1 链类型与识别条件

| 链类型 | 描述 | 条件键 | 入口字段 |
|--------|------|--------|----------|
| BES | 业务执行系统 | menuId | url |
| API | 外部接口 | serviceName | url, serviceName |
| BPM | 业务流程管理 | AppendInfo | busiCode, processName |
| JOB | 定时任务 | jobDefinedId | jobDefinedId |

### 7.2 构建流程

1. **TraceID 分组**：将所有 TraceLogRecord 按 TraceID 分组
2. **seqNo 排序**：按点分记号（如 1, 1.1, 1.2, 2）排序
3. **序列签名生成**：为每个 TraceID 生成序列签名（URL 或服务名序列）
4. **流程分组**：按序列签名分组，形成多个 CallFlow
5. **统计计算**：计算每个流程的调用次数、调用比例，各节点的 IP/集群分布、耗时统计
6. **过滤低频流程**：过滤掉调用比例低于 3% 的流程

### 7.3 统计维度

每个 CallFlow 和 FlowNode 包含以下统计信息：

- **IP 分布**：按 IP 统计调用次数和占比
- **集群分布**：按集群类型统计调用次数和占比
- **耗时统计**：P50、P90、P95、P99、平均、最小、最大
- **成功率**：基于 LogMessage 判断的成功调用比例

## 7. 接口列表

### 7.1 健康指标接口

- `POST /operation-intelligence/qos/getHealthIndicator`
  - 用途：获取综合健康评分与各维度评分概要
  - 请求体包含环境标识与时间范围
  - 返回综合评分及可用性、性能、资源/告警三个维度的分项评分

### 7.2 指标明细接口

- `POST /operation-intelligence/qos/getAvailableIndicatorDetail`
  - 用途：获取可用性维度明细数据
  - 返回可用率、故障次数、MTTR 等子指标

- `POST /operation-intelligence/qos/getPerformanceIndicatorDetail`
  - 用途：获取性能维度明细数据
  - 返回响应时间 P50/P90/P95/P99、吞吐量等子指标

- `POST /operation-intelligence/qos/getResourceIndicatorDetail`
  - 用途：获取资源维度明细数据
  - 返回 CPU、内存、磁盘等资源使用率子指标

- `POST /operation-intelligence/qos/getAlarmIndicatorDetail`
  - 用途：获取告警维度明细数据
  - 返回告警数量、告警级别分布、告警趋势等子指标

### 7.3 分析与配置接口

- `POST /operation-intelligence/qos/getContributionData`
  - 用途：获取各子指标对总评分的贡献度分析
  - 返回子指标贡献权重与实际贡献值

- `POST /operation-intelligence/qos/getProductConfigRule`
  - 用途：获取当前生效的产品配置规则
  - 返回评分阈值、分段策略、权重配置

- `GET /operation-intelligence/qos/getEnvironments`
  - 用途：获取已配置的 DV 环境列表
  - 返回环境标识与名称

### 7.4 调用链接口

- `POST /operation-intelligence/call-chain/query`
  - 用途：查询调用链树结构
  - 请求体包含 solutionType（DV 环境）、条件列表、时间范围
  - 返回调用链树，包含多个 CallFlow，每个 CallFlow 包含多个 FlowNode

- `POST /operation-intelligence/call-chain/config/menu-mapping`
  - 用途：导入 menuId 映射配置
  - 请求体为文件上传（multipart/form-data）
  - 返回导入的配置数量

- `POST /operation-intelligence/call-chain/config/chain-type`
  - 用途：导入链类型配置
  - 请求体为文件上传（multipart/form-data），格式为 pipe 分隔的文本
  - 返回导入的配置数量

- `GET /operation-intelligence/call-chain/environments`
  - 用途：获取已配置的 DV 环境列表（用于调用链查询）
  - 返回环境标识与名称

## 8. 推荐接入方式

### 8.1 给前端管理台

推荐链路：

1. 初始化先调 `getEnvironments` 获取可用环境列表
2. 调 `getProductConfigRule` 获取当前评分规则
3. 健康曲线主页调 `getHealthIndicator` 展示综合评分
4. 各维度详情页按需调对应的 `getXXXIndicatorDetail`
5. 贡献度分析调 `getContributionData`
6. 调用链页面调 `/call-chain/query` 展示调用链树结构

### 8.2 给生产部署

推荐链路：

1. 保持 `operation-intelligence` 作为独立服务部署与演进
2. 前端按服务边界直接访问，无需经由 `gateway` 收口
3. 确保服务能访问 DV 系统的网络与认证信息
4. 关注 JSON 文件存储的磁盘空间

## 9. 当前实现限制

- 当前仅支持 JSON 文件存储，不支持数据库持久化
- 采集任务依赖 `QosDataScheduler` 的定时触发，不支持实时推送
- DV 系统集成依赖 `DvClient` 的 HTTP 调用，网络波动会影响数据完整性
- 健康评分算法的权重与阈值当前通过配置文件管理，不支持运行时动态调整
- 前端当前直接访问 `operation-intelligence`，与独立服务部署模式一致
- 数据归一化策略当前为固定实现，暂不支持自定义归一化规则
- 调用链查询依赖 DV TraceLog API，单次查询最多返回 10000 条记录
- 调用链低频流程过滤阈值为 3%，当前不可配置
- 调用链树结构仅支持 4 种预定义链类型（BES/API/BPM/JOB）

## 10. 结论

当前 `operation-intelligence` 已经形成两条可用闭环：

### QoS 健康曲线闭环

- 定时从 DV 系统采集性能与告警数据
- 原始数据归一化与分段评分
- 加权健康评分计算
- JSON 文件存储与自动轮转清理
- 健康指标、明细数据与贡献度查询 API

### 调用链挖掘闭环

- 从 DV TraceLog API 查询调用链原始数据
- 支持时间范围拆分处理大规模查询
- 按 TraceID 和序列签名构建调用链树
- 计算各节点 IP/集群分布和耗时统计
- JSON 文件存储构建完成的调用链树
- 调用链查询和配置导入 API

如果要在当前阶段做稳定集成，应优先依赖以下闭环能力：

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
