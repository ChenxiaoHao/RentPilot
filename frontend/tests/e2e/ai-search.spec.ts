import { test, expect } from '@playwright/test'
const result = { status:'NO_MATCH', message:'没有符合条件的房源', criteria:{maxRent:1000}, recommendations:[], total:0, limitations:['模拟数据'], dataKind:'SIMULATED', source:{id:'fixture',rowCount:1,sha256:'a'.repeat(64),dataKind:'SIMULATED'}, engine:'SPRING_AI' }
test('failure retries the exact message without duplication', async ({page})=>{
 let count=0; const bodies: any[]=[]
 await page.route('**/api/ai/search',async route=>{bodies.push(route.request().postDataJSON()); count++; if(count===1) await route.fulfill({status:503,json:{code:'AI_NOT_CONFIGURED',message:'服务尚未配置'}}); else await route.fulfill({json:result})})
 await page.goto('/#/chat'); await page.getByRole('textbox',{name:'找房需求'}).fill('预算1000'); await page.getByRole('button',{name:'发送消息'}).click()
 await expect(page.getByRole('alert')).toContainText('服务尚未配置'); await page.getByRole('button',{name:'重新发送'}).click()
 await expect(page.getByText('没有符合条件的房源')).toBeVisible(); expect(bodies[1]).toEqual(bodies[0]); await expect(page.locator('.message.user')).toHaveCount(1)
})
test('cancelled response cannot change a new conversation',async ({page})=>{
 await page.route('**/api/ai/search',async route=>{await new Promise(resolve=>setTimeout(resolve,1000)); await route.fulfill({json:result}).catch(()=>{})})
 await page.goto('/#/chat'); await page.getByRole('textbox',{name:'找房需求'}).fill('预算1000'); await page.getByRole('button',{name:'发送消息'}).click()
 await expect(page.getByRole('status')).toContainText('正在检索'); await page.getByRole('button',{name:'取消',exact:true}).click(); await expect(page.getByRole('alert')).toContainText('已取消'); await page.getByRole('button',{name:'新建对话'}).click(); await page.waitForTimeout(1200); await expect(page.getByText('没有符合条件的房源')).toHaveCount(0)
})
test('reload restores interrupted request for retry',async ({page})=>{
 let attempts=0
 await page.route('**/api/ai/search',async route=>{attempts++;if(attempts===1){await new Promise(resolve=>setTimeout(resolve,1200));await route.fulfill({json:result}).catch(()=>{})}else await route.fulfill({json:result})})
 await page.goto('/#/chat');await page.getByRole('textbox',{name:'找房需求'}).fill('预算1000');await page.getByRole('button',{name:'发送消息'}).click();await expect(page.getByRole('status')).toBeVisible();await page.reload();await expect(page.getByRole('alert')).toContainText('上次检索未完成');await page.getByRole('button',{name:'重新发送'}).click();await expect(page.getByText('没有符合条件的房源')).toBeVisible();await expect(page.locator('.message.user')).toHaveCount(1)
})

test('repeated limitations appear once while distinct notices remain at narrow widths', async ({ page }) => {
  let turn = 0
  await page.route('**/api/ai/search', route => {
    turn++
    return route.fulfill({ json: { ...result, message: `回复${turn}`, limitations: ['房源为模拟数据，请核实。', turn === 1 ? '预算可能影响选择。' : '通勤时间需要实测。'] } })
  })
  await page.goto('/#/chat')
  for (const text of ['预算1000', '通勤30分钟']) {
    await page.getByRole('textbox', { name: '找房需求' }).fill(text)
    await page.getByRole('button', { name: '发送消息' }).click()
    await expect(page.getByText(`回复${text === '预算1000' ? 1 : 2}`, { exact: true })).toBeVisible()
  }
  await expect(page.getByText('房源为模拟数据，请核实。', { exact: true })).toHaveCount(1)
  await expect(page.getByText('预算可能影响选择。', { exact: true })).toBeVisible()
  await expect(page.getByText('通勤时间需要实测。', { exact: true })).toBeVisible()
  await expect(page.getByText('模拟房源数据', { exact: true })).toBeVisible()
  for (const width of [320, 402]) {
    await page.setViewportSize({ width, height: 874 })
    await page.screenshot({ path: `test-results/chat-limitations-${width}.png` })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  }
})
