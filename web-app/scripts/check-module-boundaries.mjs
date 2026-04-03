import { readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const projectRoot = path.resolve(__dirname, '..')
const srcRoot = path.join(projectRoot, 'src')
const modulesRoot = path.join(srcRoot, 'app', 'modules')

const importPattern = /\b(?:import|export)\b[\s\S]*?\bfrom\s*['"]([^'"]+)['"]/g

const privateSubtrees = [
    {
        owner: 'agents',
        pathParts: ['app', 'modules', 'agents', 'components', 'mcp'],
    },
    {
        owner: 'agents',
        pathParts: ['app', 'modules', 'agents', 'components', 'prompt'],
    },
    {
        owner: 'agents',
        pathParts: ['app', 'modules', 'agents', 'components', 'memory'],
    },
    {
        owner: 'agents',
        pathParts: ['app', 'modules', 'agents', 'components', 'skill'],
    },
    {
        owner: 'agents',
        pathParts: ['app', 'modules', 'agents', 'hooks'],
    },
    {
        owner: 'knowledge',
        pathParts: ['app', 'modules', 'knowledge', 'components'],
    },
]

function walk(dir) {
    const entries = readdirSync(dir, { withFileTypes: true })
    const files = []

    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name)
        if (entry.isDirectory()) {
            files.push(...walk(fullPath))
            continue
        }

        if (/\.(ts|tsx|mts|cts)$/.test(entry.name)) {
            files.push(fullPath)
        }
    }

    return files
}

function normalizePath(value) {
    return value.split(path.sep).join('/')
}

function getModuleName(absoluteFilePath) {
    const relative = path.relative(modulesRoot, absoluteFilePath)
    if (relative.startsWith('..')) return null

    const [moduleName] = relative.split(path.sep)
    return moduleName || null
}

function resolveImportPath(importerFile, specifier) {
    if (!specifier.startsWith('.')) return null

    const candidate = path.resolve(path.dirname(importerFile), specifier)
    const directFile = statIfExists(candidate)
    if (directFile?.isFile()) return candidate

    for (const ext of ['.ts', '.tsx', '.mts', '.cts']) {
        const withExt = `${candidate}${ext}`
        const extStat = statIfExists(withExt)
        if (extStat?.isFile()) return withExt
    }

    for (const entryFile of ['index.ts', 'index.tsx', 'index.mts', 'index.cts']) {
        const nested = path.join(candidate, entryFile)
        const nestedStat = statIfExists(nested)
        if (nestedStat?.isFile()) return nested
    }

    return candidate
}

function statIfExists(targetPath) {
    try {
        return statSync(targetPath)
    } catch {
        return null
    }
}

function isInsideParts(absoluteFilePath, parts) {
    const target = normalizePath(absoluteFilePath)
    const subtree = normalizePath(path.join(srcRoot, ...parts))
    return target === subtree || target.startsWith(`${subtree}/`)
}

const violations = []
const sourceFiles = walk(srcRoot)

for (const sourceFile of sourceFiles) {
    const importerModule = getModuleName(sourceFile)
    const content = readFileSync(sourceFile, 'utf8')

    for (const match of content.matchAll(importPattern)) {
        const specifier = match[1]
        const resolvedImport = resolveImportPath(sourceFile, specifier)
        if (!resolvedImport) continue

        const importedModule = getModuleName(resolvedImport)
        if (importerModule && importedModule && importerModule !== importedModule) {
            violations.push({
                file: sourceFile,
                message: `Module "${importerModule}" must not import module "${importedModule}" via "${specifier}"`,
            })
            continue
        }

        if (!importerModule) continue

        for (const subtree of privateSubtrees) {
            if (!isInsideParts(resolvedImport, subtree.pathParts)) continue
            if (importerModule === subtree.owner) continue
            violations.push({
                file: sourceFile,
                message: `Module "${importerModule}" must not import private "${subtree.owner}" subtree via "${specifier}"`,
            })
        }
    }
}

if (violations.length > 0) {
    console.error('Module boundary violations found:\n')
    for (const violation of violations) {
        console.error(`- ${normalizePath(path.relative(projectRoot, violation.file))}: ${violation.message}`)
    }
    process.exit(1)
}

console.log('Module boundary check passed.')
