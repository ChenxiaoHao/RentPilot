import type { TravelMode, SearchResult } from './ai'
export interface SearchConditions { district:string; town:string; address:string; minRent:string; maxRent:string; rental:'ANY'|'ENTIRE'|'SHARED'; rooms:string; mode:TravelMode|''; minutes:number }
export const emptyConditions = ():SearchConditions => ({district:'',town:'',address:'',minRent:'',maxRent:'',rental:'ANY',rooms:'',mode:'',minutes:30})
export function validateBudget(min: string, max: string): string {
  const valid = (value: string) => value === '' || /^\d{1,9}(?:\.\d{1,2})?$/.test(value)
  if (!valid(min) || !valid(max)) return '请输入非负金额，最多两位小数'
  if (min !== '' && max !== '' && Number(min) > Number(max)) return '最低租金不能高于最高租金'
  return ''
}
export function conditionsFromResult(result:SearchResult):SearchConditions {
 const c=emptyConditions(), criteria=result.criteria, commute=result.commute
 c.district=commute?.workplace?.district || ''; const place=commute?.workplace; c.address=place ? (place.address && !place.address.includes(place.name) ? `${place.name}（${place.address}）` : place.address || place.name) : ''
 c.mode=commute?.mode || ''; c.minutes=commute?.maxMinutes ?? 30
 if(typeof criteria.minRent==='number') c.minRent=String(criteria.minRent)
 if(typeof criteria.maxRent==='number') c.maxRent=String(criteria.maxRent)
 if(criteria.rentalType==='ENTIRE'||criteria.rentalType==='SHARED') c.rental=criteria.rentalType
 if(c.rental==='ENTIRE' && typeof criteria.bedrooms==='number') c.rooms=String(criteria.bedrooms)
 return c
}
export function buildConditions(c:SearchConditions):string {
 const modes:Record<TravelMode,string>={WALKING:'步行',CYCLING:'骑行',TRANSIT:'公共交通（公交或地铁）',DRIVING:'驾车'}
 const budget=`${c.minRent ? `月租最低${c.minRent}元` : '不设最低租金'}，${c.maxRent ? `月租最高${c.maxRent}元` : '月租上限不限'}`
 const rental=c.rental==='ANY'?'出租方式不限，居室不限':c.rental==='SHARED'?'合租，居室不限，取消此前整租及居室限制':`整租，${c.rooms ? `${c.rooms}居` : '居室不限'}`
 return `我的工作地点在上海${c.district}${c.town}${c.address.trim()}。${budget}；${rental}。通勤方式${c.mode ? modes[c.mode] : ''}，单程${c.minutes}分钟以内。其他未提及的条件保持不变。`
}
