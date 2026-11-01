import { chromium } from '@playwright/test'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 2 })
await page.goto('file:///D:/CODE/RentPilot/docs/competition/day-01/architecture.html', { waitUntil: 'load' })
await page.waitForTimeout(300)
await page.screenshot({ path: 'D:/CODE/RentPilot/docs/competition/day-01/architecture.png', fullPage: false })
await browser.close()
console.log('architecture screenshot saved')