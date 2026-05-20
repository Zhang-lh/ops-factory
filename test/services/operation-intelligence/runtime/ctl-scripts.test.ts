import { execFile } from 'node:child_process'
import { resolve, join } from 'node:path'
import { access, constants, readFile } from 'node:fs/promises'
import { describe, it, expect } from 'vitest'

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..', '..', '..')
const OI_DIR = join(PROJECT_ROOT, 'operation-intelligence')

const SCRIPTS = {
  orchestrator: join(PROJECT_ROOT, 'scripts', 'ctl.sh'),
  operationIntelligence: join(OI_DIR, 'scripts', 'ctl.sh'),
} as const

type ScriptName = keyof typeof SCRIPTS

function run(
  cmd: string,
  args: string[],
  env?: Record<string, string>,
): Promise<{ stdout: string; stderr: string; code: number }> {
  return new Promise((resolve) => {
    execFile(
      cmd,
      args,
      {
        cwd: PROJECT_ROOT,
        env: { ...process.env, ...env },
        timeout: 15_000,
      },
      (err, stdout, stderr) => {
        const code = err && 'code' in err ? (err.code as number) : err ? 1 : 0
        resolve({ stdout: stdout.toString(), stderr: stderr.toString(), code })
      },
    )
  })
}

function runCtl(
  name: ScriptName,
  args: string[],
  env?: Record<string, string>,
) {
  return run('bash', [SCRIPTS[name], ...args], env)
}

describe('operation-intelligence ctl.sh', () => {
  it('script exists and is executable', async () => {
    await expect(
      access(SCRIPTS.operationIntelligence, constants.X_OK),
    ).resolves.toBeUndefined()
  })

  it('passes bash -n', async () => {
    const { code, stderr } = await run('bash', ['-n', SCRIPTS.operationIntelligence])
    expect(code).toBe(0)
    expect(stderr).toBe('')
  })

  it('avoids find/head pipelines that trip pipefail during startup', async () => {
    const content = await readFile(SCRIPTS.operationIntelligence, 'utf-8')
    expect(content).not.toMatch(/find[\s\S]*\|\s*head\s+-1/)
  })
})

describe('help output', () => {
  it('--help shows usage with all actions', async () => {
    const { stdout, stderr, code } = await runCtl('operationIntelligence', ['--help'])
    const output = stdout + stderr
    expect(code).toBe(1)
    expect(output).toContain('Usage:')
    expect(output).toContain('startup')
    expect(output).toContain('shutdown')
    expect(output).toContain('status')
    expect(output).toContain('restart')
  })

  it('orchestrator help lists operation-intelligence', async () => {
    const { stdout, stderr } = await runCtl('orchestrator', ['--help'])
    const output = stdout + stderr
    expect(output).toContain('operation-intelligence')
    expect(output).toContain('ENABLE_OPERATION_INTELLIGENCE')
  })
})

describe('unknown action handling', () => {
  it('rejects unknown action', async () => {
    const { stdout, stderr, code } = await runCtl('operationIntelligence', ['bogus'])
    const output = stdout + stderr
    expect(code).not.toBe(0)
    expect(output).toMatch(/[Uu]nknown action|Usage:/)
  })

  it('no args shows usage', async () => {
    const { stdout, stderr, code } = await runCtl('operationIntelligence', [])
    const output = stdout + stderr
    expect(code).toBe(1)
    expect(output).toContain('Usage:')
  })
})

describe('status output', () => {
  it('reports a concrete state', async () => {
    const { stdout, stderr, code } = await runCtl('operationIntelligence', ['status'])
    const output = stdout + stderr
    expect(typeof code).toBe('number')
    expect(output).toMatch(/operation-intelligence|not running|running|FAIL|OK/)
  })
})

describe('shutdown implementation', () => {
  it('defines do_shutdown with daemon_stop and dv_server cleanup', async () => {
    const content = await readFile(SCRIPTS.operationIntelligence, 'utf-8')
    expect(content).toContain('do_shutdown()')
    expect(content).toContain('daemon_stop "${PID_FILE}" "operation-intelligence"')
    expect(content).toContain('stop_dv_server')
  })

  it('shutdown is graceful when service is not running', async () => {
    const { code } = await runCtl('operationIntelligence', ['shutdown'])
    expect(code).toBe(0)
  })
})

describe('startup implementation', () => {
  it('supports --background flag', async () => {
    const { stdout, stderr, code } = await runCtl('operationIntelligence', ['--help', '--background'])
    const output = stdout + stderr
    expect(code).toBe(1)
    expect(output).toContain('Usage:')
  })

  it('defines do_startup with health check', async () => {
    const content = await readFile(SCRIPTS.operationIntelligence, 'utf-8')
    expect(content).toContain('do_startup()')
    expect(content).toContain('build_service')
    expect(content).toContain('wait_http_ok "operation-intelligence"')
    expect(content).toContain('/actuator/health')
  })

  it('uses OI_PORT env var for port override', async () => {
    const content = await readFile(SCRIPTS.operationIntelligence, 'utf-8')
    expect(content).toContain('OI_PORT')
  })

  it('starts dv_server when strict-ssl is false', async () => {
    const content = await readFile(SCRIPTS.operationIntelligence, 'utf-8')
    expect(content).toContain('start_dv_server')
    expect(content).toContain('check_strict_ssl_false')
    expect(content).toContain('dv_server.py')
  })
})

describe('orchestrator integration', () => {
  it('orchestrator status skips operation-intelligence when toggle is off', async () => {
    const { stdout, stderr } = await runCtl('orchestrator', ['status'], {
      ENABLE_OPERATION_INTELLIGENCE: 'false',
    })
    const output = stdout + stderr
    expect(output).not.toMatch(/Operation Intelligence running/)
  })
})
