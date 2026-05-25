import { INSTANCE_STATUS_TOKENS } from '../theme/statusTokens'

export function StatusBadge({ status }) {
  const normalized = status ?? 'UNKNOWN'
  const token = INSTANCE_STATUS_TOKENS[normalized] ?? INSTANCE_STATUS_TOKENS.UNKNOWN

  return (
    <span
      className={`status-pill ${token.pulse ? 'animate-pulse' : ''}`}
      style={{
        background: token.background,
        color: token.text,
        borderColor: token.border,
      }}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${token.pulse ? 'animate-pulse' : ''}`} style={{ background: token.dot }} />
      {normalized}
    </span>
  )
}
