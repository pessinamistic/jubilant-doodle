import { useEffect, useMemo, useState } from 'react'
import { STORAGE_KEY, ThemeContext, applyTheme, getStoredMode, resolveTheme } from './themeCore'

export function ThemeProvider({ children }) {
  const [mode, setMode] = useState(() => getStoredMode())

  useEffect(() => {
    if (typeof window === 'undefined') return undefined

    window.localStorage.setItem(STORAGE_KEY, mode)
    applyTheme(mode)

    if (mode !== 'system') return undefined

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    const handleChange = () => applyTheme('system')

    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener('change', handleChange)
      return () => mediaQuery.removeEventListener('change', handleChange)
    }

    mediaQuery.addListener(handleChange)
    return () => mediaQuery.removeListener(handleChange)
  }, [mode])

  const value = useMemo(() => ({
    mode,
    effectiveTheme: resolveTheme(mode),
    setMode,
    cycleMode: () => {
      setMode((prev) => {
        if (prev === 'system') return 'light'
        if (prev === 'light') return 'dark'
        return 'system'
      })
    },
  }), [mode])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
