import {afterEach,describe,expect,it,vi} from 'vitest'

const platform=vi.hoisted(()=>({native:false}))
vi.mock('@capacitor/core',()=>({Capacitor:{isNativePlatform:()=>platform.native},CapacitorHttp:{}}))
afterEach(()=>vi.unstubAllGlobals())
const bounds={minLongitude:121.1234567,minLatitude:31.1234567,maxLongitude:121.6543211,maxLatitude:31.6543211}
const row={id:'SH-MOCK-000001',longitude:121.52,latitude:31.22,name:'示例',district:'浦东新区',town:'潍坊',community:'潍坊一村',address:'地址',rentalType:'SHARED',layout:'一居',bedrooms:1,rent:2150,area:29.2,nearestMetro:'塘桥',metroLines:['4号线'],metroWalkMinutes:19,dataKind:'SIMULATED',coordinateSystem:'GCJ-02',coordinateScope:'COMMUNITY'}
const body={bounds,items:[row],total:2,returned:1,limit:1,truncated:true,dataKind:'SIMULATED',coordinateSystem:'GCJ-02',coordinateScope:'COMMUNITY',source:{id:'shanghai-simulation-v1',dataKind:'SIMULATED',rowCount:1500}}
describe('地图房源接口',()=>{
  it('向外取整范围、携带筛选并保留截断信息',async()=>{
    const fetch=vi.fn().mockResolvedValue(new Response(JSON.stringify(body)))
    vi.stubGlobal('fetch',fetch)
    const {fetchMapListings}=await import('../src/services/map-catalog')
    const result=await fetchMapListings(bounds,{maxRent:3000,rentalType:'SHARED'},1)
    const url=new URL(fetch.mock.calls[0]![0],'http://localhost')
    expect(url.pathname).toBe('/api/listings/map')
    expect(Object.fromEntries(url.searchParams)).toEqual({minLongitude:'121.123456',minLatitude:'31.123456',maxLongitude:'121.654322',maxLatitude:'31.654322',limit:'1',maxRent:'3000',rentalType:'SHARED'})
    expect(result.truncated).toBe(true)
  })
  it('拒绝缺失坐标或虚假的真实数据标记',async()=>{
    const {fetchMapListings}=await import('../src/services/map-catalog')
    for(const patch of [{longitude:null},{dataKind:'REAL'}]){
      vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({...body,items:[{...row,...patch}]}))))
      await expect(fetchMapListings(bounds,{},1)).rejects.toThrow('不完整')
    }
  })
})
