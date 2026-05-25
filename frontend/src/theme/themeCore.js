import { createContext } from 'react'

export const STORAGE_KEY = 'port-wrangler-theme'
const DEFAULT_MODE = 'system'
const MODES = new Set(['system', 'light', 'dark'])

export function getSystemTheme() {
  if (typeof window === 'undefined') return 'dark'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function resolveTheme(mode) {
  return mode === 'system' ? getSystemTheme() : mode
}

function normalizeMode(mode) {
  return MODES.has(mode) ? mode : DEFAULT_MODE
}

export function getStoredMode() {
  if (typeof window === 'undefined') return DEFAULT_MODE
  return normalizeMode(window.localStorage.getItem(STORAGE_KEY))
}

export function applyTheme(mode) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.setAttribute('data-theme-mode', mode)
  root.setAttribute('data-theme', resolveTheme(mode))
}

export const ThemeContext = createContext(null)
