import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
const output = '../docs/user-flow-2026-10-28/screens'
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
for (const size of [{width:320,height:640},{width:402,height:874}]) {
  const context = await browser.newContext({ viewport:size, deviceScaleFactor:1, isMobile:true, hasTouch:true })
  for (const [name,route] of [['chat','chat'],['list','listings'],['map','find-map'],['contact','messages'],['profile','profile']]) {
    const page=await context.newPage()
    await page.goto(`http://127.0.0.1:5179/#/${route}`,{waitUntil:'networkidle',timeout:30000}).catch(()=>{})
    await page.waitForTimeout(2000)
    await page.screenshot({path:`${output}/${name}-${size.width}.png`})
    await page.close()
  }
  await context.close()
}
await browser.close()
