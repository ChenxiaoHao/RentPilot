export interface MapAnchor { id:string; x:number; y:number }
export interface MapLabel extends MapAnchor { anchorX:number; anchorY:number }

/** Arrange labels in screen pixels only; anchorX/Y always remain the real projected location. */
export function layoutMapLabels(anchors:MapAnchor[], width:number, height:number):MapLabel[] {
  const clamp=(n:number,min:number,max:number)=>Math.max(min,Math.min(Math.max(min,max),n))
  const placed:MapLabel[]=[]
  for(const anchor of anchors){
    const candidates:{x:number;y:number}[]=[]
    for(const radius of [0,60,105,150,200,250]){
      const count=radius===0?1:16
      for(let i=0;i<count;i++){
        const angle=-Math.PI/2+i*2*Math.PI/count
        candidates.push({x:clamp(anchor.x+radius*Math.cos(angle),44,width-44),y:clamp(anchor.y-30+radius*Math.sin(angle),24,height-24)})
      }
    }
    const free=candidates.find(p=>placed.every(other=>Math.abs(p.x-other.x)>=82||Math.abs(p.y-other.y)>=48))
    const best=free??candidates.reduce((a,b)=>{
      const score=(p:{x:number;y:number})=>placed.reduce((sum,o)=>sum+Math.max(0,82-Math.abs(p.x-o.x))*Math.max(0,48-Math.abs(p.y-o.y)),0)
      return score(b)<score(a)?b:a
    })
    placed.push({id:anchor.id,x:best.x,y:best.y,anchorX:anchor.x,anchorY:anchor.y})
  }
  return placed
}
