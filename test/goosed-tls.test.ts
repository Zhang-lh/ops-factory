/**
 * Tests for goosed TLS support.
 *
 * Verifies the full config chain for the goosed-tls feature:
 * 1. config.yaml parsing (yaml_val reads goosedTls)
 * 2. ctl.sh injects -Dgateway.goosed-tls via GOOSED_TLS
 * 3. env var overrides config.yaml value
 * 4. Gateway Java unit tests pass (GatewayProperties, InstanceManager, GoosedProxy, etc.)
 * 5. No remaining hardcoded http://127.0.0.1 in goosed proxy code
 * 6. GOOSE_TLS is passed to goosed process environment
 */
import { execFile } from 'node:child_process'
import { resolve, join } from 'node:path'
import { readFile, writeFile, mkdir, rm } from 'node:fs/promises'
import { describe, it, expect, beforeAll, afterAll } from 'vitest'

const PROJECT_ROOT = resolve(import.meta.dirname, '..')
const GATEWAY_DIR = join(PROJECT_ROOT, 'gateway')
const CTL_SH = join(GATEWAY_DIR, 'scripts', 'ctl.sh')
const TMP_DIR = join(PROJECT_ROOT, 'test', '.tmp-tls-test')
const MVN = process.env.MVN || 'mvn'

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

// =============================================================================
// 1. config.yaml contains goosedTls
// =============================================================================
describe('config.yaml goosedTls field', () => {
  it('gateway/config.yaml contains goosedTls key', async () => {
    const content = await readFile(join(GATEWAY_DIR, 'config.yaml'), 'utf-8')
    expect(content).toMatch(/^goosedTls:\s*(true|false)/m)
  })

  it('goosedTls defaults to true', async () => {
    const content = await readFile(join(GATEWAY_DIR, 'config.yaml'), 'utf-8')
    expect(content).toMatch(/^goosedTls:\s*true/m)
  })
})

// =============================================================================
// 2. ctl.sh parses goosedTls from config.yaml
// =============================================================================
describe('ctl.sh goosedTls parsing', () => {
  it('yaml_val reads goosedTls value', async () => {
    const tmpConfig = join(TMP_DIR, 'tls-yaml-val.yaml')
    await writeFile(tmpConfig, 'goosedTls: true\ngoosedBin: "goosed"\n')

    const script = `
      yaml_val() {
        local key="$1" file="${tmpConfig}"
        [ -f "\${file}" ] || return 0
        awk -F': ' -v k="\${key}" '$1==k {print $2}' "\${file}" | head -n1 | sed "s/^[\\"']//;s/[\\"']$//"
      }
      GOOSED_TLS="\${GOOSED_TLS:-\$(yaml_val goosedTls)}"
      GOOSED_TLS="\${GOOSED_TLS:-true}"
      echo "goosedTls=\${GOOSED_TLS}"
    `
    const { stdout, code } = await run('bash', ['-c', script])
    expect(code).toBe(0)
    expect(stdout).toContain('goosedTls=true')
  })

  it('yaml_val reads goosedTls=false', async () => {
    const tmpConfig = join(TMP_DIR, 'tls-yaml-val-false.yaml')
    await writeFile(tmpConfig, 'goosedTls: false\n')

    const script = `
      yaml_val() {
        local key="$1" file="${tmpConfig}"
        [ -f "\${file}" ] || return 0
        awk -F': ' -v k="\${key}" '$1==k {print $2}' "\${file}" | head -n1 | sed "s/^[\\"']//;s/[\\"']$//"
      }
      GOOSED_TLS="\${GOOSED_TLS:-\$(yaml_val goosedTls)}"
      GOOSED_TLS="\${GOOSED_TLS:-true}"
      echo "goosedTls=\${GOOSED_TLS}"
    `
    const { stdout, code } = await run('bash', ['-c', script])
    expect(code).toBe(0)
    expect(stdout).toContain('goosedTls=false')
  })

  it('env var GOOSED_TLS overrides config.yaml', async () => {
    const tmpConfig = join(TMP_DIR, 'tls-env-override.yaml')
    await writeFile(tmpConfig, 'goosedTls: true\n')

    const script = `
      yaml_val() {
        local key="$1" file="${tmpConfig}"
        [ -f "\${file}" ] || return 0
        awk -F': ' -v k="\${key}" '$1==k {print $2}' "\${file}" | head -n1 | sed "s/^[\\"']//;s/[\\"']$//"
      }
      GOOSED_TLS="\${GOOSED_TLS:-\$(yaml_val goosedTls)}"
      GOOSED_TLS="\${GOOSED_TLS:-true}"
      echo "goosedTls=\${GOOSED_TLS}"
    `
    const { stdout, code } = await run('bash', ['-c', script], {
      env: { ...process.env, GOOSED_TLS: 'false' },
    })
    expect(code).toBe(0)
    expect(stdout).toContain('goosedTls=false')
  })

  it('defaults to true when config.yaml has no goosedTls', async () => {
    const tmpConfig = join(TMP_DIR, 'tls-no-key.yaml')
    await writeFile(tmpConfig, 'port: 3000\n')

    const script = `
      yaml_val() {
        local key="$1" file="${tmpConfig}"
        [ -f "\${file}" ] || return 0
        awk -F': ' -v k="\${key}" '$1==k {print $2}' "\${file}" | head -n1 | sed "s/^[\\"']//;s/[\\"']$//"
      }
      GOOSED_TLS="\${GOOSED_TLS:-\$(yaml_val goosedTls)}"
      GOOSED_TLS="\${GOOSED_TLS:-true}"
      echo "goosedTls=\${GOOSED_TLS}"
    `
    const { stdout, code } = await run('bash', ['-c', script])
    expect(code).toBe(0)
    expect(stdout).toContain('goosedTls=true')
  })
})

// =============================================================================
// 3. ctl.sh injects -Dgateway.goosed-tls into Java opts
// =============================================================================
describe('ctl.sh Java property injection', () => {
  it('ctl.sh contains -Dgateway.goosed-tls injection', async () => {
    const content = await readFile(CTL_SH, 'utf-8')
    expect(content).toContain('"-Dgateway.goosed-tls=${GOOSED_TLS}"')
  })

  it('ctl.sh reads GOOSED_TLS from yaml_val', async () => {
    const content = await readFile(CTL_SH, 'utf-8')
    expect(content).toContain('GOOSED_TLS="${GOOSED_TLS:-$(yaml_val goosedTls)}"')
    expect(content).toContain('GOOSED_TLS="${GOOSED_TLS:-true}"')
  })

  it('ctl.sh passes bash -n (syntax valid after changes)', async () => {
    const { code, stderr } = await run('bash', ['-n', CTL_SH])
    expect(code).toBe(0)
    expect(stderr).toBe('')
  })
})

// =============================================================================
// 4. Spring Boot application.yml has goosed-tls property
// =============================================================================
describe('application.yml goosed-tls', () => {
  it('application.yml contains goosed-tls property', async () => {
    const content = await readFile(
      join(GATEWAY_DIR, 'gateway-service', 'src', 'main', 'resources', 'application.yml'),
      'utf-8',
    )
    expect(content).toMatch(/goosed-tls:\s*\$\{GOOSED_TLS:true\}/)
  })
})

// =============================================================================
// 5. Java source code: no remaining hardcoded http://127.0.0.1 for goosed
// =============================================================================
describe('no hardcoded http:// in goosed proxy code', () => {
  const sourceFiles = [
    'gateway-service/src/main/java/com/huawei/opsfactory/gateway/proxy/GoosedProxy.java',
    'gateway-service/src/main/java/com/huawei/opsfactory/gateway/proxy/SseRelayService.java',
    'gateway-service/src/main/java/com/huawei/opsfactory/gateway/controller/McpController.java',
    'gateway-service/src/main/java/com/huawei/opsfactory/gateway/service/SessionService.java',
    'gateway-service/src/main/java/com/huawei/opsfactory/gateway/process/InstanceManager.java',
  ]

  for (const file of sourceFiles) {
    it(`${file.split('/').pop()} has no hardcoded http://127.0.0.1:port for goosed`, async () => {
      const content = await readFile(join(GATEWAY_DIR, file), 'utf-8')
      // Match pattern: "http://127.0.0.1:" + port/someVar (goosed connection URLs)
      // Exclude comments and non-goosed URLs (like corsOrigin defaults)
      const lines = content.split('\n')
      const goosedHttpLines = lines.filter(line => {
        const trimmed = line.trim()
        // Skip comments
        if (trimmed.startsWith('//') || trimmed.startsWith('*')) return false
        // Match "http://127.0.0.1:" + port variable patterns
        return /["']http:\/\/127\.0\.0\.1:["']\s*\+\s*/.test(trimmed)
      })
      expect(goosedHttpLines).toEqual([])
    })
  }
})

// =============================================================================
// 6. GatewayProperties has goosedTls field and goosedScheme() method
// =============================================================================
describe('GatewayProperties goosedTls', () => {
  let content: string

  beforeAll(async () => {
    content = await readFile(
      join(GATEWAY_DIR, 'gateway-service', 'src', 'main', 'java', 'com', 'huawei',
        'opsfactory', 'gateway', 'config', 'GatewayProperties.java'),
      'utf-8',
    )
  })

  it('declares goosedTls boolean field', () => {
    expect(content).toMatch(/private\s+boolean\s+goosedTls\s*=\s*true/)
  })

  it('has isGoosedTls() getter', () => {
    expect(content).toContain('isGoosedTls()')
  })

  it('has setGoosedTls() setter', () => {
    expect(content).toContain('setGoosedTls(boolean')
  })

  it('has goosedScheme() method returning https or http', () => {
    expect(content).toContain('goosedScheme()')
    expect(content).toMatch(/goosedTls\s*\?\s*"https"\s*:\s*"http"/)
  })
})

// =============================================================================
// 7. InstanceManager passes GOOSE_TLS to goosed process
// =============================================================================
describe('InstanceManager GOOSE_TLS env', () => {
  let content: string

  beforeAll(async () => {
    content = await readFile(
      join(GATEWAY_DIR, 'gateway-service', 'src', 'main', 'java', 'com', 'huawei',
        'opsfactory', 'gateway', 'process', 'InstanceManager.java'),
      'utf-8',
    )
  })

  it('passes GOOSE_TLS env var to goosed', () => {
    expect(content).toContain('env.put("GOOSE_TLS"')
  })

  it('uses goosedBaseUrl() for health check', () => {
    expect(content).toContain('goosedBaseUrl(port)')
  })

  it('has trust-all SSL factory for self-signed certs', () => {
    expect(content).toContain('SSLSocketFactory')
    expect(content).toContain('trustAllSslFactory')
  })

  it('configures HttpsURLConnection with trust-all when TLS enabled', () => {
    expect(content).toContain('HttpsURLConnection')
    expect(content).toContain('setSSLSocketFactory')
    expect(content).toContain('setHostnameVerifier')
  })
})

// =============================================================================
// 8. GoosedProxy WebClient insecure SSL
// =============================================================================
describe('GoosedProxy TLS WebClient', () => {
  let content: string

  beforeAll(async () => {
    content = await readFile(
      join(GATEWAY_DIR, 'gateway-service', 'src', 'main', 'java', 'com', 'huawei',
        'opsfactory', 'gateway', 'proxy', 'GoosedProxy.java'),
      'utf-8',
    )
  })

  it('imports Netty SSL classes', () => {
    expect(content).toContain('import io.netty.handler.ssl.SslContext')
    expect(content).toContain('import io.netty.handler.ssl.SslContextBuilder')
    expect(content).toContain('InsecureTrustManagerFactory')
  })

  it('configures insecure SSL when goosedTls is true', () => {
    expect(content).toContain('isGoosedTls()')
    expect(content).toContain('SslContextBuilder.forClient()')
    expect(content).toContain('InsecureTrustManagerFactory.INSTANCE')
  })

  it('uses ReactorClientHttpConnector with custom HttpClient', () => {
    expect(content).toContain('ReactorClientHttpConnector')
    expect(content).toContain('HttpClient.create()')
  })

  it('exposes goosedBaseUrl() method', () => {
    expect(content).toContain('public String goosedBaseUrl(int port)')
  })
})

// =============================================================================
// 9. Gateway Java unit tests pass
// =============================================================================
describe('gateway Java unit tests', () => {
  it('mvn test passes (354 tests)', async () => {
    const { stdout, stderr, code } = await run(
      MVN, ['test', '-q'],
      { cwd: GATEWAY_DIR, timeout: 180_000 },
    )
    const output = stdout + stderr
    expect(code).toBe(0)

    // Verify no test failures
    const summaryMatch = output.match(/Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+)/)
    if (summaryMatch) {
      expect(Number(summaryMatch[2])).toBe(0) // Failures
      expect(Number(summaryMatch[3])).toBe(0) // Errors
    }
  }, 180_000)
})
