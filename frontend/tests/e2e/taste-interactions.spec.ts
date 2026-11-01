import { test, expect } from '@playwright/test'

test('home prompt fills an editable draft without sending', async ({ page }) => {
  await page.goto('/#/chat')
  await page.getByRole('button', { name: '通勤找房', exact: true }).click()
  await expect(page.getByRole('textbox', { name: '找房需求' })).toHaveValue(/工作地点和通勤时间/)
  await expect(page.locator('.message.user')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '发送消息' })).toBeEnabled()
})

test('Enter keeps a multiline draft and sending clears it exactly once', async ({ page }) => {
  await page.goto('/#/chat')
  const input = page.getByRole('textbox', { name: '找房需求' })
  await input.fill('我在张江上班')
  await input.press('End')
  await input.press('Enter')
  await input.pressSequentially('预算5000元')
  await expect(input).toHaveValue('我在张江上班\n预算5000元')
  await expect(page.locator('.message.user')).toHaveCount(0)
  await page.getByRole('button', { name: '发送消息' }).click()
  await expect(page.locator('.message.user')).toHaveCount(1)
  await expect(input).toHaveValue('')
  await expect(page.getByRole('button', { name: '发送消息' })).toBeDisabled()
})

test('reference image remains local and can be removed before sending', async ({ page }) => {
  await page.goto('/#/chat')
  await page.locator('input[type=file]').setInputFiles({
    name: 'reference.png', mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jK1sAAAAASUVORK5CYII=', 'base64'),
  })
  await expect(page.getByText('reference.png', { exact: true })).toBeVisible()
  await expect(page.getByText('仅本地预览，尚未接入图片理解', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '移除参考图片' }).click()
  await expect(page.getByText('reference.png', { exact: true })).toHaveCount(0)
  await expect(page.locator('.message.user')).toHaveCount(0)
})

test('chat actions have usable touch areas on narrow phones', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 640 })
  await page.goto('/#/chat')
  for (const name of ['上传参考图片', '发送消息']) {
    const rect = await page.getByRole('button', { name }).boundingBox()
    expect(rect?.width).toBeGreaterThanOrEqual(44)
    expect(rect?.height).toBeGreaterThanOrEqual(44)
  }
})
