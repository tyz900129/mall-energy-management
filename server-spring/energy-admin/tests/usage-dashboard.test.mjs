import { test } from 'node:test'
import assert from 'node:assert/strict'
import { observed, percentage, periodComparison, dimensionRows } from '../src/utils/usageDashboard.ts'

test('missing water is distinct from a measured zero', () => {
  assert.equal(observed(undefined, 'water'), null)
  assert.equal(observed({ water_devices: 0, water_usage: 0 }, 'water'), null)
  assert.equal(observed({ water_devices: 3, water_usage: 0 }, 'water'), 0)
})
test('year over year requires comparable recorded months and nonzero baseline', () => {
  assert.equal(percentage(0, 10), -100)
  assert.equal(percentage(10, 0), null)
  assert.equal(periodComparison([{ electric: 120, previousElectric: 100 }], 'electric'), 20)
  assert.equal(periodComparison([{ electric: null, previousElectric: 100 }], 'electric'), null)
  assert.equal(periodComparison([{ electric: 120, previousElectric: null }], 'electric'), null)
  assert.equal(periodComparison([{ electric: 120, previousElectric: 100, incomplete: true }], 'electric'), null)
})
test('dimension shares include unassigned usage and preserve zero categories', () => {
  const rows = dimensionRows([{ dimensionValueId: 1, electricUsage: 60, waterUsage: 0 }], 100, 20)
  assert.equal(rows.length, 2)
  assert.equal(rows[1].name, '未分类')
  assert.equal(rows.reduce((n,r) => n+r.electric, 0), 100)
  assert.equal(rows.reduce((n,r) => n+r.water, 0), 20)
  assert.equal(dimensionRows([], 0, 0).length, 0)
})
