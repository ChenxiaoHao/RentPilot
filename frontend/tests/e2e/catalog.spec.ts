import { test, expect } from '@playwright/test'

test('数据库列表展示1500条，筛选真实得到68条并查看来源信息', async ({ page }) => {
  await page.goto('/#/listings')
  await expect(page.getByTestId('catalog-total')).toHaveText('1,500 套模拟房源')
  await expect(page.getByTestId('catalog-card')).toHaveCount(20)
  await expect(page.getByTestId('catalog-card').first()).toContainText('潍坊一村')
  await page.getByRole('combobox', { name: '行政区' }).selectOption('浦东新区')
  await page.getByRole('combobox', { name: '出租方式' }).selectOption('ENTIRE')
  await page.getByRole('combobox', { name: '卧室数' }).selectOption('1')
  await page.getByRole('combobox', { name: '最高月租' }).selectOption('3000')
  await expect(page.getByTestId('catalog-total')).toHaveText('68 套模拟房源')
  await page.getByTestId('catalog-card').first().click()
  const detail = page.locator('.real-detail')
  await expect(detail).toBeVisible()
  await expect(detail).toContainText('SH-MOCK-')
  await expect(detail).toContainText('非该房源实拍')
  await page.goBack()
  await expect(detail).toHaveCount(0)
  await expect(page.getByRole('combobox', { name: '行政区' })).toHaveValue('浦东新区')
})

test('后端分页继续加载不重复，刷新保留搜索条件', async ({ page }) => {
  await page.goto('/#/listings')
  await expect(page.getByTestId('catalog-card')).toHaveCount(20)
  await page.getByRole('button', { name: '加载更多房源' }).click()
  await expect(page.getByTestId('catalog-card')).toHaveCount(40)
  const ids = await page.getByTestId('catalog-card').evaluateAll(nodes => nodes.map(node => node.getAttribute('data-id')))
  expect(new Set(ids).size).toBe(40)
  await page.getByRole('searchbox', { name: '搜索房源' }).fill('潍坊一村')
  await expect(page.getByTestId('catalog-total')).toHaveText('9 套模拟房源')
  await page.reload()
  await expect(page.getByRole('searchbox', { name: '搜索房源' })).toHaveValue('潍坊一村')
  await expect(page.getByTestId('catalog-card')).toHaveCount(9)
})

test('无结果与连接失败分开，失败重试成功且不冒充示例结果', async ({ page }) => {
  await page.goto('/#/listings?q=肯定不存在的小区')
  await expect(page.getByRole('heading', { name: '暂时没有符合条件的房源' })).toBeVisible()
  await page.route('**/api/listings?**', route => route.abort('failed'))
  await page.getByRole('button', { name: '清除筛选条件' }).click()
  await expect(page.getByRole('heading', { name: '房源暂时加载不了' })).toBeVisible()
  await expect(page.getByTestId('catalog-card')).toHaveCount(0)
  await page.unroute('**/api/listings?**')
  await page.getByRole('button', { name: '重试加载房源' }).click()
  await expect(page.getByTestId('catalog-total')).toHaveText('1,500 套模拟房源')
})

test('加载下一页失败保留已加载房源，并能从失败页继续重试', async ({ page }) => {
  await page.goto('/#/listings')
  await expect(page.getByTestId('catalog-card')).toHaveCount(20)
  await page.route('**/api/listings?**', route => new URL(route.request().url()).searchParams.get('page') === '1' ? route.abort() : route.continue())
  await page.getByRole('button', { name: '加载更多房源' }).click()
  await expect(page.getByRole('button', { name: '重试加载更多房源' })).toBeVisible()
  await expect(page.getByTestId('catalog-card')).toHaveCount(20)
  await page.unroute('**/api/listings?**')
  await page.getByRole('button', { name: '重试加载更多房源' }).click()
  await expect(page.getByTestId('catalog-card')).toHaveCount(40)
})

test('迟到的搜索结果不能覆盖用户的新条件', async ({ page }) => {
  await page.goto('/#/listings')
  await expect(page.getByTestId('catalog-card')).toHaveCount(20)
  let release!: () => void, arrived!: () => void, finished!: () => void
  const held = new Promise<void>(resolve => { release = resolve })
  const ready = new Promise<void>(resolve => { arrived = resolve })
  const done = new Promise<void>(resolve => { finished = resolve })
  await page.route('**/api/listings?**', async route => {
    if (new URL(route.request().url()).searchParams.get('q') !== '潍坊一村') return route.continue()
    const response = await route.fetch()
    arrived()
    await held
    try { await route.fulfill({ response }) } catch { /* The old fetch may already be aborted. */ }
    finished()
  })
  try {
    await page.getByRole('searchbox', { name: '搜索房源' }).fill('潍坊一村')
    await page.getByRole('searchbox', { name: '搜索房源' }).press('Enter')
    await ready
    await page.getByRole('searchbox', { name: '搜索房源' }).fill('确实不存在的小区')
    await page.getByRole('searchbox', { name: '搜索房源' }).press('Enter')
    await expect(page.getByTestId('catalog-total')).toHaveText('0 套模拟房源')
  } finally { release() }
  await done
  await expect(page.getByTestId('catalog-card')).toHaveCount(0)
})

test('待核验位置与设施未知不会显示成零距离或确定设施', async ({ page }) => {
  await page.goto('/#/listings?q=位置待核验')
  await expect(page.getByTestId('catalog-total')).toHaveText('14 套模拟房源')
  await page.getByTestId('catalog-card').first().click()
  const detail = page.locator('.real-detail')
  await expect(detail).toContainText('详细位置待核验')
  await expect(detail).toContainText('地铁步行信息待补充')
  await expect(detail.getByText('未知', { exact: true }).first()).toBeVisible()
})

test('列表在不同手机宽度无横向溢出，键盘弹出时隐藏胶囊导航', async ({ page }) => {
  for (const width of [320, 360, 402, 480]) {
    await page.setViewportSize({ width, height: 874 })
    await page.goto('/#/listings')
    await expect(page.getByTestId('catalog-card')).toHaveCount(20)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width)
    await page.getByRole('button', { name: '加载更多房源' }).scrollIntoViewIfNeeded()
    const button = await page.getByRole('button', { name: '加载更多房源' }).boundingBox()
    const nav = await page.getByRole('navigation', { name: '主导航' }).boundingBox()
    expect(button!.y + button!.height).toBeLessThanOrEqual(nav!.y)
  }
  await page.getByRole('searchbox', { name: '搜索房源' }).focus()
  await page.setViewportSize({ width: 402, height: 470 })
  await expect(page.getByRole('navigation', { name: '主导航' })).toBeHidden()
})
