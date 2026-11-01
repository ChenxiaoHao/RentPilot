import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
const screenshotRoot = fileURLToPath(new URL('../../docs/screenshots/', import.meta.url))
await mkdir(screenshotRoot, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome' })
const page = await browser.newPage({ viewport: { width: 402, height: 874 } })
const issues = []
page.on('pageerror', error => issues.push(error.message))
for (const [name, route] of [['home','/'],['plans','/plans'],['map','/map'],['profile','/profile'],['details','/plans/commute'],['listing','/listing/h1'],['preferences','/preferences'],['lifesim','/lifesim/commute'],['compare','/compare'],['history','/history'],['empty','/empty'],['negotiation','/negotiate']]) {
  await page.goto(`http://127.0.0.1:5178/#${route}`)
  await page.waitForTimeout(400)
  await page.evaluate(() => document.fonts.ready)
  await page.screenshot({ path: path.join(screenshotRoot, `${name}-402.png`), fullPage: true })
  const brokenImages = await page.locator('img').evaluateAll(images => images.filter(img => !img.complete || !img.naturalWidth).map(img => img.getAttribute('src')))
  if (brokenImages.length) issues.push({ route, brokenImages })
}
await page.goto('http://127.0.0.1:5178/')
await page.setViewportSize({ width: 360, height: 640 })
await page.waitForTimeout(200)
await page.screenshot({ path: path.join(screenshotRoot, 'home-360.png'), fullPage: true })
console.log(JSON.stringify({ issues }))
await browser.close()
