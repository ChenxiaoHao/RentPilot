import { test, expect } from '@playwright/test'

test('低预算条件填入草稿且不发送', async ({ page }) => {
 let calls=0
 await page.route('**/api/ai/search', route => { calls++; return route.abort() })
 await page.goto('/#/chat')
 await page.getByRole('button',{name:'编辑找房条件'}).first().click()
 const dialog=page.getByRole('dialog',{name:'找房条件'})
 await dialog.getByRole('textbox',{name:'工作地点'}).fill('金科路')
 await dialog.getByRole('textbox',{name:'最高租金'}).fill('900')
 await dialog.getByRole('button',{name:'合租',exact:true}).click()
 await dialog.getByRole('button',{name:'骑行',exact:true}).click()
 await dialog.getByRole('button',{name:'填入对话'}).click()
 await expect(page.getByRole('textbox',{name:'找房需求'})).toHaveValue(/最高900元.*合租.*骑行/)
 expect(calls).toBe(0)
})
const source={id:'mock',rowCount:1,sha256:'a'.repeat(64),dataKind:'SIMULATED'}
const filters={districts:[{value:'闵行区',count:1},{value:'浦东新区',count:1}],towns:[{district:'闵行区',value:'吴泾',count:1},{district:'浦东新区',value:'陆家嘴',count:1}],metroLines:[],source}
async function open(page: import('@playwright/test').Page) {
 await page.goto('/#/chat'); await page.getByRole('button',{name:'编辑找房条件'}).first().click(); return page.getByRole('dialog',{name:'找房条件'})
}
test('区域联动、保留未提交条件和预算错误',async ({page})=>{
 await page.route('**/api/listing-filters',route=>route.fulfill({json:filters}))
 const d=await open(page)
 await expect(d.getByRole('textbox',{name:'工作地点'})).toHaveValue('')
 await expect(d.getByRole('button',{name:'不限',exact:true}).last()).toHaveAttribute('aria-pressed','true')
 await d.getByLabel('工作地区').selectOption('闵行区');await d.getByLabel('工作街镇').selectOption('吴泾');await d.getByLabel('工作地区').selectOption('浦东新区');await expect(d.getByLabel('工作街镇')).toHaveValue('')
 await d.getByLabel('工作地点').fill('金科路');await d.getByLabel('最低租金').fill('900');await d.getByLabel('最高租金').fill('500');await d.getByRole('button',{name:'驾车'}).click();await d.getByRole('button',{name:'填入对话'}).click();await expect(d.getByText('最低租金不能高于最高租金')).toBeVisible()
 await d.getByLabel('最低租金').fill('-1');await d.getByRole('button',{name:'填入对话'}).click();await expect(d.getByText(/请输入非负金额/)).toBeVisible()
 await d.getByRole('button',{name:'关闭找房条件'}).click();await page.getByRole('button',{name:'编辑找房条件'}).first().click();await expect(d.getByLabel('最低租金')).toHaveValue('-1')
 await d.getByLabel('最低租金').fill('0');await d.getByLabel('最高租金').fill('500');await d.getByRole('button',{name:'填入对话'}).click();await expect(page.getByLabel('找房需求')).toHaveValue(/最低0元.*最高500元/)
})
test('区域目录失败可重试并自由填写',async ({page})=>{
 let count=0;await page.route('**/api/listing-filters',route=>++count===1?route.fulfill({status:500,body:'error'}):route.fulfill({json:filters}))
 const d=await open(page);await expect(d.getByText(/区域目录暂不可用/)).toBeVisible();await d.getByLabel('工作地点').fill('独立地址');await d.getByRole('button',{name:'重试'}).click();await expect(d.getByLabel('工作地区').locator('option')).toHaveCount(3)
})
for(const [width,height] of [[320,640],[402,874],[768,900],[402,560]]) test(`布局 ${width}x${height}`,async ({page})=>{
 await page.setViewportSize({width:width!,height:height!});await page.route('**/api/listing-filters',route=>route.fulfill({json:filters}));const d=await open(page)
 const body=d.locator('.sheet-body');expect(await body.evaluate(el=>el.scrollWidth<=el.clientWidth)).toBe(true)
 const footer=d.getByRole('button',{name:'填入对话'});const rect=await footer.boundingBox();expect(rect!.height).toBeGreaterThanOrEqual(44);expect(rect!.y+rect!.height).toBeLessThanOrEqual(height!)
 await body.evaluate(el=>el.scrollTop=el.scrollHeight);await expect(d.getByRole('button',{name:'60 分钟'})).toBeVisible();await page.mouse.move(0,0);await page.screenshot({path:`test-results/conditions-${width}-${height}.png`, animations:"disabled"})
})

test('已确认结果回填并在切换账号和新对话时隔离',async({page})=>{
 const profile={id:'other',username:'other',nickname:'用户',createdAt:'2026-01-01',favoritesCount:0,historyCount:0,plannedCount:0}
 await page.route('**/api/me/favorite-ids',route=>route.fulfill({json:[]}));await page.route('**/api/me',route=>route.fulfill({json:profile}));await page.route('**/api/listing-filters',route=>route.fulfill({json:filters}))
 let calls=0;await page.route('**/api/ai/search',route=>{calls++;return route.fulfill({json:{status:'NO_MATCH',message:'没有匹配',criteria:{district:'浦东新区',town:'陆家嘴',maxRent:900,rentalType:'SHARED'},recommendations:[],total:0,limitations:[],dataKind:'SIMULATED',source,engine:'SPRING_AI',commute:{state:'CHECKED',mode:'CYCLING',maxMinutes:36,workplace:{id:'x',name:'吴泾宝龙',address:'尚义路115号',district:'闵行区',adcode:'310112',longitude:121.4,latitude:31.1},checkedCount:0,candidateCount:0,unverifiedCount:0,coverage:'COMPLETE',estimated:true}}})})
 await page.goto('/#/chat');await page.getByLabel('找房需求').fill('找房');await page.getByRole('button',{name:'发送消息'}).click();await expect(page.getByText('没有匹配')).toBeVisible();await page.getByRole('button',{name:'编辑找房条件'}).click()
 const d=page.getByRole('dialog',{name:'找房条件'});await expect(d.getByLabel('工作地区')).toHaveValue('闵行区');await expect(d.getByLabel('工作地点')).toHaveValue(/吴泾宝龙.*尚义路115号/);await expect(d.getByLabel('最高租金')).toHaveValue('900');await expect(d.getByRole('button',{name:'合租'})).toHaveAttribute('aria-pressed','true');await expect(d.getByRole('button',{name:'骑行'})).toHaveAttribute('aria-pressed','true');await expect(d.getByText('单程通勤时间 · 36 分钟')).toBeVisible();expect(calls).toBe(1)
 await d.getByLabel('工作地点').fill('未提交地址');await d.getByLabel('最高租金').fill('500');await d.getByRole('button',{name:'关闭找房条件'}).click();await page.getByRole('button',{name:'编辑找房条件'}).click();await expect(d.getByLabel('工作地点')).toHaveValue('未提交地址');await expect(d.getByLabel('最高租金')).toHaveValue('500')
 await page.evaluate(profile=>{localStorage.setItem('rentpilot-account-v1',JSON.stringify({token:'a'.repeat(43),profile,expiresAt:'2030-01-01T00:00:00Z'}));window.dispatchEvent(new StorageEvent('storage',{key:'rentpilot-account-v1'}))},profile);await expect(d).not.toBeVisible();await expect(page.getByLabel('找房需求')).toHaveValue('');await page.getByRole('button',{name:'编辑找房条件'}).first().click();await expect(d.getByLabel('工作地点')).toHaveValue('');await d.getByLabel('工作地点').fill('旧账号草稿');await d.getByRole('button',{name:'关闭找房条件'}).click();await page.getByLabel('找房需求').fill('新查询');await page.getByRole('button',{name:'发送消息'}).click();await expect(page.getByText('没有匹配')).toBeVisible();await page.getByRole('button',{name:'新建对话'}).click();await page.getByRole('button',{name:'编辑找房条件'}).first().click();await expect(d.getByLabel('工作地点')).toHaveValue('')
})

test('切换历史对话清除未发送草稿',async({page})=>{
 await page.route('**/api/ai/search',route=>route.fulfill({json:{status:'NO_MATCH',message:'无结果',criteria:{},recommendations:[],total:0,limitations:[],dataKind:'SIMULATED',source,engine:'SPRING_AI'}}))
 await page.goto('/#/chat');await page.getByLabel('找房需求').fill('历史查询');await page.getByRole('button',{name:'发送消息'}).click();await expect(page.getByText('无结果')).toBeVisible();await page.getByRole('button',{name:'新建对话'}).click();await page.getByLabel('找房需求').fill('旧的未发送草稿');await page.getByRole('button',{name:'查看历史对话'}).click();await page.getByRole('dialog',{name:'历史对话'}).getByRole('button',{name:/历史查询/}).click();await expect(page.getByLabel('找房需求')).toHaveValue('')
})
