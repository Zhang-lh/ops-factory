# knowledge-service 功能测试报告

- 执行时间：2026-03-31 13:45 CST
- 仓库路径：`/Users/buyangnie/Documents/GitHub/ops-factory`
- 测试目标：对 `knowledge-service` 做一轮完整功能测试，覆盖后端单元/集成测试与前端 E2E，并判断现有测试用例是否完整

## 1. 执行范围

### 后端

- 模块：`knowledge-service`
- 执行命令：`mvn test`
- 结果来源：
  - `knowledge-service/target/surefire-reports/*.txt`
  - `knowledge-service/target/surefire-reports/*.xml`

### 前端

- 模块：`web-app` + `test`
- 执行命令：
  - `npm run dev -- --host 127.0.0.1`（`web-app`）
  - `java -jar target/knowledge-service.jar`（`knowledge-service` 前台 PTY）
  - `npx playwright test --config playwright.config.ts test/e2e/knowledge-retrieval.spec.ts`
  - `npx playwright test --config playwright.config.ts test/e2e/knowledge-management.spec.ts`
- 补充验证：
  - 基于当前真实路由补执行一条手工自动化 smoke，用于区分“产品功能故障”和“E2E 脚本过期”

## 2. 总体结论

本轮测试结论是：

1. `knowledge-service` 后端测试不是全绿，`33` 个测试中有 `4` 个失败，失败集中在 ingestion 相关断言，属于真实回归或测试数据与实现不一致。
2. 前端现有 knowledge 相关 Playwright 用例 `6/6` 失败，但主因不是 knowledge 页面功能整体不可用，而是测试脚本已经落后于当前前端登录与路由模型。
3. 我补做的当前路由 smoke 测试通过，证明 `knowledge-service` 与知识库检索 UI 的核心链路在当前版本下仍然可跑通。
4. 现有测试用例“不完整”，更准确地说是“后端覆盖面中等偏好，但 ingestion 断言失真；前端 E2E 资产已明显失效，不能再作为有效回归门禁”。

## 3. 后端测试结果

### 汇总

- 总数：`33`
- 失败：`4`
- 错误：`0`
- 跳过：`0`

### 通过的测试域

- 配置与迁移
  - `KnowledgeDatabasePropertiesTest`
  - `KnowledgePropertiesTest`
  - `FlywayMigrationResourceTest`
- 错误处理
  - `ApiExceptionHandlerTest`
- 系统能力与默认值
  - `SystemControllerTest`
- 召回与检索
  - `KnowledgeRetrievalIntegrationTest`
- 维护任务
  - `KnowledgeMaintenanceIntegrationTest`
- Chunk 变更
  - `KnowledgeChunkMutationIntegrationTest`
- 嵌入、转换、索引底层服务
  - `EmbeddingServiceTest`
  - `TikaConversionServiceTest`
  - `VectorIndexServiceTest`
- 真实 HTTP 回路
  - `KnowledgeRealHttpIntegrationTest` 中 `4/5` 通过

### 失败项

1. `KnowledgeIngestionIntegrationTest.shouldIngestSupportedDocumentsChunkThemAndServeArtifactsAndRetrieval`
   - 失败现象：期望 `12`，实际 `11`
   - 判定：测试输入文件数量或 ingestion 过滤逻辑发生变化，导致断言已失真，或真实 ingest 少处理了 1 个文件

2. `KnowledgeIngestionIntegrationTest.shouldRejectUnsupportedContentTypeWithBadRequest`
   - 失败现象：期望报错消息包含 `Unsupported content type`，实际为 `Failed to ingest file malware.exe`
   - 判定：接口行为发生变化，错误语义被包裹成泛化文案，测试与产品约定不一致

3. `KnowledgeIngestionIntegrationTest.shouldDeduplicateIdenticalFileOnSecondUpload`
   - 失败现象：期望 `1`，实际 `0`
   - 判定：重复上传后的计数/返回语义与测试预期不一致，需要核对 dedup 规则

4. `KnowledgeRealHttpIntegrationTest.shouldBehaveLikeARealClientUsingHttpAndMultipartUpload`
   - 失败现象：期望 `12`，实际 `11`
   - 判定：与第 1 项属于同类问题，说明不仅 MockMvc 级别失败，真实 HTTP 端到端也复现

## 4. 前端 E2E 结果

### 现有 Playwright 用例结果

- `test/e2e/knowledge-retrieval.spec.ts`：`1/1` 失败
- `test/e2e/knowledge-management.spec.ts`：`5/5` 失败

### 失败归因

#### A. 登录步骤已失效

两个 spec 都依赖：

- `page.goto('/login')`
- `input[placeholder="Your name"]`

但当前前端实际情况是：

- 应用使用 `HashRouter`
- `App.tsx` 没有注册 `/login` 路由
- `UserContext` 在无用户信息时会直接 fallback 到 `admin`

因此，测试打开 `/login` 后并不会进入一个真实可交互的登录页，而是落到当前默认首页。Playwright 快照中可直接看到页面顶部已经显示 `admin`，且主区域是 Home 页面而不是登录页。

#### B. 现有 E2E 脚本与当前路由模型不一致

当前可工作的知识库 URL 形态是：

- `http://127.0.0.1:5173/#/knowledge`
- `http://127.0.0.1:5173/#/knowledge/{sourceId}?tab=retrieval`

但现有用例仍使用：

- `/knowledge/...`
- `/login`

在 `HashRouter` 下，这类导航方式已经不是当前应用的有效测试入口。

#### C. 首条 management 用例的 API setup 失败

`knowledge-management.spec.ts` 第一条在 `createSource` 阶段出现 `response.ok() === false`。这次失败发生在 Playwright 与服务启动几乎同时进行时，更像是环境就绪竞态，不足以单独判断为产品缺陷。

### 补充 smoke 结果

我补做了一条基于当前真实入口的浏览器 smoke：

- 先通过 API 创建知识库 source
- 上传 `itsm-deployment.md`
- 浏览器直接访问 `/#/knowledge/{sourceId}?tab=retrieval`
- 在 `#knowledge-retrieval-query` 中输入 `ITSM`
- 点击 `Run Test`
- 成功看到 `Comparison / 结果对比` 与命中文档内容

结果：`PASS`

说明：

- 当前知识库检索 UI 核心链路是通的
- 现有前端 E2E 失败的主因是测试脚本老化，不是产品整体失效

## 5. 覆盖性评估

### 已覆盖的核心能力

- source 创建
- document ingest
- document/chunk 查询
- artifact markdown 导出
- retrieval search / compare / fetch
- maintenance job 基本流程
- chunk create / update / delete
- embedding / vector index / tika conversion
- config / defaults / capabilities
- API exception handler

### 缺失或明显不足的场景

1. Source API 覆盖不完整
   - 缺少对 source 列表筛选、分页、更新、删除成功路径的稳定后端断言

2. Ingestion 边界覆盖不足
   - 缺少大文件上限、混合文件批量部分失败、空文件、异常编码、重复文件多轮上传后的稳定语义验证

3. 文档与 chunk 生命周期覆盖不足
   - 缺少 document rename、delete、preview、跨 tab 状态同步的后端/前端联合断言

4. 检索质量覆盖不足
   - 当前更多验证“有结果”，但缺少排序稳定性、阈值过滤、生效 profile、不同 mode 差异的精确断言

5. 维护态限制覆盖不足
   - 缺少 MAINTENANCE 期间上传、检索、chunk 编辑被正确阻断的测试

6. 并发与状态竞争没有覆盖
   - 缺少 rebuild 中再次 ingest、删除 source 时存在 job、并发编辑 chunk 等高风险路径

7. 前端 E2E 基本不可用
   - 现有 knowledge E2E 已无法反映真实产品状态，等于 coverage 在 UI 层出现空洞

## 6. 对“测试用例是否完整”的结论

结论：**不完整。**

具体判断如下：

- 后端：有一定深度，但 ingestion 断言和真实输入已经不一致，且 source/document/maintenance 的边界条件不够完整。
- 前端：现有 E2E 与当前应用结构脱节，已经不能作为有效回归测试；这部分完整性可以认为严重不足。

如果以“可作为发布前 knowledge-service 回归门禁”为标准，当前测试集不达标。

## 7. 建议优先级

### P0

- 修复 `knowledge-service` 当前 4 个失败测试，先确认是实现回归还是测试数据/断言过期
- 重写 knowledge 前端 E2E 入口，统一改为 `HashRouter` 真实路径，删除 `/login` 依赖

### P1

- 补齐 source/document API 生命周期测试
- 为 ingestion 增加边界与异常输入测试
- 为 maintenance 状态增加禁止性断言

### P2

- 增加 retrieval 排序质量与 profile 生效断言
- 增加并发/竞态场景

## 8. 关键证据路径

- 后端 surefire 报告目录：
  - `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/target/surefire-reports`
- 前端 Playwright 失败上下文：
  - `/Users/buyangnie/Documents/GitHub/ops-factory/test/test-results`
- 当前报告：
  - `/Users/buyangnie/Documents/GitHub/ops-factory/test/report/knowledge-service-functional-test-report_20260331_134500.md`

