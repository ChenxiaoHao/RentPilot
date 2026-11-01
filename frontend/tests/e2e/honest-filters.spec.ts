import { test, expect } from '@playwright/test'

test('empty map result preserves unset budget and commute when returning or changing a filter', async ({ page }) => {
  await page.goto('/#/map')
  await page.getByRole('combobox', { name: '户型筛选' }).selectOption('two')
  await page.getByRole('link', { name: '查看条件调整建议' }).click()
  await expect(page.getByText('预算不限', { exact: false }).first()).toBeVisible()
  await expect(page.getByText('通勤不限', { exact: false }).first()).toBeVisible()
  await page.getByRole('link', { name: '返回房源地图' }).click()
  await expect(page.getByRole('combobox', { name: '租金预算' })).toHaveValue('0')
  await expect(page.getByRole('combobox', { name: '户型筛选' })).toHaveValue('two')
  await expect(page.getByText(/通勤 ≤/)).toHaveCount(0)
  await page.getByRole('link', { name: '查看条件调整建议' }).click()
  await page.getByRole('button', { name: '调整户型为一居' }).click()
  await expect(page.getByRole('combobox', { name: '户型筛选' })).toHaveValue('one')
  await expect(page.getByRole('combobox', { name: '租金预算' })).toHaveValue('0')
  await expect(page.getByText(/通勤 ≤/)).toHaveCount(0)
})

test('generation explicitly identifies its conditions as an example', async ({ page }) => {
  await page.goto('/#/generating')
  await expect(page.getByText(/固定示例条件/)).toBeVisible()
})

test('a suggested nonpreset rent is visible in the map filter', async ({ page }) => {
  await page.goto('/#/empty?budget=4200&commute=0&type=two')
  await page.getByRole('button', { name: '应用此建议' }).first().click()
  await expect(page.getByRole('combobox', { name: '租金预算' })).toHaveValue('4700')
  await expect(page.getByRole('combobox', { name: '户型筛选' })).toHaveValue('two')
})
