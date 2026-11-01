import { describe, expect, it } from 'vitest'
import { validateBudget } from '../src/services/search-conditions'

describe('search budget', () => {
  it('accepts low and unlimited budgets', () => {
    expect(validateBudget('', '900')).toBe('')
    expect(validateBudget('0', '')).toBe('')
  })
  it('rejects invalid and reversed bounds', () => {
    for (const pair of [['-1', ''], ['abc', ''], ['1.001', ''], ['900', '500'], ['1000000000', ''], ['9'.repeat(400), '']]) {
      expect(validateBudget(pair[0]!, pair[1]!)).not.toBe('')
    }
  })
})

import { buildConditions, conditionsFromResult, emptyConditions } from '../src/services/search-conditions'
import type { SearchResult } from '../src/services/ai'

describe('condition drafts', () => {
 it('explicitly clears old bounds and rental restrictions', () => {
  const c=emptyConditions(); c.address='园区'; c.mode='WALKING'; c.maxRent='900'
  expect(buildConditions(c)).toMatch(/不设最低租金.*最高900元.*出租方式不限，居室不限.*步行/)
  c.minRent='0'; c.maxRent=''; c.rental='SHARED'
  expect(buildConditions(c)).toMatch(/最低0元.*上限不限.*合租.*取消此前整租及居室限制/)
 })
 it.each([['WALKING','步行'],['CYCLING','骑行'],['TRANSIT','公共交通'],['DRIVING','驾车']] as const)('includes %s', (mode,label) => {
  const c=emptyConditions(); c.mode=mode; expect(buildConditions(c)).toContain(label)
 })
 it('prefills workplace rather than housing location', () => {
  const result={criteria:{district:'徐汇区',town:'漕河泾',maxRent:900},commute:{workplace:{name:'科技园',address:'金科路1号',district:'浦东新区'},mode:'CYCLING',maxMinutes:36}} as unknown as SearchResult
  const c=conditionsFromResult(result); expect(c.district).toBe('浦东新区'); expect(c.town).toBe(''); expect(c.address).toContain('科技园'); expect(c.address).toContain('金科路1号'); expect(c.minutes).toBe(36); const whole=conditionsFromResult({...result,criteria:{minRent:0,maxRent:900,rentalType:'ENTIRE',bedrooms:2}}); expect(whole.minRent).toBe('0'); expect(whole.rental).toBe('ENTIRE'); expect(whole.rooms).toBe('2')
 })
})
