/**
 * Stress Tests — Concurrent Multi-User Conversations
 *
 * Simulates multiple users chatting concurrently through the gateway,
 * verifying that the system handles parallel load without hangs or errors.
 *
 * Test A: 3 users × 10 rounds, simple conversations
 * Test B: 5 users × 10 rounds, with tool calls every 3rd round
 *
 * Prerequisites: goosed binary in PATH, gateway JAR built.
 * Run: cd test && npx vitest run stress.test.ts
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { startJavaGateway, type GatewayHandle } from './helpers.js'
import {
  WebClient,
  runUserConversation,
  buildStressReport,
  formatStressReport,
} from './journey-helpers.js'

const AGENT_ID = 'universal-agent'

let gw: GatewayHandle

beforeAll(async () => {
  gw = await startJavaGateway()
}, 60_000)

afterAll(async () => {
  if (gw) await gw.stop()
}, 15_000)

describe('Stress tests', () => {
  it('Test A: 3 concurrent users, 10 rounds each, simple chat', async () => {
    const userIds = ['stress-a1', 'stress-a2', 'stress-a3']
    const clients = userIds.map(id => new WebClient(gw, id, AGENT_ID))

    const reports = await Promise.all(
      clients.map(c => runUserConversation(c, 10, { roundTimeoutMs: 90_000 })),
    )

    const overall = buildStressReport('Test A: 3 users × 10 rounds (simple)', reports)
    console.log('\n' + formatStressReport(overall) + '\n')

    // All rounds should succeed
    for (const r of reports) {
      expect(r.successes).toBe(10)
      expect(r.failures).toBe(0)
      expect(r.averageResponseTime).toBeLessThan(60_000)
    }
    expect(overall.overallSuccessRate).toBe(1.0)
  }, 600_000) // 10 minute timeout

  it('Test B: 5 concurrent users, 10 rounds each, tool calls every 3rd round', async () => {
    const userIds = ['stress-b1', 'stress-b2', 'stress-b3', 'stress-b4', 'stress-b5']
    const clients = userIds.map(id => new WebClient(gw, id, AGENT_ID))

    const reports = await Promise.all(
      clients.map(c =>
        runUserConversation(c, 10, { toolCallEveryN: 3, roundTimeoutMs: 90_000 }),
      ),
    )

    const overall = buildStressReport('Test B: 5 users × 10 rounds (with tools)', reports)
    console.log('\n' + formatStressReport(overall) + '\n')

    // Allow at most 1 failure per user (LLM can be flaky under load)
    for (const r of reports) {
      expect(r.failures).toBeLessThanOrEqual(1)
      expect(r.averageResponseTime).toBeLessThan(90_000)
    }
    // Overall success rate should be at least 90%
    expect(overall.overallSuccessRate).toBeGreaterThanOrEqual(0.9)
  }, 600_000)
})
