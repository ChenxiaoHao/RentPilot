import { test, expect } from '@playwright/test'
import path from 'node:path'
const source={id:'fixture',rowCount:2,sha256:'a'.repeat(64),dataKind:'SIMULATED'}
const amenities=Object.fromEntries(['privateBathroom','privateKitchen','gas','balcony','airConditioning','refrigerator','washingMachine','waterHeater','elevator','furnished','petsAllowed','cookingAllowed'].map(k=>[k,'UNKNOWN']))
const home={id:'SH-MOCK-000001',name:'潍坊一村',district:'浦东新区',town:'潍坊',community:'潍坊一村',address:'潍坊路',rentalType:'ENTIRE',layout:'一室一厅',bedrooms:1,rent:4500,area:40,floor:'3/6',orientation:'南',decoration:'简装',nearestMetro:'世纪大道',metroLines:['2号线'],longitude:null,latitude:null,metroWalkMinutes:10,metroDistanceMeters:800,amenities,deposit:4500,paymentMethod:'押一付三',propertyFee:null,propertyFeePeriod:'UNSPECIFIED',availability:'待确认',description:'模拟数据',dataKind:'SIMULATED',dataNature:'模拟',locationStatus:'UNVERIFIED',datasetId:'fixture',sourceSha256:'a'.repeat(64)}
const base={criteria:{maxRent:5000},total:0,limitations:['房源为模拟数据，具体信息请核实。'],dataKind:'SIMULATED',source,engine:'SPRING_AI'}
for(const width of [320,402,768]) test(`AI visual states ${width}`,async ({page})=>{
 await page.setViewportSize({width,height:874})
 await page.route('**/api/listings?**',route=>route.fulfill({json:{items:[home,{...home,id:'SH-MOCK-000002'}],page:0,size:20,total:2,totalPages:1,dataKind:'SIMULATED',source}}))
 let state='NEEDS_INPUT'
 await page.route('**/api/ai/search',route=> state==='ERROR'?route.fulfill({status:503,json:{message:'服务暂不可用，请稍后重试'}}):route.fulfill({json:{...base,status:state,message:state==='MATCHES'?'找到符合条件的房源。':'请补充期望区域。',total:state==='MATCHES'?1:0,recommendations:state==='MATCHES'?[{listing:home,reasons:['月租不超过预算']}]:[]}}))
 const capture=async(name:string)=>{await expect(page.locator('body')).toBeVisible();const dimensions=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,width:innerWidth,footer:document.querySelector('.chat-footer')!.getBoundingClientRect().bottom,nav:document.querySelector('nav')?.getBoundingClientRect().top}));expect(dimensions.scroll).toBeLessThanOrEqual(dimensions.width);if(dimensions.nav)expect(dimensions.footer).toBeLessThanOrEqual(dimensions.nav+2);await page.screenshot({path:path.resolve(`../docs/ai-search-2026-10-30/${width}-${name}.png`)})}
 await page.goto('/#/chat'); await expect(page.locator('.home-preview')).toHaveCount(2); await capture('home')
 const input=page.getByRole('textbox',{name:'找房需求'}),send=page.getByRole('button',{name:'发送消息'})
 await input.fill('预算5000元');await send.click();await expect(page.getByText('请补充期望区域。')).toBeVisible();await capture('needs-input')
 state='MATCHES';await input.fill('浦东新区');await send.click();await expect(page.getByTestId('catalog-card')).toBeVisible();await page.getByTestId('catalog-card').scrollIntoViewIfNeeded();await capture('matches')
 state='ERROR';await input.fill('预算改为4000');await send.click();await expect(page.getByRole('alert')).toBeVisible();await page.getByRole('alert').scrollIntoViewIfNeeded();await capture('error')
})
