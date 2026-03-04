import { readFileSync, existsSync } from 'node:fs'
import { join, resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from 'yaml'

export interface AgentConfig {
  id: string
  name: string
  host: string
  secret_key: string
}

export interface GatewayYamlConfig {
  agents: Array<{
    id: string
    name: string
  }>
}

export interface OfficePreviewConfig {
  enabled: boolean
  onlyofficeUrl: string
  fileBaseUrl: string
}

export interface VisionGlobalConfig {
  mode: string            // 'off' | 'passthrough' | 'preprocess'
  provider: string
  model: string
  apiKey: string
  baseUrl: string
  maxTokens: number
  prompt: string
}

export interface UploadConfig {
  maxFileSizeMb: number
  maxImageSizeMb: number
  retentionHours: number
}

export interface LangfuseConfig {
  host: string
  publicKey: string
  secretKey: string
}

export interface TlsConfig {
  enabled: boolean
  cert: string
  key: string
}

export interface GatewayConfig {
  host: string
  port: number
  secretKey: string
  corsOrigin: string
  tls: TlsConfig
  projectRoot: string
  agentsDir: string
  usersDir: string
  goosedBin: string
  agents: AgentConfig[]
  officePreview: OfficePreviewConfig
  idleTimeoutMs: number
  idleCheckIntervalMs: number
  upload: UploadConfig
  vision: VisionGlobalConfig
  langfuse: LangfuseConfig | null
}

// --- config.yaml type ---
interface ConfigYaml {
  server?: {
    host?: string
    port?: number
    secretKey?: string
    corsOrigin?: string
  }
  tls?: {
    cert?: string
    key?: string
  }
  paths?: {
    projectRoot?: string
    agentsDir?: string
    usersDir?: string
    goosedBin?: string
  }
  idle?: {
    timeoutMinutes?: number
    checkIntervalMs?: number
  }
  upload?: {
    maxFileSizeMb?: number
    maxImageSizeMb?: number
    retentionHours?: number
  }
  officePreview?: {
    enabled?: boolean
    onlyofficeUrl?: string
    fileBaseUrl?: string
  }
  vision?: {
    mode?: string
    provider?: string
    model?: string
    apiKey?: string
    baseUrl?: string
    maxTokens?: number
    prompt?: string
  }
  langfuse?: {
    host?: string
    publicKey?: string
    secretKey?: string
  }
}

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

/**
 * Load a YAML file and return the parsed object, or an empty object if not found.
 */
function loadYamlFile<T>(filePath: string): T {
  if (!existsSync(filePath)) return {} as T
  return (parse(readFileSync(filePath, 'utf-8')) as T) || ({} as T)
}

export function loadGatewayConfig(): GatewayConfig {
  // Load gateway config.yaml (one level up from src/)
  const configYamlPath = resolve(__dirname, '..', 'config.yaml')
  const cfg = loadYamlFile<ConfigYaml>(configYamlPath)

  // --- Server ---
  const host = process.env.GATEWAY_HOST || cfg.server?.host || '0.0.0.0'
  const port = parseInt(process.env.GATEWAY_PORT || String(cfg.server?.port ?? 3000), 10)
  const corsOrigin = process.env.CORS_ORIGIN || cfg.server?.corsOrigin || '*'

  // secretKey: REQUIRED — env var > config.yaml > error
  const secretKey = process.env.GATEWAY_SECRET_KEY || cfg.server?.secretKey || ''
  if (!secretKey) {
    throw new Error(
      'Missing required config: set "server.secretKey" in gateway/config.yaml or GATEWAY_SECRET_KEY env var'
    )
  }

  // --- TLS ---
  const tlsCert = process.env.TLS_CERT || cfg.tls?.cert || ''
  const tlsKey = process.env.TLS_KEY || cfg.tls?.key || ''
  const tls: TlsConfig = {
    enabled: !!(tlsCert && tlsKey),
    cert: tlsCert,
    key: tlsKey,
  }

  // --- Paths ---
  const projectRoot = resolve(
    process.env.PROJECT_ROOT || cfg.paths?.projectRoot || join(__dirname, '../..')
  )
  const agentsDir = resolve(
    process.env.AGENTS_DIR || cfg.paths?.agentsDir || join(projectRoot, 'gateway', 'agents')
  )
  const usersDir = resolve(
    process.env.USERS_DIR || cfg.paths?.usersDir || join(projectRoot, 'gateway', 'users')
  )
  const goosedBin = process.env.GOOSED_BIN || cfg.paths?.goosedBin || 'goosed'

  // --- Load agents registry (separate file, unchanged) ---
  const gatewayConfigDir = resolve(__dirname, '../config')
  const agentsConfigPath = join(gatewayConfigDir, 'agents.yaml')

  let agentsYaml: GatewayYamlConfig = { agents: [] }
  if (existsSync(agentsConfigPath)) {
    agentsYaml = parse(readFileSync(agentsConfigPath, 'utf-8')) as GatewayYamlConfig
  } else {
    console.warn(`Warning: Gateway agents config not found at ${agentsConfigPath}`)
  }

  const agents: AgentConfig[] = (agentsYaml.agents || []).map(agent => ({
    id: agent.id,
    name: agent.name,
    host,
    secret_key: secretKey,
  }))

  // --- Office Preview ---
  const yamlOp = cfg.officePreview || {}
  const officePreview: OfficePreviewConfig = {
    enabled: process.env.OFFICE_PREVIEW_ENABLED
      ? process.env.OFFICE_PREVIEW_ENABLED === 'true'
      : yamlOp.enabled ?? false,
    onlyofficeUrl: process.env.ONLYOFFICE_URL || yamlOp.onlyofficeUrl || 'http://localhost:8080',
    fileBaseUrl: process.env.ONLYOFFICE_FILE_BASE_URL || yamlOp.fileBaseUrl || `http://host.docker.internal:${port}`,
  }

  // --- Idle ---
  const idleTimeoutMinutes = cfg.idle?.timeoutMinutes ?? 15
  const idleTimeoutMs = parseInt(
    process.env.IDLE_TIMEOUT_MS || String(idleTimeoutMinutes * 60 * 1000), 10
  )
  const idleCheckIntervalMs = parseInt(
    process.env.IDLE_CHECK_INTERVAL_MS || String(cfg.idle?.checkIntervalMs ?? 60000), 10
  )

  // --- Upload ---
  const upload: UploadConfig = {
    maxFileSizeMb: parseInt(
      process.env.MAX_UPLOAD_FILE_SIZE_MB || String(cfg.upload?.maxFileSizeMb ?? 10), 10
    ),
    maxImageSizeMb: parseInt(
      process.env.MAX_UPLOAD_IMAGE_SIZE_MB || String(cfg.upload?.maxImageSizeMb ?? 5), 10
    ),
    retentionHours: parseInt(
      process.env.UPLOAD_RETENTION_HOURS || String(cfg.upload?.retentionHours ?? 24), 10
    ),
  }

  // --- Vision ---
  const DEFAULT_VISION_PROMPT = `Analyze this image thoroughly. Describe:
- Main content and subject matter
- Any text, numbers, or data visible
- Charts, tables, or diagrams if present
- Layout and structural elements
- Any relevant details that would help answer questions about this image
Be precise and factual.`

  const vision: VisionGlobalConfig = {
    mode: process.env.VISION_MODE || cfg.vision?.mode || 'passthrough',
    provider: process.env.VISION_PROVIDER || process.env.GOOSE_PROVIDER || cfg.vision?.provider || '',
    model: process.env.VISION_MODEL || process.env.GOOSE_MODEL || cfg.vision?.model || '',
    apiKey: process.env.VISION_API_KEY || process.env.OPENAI_API_KEY || process.env.LITELLM_API_KEY || cfg.vision?.apiKey || '',
    baseUrl: process.env.VISION_BASE_URL || process.env.OPENAI_HOST || process.env.LITELLM_HOST || cfg.vision?.baseUrl || '',
    maxTokens: parseInt(
      process.env.VISION_MAX_TOKENS || String(cfg.vision?.maxTokens ?? 1024), 10
    ),
    prompt: process.env.VISION_PROMPT || cfg.vision?.prompt || DEFAULT_VISION_PROMPT,
  }

  // --- Langfuse ---
  // Priority: env var > config.yaml > auto-detect from agent configs
  let langfuse: LangfuseConfig | null = null
  {
    let lfHost = process.env.LANGFUSE_HOST || cfg.langfuse?.host || ''
    let lfPub  = process.env.LANGFUSE_PUBLIC_KEY || cfg.langfuse?.publicKey || ''
    let lfSec  = process.env.LANGFUSE_SECRET_KEY || cfg.langfuse?.secretKey || ''

    if (!lfHost || !lfPub || !lfSec) {
      // Auto-detect from agent configs
      for (const agent of agentsYaml.agents || []) {
        const cfgPath = join(agentsDir, agent.id, 'config', 'config.yaml')
        if (!existsSync(cfgPath)) continue
        try {
          const agentCfg = parse(readFileSync(cfgPath, 'utf-8')) as Record<string, unknown>
          if (!lfHost && agentCfg.LANGFUSE_URL) lfHost = String(agentCfg.LANGFUSE_URL)
          if (!lfPub && agentCfg.LANGFUSE_INIT_PROJECT_PUBLIC_KEY) lfPub = String(agentCfg.LANGFUSE_INIT_PROJECT_PUBLIC_KEY)
          if (!lfSec && agentCfg.LANGFUSE_INIT_PROJECT_SECRET_KEY) lfSec = String(agentCfg.LANGFUSE_INIT_PROJECT_SECRET_KEY)
          if (lfHost && lfPub && lfSec) break
        } catch { /* skip */ }
      }
    }

    if (lfHost && lfPub && lfSec) {
      langfuse = { host: lfHost.replace(/\/+$/, ''), publicKey: lfPub, secretKey: lfSec }
    }
  }

  return {
    host,
    port,
    secretKey,
    corsOrigin,
    tls,
    projectRoot,
    agentsDir,
    usersDir,
    goosedBin,
    agents,
    officePreview,
    idleTimeoutMs,
    idleCheckIntervalMs,
    upload,
    vision,
    langfuse,
  }
}
