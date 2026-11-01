import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
const output='../docs/map-contact-2026-10-29/screens'
await mkdir(output,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true})
const profile={id:'a',username:'demo',nickname:'演示租客',createdAt:'2026-10-28T00:00:00Z',favoritesCount:0,historyCount:0,plannedCount:0}
for(const size of [{width:320,height:640},{width:402,height:874}]){
  const context=await browser.newContext({viewport:size,deviceScaleFactor:1})
  const page=await context.newPage()
  await page.goto('http://127.0.0.1:5179/#/find-map')
  await page.getByTestId('map-pin').first().waitFor({timeout:30000})
  await page.screenshot({path:`${output}/map-${size.width}.png`})
  await page.getByTestId('map-pin').first().click()
  await page.getByTestId('map-preview').waitFor()
  await page.waitForTimeout(250)
  await page.screenshot({path:`${output}/map-preview-${size.width}.png`})
  await page.route('**/api/me',route=>route.fulfill({json:profile}))
  await page.route('**/api/me/favorite-ids',route=>route.fulfill({json:[]}))
  await page.route('**/api/conversations',route=>route.fulfill({json:[]}))
  await page.evaluate(profile=>localStorage.setItem('rentpilot-account-v1',JSON.stringify({token:'a'.repeat(43),profile,expiresAt:'2027-01-01T00:00:00Z'})),profile)
  await page.goto('http://127.0.0.1:5179/#/messages')
  await page.reload()
  await page.getByText('还没有对话').waitFor()
  await page.screenshot({path:`${output}/contact-${size.width}.png`})
  await context.close()
}
await browser.close()
