// Missing observations stay null: a measured zero is different from no data.
export function observed(row: any, energy: 'electric' | 'water'): number | null {
  if (!row || Number(row[`${energy}_devices`]) <= 0 || row[`${energy}_usage`] == null) return null
  const result = Number(row[`${energy}_usage`])
  return Number.isFinite(result) ? result : null
}
export function percentage(current: number | null, prior: number | null): number | null {
  if (current == null || prior == null || prior === 0) return null
  return (current - prior) / prior * 100
}
export function periodComparison(rows: any[], energy: 'electric' | 'water'): number | null {
  const prior = energy === 'electric' ? 'previousElectric' : 'previousWater'
  if (!rows.length || rows.some(row => row[energy] == null || row[prior] == null || row.incomplete)) return null
  return percentage(rows.reduce((n, row) => n + row[energy], 0), rows.reduce((n, row) => n + row[prior], 0))
}
export function dimensionRows(raw: any[], electricTotal: number, waterTotal: number) {
  const rows = raw.map(row => ({ id: row.dimensionValueId, name: row.dimensionValueName || '未命名', color: row.color || '#38bdf8', electric: Number(row.electricUsage) || 0, water: Number(row.waterUsage) || 0 }))
  const electric = Math.max(0, electricTotal - rows.reduce((n, row) => n + row.electric, 0))
  const water = Math.max(0, waterTotal - rows.reduce((n, row) => n + row.water, 0))
  if (electric > 0.000001 || water > 0.000001) rows.push({ id: 'unassigned', name: '未分类', color: '#94a3b8', electric, water })
  return rows
}
