const separatorPattern = /[—－_]/g

function normalizedText(value: unknown) {
  return String(value || '')
    .replace(separatorPattern, '-')
    .replace(/\s*-\s*/g, '-')
    .trim()
}

function stripStallPrefix(value: string) {
  return value.replace(/^(?:[A-Z]{1,3}\d{2,5}[A-Z]?(?:[-+/]\d+)?|[A-Z]\d+-\d+)\s*-/i, '').trim()
}

/**
 * Extract a merchant/brand name from both standard and historical source-account names.
 * Historical examples include 3066-B1-814239-超市烤鸭 and 3066-优图碧彩妆-000249.
 */
export function extractMerchantName(value: unknown, merchantCode: unknown) {
  const code = String(merchantCode || '').trim()
  let text = normalizedText(value)
  if (!text) return ''

  const standard = /^3066-(\d{6})-(.+)$/i.exec(text)
  if (standard?.[2]) return stripStallPrefix(standard[2])

  text = text.replace(/^3066-?/i, '').replace(/^-+|-+$/g, '').trim()
  if (/^\d{6}$/.test(code)) {
    const parts = text.split('-').map(part => part.trim()).filter(Boolean)
    const codeIndex = parts.findIndex(part => part === code)
    if (codeIndex >= 0) {
      const afterCode = parts.slice(codeIndex + 1).join('-').trim()
      if (afterCode) return stripStallPrefix(afterCode)
      const beforeCode = parts.slice(0, codeIndex).join('-').trim()
      if (beforeCode) return stripStallPrefix(beforeCode)
    }
  }

  // Some old accounts start with a stall code, for example B101-百品好炒货.
  text = stripStallPrefix(text)
  return text === code ? '' : text
}

export function buildStandardAccountName(merchantCode: unknown, sourceName: unknown) {
  const code = String(merchantCode || '').trim()
  if (!/^\d{6}$/.test(code)) return ''
  const merchantName = extractMerchantName(sourceName, code) || '商户名称待补充'
  return `3066-${code}-${merchantName}`
}
