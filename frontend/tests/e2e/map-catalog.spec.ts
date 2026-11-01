import {test,expect} from '@playwright/test'

test('地图展示六套房源，悬停或点击价格查看信息并可打开详情',async({page})=>{
  await page.addInitScript(()=>{
    class FakeMap {
      host:HTMLElement
      listeners:Record<string,()=>void>={}
      center=[121.480123456,31.230123456]
      zoom=11
      constructor(host:HTMLElement,options:{resizeEnable?:boolean}){this.host=host;host.dataset.resizeEnabled=String(options.resizeEnable);host.addEventListener('test-move',()=>{this.center=[121.6,31.3];this.listeners.moveend?.()})}
      on(event:string,callback:()=>void){this.listeners[event]=callback}
      getCenter(){return {getLng:()=>this.center[0],getLat:()=>this.center[1]}}
      getZoom(){return this.zoom}
      setZoom(value:number){this.zoom=value;this.listeners.zoomend?.()}
      getBounds(){return {getSouthWest:()=>({getLng:()=>120,getLat:()=>30}),getNorthEast:()=>({getLng:()=>123,getLat:()=>32.5})}}
      lngLatToContainer(){return {getX:()=>this.host.clientWidth/2,getY:()=>this.host.clientHeight/2}}
      destroy(){}
    }
    ;(window as unknown as {AMap:unknown}).AMap={Map:FakeMap}
  })
  await page.goto('/#/listings?bedrooms=3&maxRent=1500')
  await page.getByTestId('catalog-card').first().waitFor()
  await page.getByRole('link',{name:'地图找房'}).click()
  await expect(page.getByLabel('高德地图')).toHaveAttribute('data-resize-enabled','true')
  await page.getByRole('button',{name:'筛选',exact:true}).click()
  await expect(page.getByText('3 室户型')).toBeVisible()
  await page.getByRole('button',{name:'重置',exact:true}).click()
  await page.getByRole('button',{name:'查看房源',exact:true}).click()
  await expect(page.getByTestId('map-pin')).toHaveCount(6)
  await expect(page.locator('.map-results')).toHaveCount(0)
  await expect(page.locator('.map-crosshair')).toBeVisible()
  const boxes=await page.getByTestId('map-pin').evaluateAll(nodes=>nodes.map(node=>{const r=node.getBoundingClientRect();return {x:r.x,y:r.y}}))
  expect(new Set(boxes.map(b=>`${b.x},${b.y}`)).size).toBe(6)
  const moved=page.waitForResponse(response=>{const url=new URL(response.url());return url.pathname.endsWith('/listings/map')&&Number(url.searchParams.get('centerLongitude'))===121.6&&Number(url.searchParams.get('centerLatitude'))===31.3})
  await page.getByLabel('高德地图').dispatchEvent('test-move')
  expect(new URL((await moved).url()).searchParams.get('limit')).toBe('6')
  await expect(page.getByRole('status')).toHaveCount(0)
  await expect(page.getByTestId('map-pin')).toHaveCount(6)
  const pin=page.getByTestId('map-pin').first(),price=await pin.innerText()
  await pin.hover()
  await expect(page.getByTestId('map-preview')).toBeVisible()
  await expect(page.getByTestId('map-preview')).toContainText(price)
  await page.getByRole('button',{name:'关闭房源信息'}).click()
  await expect(page.getByTestId('map-preview')).toHaveCount(0)
  await pin.click()
  await expect(page.getByTestId('map-preview')).toBeVisible()
  await page.getByTestId('map-preview').getByRole('link',{name:'查看房源详情'}).click()
  await expect(page.locator('.real-detail')).toBeVisible()
})

test('不足六套时显示实际数量，手机点击后信息不会自动消失',async({browser})=>{
  const context=await browser.newContext({baseURL:'http://127.0.0.1:5178',hasTouch:true,isMobile:true,viewport:{width:320,height:640}})
  const page=await context.newPage()
  await page.addInitScript(()=>{
    class FakeMap {
      constructor(_host:HTMLElement,_options:object){}
      on(_event:string,_callback:()=>void){}
      getCenter(){return {getLng:()=>121.48,getLat:()=>31.23}}
      getZoom(){return 11} setZoom(_value:number){}
      getBounds(){return {getSouthWest:()=>({getLng:()=>120,getLat:()=>30}),getNorthEast:()=>({getLng:()=>123,getLat:()=>32.5})}}
      lngLatToContainer(){return {getX:()=>160,getY:()=>220}} destroy(){}
    }
    ;(window as unknown as {AMap:unknown}).AMap={Map:FakeMap}
  })
  await page.route('**/api/listings/map?**',async route=>{
    const upstream=await route.fetch(),data=await upstream.json()
    data.items=data.items.slice(0,2);data.total=2;data.returned=2;data.truncated=false
    await route.fulfill({response:upstream,json:data})
  })
  await page.goto('/#/find-map')
  await expect(page.getByTestId('map-pin')).toHaveCount(2)
  await page.getByTestId('map-pin').nth(1).tap()
  await expect(page.getByTestId('map-preview')).toBeVisible()
  await page.waitForTimeout(350)
  await expect(page.getByTestId('map-preview')).toBeVisible()
  await context.close()
})
