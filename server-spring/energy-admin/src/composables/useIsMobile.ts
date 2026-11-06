/**
 * 响应式断点判断（营运巡场等页面移动端适配用）
 */
import { onUnmounted, ref } from 'vue'

export function useIsMobile(query = '(max-width: 768px)') {
  const mql = typeof window !== 'undefined' ? window.matchMedia(query) : null
  const isMobile = ref(mql ? mql.matches : false)
  const handler = (e: MediaQueryListEvent) => { isMobile.value = e.matches }
  if (mql) {
    if (mql.addEventListener) mql.addEventListener('change', handler)
    else (mql as any).addListener(handler)
  }
  onUnmounted(() => {
    if (!mql) return
    if (mql.removeEventListener) mql.removeEventListener('change', handler)
    else (mql as any).removeListener(handler)
  })
  return isMobile
}
