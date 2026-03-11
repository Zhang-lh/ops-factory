# goosed TLS Support — Test Report

**Date:** 2026-03-11
**Feature:** Gateway goosed-tls config switch for goosed 1.27+ TLS support
**Test file:** `test/goosed-tls.test.ts`
**Result:** 28/28 passed + 354/354 Java unit tests passed

## Background

goosed 1.27 defaults to TLS (`tls: true`) with self-signed certificates. The Gateway previously connected to goosed via plain HTTP, causing all health checks to fail with `goosed failed to start on port XXXXX`. This feature adds a `goosedTls` config switch that controls:

1. Gateway HTTP clients use HTTPS with trust-all SSL (for self-signed certs)
2. `GOOSE_TLS` environment variable is passed to goosed processes
3. Full backward compatibility: `goosedTls: false` reverts to HTTP

## Config Chain

```
gateway/config.yaml (goosedTls: true)
    → ctl.sh (GOOSED_TLS env var + -Dgateway.goosed-tls)
    → application.yml (goosed-tls: ${GOOSED_TLS:true})
    → GatewayProperties.java (boolean goosedTls = true)
```

## Modified Files

| File | Change |
|------|--------|
| `gateway/config.yaml` | Added `goosedTls: true` |
| `gateway/scripts/ctl.sh` | Read goosedTls, inject `-Dgateway.goosed-tls` |
| `application.yml` | Added `goosed-tls: ${GOOSED_TLS:true}` |
| `GatewayProperties.java` | Field, getter/setter, `goosedScheme()` |
| `InstanceManager.java` | `GOOSE_TLS` env, trust-all SSL for health checks |
| `GoosedProxy.java` | Netty insecure SSL WebClient, `goosedBaseUrl()` |
| `SseRelayService.java` | Use `goosedProxy.goosedBaseUrl()` |
| `McpController.java` | 4x `http://` → `goosedBaseUrl()` |
| `SessionService.java` | Use `goosedProxy.goosedBaseUrl()` |
| `McpEndpointE2ETest.java` | Mock `goosedBaseUrl()` |

## Test Results

### Integration Tests (vitest) — 28/28 passed

```
 ✓ config.yaml goosedTls field > gateway/config.yaml contains goosedTls key          1ms
 ✓ config.yaml goosedTls field > goosedTls defaults to true                          0ms
 ✓ ctl.sh goosedTls parsing > yaml_val reads goosedTls value                         8ms
 ✓ ctl.sh goosedTls parsing > yaml_val reads goosedTls=false                         6ms
 ✓ ctl.sh goosedTls parsing > env var GOOSED_TLS overrides config.yaml               3ms
 ✓ ctl.sh goosedTls parsing > defaults to true when config.yaml has no goosedTls     5ms
 ✓ ctl.sh Java property injection > ctl.sh contains -Dgateway.goosed-tls injection   0ms
 ✓ ctl.sh Java property injection > ctl.sh reads GOOSED_TLS from yaml_val            0ms
 ✓ ctl.sh Java property injection > ctl.sh passes bash -n (syntax valid)             4ms
 ✓ application.yml goosed-tls > application.yml contains goosed-tls property         0ms
 ✓ no hardcoded http:// > GoosedProxy.java                                          1ms
 ✓ no hardcoded http:// > SseRelayService.java                                      0ms
 ✓ no hardcoded http:// > McpController.java                                        0ms
 ✓ no hardcoded http:// > SessionService.java                                       0ms
 ✓ no hardcoded http:// > InstanceManager.java                                      0ms
 ✓ GatewayProperties goosedTls > declares goosedTls boolean field                   0ms
 ✓ GatewayProperties goosedTls > has isGoosedTls() getter                           0ms
 ✓ GatewayProperties goosedTls > has setGoosedTls() setter                          0ms
 ✓ GatewayProperties goosedTls > has goosedScheme() method returning https or http  0ms
 ✓ InstanceManager GOOSE_TLS env > passes GOOSE_TLS env var to goosed               0ms
 ✓ InstanceManager GOOSE_TLS env > uses goosedBaseUrl() for health check            0ms
 ✓ InstanceManager GOOSE_TLS env > has trust-all SSL factory for self-signed certs  0ms
 ✓ InstanceManager GOOSE_TLS env > configures HttpsURLConnection with trust-all     0ms
 ✓ GoosedProxy TLS WebClient > imports Netty SSL classes                            0ms
 ✓ GoosedProxy TLS WebClient > configures insecure SSL when goosedTls is true       0ms
 ✓ GoosedProxy TLS WebClient > uses ReactorClientHttpConnector with custom HttpClient 0ms
 ✓ GoosedProxy TLS WebClient > exposes goosedBaseUrl() method                       0ms
 ✓ gateway Java unit tests > mvn test passes (354 tests)                            7348ms

 Test Files  1 passed (1)
      Tests  28 passed (28)
   Duration  7.48s
```

### Java Unit Tests (mvn test) — 354/354 passed

All existing Gateway unit tests continue to pass, confirming no regressions from the TLS changes.

## Test Coverage by Area

| Area | Tests | What is verified |
|------|-------|-----------------|
| config.yaml | 2 | `goosedTls` key exists, defaults to `true` |
| ctl.sh parsing | 4 | yaml_val reads true/false, env override, missing-key default |
| ctl.sh injection | 3 | `-Dgateway.goosed-tls` present, GOOSED_TLS read, bash syntax valid |
| application.yml | 1 | Spring property placeholder `${GOOSED_TLS:true}` |
| No hardcoded http | 5 | 5 source files have no `"http://127.0.0.1:" + port` patterns |
| GatewayProperties | 4 | Field, getter, setter, `goosedScheme()` |
| InstanceManager | 4 | GOOSE_TLS env, goosedBaseUrl, trust-all SSL, HttpsURLConnection |
| GoosedProxy | 4 | Netty SSL imports, insecure config, ReactorClientHttpConnector, goosedBaseUrl |
| Java unit tests | 1 | Full `mvn test` passes (354 tests, 0 failures, 0 errors) |
