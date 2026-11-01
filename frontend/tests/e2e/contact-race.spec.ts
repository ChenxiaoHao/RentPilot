import { test, expect } from '@playwright/test'

const profile=(id:string)=>({id,username:id,nickname:id,createdAt:'2026-10-28T00:00:00Z',favoritesCount:0,historyCount:0,plannedCount:0})
const session=(id:string)=>({token:id.repeat(43),profile:profile(id),expiresAt:'2027-01-01T00:00:00Z'})
const conversation={id:'1',listingId:'SH-MOCK-000001',tenantId:'a',ownerId:'b',tenantNickname:'a',ownerNickname:'b',messages:[]}

test('late message send cannot change a newly switched account or its draft',async({page})=>{
  await page.addInitScript(value=>localStorage.setItem('rentpilot-account-v1',JSON.stringify(value)),session('a'))
  let release!:()=>void
  const pending=new Promise<void>(resolve=>{release=resolve})
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname
    if(path.endsWith('/messages')&&route.request().method()==='POST'){await pending;await route.fulfill({json:{id:'m',senderId:'a',content:'old',createdAt:new Date().toISOString()}}).catch(()=>{});return}
    if(path==='/api/me')return route.fulfill({json:profile(route.request().headers().authorization?.includes('bbbb')?'b':'a')})
    if(path==='/api/me/favorite-ids')return route.fulfill({json:[]})
    return route.fulfill({json:conversation})
  })
  await page.goto('/#/messages/1')
  await page.getByLabel('输入消息').fill('old draft')
  await page.getByRole('button',{name:'发送',exact:true}).click()
  await page.evaluate(value=>{localStorage.setItem('rentpilot-account-v1',JSON.stringify(value));window.dispatchEvent(new StorageEvent('storage',{key:'rentpilot-account-v1'}))},session('b'))
  await expect(page.getByLabel('输入消息')).toHaveValue('')
  await page.getByLabel('输入消息').fill('new draft')
  release()
  await expect(page.getByLabel('输入消息')).toHaveValue('new draft')
  await expect(page.getByRole('button',{name:'发送',exact:true})).toBeEnabled()
})
