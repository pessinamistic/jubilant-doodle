import { useState } from 'react'
import { Clipboard, ClipboardCheck, Eye, EyeOff } from 'lucide-react'
import toast from 'react-hot-toast'

export function ConnectionString({ value, masked }) {
  const [copied, setCopied]   = useState(false)
  const [visible, setVisible] = useState(false)

  if (!value) return <span className="text-[var(--text-muted)] text-sm">N/A</span>

  const display = visible ? value : (masked ?? value.replace(/:[^@:/]+@/, ':****@'))

  const copy = async () => {
    await navigator.clipboard.writeText(value)
    setCopied(true)
    toast.success('Connection string copied!')
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="flex items-center gap-2 rounded-[6px] px-3 py-2 font-mono text-sm max-w-full overflow-hidden border-2" style={{
      background: 'var(--bg-inset)',
      borderColor: 'var(--border-strong)',
      color: 'var(--status-running)',
    }}>
      <span className="truncate flex-1">{display}</span>
      <button
        onClick={() => setVisible(v => !v)}
        className="text-[var(--text-muted)] hover:text-[var(--text-primary)] shrink-0"
        title={visible ? 'Hide password' : 'Reveal password'}>
        {visible ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
      </button>
      <button
        onClick={copy}
        className="text-[var(--text-muted)] hover:text-[var(--text-primary)] shrink-0"
        title="Copy full connection string">
        {copied
          ? <ClipboardCheck className="w-4 h-4" style={{ color: 'var(--status-running)' }} />
          : <Clipboard className="w-4 h-4" />}
      </button>
    </div>
  )
}
