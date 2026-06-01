import { useEffect, useState } from 'react'
import { Container, Zap } from 'lucide-react'

/**
 * Brutalist splash screen shown on first load.
 * - Animates a logo + tagline in
 * - Auto-dismisses after `duration` ms (default 1400)
 * - Fades out for 280ms before calling `onDone`
 */
export function SplashScreen({ duration = 1400, onDone }) {
  const [leaving, setLeaving] = useState(false)

  useEffect(() => {
    const t1 = setTimeout(() => setLeaving(true), duration)
    const t2 = setTimeout(() => onDone?.(), duration + 320)
    return () => { clearTimeout(t1); clearTimeout(t2) }
  }, [duration, onDone])

  return (
    <div
      role="status"
      aria-label="Loading Port Wrangler"
      className={`splash-root ${leaving ? 'splash-leaving' : ''}`}
    >
      <div className="splash-grid" aria-hidden="true" />

      <div className="splash-inner">
        <div className="splash-logo-wrap">
          <div className="splash-logo-tile">
            <Container className="w-12 h-12" strokeWidth={2.5} />
          </div>
          <div className="splash-zap" aria-hidden="true">
            <Zap className="w-6 h-6" strokeWidth={3} />
          </div>
        </div>

        <h1 className="splash-title">PORT&nbsp;WRANGLER</h1>
        <p className="splash-tagline">DEPLOY · WRANGLE · SHIP</p>

        <div className="splash-bar">
          <div className="splash-bar-fill" />
        </div>
      </div>

      <div className="splash-corner top-left">v1</div>
      <div className="splash-corner bottom-right">LOCAL · DEV · STATION</div>
    </div>
  )
}
