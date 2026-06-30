import { useState, useRef, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { AppShell } from '../components/AppShell'
import { Bot, Columns2, Send, User } from 'lucide-react'

// Stable per-tab session id so the verbatim chat-memory window keys correctly.
function newSessionId() {
  return (crypto.randomUUID ? crypto.randomUUID() : `s-${Date.now()}-${Math.random()}`)
}

export function ChatPage() {
  const [sessionId] = useState(newSessionId)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [model, setModel] = useState('')
  const [streaming, setStreaming] = useState(false)
  const esRef = useRef(null)
  const bottomRef = useRef(null)

  useEffect(() => () => { if (esRef.current) esRef.current.close() }, [])
  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages])

  const send = useCallback(() => {
    const text = input.trim()
    if (!text || streaming) return
    setInput('')
    setMessages(m => [...m, { role: 'user', content: text }, { role: 'assistant', content: '' }])
    setStreaming(true)

    const params = new URLSearchParams({ sessionId, message: text })
    if (model.trim()) params.set('model', model.trim())
    const es = new EventSource(`/api/chat/stream?${params.toString()}`)
    esRef.current = es

    es.addEventListener('token', (e) => {
      try {
        const { token } = JSON.parse(e.data)
        setMessages(m => {
          const copy = [...m]
          copy[copy.length - 1] = { role: 'assistant', content: copy[copy.length - 1].content + token }
          return copy
        })
      } catch { /* ignore malformed chunk */ }
    })
    const finish = () => { es.close(); esRef.current = null; setStreaming(false) }
    es.addEventListener('done', finish)
    es.onerror = finish
  }, [input, streaming, sessionId, model])

  return (
    <AppShell>
      <div className="flex items-center justify-between mb-4 animate-fade-up">
        <div>
          <h1 className="text-xl font-semibold text-[var(--text-primary)] flex items-center gap-2">
            <Bot className="w-5 h-5 text-[var(--status-deploying)]" /> Assistant
          </h1>
          <p className="text-sm text-[var(--text-muted)] mt-0.5">Chat with a local model about your infrastructure.</p>
        </div>
        <Link to="/compare" className="btn-secondary text-sm"><Columns2 className="w-4 h-4" /> Compare models</Link>
      </div>

      <div className="card p-4 mb-4 min-h-[50vh] max-h-[60vh] overflow-y-auto space-y-4 animate-fade-up delay-100">
        {messages.length === 0 ? (
          <div className="text-center text-[var(--text-muted)] py-16 text-sm">
            Ask something like “what databases can I deploy?”
          </div>
        ) : messages.map((m, i) => (
          <div key={i} className={`flex gap-3 ${m.role === 'user' ? 'justify-end' : ''}`}>
            {m.role === 'assistant' && <Bot className="w-5 h-5 text-[var(--status-deploying)] shrink-0 mt-1" />}
            <div className={`max-w-[80%] rounded-lg px-3 py-2 text-sm whitespace-pre-wrap ${
              m.role === 'user'
                ? 'bg-[var(--accent-soft)] text-[var(--text-primary)]'
                : 'bg-[var(--bg-surface-2)] text-[var(--text-primary)]'
            }`}>
              {m.content || (streaming && i === messages.length - 1 ? '…' : '')}
            </div>
            {m.role === 'user' && <User className="w-5 h-5 text-[var(--text-muted)] shrink-0 mt-1" />}
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      <div className="flex items-center gap-2 animate-fade-up delay-150">
        <input
          value={model}
          onChange={e => setModel(e.target.value)}
          placeholder="model (optional, e.g. llama3.1:8b)"
          className="input max-w-56"
        />
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') send() }}
          placeholder="Type a message…"
          className="input flex-1"
        />
        <button onClick={send} disabled={streaming || !input.trim()} className="btn-primary disabled:opacity-50">
          <Send className="w-4 h-4" /> Send
        </button>
      </div>
    </AppShell>
  )
}
