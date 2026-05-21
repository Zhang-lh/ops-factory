import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { readFileSync, existsSync, copyFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import type { Plugin } from 'vite'

function runtimeConfigPlugin(): Plugin {
    const cwd = process.cwd()
    const configJsonPath = resolve(cwd, 'config.json')
    const configTemplates = [
        'config.standalone.json.example',
        'config.embed.json.example',
    ]

    return {
        name: 'runtime-config',
        configureServer(server) {
            server.middlewares.use((req, res, next) => {
                if (req.url === '/config.json') {
                    if (!existsSync(configJsonPath)) {
                        res.statusCode = 404
                        res.end('Not Found')
                        return
                    }
                    res.setHeader('Content-Type', 'application/json; charset=utf-8')
                    res.end(readFileSync(configJsonPath, 'utf-8'))
                    return
                }
                for (const tpl of configTemplates) {
                    if (req.url === `/${tpl}`) {
                        const tplPath = resolve(cwd, tpl)
                        if (!existsSync(tplPath)) {
                            res.statusCode = 404
                            res.end('Not Found')
                            return
                        }
                        res.setHeader('Content-Type', 'application/json; charset=utf-8')
                        res.end(readFileSync(tplPath, 'utf-8'))
                        return
                    }
                }
                next()
            })
        },
        writeBundle(options) {
            const outDir = resolve(cwd, options.dir || 'dist')
            mkdirSync(outDir, { recursive: true })
            if (existsSync(configJsonPath)) {
                copyFileSync(configJsonPath, resolve(outDir, 'config.json'))
            }
            for (const tpl of configTemplates) {
                const tplPath = resolve(cwd, tpl)
                if (existsSync(tplPath)) {
                    copyFileSync(tplPath, resolve(outDir, tpl))
                }
            }
        },
    }
}

export default defineConfig(() => {
    const sdkEntry = resolve(process.cwd(), '../typescript-sdk/src/index.ts')

    return {
        plugins: [react(), runtimeConfigPlugin()],
        resolve: {
            alias: {
                '@goosed/sdk': sdkEntry,
            },
        },
        server: {
            port: 5173,
            proxy: {
                '/gateway': 'http://127.0.0.1:3000',
                '/knowledge': 'http://127.0.0.1:8092',
                '/control-center': 'http://127.0.0.1:8094',
                '/business-intelligence': 'http://127.0.0.1:8093',
                '/skill-market': 'http://127.0.0.1:8095',
                '/operation-intelligence': 'http://127.0.0.1:8096',
            },
        },
    }
})
