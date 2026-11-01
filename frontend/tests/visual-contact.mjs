import {chromium} from '@playwright/test'
const browser=await chromium.launch({channel:'chrome',headless:true})
const profile={id:'a',username:'demo',nickname:'演示租客',createdAt:'2026-10-28T00:00:00Z',favoritesCount:0,historyCount:0,plannedCount:0}
for(const size of [{width:320,height:640},{width:390,height:844},{width:402,height:874}]){
  for(const kind of ['empty','list']){
    const context=await browser.newContext({viewport:size})
    await context.addInitScript(profile=>localStorage.setItem('rentpilot-account-v1',JSON.stringify({token:'a'.repeat(43),profile,expiresAt:'2027-01-01T00:00:00Z'})),profile)
    const page=await context.newPage()
    await page.route('**/api/**',route=>{
      const path=new URL(route.request().url()).pathname
      if(path==='/api/me')return route.fulfill({json:profile})
      if(path==='/api/me/favorite-ids')return route.fulfill({json:[]})
      if(path==='/api/conversations')return route.fulfill({json:kind==='empty'?[]:[{id:'c1',listingId:'SH-MOCK-000001',tenantId:'a',ownerId:'b',tenantNickname:'演示租客',ownerNickname:'张先生'},{id:'c2',listingId:'SH-MOCK-000002',tenantId:'a',ownerId:'c',tenantNickname:'演示租客',ownerNickname:'李女士'}]})
      return route.fulfill({status:404,json:{message:'不存在'}})
    })
    await page.goto('http://127.0.0.1:5179/#/messages')
    await page.getByText(kind==='empty'?'还没有对话':'张先生').waitFor()
    await page.screenshot({path:`../docs/contact-redesign-2026-10-29/screens/${kind}-${size.width}.png`})
    await context.close()
  }
}
await browser.close()
