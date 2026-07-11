import { useState, useRef, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { AppShell } from '../components/AppShell'
import { ModelSelect } from '../components/ModelSelect'
import { ConfirmModal } from '../components/ConfirmModal'
import { getChatSessions, getChatHistory, renameChatSession, deleteChatSession } from '../api/client'
import { Bot, Check, Columns2, MessageSquarePlus, Pencil, Send, Trash2, User, X } from 'lucide-react'

function newSessionId() {
  return (crypto.randomUUID ? crypto.randomUUID() : `s-${Date.now()}-${Math.random()}`)
}

function relativeTime(iso) {
  if (!iso) return ''
  const diff = Date.now() - new Date(iso).getTime()
  const m = Math.floor(diff / 60000)
  if (m < 1) return 'just now'
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  return `${Math.floor(h / 24)}d ago`
}

export function ChatPage() {
  const [sessionId, setSessionId] = useState(newSessionId)
  const [sessions, setSessions] = useState([])
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [model, setModel] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)   // session object pending delete
  const [renamingId, setRenamingId] = useState(null)
  const [renameText, setRenameText] = useState('')
  const esRef = useRef(null)
  const bottomRef = useRef(null)

  const refreshSessions = useCallback(() => {
    getChatSessions().then(setSessions).catch(() => {})
  }, [])

  useEffect(() => { refreshSessions() }, [refreshSessions])
  useEffect(() => () => { if (esRef.current) esRef.current.close() }, [])
  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages])

  const stopStream = () => { if (esRef.current) { esRef.current.close(); esRef.current = null } }

  const newChat = useCallback(() => {
    stopStream()
    setStreaming(false)
    setSessionId(newSessionId())
    setMessages([])
  }, [])

  const openSession = useCallback((s) => {
    if (s.id === sessionId) return
    stopStream()
    setStreaming(false)
    setSessionId(s.id)
    setMessages([])
    if (s.modelId) setModel(s.modelId)
    getChatHistory(s.id)
      .then(history => setMessages(history.map(m => ({ role: m.role.toLowerCase(), content: m.content }))))
      .catch(() => setMessages([]))
  }, [sessionId])

  const confirmDelete = useCallback(() => {
    const target = deleteTarget
    setDeleteTarget(null)
    if (!target) return
    deleteChatSession(target.id)
      .then(() => {
        if (target.id === sessionId) newChat()
        refreshSessions()
      })
      .catch(() => {})
  }, [deleteTarget, sessionId, newChat, refreshSessions])

  const startRename = (s) => { setRenamingId(s.id); setRenameText(s.title || '') }
  const commitRename = () => {
    const id = renamingId
    const title = renameText.trim()
    setRenamingId(null)
    if (!id || !title) return
    renameChatSession(id, title).then(refreshSessions).catch(() => {})
  }

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
    const finish = () => {
      es.close(); esRef.current = null; setStreaming(false)
      // Persistence is async after stream completion — give it a beat before refreshing.
      setTimeout(refreshSessions, 800)
    }
    es.addEventListener('done', finish)
    es.onerror = finish
  }, [input, streaming, sessionId, model, refreshSessions])

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

      <div className="flex gap-4 animate-fade-up delay-100">
        {/* ── Session sidebar ── */}
        <div className="card w-64 shrink-0 p-3 flex flex-col max-h-[68vh]">
          <button onClick={newChat} className="btn-primary w-full mb-3 text-sm">
            <MessageSquarePlus className="w-4 h-4" /> New chat
          </button>
          <div className="overflow-y-auto space-y-1 flex-1">
            {sessions.length === 0 && (
              <p className="text-xs text-[var(--text-muted)] text-center py-6">No conversations yet</p>
            )}
            {sessions.map(s => (
              <div
                key={s.id}
                onClick={() => renamingId !== s.id && openSession(s)}
                className={`group rounded-lg px-2.5 py-2 cursor-pointer transition-colors ${
                  s.id === sessionId
                    ? 'bg-[var(--accent-soft)]'
                    : 'hover:bg-[var(--bg-surface-2)]'
                }`}
              >
                {renamingId === s.id ? (
                  <div className="flex items-center gap-1" onClick={e => e.stopPropagation()}>
                    <input
                      value={renameText}
                      onChange={e => setRenameText(e.target.value)}
                      onKeyDown={e => { if (e.key === 'Enter') commitRename(); if (e.key === 'Escape') setRenamingId(null) }}
                      className="input flex-1 text-xs px-1.5 py-1"
                      autoFocus
                    />
                    <button onClick={commitRename} className="p-1 text-[var(--text-muted)] hover:text-[var(--text-primary)]"><Check className="w-3.5 h-3.5" /></button>
                    <button onClick={() => setRenamingId(null)} className="p-1 text-[var(--text-muted)] hover:text-[var(--text-primary)]"><X className="w-3.5 h-3.5" /></button>
                  </div>
                ) : (
                  <>
                    <div className="flex items-start justify-between gap-1">
                      <span className="text-sm text-[var(--text-primary)] line-clamp-2 leading-snug">
                        {s.title || 'New conversation'}
                      </span>
                      <span className="hidden group-hover:flex items-center gap-0.5 shrink-0">
                        <button
                          onClick={e => { e.stopPropagation(); startRename(s) }}
                          className="p-0.5 text-[var(--text-muted)] hover:text-[var(--text-primary)]"
                          title="Rename"
                        >
                          <Pencil className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={e => { e.stopPropagation(); setDeleteTarget(s) }}
                          className="p-0.5 text-[var(--text-muted)] hover:text-[var(--status-error)]"
                          title="Delete"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </span>
                    </div>
                    <div className="text-[11px] text-[var(--text-muted)] mt-0.5">
                      {relativeTime(s.updatedAt || s.createdAt)}{s.modelId ? ` · ${s.modelId}` : ''}
                    </div>
                  </>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* ── Conversation ── */}
        <div className="flex-1 min-w-0 flex flex-col">
          <div className="card p-4 mb-4 min-h-[50vh] max-h-[60vh] overflow-y-auto space-y-4 flex-1">
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

          <div className="flex items-center gap-2">
            <ModelSelect
              value={model}
              onChange={setModel}
              allowEmpty
              placeholder="model (optional)"
              className="w-56 shrink-0"
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
        </div>
      </div>

      <ConfirmModal
        open={!!deleteTarget}
        title="Delete conversation?"
        message={`“${deleteTarget?.title || 'New conversation'}” and its memories will be permanently removed.`}
        confirmLabel="Delete"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </AppShell>
  )
}
