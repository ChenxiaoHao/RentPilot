import assets from './stitch-assets.json'

export interface Listing {
  id: string; name: string; price: number; area: number; district: string;
  address: string; commute: number; metro: string; image: string; tags: string[];
}
export interface Plan {
  id: string; name: string; shortName: string; subtitle: string; rent: number;
  cost: number; commute: number; score: number; convenience: number; area: string;
  image: string; tags: string[]; description: string; listingIds: string[];
}
export interface Weights { budget: number; commute: number; lifestyle: number }
export interface Version { id: string; title: string; date: string; planId: string; weights: Weights; rent: number; commute: number; match: number }
export const assetFor = (page: string, index = 0) => (assets as Record<string, string[]>)[page]?.[index] || '/assets/stitch/rentpilot_6-0.jpg'
export const money = (n: number) => `¥${n.toLocaleString('zh-CN')}`
export const listings: Listing[] = [
  { id: 'h1', name: '张江高科小区 · 经典一室一厅', price: 4800, area: 45, district: '张江高科', address: '浦东新区张江镇 · 近金科路', commute: 12, metro: '2号线金科路站', image: assetFor('rentpilot_6', 0), tags: ['近地铁', '有阳台', '独立厨卫'] },
  { id: 'h2', name: '康桥新苑 · 明亮一居室', price: 4500, area: 42, district: '康桥', address: '浦东新区康桥镇 · 示意地址', commute: 28, metro: '11号线康新公路站', image: assetFor('rentpilot_ai_1', 1), tags: ['近地铁', '精装修', '采光通透'] },
  { id: 'h3', name: '金科新苑 · 安静一居室', price: 4600, area: 42, district: '张江高科', address: '浦东新区张江镇 · 示意地址', commute: 18, metro: '2号线金科路站', image: assetFor('rentpilot_2', 1), tags: ['有阳台', '家电齐全'] },
  { id: 'h4', name: '唐镇新苑 · 舒适一居室', price: 3800, area: 38, district: '唐镇', address: '浦东新区唐镇 · 示意地址', commute: 38, metro: '2号线唐镇站', image: assetFor('rentpilot_1', 1), tags: ['近地铁', '独立厨卫'] },
  { id: 'h5', name: '张江公寓 · 品质一居室', price: 5800, area: 52, district: '张江高科', address: '浦东新区张江镇 · 示意地址', commute: 8, metro: '2号线张江高科站', image: assetFor('rentpilot_6', 0), tags: ['品质社区', '精装修'] },
  { id: 'h6', name: '川沙雅居 · 经济一居室', price: 3500, area: 35, district: '川沙', address: '浦东新区川沙 · 示意地址', commute: 32, metro: '2号线川沙站', image: assetFor('rentpilot_ai_1', 0), tags: ['近地铁', '高性价比'] },
]
export const plans: Plan[] = [
  { id: 'economy', name: '经济优先方案', shortName: '经济', subtitle: '方案 01 · 成本优选', rent: 3800, cost: 5200, commute: 38, score: 8.9, convenience: 83, area: '浦东 · 川沙 / 康桥', image: assetFor('rentpilot_3', 0), tags: ['极高性价比', '生活便利', '独立一居'], description: '让租金回归轻松，把预算留给生活。地铁连接工作与日常，适合注重收支平衡的你。', listingIds: ['h4', 'h6'] },
  { id: 'commute', name: '通勤优先方案', shortName: '通勤', subtitle: '方案 02 · 核心推荐', rent: 4800, cost: 6500, commute: 12, score: 9.4, convenience: 92, area: '浦东 · 张江高科 / 金科路', image: assetFor('rentpilot_3', 1), tags: ['极速通勤', '园区核心', '精力充沛'], description: '步行与骑行即可覆盖日常通勤，每天可节省约 50 分钟路上时间，让更多时间回到自己的生活。', listingIds: ['h1', 'h3'] },
  { id: 'quality', name: '品质生活方案', shortName: '品质', subtitle: '方案 03 · 舒适进阶', rent: 5800, cost: 7600, commute: 8, score: 9.6, convenience: 96, area: '浦东 · 张江品质社区', image: assetFor('rentpilot_3', 2), tags: ['品质社区', '完善配套', '从容生活'], description: '在工作与生活之间留出舒适的距离，以更好的空间、采光和社区配套，换取日常的从容。', listingIds: ['h5', 'h1'] },
]
export const initialVersions: Version[] = [
  { id: 'v2.1', title: '通勤优先方案', date: '2026.10.24', planId: 'commute', weights: { budget: 60, commute: 85, lifestyle: 50 }, rent: 4800, commute: 12, match: 94 },
  { id: 'v2.0', title: '平衡方案', date: '2026.10.23', planId: 'economy', weights: { budget: 75, commute: 60, lifestyle: 50 }, rent: 4200, commute: 25, match: 89 },
  { id: 'v1.0', title: '初始探索', date: '2026.10.22', planId: 'economy', weights: { budget: 85, commute: 40, lifestyle: 40 }, rent: 3800, commute: 40, match: 82 },
]
