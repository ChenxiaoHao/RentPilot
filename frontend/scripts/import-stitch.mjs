import { readdir, readFile, mkdir, writeFile, stat, rename } from 'node:fs/promises'
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// Preserve assetFor(page, index): original <img> indices precede CSS backgrounds.
// Offline fallback extracts existing Stitch screenshot pixels, without generation.
const frontendRoot = fileURLToPath(new URL('..', import.meta.url))
const repositoryRoot = path.resolve(frontendRoot, '..')
const sourceRoot = path.join(repositoryRoot, 'design-reference/stitch_rentpilot_ai_home_ui')
const root = sourceRoot
const docsRoot = path.join(repositoryRoot, 'docs')
const localAsset = asset => path.join(frontendRoot, `public${asset}`)
const assetRoot = path.join(frontendRoot, 'public/assets/stitch')
await mkdir(assetRoot, { recursive: true })
await mkdir(path.join(frontendRoot, 'src/data'), { recursive: true })
await mkdir(docsRoot, { recursive: true })
const crops = {
  rentpilot_1: [
    ['image.png', [563, 543, 136, 122], 'Page screenshot is invalid (28 bytes); first listing from the supplied composite reference.'],
    ['image.png', [563, 698, 136, 125], 'Page screenshot is invalid (28 bytes); second listing from the supplied composite reference.'],
  ],
  rentpilot_2: [['rentpilot_2', [50, 1089, 69, 69]], ['rentpilot_2', [50, 1197, 69, 69]]],
  'rentpilot_2.0': [['rentpilot_2.0', [1003, 62, 43, 43]]],
  rentpilot_3: [
    ['rentpilot_6', [18, 213, 295, 154], 'Only the commute slide is visible in this screenshot. Economy uses the existing interior photo from the supplied detail-page reference.'],
    ['rentpilot_3', [116, 452, 347, 143], 'Visible commute slide; crop excludes the overlaid location pill.'],
    ['image.png', [563, 698, 136, 125], 'Only the commute slide is visible in this screenshot. Quality uses another existing interior photo from the supplied composite reference.'],
  ],
  rentpilot_5: [['rentpilot_5', [47, 257, 70, 70]]],
  rentpilot_6: [
    ['rentpilot_6', [18, 213, 295, 154], 'Photo region excludes screenshot controls overlaid at its top and bottom.'],
    ['rentpilot_6', [34, 926, 263, 149], 'Design map illustration from the supplied screenshot; not live geographic data or a map API.'],
  ],
  rentpilot_ai_1: [
    ['image.png', [563, 543, 136, 122], 'Page screenshot is invalid (28 bytes); first listing from the supplied composite reference.'],
    ['image.png', [563, 698, 136, 125], 'Page screenshot is invalid (28 bytes); second listing from the supplied composite reference.'],
  ],
  rentpilot_ai_2: [['image.png', [428, 93, 43, 41], 'Page screenshot is invalid (28 bytes); existing avatar variant from the supplied composite reference.']],
  rentpilot_lifesim_1: [['rentpilot_lifesim_1', [29, 1347, 244, 111], 'Design map illustration from the supplied screenshot; not live geographic data or a map API.']],
}

function powershell(script, env = {}) {
  return spawnSync('pwsh', ['-NoLogo', '-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')], {
    env: { ...process.env, ...env }, encoding: 'utf8', windowsHide: true, timeout: 20000,
  })
}

const manifest = {}
const entries = []
for (const entry of await readdir(root, { withFileTypes: true })) {
  if (!entry.isDirectory()) continue
  let html
  try { html = await readFile(path.join(root, entry.name, 'code.html'), 'utf8') } catch { continue }
  const images = [...html.matchAll(/<img\b[^>]*\bsrc=["']([^"']+)["']/g)].map(m => ({ url: m[1], kind: 'img' }))
  const backgrounds = [...html.matchAll(/background-image\s*:\s*url\(\s*["']?(https?:\/\/[^\s"')]+)["']?\s*\)/g)].map(m => ({ url: m[1], kind: 'css-background' }))
  manifest[entry.name] = []
  for (const [index, item] of [...images, ...backgrounds].entries()) entries.push({ page: entry.name, index, kind: item.kind, url: item.url.replaceAll('&amp;', '&') })
}

// All URLs share one host: test each transport once to avoid repeated timeouts.
const network = []
let transport = null
if (entries.length && !process.argv.includes('--offline')) {
  const probe = powershell("try { $r=Invoke-WebRequest -Method Head -Uri $env:STITCH_URL -TimeoutSec 5; if($r.StatusCode -eq 200){Write-Output 'OK'} else {exit 1} } catch { Write-Output $_.Exception.Message; exit 1 }", { STITCH_URL: entries[0].url })
  network.push({ method: 'Invoke-WebRequest HEAD', success: probe.status === 0, detail: (probe.stdout + probe.stderr).trim() })
  if (probe.status === 0) transport = 'powershell'
  else {
    const curl = spawnSync('curl.exe', ['--head', '--fail', '--silent', '--show-error', '--connect-timeout', '4', '--max-time', '6', entries[0].url], { encoding: 'utf8', windowsHide: true, timeout: 10000 })
    network.push({ method: 'curl.exe HEAD', success: curl.status === 0, detail: (curl.stdout + curl.stderr).trim() })
    if (curl.status === 0) transport = 'curl'
  }
}
const assets = []
const cropJobs = []
const failures = []
let previousAssets = []
try { previousAssets = JSON.parse(await readFile(path.join(docsRoot, 'stitch-assets-report.json'), 'utf8')).assets ?? [] } catch { /* Initial import. */ }
for (const { page, index, url, kind } of entries) {
  let downloaded = false
  let downloadMethod = transport
  const previous = previousAssets.find(asset => asset.page === page && asset.index === index && asset.originalUrl === url && asset.method !== 'source-screenshot-crop')
  const downloadDest = previous ? localAsset(previous.asset) : path.join(assetRoot, `${page}-${index}.jpg`)
  try {
    const bytes = await readFile(downloadDest)
    downloaded = bytes.length > 100 && ((bytes[0] === 0xff && bytes[1] === 0xd8) || bytes.toString('ascii', 1, 4) === 'PNG' || bytes.toString('ascii', 0, 4) === 'RIFF')
    if (downloaded) downloadMethod = 'cached-original-download'
  } catch { /* No valid cached download yet. */ }
  if (transport && !downloaded) {
    const result = transport === 'powershell'
      ? powershell("for($attempt=1;$attempt -le 3;$attempt++){try { Invoke-WebRequest -Uri $env:STITCH_URL -OutFile $env:STITCH_DEST -TimeoutSec 5; exit 0 } catch { if($attempt -eq 3){Write-Output $_.Exception.Message; exit 1} }}", { STITCH_URL: url, STITCH_DEST: downloadDest })
      : spawnSync('curl.exe', ['--location', '--fail', '--silent', '--show-error', '--connect-timeout', '4', '--max-time', '12', '--output', downloadDest, url], { encoding: 'utf8', windowsHide: true, timeout: 15000 })
    if (result.status === 0) {
      const bytes = await readFile(downloadDest)
      downloaded = bytes.length > 100 && ((bytes[0] === 0xff && bytes[1] === 0xd8) || bytes.toString('ascii', 1, 4) === 'PNG' || bytes.toString('ascii', 0, 4) === 'RIFF')
    }
    if (!downloaded) failures.push({ page, index, reason: (result.stdout + result.stderr).trim() || 'Response was not an image' })
  }
  if (downloaded) {
    const bytes = await readFile(downloadDest)
    const extension = bytes.toString('ascii', 1, 4) === 'PNG' ? 'png' : bytes.toString('ascii', 0, 4) === 'RIFF' ? 'webp' : 'jpg'
    const asset = `/assets/stitch/${page}-${index}.${extension}`
    if (downloadDest !== localAsset(asset)) await rename(downloadDest, localAsset(asset))
    manifest[page][index] = asset
    assets.push({ page, index, kind, originalUrl: url, asset, method: downloadMethod })
    continue
  }
  const crop = crops[page]?.[index]
  if (!crop) throw new Error(`No source-image fallback configured for ${page}[${index}]`)
  const [sourcePage, bounds, note] = crop
  const source = `${sourceRoot}/${sourcePage}/screen.png`
  const asset = `/assets/stitch/${page}-${index}.png`
  manifest[page][index] = asset
  cropJobs.push({ source: path.resolve(source), destination: localAsset(asset), bounds })
  assets.push({ page, index, kind, originalUrl: url, asset, method: 'source-screenshot-crop', source, crop: { x: bounds[0], y: bounds[1], width: bounds[2], height: bounds[3], units: 'source pixels' }, note: note ?? 'Photo extracted from the matching supplied Stitch screen without generation or retouching.' })
}
if (cropJobs.length) {
  const result = powershell(`
Add-Type -AssemblyName System.Drawing
$jobs=ConvertFrom-Json -InputObject $env:STITCH_CROPS
foreach($job in $jobs) {
  $source=[System.Drawing.Bitmap]::FromFile($job.source)
  try {
    $b=$job.bounds
    $rect=[System.Drawing.Rectangle]::new([int]$b[0],[int]$b[1],[int]$b[2],[int]$b[3])
    if($rect.Right -gt $source.Width -or $rect.Bottom -gt $source.Height){throw "Crop exceeds source image: $($job.source)"}
    $cropped=$source.Clone($rect,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {$cropped.Save($job.destination,[System.Drawing.Imaging.ImageFormat]::Png)} finally {$cropped.Dispose()}
  } finally {$source.Dispose()}
}
`, { STITCH_CROPS: JSON.stringify(cropJobs) })
  if (result.status !== 0) throw new Error(result.stdout + result.stderr)
}
for (const asset of assets) {
  const file = await stat(localAsset(asset.asset))
  if (file.size < 100) throw new Error(`Invalid local image: ${asset.asset}`)
  asset.bytes = file.size
}
await writeFile(path.join(frontendRoot, 'src/data/stitch-assets.json'), JSON.stringify(manifest, null, 2) + '\n')
await writeFile(path.join(docsRoot, 'stitch-assets-report.json'), JSON.stringify({
  uniqueImages: new Set(assets.map(asset => asset.asset)).size,
  remoteImages: assets.filter(asset => asset.method !== 'source-screenshot-crop').length,
  screenshotCrops: cropJobs.length,
  imageIndexPolicy: 'Original img order is preserved. CSS background-image URLs are appended after img assets for each page.',
  mapNotice: 'Map assets reproduce supplied Stitch design illustrations only. They are not live maps, verified geography, or map API integration.',
  network, failures, assets,
}, null, 2) + '\n')
console.log(JSON.stringify({ localImages: assets.length, screenshotCrops: cropJobs.length, transport, failures: failures.length }))
