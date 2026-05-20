import { execFile } from 'node:child_process'
import { access, constants, readFile, writeFile, mkdir, rm } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import YAML from 'yaml'

const PROJECT_ROOT = resolve(import.meta.dirname, '..', '..', '..', '..')
const OI_DIR = join(PROJECT_ROOT, 'operation-intelligence')
const TMP_DIR = join(PROJECT_ROOT, 'test', '.tmp-oi-config-test')

function run(
  cmd: string,
  args: string[],
  opts?: { cwd?: string; env?: Record<string, string>; timeout?: number },
): Promise<{ stdout: string; stderr: string; code: number }> {
  return new Promise((resolve) => {
    execFile(
      cmd,
      args,
      {
        cwd: opts?.cwd || PROJECT_ROOT,
        env: { ...process.env, ...opts?.env },
        timeout: opts?.timeout || 30_000,
      },
      (err, stdout, stderr) => {
        const code = err && 'code' in err ? (err.code as number) : err ? 1 : 0
        resolve({ stdout: stdout.toString(), stderr: stderr.toString(), code })
      },
    )
  })
}

beforeAll(async () => {
  await mkdir(TMP_DIR, { recursive: true })
})

afterAll(async () => {
  await rm(TMP_DIR, { recursive: true, force: true })
})

describe('operation-intelligence config files', () => {
  it('config.yaml exists and is readable', async () => {
    await expect(
      access(join(OI_DIR, 'config.yaml'), constants.R_OK),
    ).resolves.toBeUndefined()
  })

  it('config.yaml.example exists and is readable', async () => {
    await expect(
      access(join(OI_DIR, 'config.yaml.example'), constants.R_OK),
    ).resolves.toBeUndefined()
  })

  it('config.yaml.example is a valid subset of config.yaml keys', async () => {
    const example = YAML.parse(await readFile(join(OI_DIR, 'config.yaml.example'), 'utf-8'))
    const main = YAML.parse(await readFile(join(OI_DIR, 'config.yaml'), 'utf-8'))

    expect(main.server).toBeDefined()
    expect(main['operation-intelligence']).toBeDefined()
    expect(example.server.port).toBe(main.server.port)
    expect(example['operation-intelligence']['cors-origin']).toBe(main['operation-intelligence']['cors-origin'])
  })
})

describe('operation-intelligence ctl.sh config parsing', () => {
  it('yaml_val logic matches ctl.sh node-based parsing', async () => {
    const tmpConfig = join(TMP_DIR, 'oi-yaml-val.yaml')
    await writeFile(tmpConfig, [
      'server:',
      '  port: 8096',
      'operation-intelligence:',
      '  secret-key: "my-test-key"',
      '  cors-origin: "*"',
    ].join('\n'))

    // Verify the same logic that ctl.sh uses (node + yaml module)
    const content = await readFile(tmpConfig, 'utf-8')
    const config = YAML.parse(content)
    const port = config.server?.port
    const secretKey = config['operation-intelligence']?.['secret-key']
    const corsOrigin = config['operation-intelligence']?.['cors-origin']

    expect(String(port)).toBe('8096')
    expect(secretKey).toBe('my-test-key')
    expect(corsOrigin).toBe('*')
  })

  it('OI_PORT env var overrides config.yaml port', async () => {
    const tmpConfig = join(TMP_DIR, 'oi-env-override.yaml').replace(/\\/g, '/')
    await writeFile(tmpConfig, 'server:\n  port: 8096\n')

    const script = `
      yaml_val() {
        local key="$1" file="${tmpConfig}"
        [ -f "\${file}" ] || return 0
        node -e "const y=require('yaml');const f=require('fs').readFileSync('\${file}','utf-8');const c=y.parse(f);const keys='\${key}'.split('.');let v=c;for(const k of keys){v=v?.[k]};if(v!=null)process.stdout.write(String(v))" 2>/dev/null || true
      }
      OI_PORT="\${OI_PORT:-\$(yaml_val server.port)}"
      OI_PORT="\${OI_PORT:-8096}"
      echo "port=\${OI_PORT}"
    `
    const withEnv = await run('bash', ['-c', script], {
      env: { ...process.env, OI_PORT: '19096' },
    })
    expect(withEnv.code).toBe(0)
    expect(withEnv.stdout).toContain('port=19096')

    const withoutEnv = await run('bash', ['-c', script], {
      env: { ...process.env, OI_PORT: '' },
    })
    expect(withoutEnv.code).toBe(0)
    expect(withoutEnv.stdout).toContain('port=8096')
  })

  it('yaml_val returns empty when config file is missing', async () => {
    const script = `
      yaml_val() {
        local key="$1" file="${TMP_DIR}/nonexistent.yaml"
        [ -f "\${file}" ] || return 0
        node -e "const y=require('yaml');const f=require('fs').readFileSync('\${file}','utf-8');const c=y.parse(f);const keys='\${key}'.split('.');let v=c;for(const k of keys){v=v?.[k]};if(v!=null)process.stdout.write(String(v))" 2>/dev/null || true
      }
      result="$(yaml_val server.port)"
      echo "result=[\${result}]"
    `
    const { stdout, code } = await run('bash', ['-c', script])
    expect(code).toBe(0)
    expect(stdout).toContain('result=[]')
  })
})

describe('operation-intelligence DV environment config', () => {
  it('config.yaml.example dv-environments defaults to empty list', async () => {
    const content = await readFile(join(OI_DIR, 'config.yaml.example'), 'utf-8')
    const config = YAML.parse(content)

    expect(config['operation-intelligence'].qos['dv-environments']).toEqual([])
  })

  it('config.yaml dv-environments entries have required fields', async () => {
    const content = await readFile(join(OI_DIR, 'config.yaml'), 'utf-8')
    const config = YAML.parse(content)
    const envs = config['operation-intelligence'].qos['dv-environments']

    if (Array.isArray(envs) && envs.length > 0) {
      for (const env of envs) {
        expect(env['env-code']).toBeTruthy()
        expect(env['env-name']).toBeTruthy()
        expect(env['server-url']).toBeTruthy()
      }
    }
  })

  it('ctl.sh detects strict-ssl=false for dv_server startup', async () => {
    const content = await readFile(join(OI_DIR, 'scripts', 'ctl.sh'), 'utf-8')
    expect(content).toContain('check_strict_ssl_false')
    expect(content).toContain('strict-ssl')
  })
})

describe('operation-intelligence application.yml', () => {
  it('Spring Boot application.yml imports OI_CONFIG_PATH', async () => {
    const content = await readFile(join(OI_DIR, 'src', 'main', 'resources', 'application.yml'), 'utf-8')
    expect(content).toContain('OI_CONFIG_PATH')
    expect(content).toContain('import:')
  })

  it('default server port matches config.yaml', async () => {
    const appYml = YAML.parse(
      await readFile(join(OI_DIR, 'src', 'main', 'resources', 'application.yml'), 'utf-8'),
    )
    const configYaml = YAML.parse(
      await readFile(join(OI_DIR, 'config.yaml'), 'utf-8'),
    )
    expect(appYml.server.port).toBe(configYaml.server.port)
  })

  it('actuator endpoints are exposed', async () => {
    const content = await readFile(join(OI_DIR, 'src', 'main', 'resources', 'application.yml'), 'utf-8')
    expect(content).toContain('health')
    expect(content).toContain('management')
  })
})
