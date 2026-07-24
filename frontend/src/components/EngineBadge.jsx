const ENGINE_LABELS = {
  HOMEBREW: 'Homebrew',
}

function engineLabel(method) {
  return ENGINE_LABELS[method] ?? (method.charAt(0) + method.slice(1).toLowerCase())
}

/**
 * Marks the deploy engine for an instance when it differs from the Docker default.
 * Renders nothing for DOCKER (or null/undefined) — the badge exists to flag the
 * exception, not the common case.
 */
export function EngineBadge({ method }) {
  if (!method || method === 'DOCKER') return null

  return (
    <span
      className="status-pill"
      style={{
        background: 'var(--status-warning-bg)',
        color: 'var(--status-warning)',
        borderColor: 'var(--status-warning-border)',
      }}
    >
      {engineLabel(method)}
    </span>
  )
}
