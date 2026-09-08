/** 统一显示为 2026-08-22 11:04:21，不改变后端返回的实际时间值。 */
export function formatDateTime(value?: string | number | Date | null): string {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'string') {
    const matched = value.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})/)
    if (matched) return `${matched[1]}-${matched[2]}-${matched[3]} ${matched[4]}:${matched[5]}:${matched[6]}`
  }
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const labels: Record<string, string> = {
  SUCCESS: '成功', FAILED: '失败', PARTIAL: '部分成功', PARTIAL_FAILED: '部分失败', RUNNING: '运行中', PENDING: '等待中', MANUAL: '手动', SCHEDULED: '定时',
  OPEN: '待处理', ACK: '已确认', HANDLING: '处理中', RESOLVED: '已解决', IGNORED: '已忽略',
  INFO: '提示', WARN: '警告', ERROR: '错误', CRITICAL: '严重',
  ACTIVE: '生效中', INACTIVE: '已失效', ELECTRIC: '电', WATER: '水', GAS: '燃气', OTHER: '其他',
  BIND: '绑定', UNBIND: '解绑', DEVICE_BIND: '设备绑定', DEVICE_UNBIND: '设备解绑', ACCOUNT_DISABLE: '账户停用', ACCOUNT_CHANGE: '账户变更',
  UNVERIFIED: '未核对', VERIFIED: '已核对', MATCHED: '已匹配', MISMATCH: '不一致', PARTIAL_MATCH: '部分匹配',
  ACCOUNT_NOT_FOUND: '未找到账户', ACCOUNT_AMBIGUOUS: '账户不唯一',
  SUPER_ADMIN: '超级管理员', SYSTEM_ADMIN: '系统管理员', APPLICANT: '申请人', DEVICE_VERIFIER: '现场核验员',
  PLATFORM_OPERATOR: '只读用户', AUDITOR: '只读用户', VIEWER: '只读用户'
}
export function enumLabel(value?: string | null): string { return value ? (labels[value] || value) : '-' }
