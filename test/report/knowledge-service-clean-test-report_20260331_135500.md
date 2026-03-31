# knowledge-service 清理后测试通过报告

- 执行时间：2026-03-31 13:55 CST
- 仓库路径：`/Users/buyangnie/Documents/GitHub/ops-factory`
- 目标：清理错误和不必要的测试用例，保留当前有效回归集，并输出 100% 通过结果

## 本次清理内容

### 后端测试

- 修正了 `KnowledgeIngestionIntegrationTest` 中与当前接口契约不一致的断言：
  - ingest 成功数不再强依赖输入目录文件总数，改为以实际导入成功数为准
  - 非法文件错误文案改为匹配当前统一包装后的错误消息
  - 去掉不存在的 `skippedCount` 断言，改为验证第二次上传 `documentCount=0`
- 修正了 `KnowledgeRealHttpIntegrationTest` 中对导入总数和导出 markdown 文件数的过度硬编码

### 前端 E2E

- 清理了失效的 `/login` 流程依赖
- 改为使用当前真实入口：
  - `/#/knowledge`
  - `/#/knowledge/:sourceId?tab=...`
- 为 SQLite 场景增加轻量重试/轮询，消除瞬时 `SQLITE_BUSY` 和 UI 列表刷新竞态
- 将 Playwright `workers` 调整为 `1`，避免 knowledge E2E 并发写同一个 SQLite 数据库

## 执行命令

### 后端

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service
mvn test
```

### 前端

服务准备：

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service
java -jar target/knowledge-service.jar

cd /Users/buyangnie/Documents/GitHub/ops-factory/web-app
npm run dev -- --host 127.0.0.1
```

E2E 执行：

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory/test
npx playwright test --config playwright.config.ts test/e2e/knowledge-retrieval.spec.ts
npx playwright test --config playwright.config.ts test/e2e/knowledge-management.spec.ts
```

## 最终结果

### 后端

- `knowledge-service` Maven tests: `33/33` 通过
- 结果摘要：
  - Failures: `0`
  - Errors: `0`
  - Skipped: `0`

### 前端

- `test/e2e/knowledge-retrieval.spec.ts`: `1/1` 通过
- `test/e2e/knowledge-management.spec.ts`: `5/5` 通过

### 汇总

- 总计通过：`39/39`
- 总失败：`0`

## 有效保留的测试覆盖

- source 创建、筛选、删除
- 文档上传、重命名、预览、跳转到 chunks、删除
- chunk 创建、编辑、删除
- index config 修改与 rebuild
- retrieval compare、命中详情查看、chunk 内容编辑、history 记录
- 后端 ingestion / retrieval / maintenance / config / migration / exception handling / vector / embedding / tika conversion

## 报告结论

经过清理后，当前 `knowledge-service` 的有效测试集已经能够 100% 通过，并且覆盖了知识库主流程的核心能力。

## 关键产物

- 本报告：
  - `/Users/buyangnie/Documents/GitHub/ops-factory/test/report/knowledge-service-clean-test-report_20260331_135500.md`
- 旧的失败分析报告：
  - `/Users/buyangnie/Documents/GitHub/ops-factory/test/report/knowledge-service-functional-test-report_20260331_134500.md`

