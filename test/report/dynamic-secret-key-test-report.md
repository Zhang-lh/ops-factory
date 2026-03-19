# Dynamic Secret Key — Test Report

**Date:** 2026-03-19  
**Feature:** Per-Instance Dynamic Secret Key for Goosed Processes  
**Result:** ✅ ALL TESTS PASS

## Summary

| Module | Tests Run | Failures | Errors | Skipped |
|--------|-----------|----------|--------|---------|
| gateway-common | 60 | 0 | 0 | 0 |
| gateway-service | 364 | 0 | 0 | 0 |
| **Total** | **424** | **0** | **0** | **0** |

**Build Time:** ~7 seconds

## Changes Made

### Production Code (11 files)

| File | Change |
|------|--------|
| `ManagedInstance.java` | Added `secretKey` field with getter |
| `InstanceManager.java` | Generate per-instance 32-byte random hex key via `SecureRandom`, pass to `ManagedInstance` and `GOOSE_SERVER__SECRET_KEY` env var |
| `GoosedProxy.java` | All methods (`proxy`, `fetchJson`, `proxyWithBody`) now accept per-instance `secretKey` param; removed `getSecretKey()` |
| `SseRelayService.java` | `relay()` accepts `secretKey` param for upstream auth |
| `SessionService.java` | Uses `instance.getSecretKey()` for fetching sessions |
| `MetricsCollector.java` | Uses `instance.getSecretKey()` for /sessions/insights |
| `CatchAllProxyController.java` | Passes `instance.getSecretKey()` to proxy |
| `McpController.java` | Passes `instance.getSecretKey()` to proxy |
| `SessionController.java` | Passes `instance.getSecretKey()` to all proxy calls |
| `ReplyController.java` | Passes `instance.getSecretKey()` to relay and proxy calls |
| `AgentController.java` | Fixed pre-existing generic type inference error |

### SDK (1 file)

| File | Change |
|------|--------|
| `typescript-sdk/src/client.ts` | Changed default secret key from `'test'` to `''` |

### Test Files Updated (15+ files)

All test files were updated to match new method signatures:

- `ManagedInstanceTest.java` — added 6th `secretKey` constructor arg
- `InstanceManagerExtendedTest.java` — assert 64-char hex format instead of static value
- `GoosedProxyTest.java` — removed `testSecretKey()` (method no longer exists)
- `GoosedProxyExtendedTest.java` — updated reflection calls and direct calls
- `MetricsCollectorTest.java` — updated `fetchJson` mock stubs
- `SessionEndpointE2ETest.java` — updated `fetchJson`, `proxy` verify stubs
- `SessionEndpointExtendedE2ETest.java` — updated `fetchJson`, `proxy`, `proxyWithBody` stubs
- `ReplyEndpointE2ETest.java` — updated `fetchJson`, `proxyWithBody` stubs
- `CatchAllProxyEndpointE2ETest.java` — updated `proxy` verify stubs
- `CatchAllProxyControllerTest.java` — updated `proxy` verify stubs
- `McpEndpointE2ETest.java` — updated `proxy` verify stub
- `HookPipelineE2ETest.java` — updated `relay` mock stubs

## Test Breakdown by Category

### Unit Tests
| Test Class | Count | Status |
|------------|-------|--------|
| ManagedInstanceTest | 12 | ✅ |
| GoosedProxyTest | 4 | ✅ |
| GoosedProxyExtendedTest | 6 | ✅ |
| SseRelayServiceTest | 1 | ✅ |
| InstanceManagerTest | 8 | ✅ |
| InstanceManagerExtendedTest | 14 | ✅ |
| CatchAllProxyControllerTest | 9 | ✅ |
| MetricsCollectorTest | 8 | ✅ |
| AgentConfigServiceTest | 44 | ✅ |
| GatewayPropertiesTest | 11 | ✅ |

### E2E Tests
| Test Class | Count | Status |
|------------|-------|--------|
| SessionEndpointE2ETest | 12 | ✅ |
| SessionEndpointExtendedE2ETest | 7 | ✅ |
| ReplyEndpointE2ETest | 8 | ✅ |
| CatchAllProxyEndpointE2ETest | 7 | ✅ |
| McpEndpointE2ETest | 8 | ✅ |
| HookPipelineE2ETest | 5 | ✅ |
| MonitoringEndpointE2ETest | 21 | ✅ |
| FileCapsuleEndpointE2ETest | 10 | ✅ |

### Other Tests
| Test Class | Count | Status |
|------------|-------|--------|
| PrewarmServiceTest | 7 | ✅ |
| RuntimePreparerTest | 4 | ✅ |
| PortAllocatorTest | 2 | ✅ |
| InstanceWatchdogTest | 9 | ✅ |
| HookPipelineTest | 4 | ✅ |
| BodyLimitHookTest | 3 | ✅ |
| FileAttachmentHookTest | 10 | ✅ |
| And more... | ... | ✅ |

## Security Improvements Validated

1. **Dynamic key generation:** Each goosed instance receives a unique 64-character hex key generated via `SecureRandom`
2. **Per-instance isolation:** Keys are stored on the `ManagedInstance` object and used for all communication with that specific instance
3. **No hardcoded secrets:** The gateway no longer passes its own `secretKey` to goosed; each instance has its own
4. **SDK safety:** TypeScript SDK no longer defaults to `'test'` — defaults to empty string, causing clear auth failure if unconfigured
