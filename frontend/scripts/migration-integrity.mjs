import { createHash } from 'node:crypto'
import { readdir, readFile, mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

// This utility verifies relocation only. It never changes application or source data.
const [mode, rootArg = '.'] = process.argv.slice(2)
const root = path.resolve(rootArg)
const manifestPath = path.join(root, 'docs/migration/before.json')
const relocated = ['src', 'public', 'tests', 'android/app/src/main', 'package.json', 'package-lock.json', 'index.html', 'tsconfig.json', 'vite.config.ts', 'vitest.config.ts', 'playwright.config.ts', 'capacitor.config.ts']
const preserved = ['deliverables', 'design-reference']
const digest = bytes => createHash('sha256').update(bytes).digest('hex')
const normalize = value => value.split(path.sep).join('/')

async function files(relative) {
  const absolute = path.join(root, relative)
  const { stat } = await import('node:fs/promises')
  if ((await stat(absolute)).isFile()) return [relative]
  const results = []
  for (const entry of await readdir(absolute, { withFileTypes: true })) {
    const child = normalize(path.join(relative, entry.name))
    if (child.includes('android/app/src/main/assets/')) continue // cap sync regenerates these.
    if (entry.isDirectory()) results.push(...await files(child))
    else if (entry.isFile()) results.push(child)
  }
  return results
}

if (mode === 'capture') {
  const rows = []
  for (const group of [...relocated, ...preserved]) {
    for (const oldPath of await files(group)) rows.push({ oldPath, newPath: relocated.includes(group) ? `frontend/${oldPath}` : oldPath, sha256: digest(await readFile(path.join(root, oldPath))) })
  }
  const oldPath = 'stitch_rentpilot_ai_home_ui.zip'
  rows.push({ oldPath, newPath: `design-reference/${oldPath}`, sha256: digest(await readFile(path.join(root, oldPath))) })
  await mkdir(path.dirname(manifestPath), { recursive: true })
  await writeFile(manifestPath, JSON.stringify({ algorithm: 'SHA-256', files: rows }, null, 2) + '\n')
  console.log(JSON.stringify({ recorded: rows.length, manifest: manifestPath }))
} else if (mode === 'verify') {
  const { files: rows } = JSON.parse(await readFile(manifestPath, 'utf8'))
  const failures = []
  for (const row of rows) {
    try {
      if (digest(await readFile(path.join(root, row.newPath))) !== row.sha256) failures.push({ path: row.newPath, reason: 'Hash mismatch' })
    } catch (error) { failures.push({ path: row.newPath, reason: String(error) }) }
  }
  const result = { checked: rows.length, passed: rows.length - failures.length, failures }
  await writeFile(path.join(root, 'docs/migration/integrity-result.json'), JSON.stringify(result, null, 2) + '\n')
  console.log(JSON.stringify(result))
  if (failures.length) process.exitCode = 1
} else { throw new Error('Usage: node migration-integrity.mjs capture|verify <repository-root>') }
