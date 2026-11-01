/** Integration seam for a future AMap SDK. No key, network request or geolocation is used by the demo. */
export interface MapPoint { id: string; label: string; position: { lng: number; lat: number; coordinateSystem: 'GCJ-02' } }
export interface MapAdapter {
  mount(container: HTMLElement): void | Promise<void>
  setMarkers(markers: MapPoint[], onSelect: (id: string) => void): void
  focus(point: MapPoint['position']): void
  destroy(): void
}
export type MapAdapterFactory = () => MapAdapter
let factory: MapAdapterFactory | undefined
export function installMapAdapter(adapterFactory: MapAdapterFactory) { factory = adapterFactory }
export function createMapAdapter(): MapAdapter | null { return factory?.() ?? null }
