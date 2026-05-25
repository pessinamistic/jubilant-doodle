import { applyTheme, getStoredMode } from './themeCore'

export function initializeTheme() {
  applyTheme(getStoredMode())
}
