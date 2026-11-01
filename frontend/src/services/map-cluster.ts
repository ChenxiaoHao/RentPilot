import type {MapListing} from './map-catalog'
export interface ListingCluster {key:string;longitude:number;latitude:number;items:MapListing[]}
export type ProjectPoint=(longitude:number,latitude:number)=>{x:number;y:number}|null

/** Group adjacent points in screen space; at higher zoom levels they naturally separate. */
export function clusterMapListings(items:MapListing[],project:ProjectPoint):ListingCluster[]{
  const groups=new Map<string,ListingCluster>()
  for(const item of items){
    const p=project(item.longitude,item.latitude)
    const key=p&&Number.isFinite(p.x)&&Number.isFinite(p.y)?`cell:${Math.floor(p.x/88)}:${Math.floor(p.y/70)}`:`point:${item.longitude}:${item.latitude}`
    const group=groups.get(key)
    if(group){const count=group.items.length;group.longitude=(group.longitude*count+item.longitude)/(count+1);group.latitude=(group.latitude*count+item.latitude)/(count+1);group.items.push(item)}
    else groups.set(key,{key,longitude:item.longitude,latitude:item.latitude,items:[item]})
  }
  return [...groups.values()]
}
