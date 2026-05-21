import { execFile } from 'node:child_process'
import { access, constants, readFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..', '..', '..')

function run(
  cmd: string,
  args: string[],
  cwd = PROJECT_ROOT,
): Promise<{ stdout: string; stderr: string; code: number }> {
  return new Promise((resolveRun) => {
    execFile(cmd, args, { cwd, timeout: 20_000 }, (err, stdout, stderr) => {
      const code = err && 'code' in err ? Number(err.code) : err ? 1 : 0
      resolveRun({
        stdout: stdout.toString(),
        stderr: stderr.toString(),
        code,
      })
    })
  })
}

describe('repository basic smoke checks', () => {
  const readableFiles = [
    'gateway/config.yaml',
    'gateway/config.yaml.example',
    'web-app/config.json',
    'web-app/config.standalone.json.example',
    'web-app/config.embed.json.example',
    'langfuse/config.yaml',
    'langfuse/config.yaml.example',
    'onlyoffice/config.yaml',
    'onlyoffice/config.yaml.example',
    'prometheus-exporter/config.yaml',
    'prometheus-exporter/config.yaml.example',
    'operation-intelligence/config.yaml',
    'operation-intelligence/config.yaml.example',
    'knowledge-service/pom.xml',
  ]

  for (const relativePath of readableFiles) {
    it(`${relativePath} exists`, async () => {
      await expect(
        access(join(PROJECT_ROOT, relativePath), constants.R_OK),
      ).resolves.toBeUndefined()
    })
  }

  const shellScripts = [
    'scripts/ctl.sh',
    'gateway/scripts/ctl.sh',
    'knowledge-service/scripts/ctl.sh',
    'web-app/scripts/ctl.sh',
    'langfuse/scripts/ctl.sh',
    'onlyoffice/scripts/ctl.sh',
    'prometheus-exporter/scripts/ctl.sh',
    'operation-intelligence/scripts/ctl.sh',
  ]

  for (const relativePath of shellScripts) {
    it(`${relativePath} passes bash -n`, async () => {
      const fullPath = join(PROJECT_ROOT, relativePath)
      const { code, stderr } = await run('bash', ['-n', fullPath])
      expect(code).toBe(0)
      expect(stderr).toBe('')
    })
  }

  it('root orchestrator documents service toggles', async () => {
    const content = await readFile(join(PROJECT_ROOT, 'scripts', 'ctl.sh'), 'utf-8')
    expect(content).toContain('ENABLE_ONLYOFFICE')
    expect(content).toContain('ENABLE_LANGFUSE')
    expect(content).toContain('ENABLE_EXPORTER')
    expect(content).toContain('ENABLE_OPERATION_INTELLIGENCE')
    expect(content).toContain('operation-intelligence')
  })

  it('operation-intelligence startup script health check uses the public actuator endpoint', async () => {
    const content = await readFile(join(PROJECT_ROOT, 'operation-intelligence', 'scripts', 'ctl.sh'), 'utf-8')
    expect(content).toContain('/actuator/health')
    expect(content).toContain('wait_http_ok "operation-intelligence"')
  })

  it('business intelligence reporting presets avoid UTC date serialization shortcuts', async () => {
    const content = await readFile(
      join(
        PROJECT_ROOT,
        'web-app',
        'src',
        'app',
        'modules',
        'business-intelligence',
        'pages',
        'BusinessIntelligencePage.tsx',
      ),
      'utf-8',
    )

    expect(content).not.toMatch(/toISOString\(\)\.split\('T'\)\[0\]/)
  })

  it('docker helper compose files render with defaults when docker is available', async () => {
    const { code: dockerCode } = await run('bash', ['-lc', 'command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1'])
    if (dockerCode !== 0) {
      return
    }

    const langfuse = await run('docker', ['compose', '-f', 'docker-compose.yml', 'config', '-q'], join(PROJECT_ROOT, 'langfuse'))
    expect(langfuse.code).toBe(0)

    const onlyoffice = await run('docker', ['compose', '-f', 'docker-compose.yml', 'config', '-q'], join(PROJECT_ROOT, 'onlyoffice'))
    expect(onlyoffice.code).toBe(0)
  })
})
