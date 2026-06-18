import { ref, computed } from 'vue'
import localforage from 'localforage'
import { useAuth } from './useAuth'

const store = localforage.createInstance({ name: 'travel-agent', storeName: 'conversations' })

const conversations = ref([])
const activeId = ref(null)

// 游客用的 IndexedDB key（登录用户不走 IndexedDB，所以无需隔离）
const CONV_LIST_KEY = 'conversations-list'
const CONV_KEY_PREFIX = 'conv-'

// 后端保存防抖定时器
let saveTimer = null

export function useConversation() {
  const { userId } = useAuth()

  const activeConversation = computed(() =>
    conversations.value.find((c) => c.id === activeId.value) || null,
  )

  const activeMessages = computed(() => activeConversation.value?.messages || [])

  // ── 加载 ──────────────────────────────────────────────
  async function load() {
    const uid = userId.value
    if (uid) {
      // 登录用户：从数据库加载
      try {
        const res = await fetch(`/api/conversations?userId=${uid}`)
        const data = await res.json()
        if (data.code === 200 && Array.isArray(data.data)) {
          conversations.value = data.data.map((item) => ({
            id: String(item.id),
            backendId: item.id,
            title: item.title || '新对话',
            messages: safeParseJson(item.messagesJson, []),
            result: safeParseJson(item.resultJson, null),
            createdAt: new Date(item.createdAt).getTime(),
            updatedAt: new Date(item.updatedAt).getTime(),
          }))
          conversations.value.sort((a, b) => b.updatedAt - a.updatedAt)
          if (conversations.value.length && !activeId.value) {
            activeId.value = conversations.value[0].id
          }
        }
      } catch {
        // 后端不可用时保持空列表
      }
    } else {
      // 游客：从 IndexedDB 加载
      try {
        const saved = await store.getItem(CONV_LIST_KEY)
        if (saved) {
          conversations.value = saved
          if (saved.length && !activeId.value) activeId.value = saved[0].id
        }
      } catch {}
    }
  }

  function safeParseJson(str, fallback) {
    if (!str) return fallback
    try { return JSON.parse(str) } catch { return fallback }
  }

  // ── 持久化（根据登录状态走不同路径）──────────────────
  function persist() {
    if (userId.value) {
      // 登录用户：防抖保存到后端
      saveToBackendDebounced()
    } else {
      // 游客：保存到 IndexedDB
      persistToIndexedDB()
    }
  }

  async function persistToIndexedDB() {
    try {
      await store.setItem(CONV_LIST_KEY, conversations.value)
      const conv = activeConversation.value
      if (conv) await store.setItem(CONV_KEY_PREFIX + conv.id, conv)
    } catch {}
  }

  function saveToBackendDebounced() {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(() => saveConversationToBackend(activeConversation.value), 2000)
  }

  async function saveConversationToBackend(conv) {
    const uid = userId.value
    if (!uid || !conv) return

    try {
      const body = {
        userId: uid,
        title: conv.title || '新对话',
        messagesJson: JSON.stringify(conv.messages),
        resultJson: conv.result ? JSON.stringify(conv.result) : null,
      }
      if (conv.backendId) body.id = conv.backendId

      const res = await fetch('/api/conversations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      const data = await res.json()
      if (data.code === 200 && data.data?.id) {
        conv.backendId = data.data.id
        conv.id = String(data.data.id)
        if (activeId.value && activeId.value !== conv.id) {
          activeId.value = conv.id
        }
      }
    } catch {}
  }

  // ── 立即同步（用于需要 backendId 的场景，如工作台）──
  async function syncActiveToBackend(targetConv) {
    if (saveTimer) clearTimeout(saveTimer)
    await saveConversationToBackend(targetConv || activeConversation.value)
    return (targetConv || activeConversation.value)?.backendId || null
  }

  // ── 对话操作 ─────────────────────────────────────────
  function newConversation() {
    const id = Date.now().toString() + Math.random().toString(36).slice(2, 6)
    const conv = {
      id,
      backendId: null,
      title: '新对话',
      messages: [],
      result: null,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    }
    conversations.value.unshift(conv)
    activeId.value = id
    if (!userId.value) persistToIndexedDB()
    return conv
  }

  function selectConversation(id) {
    activeId.value = id
  }

  function deleteConversation(id) {
    const idx = conversations.value.findIndex((c) => c.id === id)
    if (idx === -1) return
    const conv = conversations.value[idx]

    // 登录用户：同时删除后端数据
    const uid = userId.value
    if (uid && conv.backendId) {
      fetch(`/api/conversations/${conv.backendId}?userId=${uid}`, {
        method: 'DELETE',
      }).catch(() => {})
    }

    conversations.value.splice(idx, 1)
    if (activeId.value === id) {
      activeId.value = conversations.value.length ? conversations.value[0].id : null
    }
    if (!uid) persistToIndexedDB()
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

  return {
    conversations,
    activeId,
    activeConversation,
    activeMessages,
    load,
    persist,
    newConversation,
    selectConversation,
    deleteConversation,
    addMessage,
    setResult,
    updateLastAssistantMessage,
    syncActiveToBackend,
  }
}
