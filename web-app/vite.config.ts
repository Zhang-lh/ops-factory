import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import { readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { parse } from 'yaml'

interface ConfigYaml {
    gatewayUrl?: string
    gatewaySecretKey?: string
    port?: number
}

function loadYamlConfig(): ConfigYaml {
    const configPath = resolve(process.cwd(), 'config.yaml')
    if (!existsSync(configPath)) return {}
    return (parse(readFileSync(configPath, 'utf-8')) as ConfigYaml) || {}
}

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '')
    const yaml = loadYamlConfig()

    // Priority: env var > config.yaml > error (required fields)
    const gatewayUrl = env.GATEWAY_URL || yaml.gatewayUrl
    const gatewaySecretKey = env.GATEWAY_SECRET_KEY || yaml.gatewaySecretKey
    const port = parseInt(env.VITE_PORT || String(yaml.port ?? 5173), 10)

    const missing: string[] = []
    if (!gatewayUrl) missing.push('gatewayUrl (config.yaml) or GATEWAY_URL (env)')
    if (!gatewaySecretKey) missing.push('gatewaySecretKey (config.yaml) or GATEWAY_SECRET_KEY (env)')

    if (missing.length > 0) {
        console.error('\n Missing required configuration:\n')
        missing.forEach(key => console.error(`   - ${key}`))
        console.error('\n Create config.yaml in web-app/ or set environment variables.\n')
        process.exit(1)
    }

    return {
        plugins: [react()],
        define: {
            'import.meta.env.VITE_GATEWAY_URL': JSON.stringify(gatewayUrl),
            'import.meta.env.VITE_GATEWAY_SECRET_KEY': JSON.stringify(gatewaySecretKey),
        },
        server: {
            port,
        },
    }
})
