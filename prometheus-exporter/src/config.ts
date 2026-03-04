import { readFileSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from 'yaml'

export interface ExporterConfig {
  port: number
  gatewayUrl: string
  gatewaySecretKey: string
  collectTimeoutMs: number
}

interface ConfigYaml {
  port?: number
  gatewayUrl?: string
  gatewaySecretKey?: string
  collectTimeoutMs?: number
}

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

function loadYaml(): ConfigYaml {
  const configPath = resolve(__dirname, '..', 'config.yaml')
  if (!existsSync(configPath)) return {}
  return (parse(readFileSync(configPath, 'utf-8')) as ConfigYaml) || {}
}

function required(name: string, yamlVal: unknown, envVar: string): string {
  const val = process.env[envVar] || (yamlVal != null ? String(yamlVal) : '')
  if (!val) {
    throw new Error(
      `Missing required config: set "${name}" in config.yaml or "${envVar}" env var`
    )
  }
  return val
}

export function loadConfig(): ExporterConfig {
  const yaml = loadYaml()

  return {
    port: parseInt(process.env.EXPORTER_PORT || String(yaml.port ?? 9091), 10),
    gatewayUrl: required('gatewayUrl', yaml.gatewayUrl, 'GATEWAY_URL').replace(/\/$/, ''),
    gatewaySecretKey: required('gatewaySecretKey', yaml.gatewaySecretKey, 'GATEWAY_SECRET_KEY'),
    collectTimeoutMs: parseInt(
      process.env.COLLECT_TIMEOUT_MS || String(yaml.collectTimeoutMs ?? 5000), 10
    ),
  }
}
