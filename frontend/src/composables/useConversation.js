import { ref, computed } from 'vue'

const STORAGE_KEY = 'travel_conversations'

function loadConversations() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function saveConversations(list) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
}

const conversations = ref(loadConversations())
const activeId = ref(conversations.value.length ? conversations.value[0].id : null)

function persist() {
  saveConversations(conversations.value)
  syncActiveToBackend()
}

async function syncActiveToBackend() {
  const conv = activeConversation.value
  if (!conv) return
  const userId = Number(localStorage.getItem('userId')) || 1

  const body = {
    userId,
    title: conv.title,
    messagesJson: JSON.stringify(
      conv.messages.map(m => {
        if (m.role === 'assistant') {
          return { role: 'assistant', content: m.answer || m.content || '' }
        }
        return { role: m.role, content: m.content || '' }
      })
    ),
    resultJson: conv.result ? JSON.stringify(conv.result) : null,
  }

  if (conv.backendId) {
    body.id = conv.backendId
  }

  try {
    const res = await fetch('/api/conversations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const data = await res.json()
    if (data.code === 200 && data.data?.id) {
      conv.backendId = data.data.id
      saveConversations(conversations.value)
    }
  } catch {}
}

const activeConversation = computed(() =>
  conversations.value.find((c) => c.id === activeId.value) || null
)

export function useConversation() {
  function newConversation() {
    const conv = {
      id: Date.now().toString(),
      title: '新对话',
      messages: [],
      result: null,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    }
    conversations.value.unshift(conv)
    activeId.value = conv.id
    persist()
    return conv
  }

  function selectConversation(id) {
    activeId.value = id
  }

  function deleteConversation(id) {
    const idx = conversations.value.findIndex((c) => c.id === id)
    if (idx === -1) return
    const conv = conversations.value[idx]

    // Delete from backend if synced
    const userId = Number(localStorage.getItem('userId'))
    if (userId && conv.backendId) {
      fetch(`/api/conversations/${conv.backendId}?userId=${userId}`, {
        method: 'DELETE',
      }).catch(() => {})
    }

    conversations.value.splice(idx, 1)
    if (activeId.value === id) {
      activeId.value = conversations.value.length ? conversations.value[0].id : null
    }
    persist()
  }

  function addMessage(msg) {
    const conv = activeConversation.value
    if (!conv) return
    conv.messages.push(msg)
    conv.updatedAt = Date.now()
    if (conv.messages.length === 1 && msg.role === 'user') {
      conv.title = msg.content.slice(0, 40) || '新对话'
    }
    persist()
  }

  function setResult(result) {
    const conv = activeConversation.value
    if (!conv) return
    conv.result = result
    conv.updatedAt = Date.now()
    persist()
  }

  function updateLastAssistantMessage(content) {
    const conv = activeConversation.value
    if (!conv) return
    const last = [...conv.messages].reverse().find((m) => m.role === 'assistant')
    if (last) {
      last.content = content
    }
    persist()
  }

  async function loadFromBackend() {
    const userId = Number(localStorage.getItem('userId')) || 1

    try {
      const res = await fetch(`/api/conversations?userId=${userId}`)
      const data = await res.json()
      if (data.code !== 200 || !data.data) return

      // Merge backend conversations into localStorage
      for (const remote of data.data) {
        const existing = conversations.value.find(c => c.backendId === remote.id)
        if (existing) {
          // Update existing with backend data if newer
          const remoteTime = new Date(remote.updatedAt).getTime()
          if (remoteTime > existing.updatedAt) {
            existing.title = remote.title
            try { existing.messages = JSON.parse(remote.messagesJson) } catch {}
            try { existing.result = remote.resultJson ? JSON.parse(remote.resultJson) : null } catch {}
            existing.updatedAt = remoteTime
          }
        } else {
          // Add new conversation from backend
          const conv = {
            id: Date.now().toString() + Math.random().toString(36).slice(2, 6),
            backendId: remote.id,
            title: remote.title,
            messages: [],
            result: null,
            createdAt: new Date(remote.createdAt).getTime(),
            updatedAt: new Date(remote.updatedAt).getTime(),
          }
          try { conv.messages = JSON.parse(remote.messagesJson) } catch {}
          try { conv.result = remote.resultJson ? JSON.parse(remote.resultJson) : null } catch {}
          conversations.value.push(conv)
        }
      }

      // Sort by updatedAt descending
      conversations.value.sort((a, b) => b.updatedAt - a.updatedAt)
      if (!activeId.value && conversations.value.length) {
        activeId.value = conversations.value[0].id
      }
      persist()
    } catch {
      // Backend unavailable, keep localStorage data
    }
  }

  return {
    conversations,
    activeId,
    activeConversation,
    persist,
    newConversation,
    selectConversation,
    deleteConversation,
    addMessage,
    setResult,
    updateLastAssistantMessage,
    loadFromBackend,
    syncActiveToBackend,
  }
}
