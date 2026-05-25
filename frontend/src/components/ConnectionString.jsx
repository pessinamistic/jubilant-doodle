import { useState } from 'react'
import { ClipboardDocumentIcon, ClipboardDocumentCheckIcon, EyeIcon, EyeSlashIcon } from '@heroicons/react/24/outline'
import toast from 'react-hot-toast'

export function ConnectionString({ value, masked }) {
  const [copied, setCopied]   = useState(false)
  const [visible, setVisible] = useState(false)

  if (!value) return <span className="text-gray-400 text-sm">N/A</span>

  const display = visible ? value : (masked ?? value.replace(/:[^@:/]+@/, ':****@'))

  const copy = async () => {
    await navigator.clipboard.writeText(value)
    setCopied(true)
    toast.success('Connection string copied!')
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="flex items-center gap-2 bg-gray-900 rounded-lg px-3 py-2 font-mono text-sm text-green-400 max-w-full overflow-hidden">
      <span className="truncate flex-1">{display}</span>
      <button
        onClick={() => setVisible(v => !v)}
        className="text-gray-400 hover:text-white shrink-0"
        title={visible ? 'Hide password' : 'Reveal password'}>
        {visible ? <EyeSlashIcon className="w-4 h-4" /> : <EyeIcon className="w-4 h-4" />}
      </button>
      <button
        onClick={copy}
        className="text-gray-400 hover:text-white shrink-0"
        title="Copy full connection string">
        {copied
          ? <ClipboardDocumentCheckIcon className="w-4 h-4 text-green-400" />
          : <ClipboardDocumentIcon className="w-4 h-4" />}
      </button>
    </div>
  )
}
