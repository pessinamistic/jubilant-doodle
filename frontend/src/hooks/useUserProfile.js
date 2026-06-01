import { useEffect, useState, useCallback } from 'react'

const LS_KEY = 'pw_user_profile_v1'

const DEFAULT_PROFILE = null

function load() {
  try {
    const raw = localStorage.getItem(LS_KEY)
    if (!raw) return DEFAULT_PROFILE
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || !parsed.name) return DEFAULT_PROFILE
    return parsed
  } catch {
    return DEFAULT_PROFILE
  }
}

/**
 * Local-only user profile captured by the first-run welcome wizard.
 * Shape: { name, role, favTools: string[], createdAt, version }
 */
export function useUserProfile() {
  const [profile, setProfile] = useState(load)

  useEffect(() => {
    const onStorage = (e) => {
      if (e.key === LS_KEY) setProfile(load())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const save = useCallback((next) => {
    const payload = { ...next, version: 1, createdAt: next.createdAt || new Date().toISOString() }
    try {
      localStorage.setItem(LS_KEY, JSON.stringify(payload))
    } catch { /* ignore quota errors */ }
    setProfile(payload)
  }, [])

  const clear = useCallback(() => {
    try { localStorage.removeItem(LS_KEY) } catch { /* ignore */ }
    setProfile(null)
  }, [])

  return { profile, save, clear, isReady: profile != null }
}
