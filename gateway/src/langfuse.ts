import type { LangfuseConfig } from './config.js'

interface DailyRow {
  date: string
  countTraces: number
  countObservations: number
  totalCost: number
  usage: Array<{
    model: string | null
    inputUsage: number
    outputUsage: number
    totalUsage: number
    totalCost: number
    countObservations: number
    countTraces: number
  }>
}

/**
 * Thin wrapper around the Langfuse public REST API.
 * All methods return plain JSON-serialisable objects ready for the gateway response.
 */

export class LangfuseClient {
  private baseUrl: string
  private authHeader: string

  constructor(cfg: LangfuseConfig) {
    this.baseUrl = `${cfg.host}/api/public`
    this.authHeader = 'Basic ' + Buffer.from(`${cfg.publicKey}:${cfg.secretKey}`).toString('base64')
  }

  // ---- low-level --------------------------------------------------------

  private async get<T = unknown>(path: string, params?: Record<string, string>): Promise<T> {
    const url = new URL(`${this.baseUrl}${path}`)
    if (params) {
      for (const [k, v] of Object.entries(params)) {
        if (v !== undefined && v !== '') url.searchParams.set(k, v)
      }
    }
    const res = await fetch(url.toString(), {
      headers: { Authorization: this.authHeader },
      signal: AbortSignal.timeout(10_000),
    })
    if (!res.ok) {
      const body = await res.text().catch(() => '')
      throw new Error(`Langfuse ${path}: ${res.status} ${res.statusText} — ${body}`)
    }
    return res.json() as Promise<T>
  }

  // ---- health -----------------------------------------------------------

  async healthy(): Promise<boolean> {
    try {
      const res = await fetch(`${this.baseUrl.replace('/api/public', '')}/api/public/health`, {
        signal: AbortSignal.timeout(3_000),
      })
      return res.ok
    } catch {
      return false
    }
  }

  // ---- /metrics/daily ---------------------------------------------------

  async dailyMetrics(opts?: { page?: number; limit?: number; traceName?: string; fromTimestamp?: string; toTimestamp?: string }): Promise<{ data: DailyRow[]; meta: { totalItems: number } }> {
    const params: Record<string, string> = {}
    if (opts?.page) params.page = String(opts.page)
    if (opts?.limit) params.limit = String(opts.limit)
    if (opts?.traceName) params.traceName = opts.traceName
    if (opts?.fromTimestamp) params.fromTimestamp = opts.fromTimestamp
    if (opts?.toTimestamp) params.toTimestamp = opts.toTimestamp
    return this.get('/metrics/daily', params)
  }

  // ---- /traces ----------------------------------------------------------

  async traces(opts?: { page?: number; limit?: number; name?: string; fromTimestamp?: string; toTimestamp?: string }): Promise<{
    data: Array<{
      id: string
      name: string
      timestamp: string
      input: unknown
      totalCost: number
      latency: number
      observations: string[]
      tags: string[]
      metadata: Record<string, unknown>
    }>
    meta: { totalItems: number; totalPages: number; page: number; limit: number }
  }> {
    const params: Record<string, string> = {}
    if (opts?.page) params.page = String(opts.page)
    if (opts?.limit) params.limit = String(opts.limit)
    if (opts?.name) params.name = opts.name
    if (opts?.fromTimestamp) params.fromTimestamp = opts.fromTimestamp
    if (opts?.toTimestamp) params.toTimestamp = opts.toTimestamp
    return this.get('/traces', params)
  }

  // ---- /observations ----------------------------------------------------

  async observations(opts?: { page?: number; limit?: number; name?: string; type?: string; traceId?: string; fromStartTime?: string; toStartTime?: string }): Promise<{
    data: Array<{
      id: string
      name: string
      type: string
      startTime: string
      endTime: string | null
      latency: number
      level: string
      model: string | null
      promptTokens: number
      completionTokens: number
      totalTokens: number
      calculatedTotalCost: number | null
      metadata: Record<string, unknown> | null
      traceId: string
    }>
    meta: { totalItems: number; totalPages: number; page: number; limit: number }
  }> {
    const params: Record<string, string> = {}
    if (opts?.page) params.page = String(opts.page)
    if (opts?.limit) params.limit = String(opts.limit)
    if (opts?.name) params.name = opts.name
    if (opts?.type) params.type = opts.type
    if (opts?.traceId) params.traceId = opts.traceId
    if (opts?.fromStartTime) params.fromStartTime = opts.fromStartTime
    if (opts?.toStartTime) params.toStartTime = opts.toStartTime
    return this.get('/observations', params)
  }

  // ---- high-level: overview KPIs ----------------------------------------

  async overview(from: string, to: string): Promise<{
    totalTraces: number
    totalObservations: number
    totalCost: number
    avgLatency: number
    p95Latency: number
    errorCount: number
    daily: Array<{ date: string; traces: number; observations: number; cost: number }>
  }> {
    // Fetch daily metrics, traces, and observations in parallel
    const [daily, traces, obs] = await Promise.all([
      this.dailyMetrics({ fromTimestamp: from, toTimestamp: to, limit: 50 }),
      this.traces({ fromTimestamp: from, toTimestamp: to, limit: 100 }),
      this.observations({ fromStartTime: from, toStartTime: to, limit: 100 }),
    ])

    let totalTraces = 0
    let totalObservations = 0
    let totalCost = 0
    const dailyArr = daily.data.map(d => {
      totalTraces += d.countTraces
      totalObservations += d.countObservations
      totalCost += d.totalCost
      return { date: d.date, traces: d.countTraces, observations: d.countObservations, cost: d.totalCost }
    })

    const latencies = traces.data.map(t => t.latency).filter(l => l > 0).sort((a, b) => a - b)
    const avgLatency = latencies.length > 0 ? latencies.reduce((s, l) => s + l, 0) / latencies.length : 0
    const p95Latency = latencies.length > 0 ? latencies[Math.floor(latencies.length * 0.95)] ?? latencies[latencies.length - 1] : 0
    let errorCount = 0
    for (const o of obs.data) {
      if (o.level === 'ERROR') { errorCount++; continue }
      const msg = (o.metadata as Record<string, unknown>)?.message
      if (typeof msg === 'string' && (/error|fail|exception/i.test(msg))) errorCount++
    }

    return { totalTraces, totalObservations, totalCost, avgLatency, p95Latency, errorCount, daily: dailyArr }
  }

  // ---- high-level: recent traces ----------------------------------------

  async recentTraces(opts: { from: string; to: string; limit?: number; errorsOnly?: boolean }): Promise<Array<{
    id: string
    name: string
    timestamp: string
    input: string
    latency: number
    totalCost: number
    observationCount: number
    hasError: boolean
    errorMessage?: string
  }>> {
    // Fetch traces and all observations for the range in parallel
    const [traces, allObs] = await Promise.all([
      this.traces({ fromTimestamp: opts.from, toTimestamp: opts.to, limit: opts.limit || 30 }),
      this.observations({ fromStartTime: opts.from, toStartTime: opts.to, limit: 100 }),
    ])

    // Index observation errors by traceId
    const errorsByTrace = new Map<string, string>()
    for (const o of allObs.data) {
      if (errorsByTrace.has(o.traceId)) continue
      const msg = (o.metadata as Record<string, unknown>)?.message
      if (o.level === 'ERROR' || (typeof msg === 'string' && /error|fail|exception/i.test(msg))) {
        errorsByTrace.set(o.traceId, typeof msg === 'string' ? msg.slice(0, 200) : o.level)
      }
    }

    const results: Array<{
      id: string; name: string; timestamp: string; input: string
      latency: number; totalCost: number; observationCount: number
      hasError: boolean; errorMessage?: string
    }> = []

    for (const t of traces.data) {
      const hasError = errorsByTrace.has(t.id)
      if (opts.errorsOnly && !hasError) continue

      const inputStr = typeof t.input === 'string' ? t.input.slice(0, 120) : JSON.stringify(t.input)?.slice(0, 120) || ''
      results.push({
        id: t.id,
        name: t.name,
        timestamp: t.timestamp,
        input: inputStr,
        latency: t.latency,
        totalCost: t.totalCost,
        observationCount: t.observations.length,
        hasError,
        errorMessage: errorsByTrace.get(t.id),
      })
    }

    return results
  }

  // ---- high-level: latency distribution ---------------------------------

  async latencyDistribution(from: string, to: string): Promise<{
    observations: Array<{ name: string; count: number; avgLatency: number; p95Latency: number; totalTokens: number; totalCost: number }>
  }> {
    const obs = await this.observations({ fromStartTime: from, toStartTime: to, limit: 100 })
    const byName = new Map<string, { latencies: number[]; tokens: number; cost: number }>()

    for (const o of obs.data) {
      let entry = byName.get(o.name)
      if (!entry) {
        entry = { latencies: [], tokens: 0, cost: 0 }
        byName.set(o.name, entry)
      }
      if (o.latency > 0) entry.latencies.push(o.latency)
      entry.tokens += o.totalTokens
      entry.cost += o.calculatedTotalCost ?? 0
    }

    const observations = Array.from(byName.entries()).map(([name, data]) => {
      const sorted = data.latencies.sort((a, b) => a - b)
      return {
        name,
        count: sorted.length,
        avgLatency: sorted.length > 0 ? sorted.reduce((s, l) => s + l, 0) / sorted.length : 0,
        p95Latency: sorted.length > 0 ? sorted[Math.floor(sorted.length * 0.95)] ?? sorted[sorted.length - 1] : 0,
        totalTokens: data.tokens,
        totalCost: data.cost,
      }
    }).sort((a, b) => b.count - a.count)

    return { observations }
  }
}
