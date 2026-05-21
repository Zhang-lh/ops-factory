import { trackedFetch } from '../app/platform/logging/requestClient'
import { configureWebappLogging, type WebappLoggingRuntimeConfig } from '../app/platform/logging/settings'

interface RuntimeConfig {
    gatewayUrl?: string
    gatewaySecretKey?: string
    controlCenterUrl?: string
    controlCenterSecretKey?: string
    knowledgeServiceUrl?: string
    businessIntelligenceServiceUrl?: string
    skillMarketServiceUrl?: string
    operationIntelligenceServiceUrl?: string
    operationIntelligenceSecretKey?: string
    logging?: {
        level?: WebappLoggingRuntimeConfig['level']
        consoleEnabled?: boolean
        bufferSize?: number
        sink?: 'console'
        logDirectory?: string | null
    }
}

const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '::1'])

function isLoopbackHost(host: string): boolean {
    return LOOPBACK_HOSTS.has(host)
}

interface ServiceEndpoint {
    pathPrefix: string
    fallbackPort: number
}

const SERVICE_ENDPOINTS: Record<string, ServiceEndpoint> = {
    gateway:                  { pathPrefix: '/gateway',                 fallbackPort: 3000 },
    knowledge:                { pathPrefix: '/knowledge',               fallbackPort: 8092 },
    controlCenter:            { pathPrefix: '/control-center',          fallbackPort: 8094 },
    businessIntelligence:     { pathPrefix: '/business-intelligence',   fallbackPort: 8093 },
    skillMarket:              { pathPrefix: '/skill-market',            fallbackPort: 8095 },
    operationIntelligence:    { pathPrefix: '/operation-intelligence',  fallbackPort: 8096 },
}

function resolveServiceUrl(raw: string | undefined, endpoint: ServiceEndpoint): string {
    const pageHost = window.location.hostname || '127.0.0.1'
    const pageProtocol = window.location.protocol || 'http:'
    const fallbackOrigin = `${pageProtocol}//${pageHost}:${endpoint.fallbackPort}`

    if (!raw) return endpoint.pathPrefix

    try {
        const url = new URL(raw)
        if (isLoopbackHost(url.hostname) && url.hostname !== pageHost) {
            url.hostname = pageHost
        }
        return `${url.origin}${endpoint.pathPrefix}`
    } catch {
        return `${fallbackOrigin}${endpoint.pathPrefix}`
    }
}

export const runtime = {
    GATEWAY_URL: resolveServiceUrl(undefined, SERVICE_ENDPOINTS.gateway),
    GATEWAY_SECRET_KEY: '',
    CONTROL_CENTER_URL: resolveServiceUrl(undefined, SERVICE_ENDPOINTS.controlCenter),
    CONTROL_CENTER_SECRET_KEY: '',
    KNOWLEDGE_SERVICE_URL: resolveServiceUrl(undefined, SERVICE_ENDPOINTS.knowledge),
    BUSINESS_INTELLIGENCE_SERVICE_URL: resolveServiceUrl(undefined, SERVICE_ENDPOINTS.businessIntelligence),
    SKILL_MARKET_SERVICE_URL: resolveServiceUrl(undefined, SERVICE_ENDPOINTS.skillMarket),
    OPERATION_INTELLIGENCE_SERVICE_URL: resolveServiceUrl(undefined, SERVICE_ENDPOINTS.operationIntelligence),
    OPERATION_INTELLIGENCE_SECRET_KEY: '',
}

function setRuntimeConfig(config: RuntimeConfig): void {
    runtime.GATEWAY_URL = resolveServiceUrl(config.gatewayUrl, SERVICE_ENDPOINTS.gateway)
    runtime.GATEWAY_SECRET_KEY = config.gatewaySecretKey ?? ''
    runtime.CONTROL_CENTER_URL = resolveServiceUrl(config.controlCenterUrl, SERVICE_ENDPOINTS.controlCenter)
    runtime.CONTROL_CENTER_SECRET_KEY = config.controlCenterSecretKey ?? ''
    runtime.KNOWLEDGE_SERVICE_URL = resolveServiceUrl(config.knowledgeServiceUrl, SERVICE_ENDPOINTS.knowledge)
    runtime.BUSINESS_INTELLIGENCE_SERVICE_URL = resolveServiceUrl(config.businessIntelligenceServiceUrl, SERVICE_ENDPOINTS.businessIntelligence)
    runtime.SKILL_MARKET_SERVICE_URL = resolveServiceUrl(config.skillMarketServiceUrl, SERVICE_ENDPOINTS.skillMarket)
    runtime.OPERATION_INTELLIGENCE_SERVICE_URL = resolveServiceUrl(config.operationIntelligenceServiceUrl, SERVICE_ENDPOINTS.operationIntelligence)
    runtime.OPERATION_INTELLIGENCE_SECRET_KEY = config.operationIntelligenceSecretKey ?? ''
    configureWebappLogging(config.logging)
}

async function loadRuntimeConfig(): Promise<RuntimeConfig> {
    // Resolve config.json relative to the HTML page directory at runtime,
    // so it works regardless of deployment path (e.g. /adc-static/.../dist/)
    const baseDir = window.location.pathname.replace(/[^/]*$/, '')
    const configUrl = baseDir + 'config.json'
    try {
        const response = await trackedFetch(configUrl, {
            cache: 'no-store',
            category: 'app',
            name: 'app.context_init',
        })
        if (!response.ok) {
            throw new Error(`Failed to load ${configUrl} (HTTP ${response.status}). Check that config.json is deployed alongside index.html.`)
        }
        return (await response.json()) as RuntimeConfig
    } catch (error) {
        if (error instanceof Error && error.message.startsWith('Failed to load')) {
            throw error
        }
        throw new Error(`Failed to load ${configUrl}: ${error instanceof Error ? error.message : String(error)}. Check that config.json is deployed alongside index.html.`)
    }
}

export async function initializeRuntimeConfig(): Promise<void> {
    let config = await loadRuntimeConfig()

    // When deployed behind a reverse proxy (e.g. GDE), add ?proxy=true to the
    // page URL so all service URLs resolve to relative paths (/gateway, etc.)
    // instead of absolute origins. This allows a single build to work both
    // standalone (config.json URLs) and behind a proxy (relative paths).
    const useProxy = /[?&]proxy=true(&|$)/i.test(window.location.search)
            || /[?&]proxy=true(&|$)/i.test(window.location.hash)
    if (useProxy) {
        config = {
            ...config,
            gatewayUrl: '',
            controlCenterUrl: '',
            knowledgeServiceUrl: '',
            businessIntelligenceServiceUrl: '',
            skillMarketServiceUrl: '',
            operationIntelligenceServiceUrl: '',
        }
    }

    setRuntimeConfig(config)

    // Verify gateway connectivity — wrong URL or secret key will surface here
    // instead of failing silently on every API call later.
    try {
        const healthUrl = `${runtime.GATEWAY_URL}/status`
        const res = await trackedFetch(healthUrl, {
            headers: { 'x-secret-key': runtime.GATEWAY_SECRET_KEY },
            cache: 'no-store',
            category: 'app',
            name: 'app.gateway_health_check',
        })
        if (!res.ok) {
            throw new Error(`Gateway health check failed (HTTP ${res.status}) at ${healthUrl}. Check gatewayUrl and gatewaySecretKey in config.json.`)
        }
    } catch (error) {
        if (error instanceof Error && error.message.startsWith('Gateway health check')) {
            throw error
        }
        throw new Error(`Cannot reach gateway at ${runtime.GATEWAY_URL}: ${error instanceof Error ? error.message : String(error)}. Check gatewayUrl in config.json.`)
    }
}

/** Build gateway request headers with secret key and optional user ID. */
export function gatewayHeaders(userId?: string | null): Record<string, string> {
    const h: Record<string, string> = {
        'Content-Type': 'application/json',
        'x-secret-key': runtime.GATEWAY_SECRET_KEY,
    }
    if (userId) h['x-user-id'] = userId
    return h
}

export function controlCenterHeaders(): Record<string, string> {
    return {
        'Content-Type': 'application/json',
        'x-secret-key': runtime.CONTROL_CENTER_SECRET_KEY,
    }
}

/** Convert a display name to a kebab-case ID. */
export function slugify(value: string): string {
    return value
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
}

/** Check if a session is a scheduled session. */
export function isScheduledSession(session: { session_type?: string; schedule_id?: string | null }): boolean {
    return session.session_type === 'scheduled' || !!session.schedule_id
}
