import { test, expect } from '@playwright/test'

test('聊天首页的浏览全部房源进入已接通的数据库列表', async ({ page }) => {
  await page.goto('/#/chat')
  await page.getByRole('link', { name: '浏览全部房源' }).click()
  await expect(page).toHaveURL(/#\/listings$/)
  await expect(page.getByTestId('catalog-total')).toHaveText('1,500 套模拟房源')
})
