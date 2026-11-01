import { describe, expect, it } from 'vitest'
import { layoutMapLabels } from '../src/services/map-label-layout'

describe('map label placement', () => {
  it('keeps six labels readable when the homes share a real coordinate', () => {
    const homes=Array.from({length:6},(_,i)=>({id:String(i),x:160,y:220}))
    const labels=layoutMapLabels(homes,320,450)
    expect(labels).toHaveLength(6)
    for(let i=0;i<labels.length;i++){
      expect(labels[i]!.anchorX).toBe(160)
      expect(labels[i]!.anchorY).toBe(220)
      for(let j=0;j<i;j++){
        const a=labels[i]!,b=labels[j]!
        expect(Math.abs(a.x-b.x)>=82 || Math.abs(a.y-b.y)>=48).toBe(true)
      }
    }
  })
  it('uses only actual supplied homes and keeps labels inside the map', () => {
    const labels=layoutMapLabels([{id:'a',x:1,y:1},{id:'b',x:319,y:449}],320,450)
    expect(labels.map(x=>x.id)).toEqual(['a','b'])
    for(const label of labels){expect(label.x).toBeGreaterThanOrEqual(44);expect(label.x).toBeLessThanOrEqual(276);expect(label.y).toBeGreaterThanOrEqual(24);expect(label.y).toBeLessThanOrEqual(426)}
  })
})
