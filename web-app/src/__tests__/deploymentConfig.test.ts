import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const ROOT = resolve(process.cwd())

function read(path: string): string {
    return readFileSync(resolve(ROOT, path), 'utf-8')
}

function readJson(path: string): Record<string, unknown> {
    return JSON.parse(read(path)) as Record<string, unknown>
}

// =============================================================================
// 1. Standalone config template
// =============================================================================
describe('config.standalone.json.example', () => {
    const config = readJson('config.standalone.json.example')

    it('has explicit gatewayUrl', () => {
        expect(config.gatewayUrl).toBe('http://127.0.0.1:3000')
    })

    it('has explicit controlCenterUrl', () => {
        expect(config.controlCenterUrl).toBe('http://127.0.0.1:8094')
    })

    it('has explicit knowledgeServiceUrl', () => {
        expect(config.knowledgeServiceUrl).toBe('http://127.0.0.1:8092')
    })

    it('has explicit businessIntelligenceServiceUrl', () => {
        expect(config.businessIntelligenceServiceUrl).toBe('http://127.0.0.1:8093')
    })

    it('has explicit skillMarketServiceUrl', () => {
        expect(config.skillMarketServiceUrl).toBe('http://127.0.0.1:8095')
    })

    it('has explicit operationIntelligenceServiceUrl', () => {
        expect(config.operationIntelligenceServiceUrl).toBe('http://127.0.0.1:8096')
    })

    it('has secret keys configured', () => {
        expect(config.gatewaySecretKey).toBeTruthy()
        expect(config.controlCenterSecretKey).toBeTruthy()
        expect(config.operationIntelligenceSecretKey).toBeTruthy()
    })

    it('has logging config', () => {
        const logging = config.logging as Record<string, unknown>
        expect(logging.level).toBeTruthy()
        expect(typeof logging.consoleEnabled).toBe('boolean')
    })
})

// =============================================================================
// 2. Embed config template
// =============================================================================
describe('config.embed.json.example', () => {
    const config = readJson('config.embed.json.example')

    it('has empty gatewayUrl for reverse proxy', () => {
        expect(config.gatewayUrl).toBe('')
    })

    it('has empty controlCenterUrl', () => {
        expect(config.controlCenterUrl).toBe('')
    })

    it('has empty knowledgeServiceUrl', () => {
        expect(config.knowledgeServiceUrl).toBe('')
    })

    it('has empty businessIntelligenceServiceUrl', () => {
        expect(config.businessIntelligenceServiceUrl).toBe('')
    })

    it('has empty skillMarketServiceUrl', () => {
        expect(config.skillMarketServiceUrl).toBe('')
    })

    it('has empty operationIntelligenceServiceUrl', () => {
        expect(config.operationIntelligenceServiceUrl).toBe('')
    })

    it('still has secret keys configured', () => {
        expect(config.gatewaySecretKey).toBeTruthy()
        expect(config.controlCenterSecretKey).toBeTruthy()
        expect(config.operationIntelligenceSecretKey).toBeTruthy()
    })

    it('has same logging config structure as standalone', () => {
        const embedLogging = config.logging as Record<string, unknown>
        const standalone = readJson('config.standalone.json.example')
        const standaloneLogging = standalone.logging as Record<string, unknown>
        expect(embedLogging.level).toBe(standaloneLogging.level)
    })
})

// =============================================================================
// 3. Both templates share same keys
// =============================================================================
describe('config template key parity', () => {
    const standalone = readJson('config.standalone.json.example')
    const embed = readJson('config.embed.json.example')

    it('both have the same top-level keys', () => {
        const standaloneKeys = Object.keys(standalone).sort()
        const embedKeys = Object.keys(embed).sort()
        expect(embedKeys).toEqual(standaloneKeys)
    })

    it('non-URL fields have identical values', () => {
        expect(embed.gatewaySecretKey).toBe(standalone.gatewaySecretKey)
        expect(embed.controlCenterSecretKey).toBe(standalone.controlCenterSecretKey)
        expect(embed.operationIntelligenceSecretKey).toBe(standalone.operationIntelligenceSecretKey)
        expect(embed.port).toBe(standalone.port)
    })
})

// =============================================================================
// 4. runtime.ts health check performs gateway connectivity check
// =============================================================================
describe('runtime.ts health check', () => {
    const source = read('src/config/runtime.ts')

    it('performs a health check to /status endpoint', () => {
        expect(source).toContain('/status')
    })

    it('checks res.ok and throws on failure', () => {
        expect(source).toContain('Gateway health check failed')
    })

    it('handles network errors gracefully', () => {
        expect(source).toContain('Cannot reach gateway')
    })
})

// =============================================================================
// 5. vite.config.ts proxy rules
// =============================================================================
describe('vite.config.ts proxy rules', () => {
    const source = read('vite.config.ts')

    it('proxies /gateway to gateway service', () => {
        expect(source).toMatch(/['"]\/gateway['"]\s*:\s*['"]http:\/\/127\.0\.0\.1:3000['"]/)
    })

    it('proxies /skill-market to skill market service', () => {
        expect(source).toMatch(/['"]\/skill-market['"]\s*:\s*['"]http:\/\/127\.0\.0\.1:8095['"]/)
    })

    it('proxies /operation-intelligence to OI service', () => {
        expect(source).toMatch(/['"]\/operation-intelligence['"]\s*:\s*['"]http:\/\/127\.0\.0\.1:8096['"]/)
    })

    it('proxies /knowledge to knowledge service', () => {
        expect(source).toMatch(/['"]\/knowledge['"]\s*:\s*['"]http:\/\/127\.0\.0\.1:8092['"]/)
    })

    it('proxies /control-center to control center service', () => {
        expect(source).toMatch(/['"]\/control-center['"]\s*:\s*['"]http:\/\/127\.0\.0\.1:8094['"]/)
    })

    it('proxies /business-intelligence to BI service', () => {
        expect(source).toMatch(/['"]\/business-intelligence['"]\s*:\s*['"]http:\/\/127\.0\.0\.1:8093['"]/)
    })
})

// =============================================================================
// 6. Build output includes all config files
// =============================================================================
describe('build output config files', () => {
    it('dist/config.json exists', () => {
        const config = readJson('../web-app/dist/config.json')
        expect(config.gatewayUrl).toBeDefined()
    })

    it('dist/config.standalone.json.example exists with explicit URLs', () => {
        const config = readJson('../web-app/dist/config.standalone.json.example')
        expect(config.gatewayUrl).toMatch(/^http/)
    })

    it('dist/config.embed.json.example exists with empty URLs', () => {
        const config = readJson('../web-app/dist/config.embed.json.example')
        expect(config.gatewayUrl).toBe('')
    })
})
