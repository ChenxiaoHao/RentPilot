import {test,expect} from '@playwright/test'
const profile={id:'a',username:'a',nickname:'测试租客',createdAt:'2026-10-28T00:00:00Z',favoritesCount:0,historyCount:0,plannedCount:0}
const session={token:'a'.repeat(43),profile,expiresAt:'2027-01-01T00:00:00Z'}

test('联系页支持搜索、刷新和认领演示房源',async({page})=>{
  await page.addInitScript(value=>localStorage.setItem('rentpilot-account-v1',JSON.stringify(value)),session)
  let requests=0
  await page.route('**/api/**',async route=>{
    const url=new URL(route.request().url())
    if(url.pathname==='/api/me')return route.fulfill({json:profile})
    if(url.pathname==='/api/me/favorite-ids')return route.fulfill({json:[]})
    if(url.pathname==='/api/conversations'){requests++;return route.fulfill({json:[{id:'c1',listingId:'SH-MOCK-000001',tenantId:'a',ownerId:'b',tenantNickname:'测试租客',ownerNickname:'张先生'},{id:'c2',listingId:'SH-MOCK-000002',tenantId:'a',ownerId:'c',tenantNickname:'测试租客',ownerNickname:'李女士'}]})}
    if(url.pathname.endsWith('/claim'))return route.fulfill({json:{claimed:true,ownerId:'a',ownerNickname:'测试租客',isMine:true}})
    return route.fulfill({status:404,json:{message:'不存在'}})
  })
  await page.goto('/#/messages')
  await expect(page.getByRole('banner').getByText('RentPilot')).toBeVisible()
  await expect(page.locator('.brand-mark')).toBeVisible()
  await expect(page.getByRole('link',{name:/张先生/})).toBeVisible()
  await page.getByLabel('搜索对话').fill('李女士')
  await expect(page.getByRole('link',{name:/张先生/})).toHaveCount(0)
  await expect(page.getByRole('link',{name:/李女士/})).toBeVisible()
  await page.getByRole('button',{name:'清空搜索'}).click()
  await page.getByRole('button',{name:'刷新对话'}).click()
  await expect.poll(()=>requests).toBeGreaterThan(1)
  await page.getByRole('button',{name:'演示工具'}).click()
  await page.locator('#claim-id').fill('SH-MOCK-000003')
  await page.getByRole('button',{name:'认领模拟房源',exact:true}).click()
  await expect(page.getByRole('status')).toContainText('已认领')
})

test('没有对话时提供找房入口，房源详情的演示认领链接仍可使用',async({page})=>{
  await page.addInitScript(value=>localStorage.setItem('rentpilot-account-v1',JSON.stringify(value)),session)
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname
    if(path==='/api/me')return route.fulfill({json:profile})
    if(path==='/api/me/favorite-ids'||path==='/api/conversations')return route.fulfill({json:[]})
    return route.fulfill({status:404,json:{message:'不存在'}})
  })
  await page.goto('/#/messages')
  await expect(page.getByRole('heading',{name:'还没有对话'})).toBeVisible()
  await expect(page.getByLabel('搜索对话')).toHaveCount(0)
  await expect(page.getByRole('link',{name:'去找房'})).toHaveAttribute('href','#/listings')
  await page.goto('/#/messages?claim=SH-MOCK-000001')
  await expect(page.getByRole('dialog',{name:'演示发布方'})).toBeVisible()
  await expect(page.locator('#claim-id')).toHaveValue('SH-MOCK-000001')
})
