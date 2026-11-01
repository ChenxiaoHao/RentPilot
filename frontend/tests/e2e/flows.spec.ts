import { test, expect } from '@playwright/test'

test('multiline chat sends real requests and reflects updated requirements', async ({ page }) => {
  const requests: any[] = []
  await page.route('**/api/ai/search', async route => {
    requests.push(route.request().postDataJSON())
    await route.fulfill({ json: { status: 'NEEDS_INPUT', message: requests.length === 1 ? '请补充区域' : '请确认预算', criteria: {}, recommendations: [], total: 0, limitations: [], dataKind: 'SIMULATED', source: { id: 'fixture', rowCount: 1, sha256: 'a'.repeat(64), dataKind: 'SIMULATED' }, engine: 'SPRING_AI' } })
  })
  await page.goto('/#/chat')
  const input = page.getByRole('textbox', { name: '找房需求' })
  await input.fill('我在张江上班\n预算 5000 元')
  await page.getByRole('button', { name: '发送消息' }).click()
  await expect(page.getByText('请补充区域')).toBeVisible()
  await input.fill('预算改成 4000 元')
  await page.getByRole('button', { name: '发送消息' }).click()
  await expect(page.getByText('请确认预算')).toBeVisible()
  expect(requests[1].messages.map((m: any) => m.content)).toEqual(['我在张江上班\n预算 5000 元', '请补充区域', '预算改成 4000 元'])
})

test('preference drafts require confirmation and persist as a new history version', async ({ page }) => {
  await page.goto('/#/preferences')
  const slider = page.getByRole('slider', { name: '通勤时效权重' })
  await slider.focus(); await slider.press('End')
  await page.goto('/#/plans')
  await expect(page.getByRole('heading', { name: '居住方案', exact: true })).toBeVisible()
  await page.goto('/#/preferences')
  await expect(page.getByRole('slider', { name: '通勤时效权重' })).toHaveValue('85')
  await slider.focus(); await slider.press('End')
  await page.getByRole('button', { name: '应用修改并重新规划' }).click()
  await page.getByRole('button', { name: '查看居住方案' }).click()
  await page.goto('/#/history')
  await expect(page.getByRole('heading', { name: /v2\.2/ }).first()).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: /v2\.2/ }).first()).toBeVisible()
  await page.locator('.version-card').filter({ hasText: 'v2.0' }).getByRole('link', { name: '查看详情' }).click()
  await expect(page.locator('.metric-value').first()).toContainText('¥4,200')
})

test('map filters reach honest empty state and saved listing survives reload', async ({ page }) => {
  await page.goto('/#/map')
  await page.getByRole('combobox', { name: '租金预算' }).selectOption('3000')
  await expect(page.getByText('0 套模拟房源', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: '查看条件调整建议' }).click()
  await expect(page.getByRole('heading', { name: /完全合适的那套/ })).toBeVisible()
  await page.goto('/#/listing/h1')
  await page.getByRole('button', { name: '收藏房源', exact: true }).click()
  await page.reload()
  await expect(page.getByRole('button', { name: '取消收藏房源', exact: true })).toBeVisible()
  await page.goto('/#/map?budget=3000&commute=12&district=唐镇&type=two&metro=2号线')
  await expect(page.getByRole('combobox', { name: '户型筛选' })).toHaveValue('two')
  await page.getByRole('link', { name: '查看条件调整建议' }).click()
  await page.getByRole('button', { name: '应用此建议' }).first().click()
  await expect(page.getByRole('combobox', { name: '户型筛选' })).toHaveValue('two')
  await expect(page.getByRole('combobox', { name: '区域筛选' })).toHaveValue('唐镇')
  await page.getByRole('link', { name: '查看条件调整建议' }).click()
  await page.getByRole('link', { name: '返回房源地图' }).click()
  await expect(page.getByRole('combobox', { name: '户型筛选' })).toHaveValue('two')
  await expect(page.getByRole('combobox', { name: '租金预算' })).toHaveValue('4200')
})

test('carousel swipes, LifeSim scenes and comparison are actionable', async ({ page }) => {
  await page.goto('/#/plans')
  await page.getByRole('tab', { name: '01 经济' }).click()
  await expect(page.getByRole('tab', { name: '01 经济' })).toHaveAttribute('aria-selected', 'true')
  await page.locator('.plan-rail').evaluate(el => el.scrollTo({ left: el.scrollWidth, behavior: 'instant' }))
  await expect(page.getByRole('tab', { name: '03 品质' })).toHaveAttribute('aria-selected', 'true')
  await page.goto('/#/lifesim/commute')
  await page.getByRole('tab', { name: '多场景模拟', exact: true }).click()
  await page.getByRole('tab', { name: '养宠物', exact: true }).click()
  await expect(page.getByRole('heading', { name: '养宠物 · 综合评估' })).toBeVisible()
  await expect(page.locator('.metric-value').first()).toContainText('¥7,300')
  await page.goto('/#/compare')
  await expect(page.locator('select')).toHaveCount(2)
  await page.locator('select').first().selectOption('quality')
  await expect(page.locator('.comparison-value').first()).toContainText('¥5,800')
})

test('focused chat remains usable when viewport shrinks for a keyboard', async ({ page }) => {
  await page.goto('/#/chat')
  await page.evaluate(() => { document.documentElement.style.setProperty('--safe-area-inset-top', '34px'); document.documentElement.style.setProperty('--safe-area-inset-bottom', '24px') })
  await page.getByRole('textbox', { name: '找房需求' }).focus()
  await page.setViewportSize({ width: 402, height: 470 })
  await expect(page.getByRole('navigation', { name: '主导航' })).toBeHidden()
  const rect = await page.getByRole('button', { name: '发送消息' }).boundingBox()
  expect(rect && rect.y + rect.height).toBeLessThan(470)
})

test('every designed state fits narrow and reference Android viewports', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  for (const width of [320, 360, 402, 480]) {
    await page.setViewportSize({ width, height: width === 360 ? 640 : 874 })
    for (const route of ['/', '/chat', '/map', '/plans', '/plans/commute', '/lifesim/commute', '/compare', '/profile', '/listing/h1', '/preferences', '/negotiate', '/empty', '/history', '/plans/invalid', '/listing/invalid']) {
      await page.goto(`/#${route}`)
      await page.waitForTimeout(80)
      await expect(page.locator('.app-shell')).toBeVisible()
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)
      expect(overflow, `horizontal overflow: ${width}px ${route}`).toBe(false)
    }
  }
  expect(errors).toEqual([])
})

