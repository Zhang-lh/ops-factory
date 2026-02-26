import { useState, useCallback, useRef, useEffect } from 'react'

const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL || 'http://127.0.0.1:3000'
const GATEWAY_SECRET_KEY = import.meta.env.VITE_GATEWAY_SECRET_KEY || 'test'

// ---- Types matching gateway /monitoring/* responses ----

export interface MonitoringStatus {
  enabled: boolean
  reachable?: boolean
  host?: string
}

export interface DailyPoint {
  date: string
  traces: number
  observations: number
  cost: number
}

export interface OverviewData {
  totalTraces: number
  totalObservations: number
  totalCost: number
  avgLatency: number
  p95Latency: number
  errorCount: number
  daily: DailyPoint[]
}

export interface TraceRow {
  id: string
  name: string
  timestamp: string
  input: string
  latency: number
  totalCost: number
  observationCount: number
  hasError: boolean
  errorMessage?: string
}

export interface ObservationGroup {
  name: string
  count: number
  avgLatency: number
  p95Latency: number
  totalTokens: number
  totalCost: number
}

export type TimeRange = '1h' | '24h' | '7d' | '30d'

function rangeToISO(range: TimeRange): { from: string; to: string } {
  const to = new Date().toISOString()
  const ms: Record<TimeRange, number> = {
    '1h': 3600_000,
    '24h': 86400_000,
    '7d': 7 * 86400_000,
    '30d': 30 * 86400_000,
  }
  const from = new Date(Date.now() - ms[range]).toISOString()
  return { from, to }
}

async function gw<T>(path: string, params?: Record<string, string>): Promise<T> {
  const url = new URL(`${GATEWAY_URL}${path}`)
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      if (v !== undefined && v !== '') url.searchParams.set(k, v)
    }
  }
  const res = await fetch(url.toString(), {
    headers: { 'x-secret-key': GATEWAY_SECRET_KEY },
    signal: AbortSignal.timeout(15_000),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`HTTP ${res.status}: ${text}`)
  }
  return res.json() as Promise<T>
}

export interface AgentInfo {
  id: string
  name: string
  status: string
  provider: string
  model: string
  skills: string[]
}

export interface UseMonitoringResult {
  status: MonitoringStatus | null
  overview: OverviewData | null
  traces: TraceRow[]
  observations: { observations: ObservationGroup[] } | null
  agents: AgentInfo[]
  isLoading: boolean
  error: string | null
  range: TimeRange
  setRange: (r: TimeRange) => void
  refresh: () => void
}

export function useMonitoring(): UseMonitoringResult {
  const [status, setStatus] = useState<MonitoringStatus | null>(null)
  const [overview, setOverview] = useState<OverviewData | null>(null)
  const [traces, setTraces] = useState<TraceRow[]>([])
  const [observations, setObservations] = useState<{ observations: ObservationGroup[] } | null>(null)
  const [agents, setAgents] = useState<AgentInfo[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [range, setRangeState] = useState<TimeRange>('24h')
  const fetchIdRef = useRef(0)

  const load = useCallback(async (r: TimeRange) => {
    const id = ++fetchIdRef.current
    setIsLoading(true)
    setError(null)

    try {
      // Step 1: check status
      const st = await gw<MonitoringStatus>('/monitoring/status')
      if (id !== fetchIdRef.current) return
      setStatus(st)

      if (!st.enabled || !st.reachable) {
        setIsLoading(false)
        return
      }

      // Step 2: fetch all data in parallel
      const { from, to } = rangeToISO(r)
      const params = { from, to }

      const [ov, tr, obs, ag] = await Promise.all([
        gw<OverviewData>('/monitoring/overview', params),
        gw<TraceRow[]>('/monitoring/traces', { ...params, limit: '30' }),
        gw<{ observations: ObservationGroup[] }>('/monitoring/observations', params),
        gw<{ agents: AgentInfo[] }>('/agents'),
      ])

      if (id !== fetchIdRef.current) return
      setOverview(ov)
      setTraces(tr)
      setObservations(obs)
      setAgents(ag.agents || [])
    } catch (err) {
      if (id !== fetchIdRef.current) return
      setError(err instanceof Error ? err.message : 'Failed to fetch monitoring data')
    } finally {
      if (id === fetchIdRef.current) setIsLoading(false)
    }
  }, [])

  // Keep a ref so refresh is stable (doesn't change when range changes)
  const rangeRef = useRef(range)
  rangeRef.current = range

  const setRange = useCallback((r: TimeRange) => {
    setRangeState(r)
    load(r)
  }, [load])

  const refresh = useCallback(() => {
    load(rangeRef.current)
  }, [load])

  // Initial load on mount
  useEffect(() => { load('24h') }, [load])

  return { status, overview, traces, observations, agents, isLoading, error, range, setRange, refresh }
}
