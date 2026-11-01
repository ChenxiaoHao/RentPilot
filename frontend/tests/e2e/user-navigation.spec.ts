import {test,expect} from '@playwright/test'

test('AI 首页及四个主入口，收藏和方案归入我的',async({page})=>{
  await page.goto('/')
  await expect(page).toHaveURL(/#\/chat/)
  await expect(page.getByLabel('找房需求')).toBeVisible()
  const nav=page.getByRole('navigation',{name:'主导航'})
  await expect(nav.getByRole('link')).toHaveText(['AI','找房','联系','我的'])
  await expect(nav.getByRole('link',{name:'收藏'})).toHaveCount(0)
  await expect(nav.getByRole('link',{name:'计划'})).toHaveCount(0)
  await nav.getByRole('link',{name:'我的'}).click()
  await expect(page.getByRole('link',{name:'我的收藏'})).toBeVisible()
  await expect(page.getByRole('link',{name:'我的方案'})).toBeVisible()
})

test('房源详情以联系为主操作，保留真实身份状态提示',async({page})=>{
  await page.goto('/#/listing/SH-MOCK-000001')
  await expect(page.locator('.real-detail')).toBeVisible()
  await expect(page.getByRole('button',{name:'联系房东'})).toBeVisible()
  await expect(page.getByRole('button',{name:'安排看房'})).toHaveCount(0)
})
