import { execFile } from 'node:child_process'
import { access, constants, readFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import YAML from 'yaml'

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..', '..', '..')
const OI_DIR = join(PROJECT_ROOT, 'operation-intelligence')

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

describe('operation-intelligence basic smoke checks', () => {
  const readableFiles = [
    'operation-intelligence/config.yaml',
    'operation-intelligence/config.yaml.example',
    'operation-intelligence/pom.xml',
  ]

  for (const relativePath of readableFiles) {
    it(`${relativePath} exists`, async () => {
      await expect(
        access(join(PROJECT_ROOT, relativePath), constants.R_OK),
      ).resolves.toBeUndefined()
    })
  }

  it('operation-intelligence/scripts/ctl.sh passes bash -n', async () => {
    const fullPath = join(OI_DIR, 'scripts', 'ctl.sh')
    const { code, stderr } = await run('bash', ['-n', fullPath])
    expect(code).toBe(0)
    expect(stderr).toBe('')
  })

  it('config.yaml.example has required top-level keys', async () => {
    const content = await readFile(join(OI_DIR, 'config.yaml.example'), 'utf-8')
    const config = YAML.parse(content)

    expect(config.server.port).toBe(8096)
    expect(config['operation-intelligence']).toBeDefined()
    expect(config['operation-intelligence']['secret-key']).toBe('')
    expect(config['operation-intelligence']['cors-origin']).toBe('*')
  })

  it('config.yaml.example declares QoS section with expected defaults', async () => {
    const content = await readFile(join(OI_DIR, 'config.yaml.example'), 'utf-8')
    const config = YAML.parse(content)
    const qos = config['operation-intelligence'].qos

    expect(qos.enabled).toBe(true)
    expect(qos.weights.availability).toBe(0.4)
    expect(qos.weights.performance).toBe(0.4)
    expect(qos.weights.resource).toBe(0.2)
  })

  it('config.yaml.example retention defaults are valid positive numbers', async () => {
    const content = await readFile(join(OI_DIR, 'config.yaml.example'), 'utf-8')
    const config = YAML.parse(content)
    const qos = config['operation-intelligence'].qos

    expect(qos['raw-data-retention-days']).toBeGreaterThan(0)
    expect(qos['detail-data-retention-days']).toBeGreaterThan(0)
    expect(qos['normalize-data-retention-days']).toBeGreaterThan(0)
  })

  it('config.yaml.example weights sum to 1.0', async () => {
    const content = await readFile(join(OI_DIR, 'config.yaml.example'), 'utf-8')
    const config = YAML.parse(content)
    const { availability, performance, resource } = config['operation-intelligence'].qos.weights

    expect(availability + performance + resource).toBeCloseTo(1.0, 6)
  })

  it('config.yaml.example secret-key is empty by default', async () => {
    const content = await readFile(join(OI_DIR, 'config.yaml.example'), 'utf-8')
    const config = YAML.parse(content)

    expect(config['operation-intelligence']['secret-key']).toBe('')
  })

  it('ctl.sh health check uses the public actuator endpoint', async () => {
    const content = await readFile(join(OI_DIR, 'scripts', 'ctl.sh'), 'utf-8')
    expect(content).toContain('/actuator/health')
    expect(content).toContain('wait_http_ok "operation-intelligence"')
  })

  it('root orchestrator documents the operation-intelligence toggle', async () => {
    const content = await readFile(join(PROJECT_ROOT, 'scripts', 'ctl.sh'), 'utf-8')
    expect(content).toContain('ENABLE_OPERATION_INTELLIGENCE')
    expect(content).toContain('operation-intelligence')
  })
})
