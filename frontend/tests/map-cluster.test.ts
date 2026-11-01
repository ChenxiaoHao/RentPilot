import {describe,it,expect} from 'vitest'
import {clusterMapListings} from '../src/services/map-cluster'
import type {MapListing} from '../src/services/map-catalog'
function item(id:string,longitude:number,latitude:number):MapListing{return {id,longitude,latitude,name:'示例',district:'浦东新区',town:'潍坊',community:'小区',address:'地址',rentalType:'SHARED',layout:'一居',bedrooms:1,rent:2150,area:29,nearestMetro:'塘桥',metroLines:[],metroWalkMinutes:null,dataKind:'SIMULATED',coordinateSystem:'GCJ-02',coordinateScope:'COMMUNITY'}}
describe('地图房源聚合',()=>{
  it('按屏幕距离合并近邻，保留所有房源和中心坐标',()=>{
    const rows=[item('SH-MOCK-000001',121,31),item('SH-MOCK-000002',121.01,31),item('SH-MOCK-000003',122,31)]
    const groups=clusterMapListings(rows,(lng)=>({x:(lng-121)*1000,y:20}))
    expect(groups).toHaveLength(2)
    expect(groups[0]!.items.map(v=>v.id)).toEqual(['SH-MOCK-000001','SH-MOCK-000002'])
    expect(groups[0]!.longitude).toBeCloseTo(121.005)
    expect(groups.flatMap(v=>v.items)).toHaveLength(3)
  })
  it('不能投影时只聚合同一坐标，避免把不同小区随意拼在一起',()=>{
    const rows=[item('SH-MOCK-000001',121,31),item('SH-MOCK-000002',121,31),item('SH-MOCK-000003',122,31)]
    const groups=clusterMapListings(rows,()=>null)
    expect(groups.map(v=>v.items.length)).toEqual([2,1])
  })
})
