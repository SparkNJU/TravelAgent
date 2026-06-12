<template>
  <div class="ai-plan-page">
    <!-- Left: Conversation sidebar -->
    <ConversationSidebar
      :conversations="conversations"
      :activeId="activeId"
      :collapsed="sidebarCollapsed"
      @toggle="sidebarCollapsed = !sidebarCollapsed"
      @new="handleNewConversation"
      @select="selectConversation"
      @delete="deleteConversation"
    />

    <!-- Center: conversation stream -->
    <div class="center-panel">
      <!-- Empty state: centered input -->
      <div v-if="!activeConversation || !activeConversation.messages.length" class="empty-state">
        <div class="brand-greeting">
          <div class="brand-icon"><img src="/logo.svg" alt="TravelMind" style="width:100%;height:100%;object-fit:cover;border-radius:12px;" /></div>
          <h1>TravelMind AI</h1>
          <p>描述你的旅行想法，智能生成个性化行程方案</p>
        </div>
        <ChatInput
          :loading="loading"
          :compressing="compressing"
          :canCompress="canCompress"
          :compressHint="compressHint"
          v-model="selectedMode"
          :selectedModel="selectedModel"
          :arenaMode="arenaMode"
          :tokenStatus="tokenStatus"
          @compress="triggerForceCompress"
          @update:selectedModel="selectedModel = $event"
          @toggleArena="toggleArenaMode"
          @submit="handleSend"
          @stop="stopActiveRequest"
        />
      </div>

      <!-- Active conversation: messages + compact input -->
      <div v-else class="conversation-view">
        <div class="messages-area" ref="messagesRef">
          <template v-for="(msg, i) in activeConversation.messages" :key="i">
            <!-- User message -->
            <MessageBubble v-if="msg.role === 'user'" role="user" :content="msg.content" />

            <!-- Agent message: events + answer -->
            <template v-else>
              <div class="agent-content-wrapper" :class="{ arena: msg.arena }">
                <ModelArenaCompare
                  v-if="msg.arena"
                  :modelA="msg.arena.modelA"
                  :modelB="msg.arena.modelB"
                  :answerA="msg.arena.answerA"
                  :answerB="msg.arena.answerB"
                  :loading="msg.arena.loading"
                  :voted="msg.arena.voted"
                  :stages="msg.arena.stages || []"
                  @vote="handleArenaVote(msg, $event)"
                />
                <template v-else>
                  <AgentPlanBlock
                    v-if="msg.planContent"
                    :content="msg.planContent"
                    :streaming="loading && i === activeConversation.messages.length - 1"
                  />
                  <AgentEventBlock
                    v-for="(ev, j) in msg.events"
                    :key="j"
                    :type="ev.type"
                    :content="ev.content"
                    :toolName="ev.metadata?.tool || ev.metadata?.tool_name || ''"
                    :metadata="ev.metadata"
                  />
                  <MessageBubble
                    v-if="msg.answer"
                    role="assistant"
                    :content="msg.answer"
                  />
                  <div v-if="msg.answer && !loading" class="message-actions">
                    <button
                      class="sync-knowledge-btn"
                      :disabled="isSyncingKnowledgeTurn(i)"
                      @click="syncTurnToKnowledge(i)"
                    >
                      {{ isSyncingKnowledgeTurn(i) ? '同步中...' : '同步本轮到知识中心' }}
                    </button>
                  </div>
                </template>
              </div>
            </template>
          </template>

          <StreamingIndicator v-if="loading" />

          <!-- Enter Workbench Button -->
          <div v-if="activeConversation && activeConversation.result && !loading" class="workbench-trigger-wrapper">
            <button class="workbench-trigger-btn" :disabled="navigatingToWorkbench" @click="goToWorkbench">
              <span v-if="navigatingToWorkbench" class="btn-loading-spinner"></span>
              {{ navigatingToWorkbench ? '正在同步会话...' : '进入可视化工作台 ➜' }}
            </button>
          </div>
        </div>

        <!-- UserConfirmBlock: aligned with agent-content-wrapper -->
        <div v-if="pendingAskUser && !loading" class="agent-content-wrapper">
          <UserConfirmBlock
            :message="pendingAskUser.message"
            :questions="pendingAskUser.questions"
            @confirm="handleConfirmResponse"
          />
        </div>

        <!-- SuggestionChips: aligned with agent-content-wrapper -->
        <div v-if="activeSuggestions.length && !loading" class="agent-content-wrapper">
          <SuggestionChips
            :questions="activeSuggestions"
            @select="handleSuggestionSelect"
          />
        </div>

        <div class="compact-input-area">
          <ChatInput
            compact
            :loading="loading"
            :compressing="compressing"
            :canCompress="canCompress"
            :compressHint="compressHint"
            :hasMessages="true"
            v-model="selectedMode"
            :selectedModel="selectedModel"
            :arenaMode="arenaMode"
            :tokenStatus="tokenStatus"
            @compress="triggerForceCompress"
            @update:selectedModel="selectedModel = $event"
            @toggleArena="toggleArenaMode"
            @submit="handleSend"
            @stop="stopActiveRequest"
          />
          <div v-if="compressNotice" class="compress-notice">{{ compressNotice }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSSE } from '../composables/useSSE'
import { useConversation } from '../composables/useConversation'
import ChatInput from '../components/ai-plan/ChatInput.vue'
import MessageBubble from '../components/ai-plan/MessageBubble.vue'
import AgentEventBlock from '../components/ai-plan/AgentEventBlock.vue'
import AgentPlanBlock from '../components/ai-plan/AgentPlanBlock.vue'
import StreamingIndicator from '../components/ai-plan/StreamingIndicator.vue'
import ConversationSidebar from '../components/ai-plan/ConversationSidebar.vue'
import UserConfirmBlock from '../components/ai-plan/UserConfirmBlock.vue'
import SuggestionChips from '../components/ai-plan/SuggestionChips.vue'
import ModelArenaCompare from '../components/ai-plan/ModelArenaCompare.vue'
import { buildKnowledgeSyncPayload } from '../utils/knowledgeSync'

const route = useRoute()
const router = useRouter()

const {
  conversations, activeId, activeConversation,
  newConversation, selectConversation, deleteConversation,
  addMessage, setResult, persist, loadFromBackend, syncActiveToBackend,
} = useConversation()

const { streamPost } = useSSE()

const sidebarCollapsed = ref(false)
const loading = ref(false)
const messagesRef = ref(null)
const activeController = ref(null)
const selectedMode = ref('agent')
const selectedModel = ref('deepseek-chat')
const arenaMode = ref(false)
const pendingAskUser = ref(null)
const activeSuggestions = ref([])
const navigatingToWorkbench = ref(false)
const syncingKnowledgeTurns = ref(new Set())

// Token / compress state
const TOKEN_STATUS_KEY = 'travel_token_status'
const COMPRESS_KEEP_LAST = 6
const tokenStatus = ref(null)
const forceCompress = ref(false)
const compressing = ref(false)
const compressNotice = ref('')
let compressNoticeTimer = null

const compressibleMessages = computed(() => {
  const messages = activeConversation.value?.messages || []
  return messages.filter(m => m.role === 'user' || m.role === 'assistant')
})

const canCompress = computed(() => compressibleMessages.value.length > COMPRESS_KEEP_LAST)

const compressHint = computed(() => {
  const count = compressibleMessages.value.length
  if (count <= COMPRESS_KEEP_LAST) {
    return `历史消息不足，至少需要 ${COMPRESS_KEEP_LAST + 1} 条对话才能压缩`
  }
  return ''
})

const contextHealth = computed(() => {
  const ratio = tokenStatus.value?.utilization || 0
  if (ratio >= 0.85) return { level: 'danger', message: 'Context almost full. Compression recommended.' }
  if (ratio >= 0.65) return { level: 'warning', message: 'Context getting large.' }
  return { level: 'safe', message: '' }
})

function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function timeTag() {
  return new Date().toTimeString().slice(0, 8)
}

function createInitialArenaStages(query) {
  return [
    {
      id: 'pick',
      title: '匿名模型抽取',
      status: 'running',
      time: timeTag(),
      expanded: true,
      detail: `已接收问题：${query}\n从候选池随机抽取两个模型，并映射到匿名标签 A/B。`,
    },
    {
      id: 'dispatch',
      title: '并行请求派发',
      status: 'pending',
      time: '--:--:--',
      expanded: false,
      detail: '等待派发请求。',
    },
    {
      id: 'reasoning',
      title: '模型思考与草拟',
      status: 'pending',
      time: '--:--:--',
      expanded: false,
      detail: '等待模型生成过程。',
    },
    {
      id: 'merge',
      title: '结果整理与匿名展示',
      status: 'pending',
      time: '--:--:--',
      expanded: true,
      detail: '等待汇总回答。',
    },
  ]
}

function updateArenaStage(msg, stageId, patch = {}) {
  if (!msg?.arena) return
  if (!Array.isArray(msg.arena.stages)) msg.arena.stages = []
  const index = msg.arena.stages.findIndex((item) => item.id === stageId)
  if (index < 0) return
  msg.arena.stages[index] = { ...msg.arena.stages[index], ...patch }
  activeConversation.value.messages = [...activeConversation.value.messages]
  scrollToBottom()
}

function appendArenaReasoningLog(msg, source, eventType, content) {
  if (!msg?.arena) return
  if (!msg.arena.reasoningLogs) {
    msg.arena.reasoningLogs = { A: [], B: [] }
  }
  const safeContent = String(content || '').trim()
  if (!safeContent) return
  const logs = msg.arena.reasoningLogs[source] || []
  logs.push(`[${timeTag()}][${eventType}] ${safeContent}`)
  msg.arena.reasoningLogs[source] = logs.slice(-10)
  updateArenaStage(msg, 'reasoning', {
    detail: [
      '模型 A 事件流：',
      ...(msg.arena.reasoningLogs.A || []).map(item => `- ${item}`),
      '',
      '模型 B 事件流：',
      ...(msg.arena.reasoningLogs.B || []).map(item => `- ${item}`),
    ].join('\n'),
  })
}

function handleArenaStreamEvent(msg, event) {
  if (!msg?.arena || !event) return
  const metadata = event.metadata || {}
  function resolveArenaSource(msg, metadata) {
    let s = metadata.source || metadata.model || ''
    if (typeof s === 'number') s = String(s)
    s = String(s || '').toUpperCase().trim()
    if (!s && typeof metadata.index !== 'undefined') s = String(metadata.index)

    if (!s) return 'A'
    if (s === '0' || s === 'A' || s.includes('A')) return 'A'
    if (s === '1' || s === 'B' || s.includes('B')) return 'B'

    const modelName = String(metadata.model || metadata.modelName || '').trim()
    if (modelName) {
      if (msg.arena.modelA && msg.arena.modelA.toUpperCase().includes(modelName.toUpperCase())) return 'A'
      if (msg.arena.modelB && msg.arena.modelB.toUpperCase().includes(modelName.toUpperCase())) return 'B'
    }

    return s.startsWith('A') ? 'A' : 'B'
  }

  const source = resolveArenaSource(msg, metadata)

  if (event.type === 'arena_init') {
    updateArenaStage(msg, 'pick', {
      status: 'done',
      detail: '已完成匿名映射：模型不会在投票前展示真实名称。',
    })
    updateArenaStage(msg, 'dispatch', {
      status: 'running',
      time: timeTag(),
      expanded: true,
      detail: '后端已开始并行建立双路模型流。',
    })
    return
  }

  if (event.type === 'arena_model_event') {
    updateArenaStage(msg, 'dispatch', {
      status: 'done',
      detail: '并行流已建立，正在持续接收模型事件。',
    })
    updateArenaStage(msg, 'reasoning', {
      status: 'running',
      time: msg.arena.reasoningStartedAt || timeTag(),
      expanded: true,
    })
    msg.arena.reasoningStartedAt = msg.arena.reasoningStartedAt || timeTag()
    appendArenaReasoningLog(msg, source, metadata.eventType || 'event', event.content)
    return
  }

  if (event.type === 'arena_answer_chunk') {
    if (source === 'A') msg.arena.answerA = (msg.arena.answerA || '') + (event.content || '')
    if (source === 'B') msg.arena.answerB = (msg.arena.answerB || '') + (event.content || '')
    updateArenaStage(msg, 'reasoning', {
      status: 'running',
      expanded: true,
    })
    activeConversation.value.messages = [...activeConversation.value.messages]
    scrollToBottom()
    return
  }

  if (event.type === 'arena_model_done') {
    if (!msg.arena.doneFlags) msg.arena.doneFlags = { A: false, B: false }
    msg.arena.doneFlags[source] = true
    appendArenaReasoningLog(msg, source, 'done', '该模型已完成流式输出。')
    if (msg.arena.doneFlags.A && msg.arena.doneFlags.B) {
      updateArenaStage(msg, 'reasoning', { status: 'done' })
      updateArenaStage(msg, 'merge', {
        status: 'running',
        time: timeTag(),
        expanded: true,
        detail: '双路输出已完成，正在整理匿名对比结果。',
      })
    }
    return
  }

  if (event.type === 'arena_model_error') {
    appendArenaReasoningLog(msg, source, 'error', event.content || '模型流式调用失败')
    updateArenaStage(msg, 'reasoning', { status: 'error' })
    updateArenaStage(msg, 'merge', {
      status: 'error',
      time: timeTag(),
      detail: '模型流中断，结果可能不完整。',
    })
    return
  }

  if (event.type === 'arena_complete') {
    const finalMeta = metadata || {}
    msg.arena.modelA = finalMeta.modelA || msg.arena.modelA
    msg.arena.modelB = finalMeta.modelB || msg.arena.modelB
    msg.arena.answerA = finalMeta.answerA || msg.arena.answerA
    msg.arena.answerB = finalMeta.answerB || msg.arena.answerB
    msg.arena.loading = false
    msg.content = 'Auto对比已完成，等待投票后揭晓模型名。'
    updateArenaStage(msg, 'merge', {
      status: 'done',
      detail: '双路回答已完成并匿名展示，当前进入投票阶段。',
    })
    activeConversation.value.messages = [...activeConversation.value.messages]
    return
  }

  if (event.type === 'arena_error') {
    msg.arena.loading = false
    msg.content = event.content || 'Auto对比失败'
    updateArenaStage(msg, 'reasoning', {
      status: 'error',
      detail: '对比流执行失败。',
    })
    updateArenaStage(msg, 'merge', {
      status: 'error',
      time: timeTag(),
      detail: event.content || '结果整理失败。',
    })
  }
}

watch(() => activeConversation.value?.messages?.length, scrollToBottom)

onMounted(() => {
  if (route.query.planId) loadSavedPlan(route.query.planId)
  loadFromBackend()
  const cached = localStorage.getItem(TOKEN_STATUS_KEY)
  if (cached) tokenStatus.value = JSON.parse(cached)
})

watch(tokenStatus, (val) => {
  if (val) localStorage.setItem(TOKEN_STATUS_KEY, JSON.stringify(val))
}, { deep: true })

function handleNewConversation() {
  newConversation()
  sidebarCollapsed.value = false
}

function toggleArenaMode() {
  arenaMode.value = !arenaMode.value
}

function startStream(query, mode = selectedMode.value, generatePlanFirst = null, file = null) {
  if (generatePlanFirst === null) {
    generatePlanFirst = false
  }

  addMessage({ role: 'user', content: query })
  addMessage({ role: 'assistant', content: '', events: [], planContent: '' })

  loading.value = true
  pendingAskUser.value = null
  activeSuggestions.value = []
  scrollToBottom()

  const formData = new FormData()
  formData.append('query', query)
  formData.append('userId', localStorage.getItem('userId') || '1')
  formData.append('mode', mode)
  formData.append('generatePlanFirst', String(generatePlanFirst))
  formData.append('model', selectedModel.value)

  // Append history (excluding the two we just added for this current turn)
  const historyRaw = activeConversation.value.messages.slice(0, -2).filter(m => m.role === 'user' || m.role === 'assistant')
  const historyToSent = historyRaw.map(m => ({ role: m.role, content: m.content || m.answer || '' }))
  formData.append('chatHistoryJson', JSON.stringify(historyToSent))

  if (forceCompress.value) { formData.append('forceCompress', 'true'); forceCompress.value = false }
  if (file) formData.append('file', file)

  const agentMsg = () => activeConversation.value?.messages.at(-1)

  activeController.value = streamPost(
    '/api/assistant/chat/stream',
    formData,
    (event) => {
      const msg = agentMsg()
      if (!msg) return
      if (event.type === 'token_status') {
        tokenStatus.value = {
          ...(tokenStatus.value || {}),
          ...(event.metadata || {}),
        }
        activeConversation.value.messages = [...activeConversation.value.messages]
        return
      }
      if (event.type === 'answer') {
        msg.answer = (msg.answer || '') + event.content
      } else if (event.type === 'plan') {
        if (!msg.planContent) msg.planContent = ''
        msg.planContent += event.content
      } else if (event.type === 'done') {
        return
      } else if (event.type === 'ask_user') {
        pendingAskUser.value = {
          message: event.content,
          questions: event.metadata?.questions || [],
        }
        msg.events.push({ type: 'ask_user', content: event.content, metadata: event.metadata })
      } else if (event.type === 'suggestions') {
        activeSuggestions.value = event.metadata?.questions || []
      } else if (['thought', 'action', 'observation', 'reflection'].includes(event.type)) {
        msg.events.push({ type: event.type, content: event.content, metadata: event.metadata })
      } else if (event.type === 'error') {
        msg.events.push({ type: 'observation', content: `Error: ${event.content}`, metadata: {} })
      }
      scrollToBottom()
      activeConversation.value.messages = [...activeConversation.value.messages]
    },
    finishStream,
    (err) => {
      loading.value = false
      activeController.value = null
      console.error('SSE error:', err)
      addMessage({ role: 'assistant', content: `请求失败: ${err.message}`, events: [] })
    },
  )
}

async function finishStream() {
  activeController.value = null
  const msg = activeConversation.value?.messages.at(-1)
  if (msg?.answer) {
    try {
      const parsed = JSON.parse(msg.answer)
      if (parsed.destination || parsed.markdown) {
        setResult(parsed)
      }
    } catch {
      setResult({ markdown: msg.answer, source: 'markdown' })
    }
  }
  // Sync first, then show button — so backendId is ready on click
  await syncActiveToBackend()
  persist()
  loading.value = false
}

function handleSend({ query, file }) {
  if (!query) return
  if (!activeConversation.value) newConversation()

  if (arenaMode.value) {
    handleAutoSend({ query, file })
    return
  }

  startStream(query, selectedMode.value, null, file)
}

function handleConfirmResponse({ answers }) {
  const parts = answers.map(a => `${a.question}: ${a.answer}`)
  const message = `[用户确认信息] ${parts.join('; ')}`
  startStream(message, 'agent', false)
}

function handleSuggestionSelect(question) {
  activeSuggestions.value = []
  startStream(question, selectedMode.value)
}

async function handleAutoSend({ query, file }) {
  addMessage({ role: 'user', content: query })
  addMessage({
    role: 'assistant',
    content: '',
    events: [],
    arena: {
      loading: true,
      modelA: '',
      modelB: '',
      answerA: '',
      answerB: '',
      voted: '',
      stages: [],
      reasoningLogs: { A: [], B: [] },
      doneFlags: { A: false, B: false },
    },
  })

  loading.value = true
  scrollToBottom()

  const formData = new FormData()
  formData.append('query', query)
  formData.append('userId', localStorage.getItem('userId') || '1')

  const historyRaw = activeConversation.value.messages.slice(0, -2).filter(m => m.role === 'user' || m.role === 'assistant')
  const historyToSent = historyRaw.map(m => ({ role: m.role, content: m.content || m.answer || '' }))
  formData.append('chatHistoryJson', JSON.stringify(historyToSent))

  if (forceCompress.value) { formData.append('forceCompress', 'true'); forceCompress.value = false }
  if (file) formData.append('file', file)

  const arenaMsg = () => activeConversation.value?.messages.at(-1)
  const msg = arenaMsg()
  if (msg?.arena) {
    msg.arena.stages = createInitialArenaStages(query)
    activeConversation.value.messages = [...activeConversation.value.messages]
  }

  activeController.value = streamPost(
    '/api/arena/auto/stream',
    formData,
    (event) => {
      const current = arenaMsg()
      handleArenaStreamEvent(current, event)
    },
    () => {
      const current = arenaMsg()
      if (current?.arena?.loading) {
        current.arena.loading = false
        updateArenaStage(current, 'merge', {
          status: 'done',
          detail: '流式连接结束，已停止接收新事件。',
        })
        activeConversation.value.messages = [...activeConversation.value.messages]
      }
      loading.value = false
      activeController.value = null
      persist()
    },
    (err) => {
      const current = arenaMsg()
      if (current?.arena) {
        current.arena.loading = false
        current.content = `Auto对比失败: ${err.message}`
        updateArenaStage(current, 'reasoning', {
          status: 'error',
          time: timeTag(),
          detail: `流式事件处理失败：${err.message}`,
        })
        updateArenaStage(current, 'merge', {
          status: 'error',
          time: timeTag(),
          detail: '对比流程中断。',
        })
        activeConversation.value.messages = [...activeConversation.value.messages]
      }
      loading.value = false
      activeController.value = null
      persist()
    },
  )
}

function stopActiveRequest() {
  if (!loading.value || !activeController.value) return
  activeController.value.abort()
  activeController.value = null
  loading.value = false

  const conv = activeConversation.value
  if (!conv) return
  const last = [...conv.messages].reverse().find(m => m.role === 'assistant')
  if (last?.arena) {
    last.arena.loading = false
    if (!last.arena.answerA) last.arena.answerA = '已停止'
    updateArenaStage(last, 'reasoning', {
      status: 'error',
      time: timeTag(),
      detail: '用户手动停止了请求。',
    })
    updateArenaStage(last, 'merge', {
      status: 'error',
      time: timeTag(),
      detail: '未能完成最终整理。',
    })
  } else if (last) {
    if (!last.events) last.events = []
    last.events.push({ type: 'observation', content: '已停止', metadata: {} })
  }
  conv.messages = [...conv.messages]
  persist()
}

async function handleArenaVote(msg, result) {
  if (!msg?.arena || msg.arena.voted) return
  msg.arena.voted = result
  activeConversation.value.messages = [...activeConversation.value.messages]
  try {
    await fetch('/api/arena/vote', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        modelA: msg.arena.modelA,
        modelB: msg.arena.modelB,
        result,
      }),
    })
  } catch {
    msg.arena.voted = ''
    activeConversation.value.messages = [...activeConversation.value.messages]
  }
  persist()
}

async function loadSavedPlan(planId) {
  try {
    const res = await fetch(`/api/travel/plan/${planId}`)
    const data = await res.json()
    if (data.code === 200) {
      const plan = data.data
      if (!activeConversation.value) newConversation()
      setResult({
        title: plan.title,
        destination: plan.destination,
        days: plan.days,
        markdown: plan.itinerary || '',
        images: [],
      })
    }
  } catch (e) {
    console.error('加载规划失败:', e)
  }
}

async function goToWorkbench() {
  const conv = activeConversation.value
  if (!conv) return
  navigatingToWorkbench.value = true

  if (!conv.backendId) {
    navigatingToWorkbench.value = false
    alert('会话尚未同步，请稍候重试...')
    return
  }
  router.push('/plan-workbench?c=' + conv.backendId)
}

async function syncTurnToKnowledge(index) {
  if (!activeConversation.value) return
  const key = knowledgeTurnKey(index)
  if (syncingKnowledgeTurns.value.has(key)) return
  syncingKnowledgeTurns.value = new Set([...syncingKnowledgeTurns.value, key])
  try {
    const payload = buildKnowledgeSyncPayload(activeConversation.value, index, {
      model: selectedModel.value,
      mode: selectedMode.value,
    })
    if (!payload.userMessage && !payload.assistantAnswer) {
      alert('没有可同步的对话内容')
      return
    }
    const res = await fetch('/api/knowledge/sync-turn', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    const data = await safeReadJson(res)
    if (!res.ok) {
      throw new Error(data?.message || `HTTP ${res.status}`)
    }
    alert(data.code === 200 ? '已同步到知识中心' : `同步失败: ${data.message}`)
  } catch (error) {
    console.error('同步知识中心失败:', error)
    alert(`同步失败: ${error.message || '请稍后重试'}`)
  } finally {
    const next = new Set(syncingKnowledgeTurns.value)
    next.delete(key)
    syncingKnowledgeTurns.value = next
  }
}

function knowledgeTurnKey(index) {
  return `${activeConversation.value?.id || 'unknown'}:${index}`
}

function isSyncingKnowledgeTurn(index) {
  return syncingKnowledgeTurns.value.has(knowledgeTurnKey(index))
}

async function safeReadJson(response) {
  try {
    return await response.json()
  } catch {
    return { message: `HTTP ${response.status}` }
  }
}

function handleUpdateItinerary(updated) {
  const result = activeConversation.value?.result
  if (result) { setResult({ ...result, itinerary: updated }) }
}

function setCompressNotice(msg, level = 'info') {
  compressNotice.value = msg; const logger = console[level] || console.log; logger('[compress]', msg)
  if (compressNoticeTimer) clearTimeout(compressNoticeTimer)
  compressNoticeTimer = setTimeout(() => { compressNotice.value = ''; compressNoticeTimer = null }, 3000)
}

function estimateTokenCount(messages) {
  return Math.max(1, Math.ceil((messages || []).reduce((s, m) => s + String(m?.content || m?.answer || m?.planContent || '').length, 0) / 4))
}

function buildTokenSnapshot(messages) {
  const c = tokenStatus.value || {}; const maxCtx = c.max_context_tokens || 12000; const outB = c.output_budget || 2000
  const inT = estimateTokenCount(messages); return { ...c, input_tokens: inT, history_tokens: Math.max(0, inT - 200), output_budget: outB, max_context_tokens: maxCtx, utilization: Number((inT / maxCtx).toFixed(4)) }
}

function triggerForceCompress() {
  if (compressing.value || loading.value) { setCompressNotice('当前有请求在进行，等结束后再压缩。', 'warn'); return }
  const conv = activeConversation.value
  if (!conv) { setCompressNotice('没有可压缩的对话。', 'warn'); return }
  const raw = conv.messages.filter(m => m.role === 'user' || m.role === 'assistant')
  if (raw.length <= COMPRESS_KEEP_LAST) { setCompressNotice(compressHint.value || `历史消息不足，至少需要 ${COMPRESS_KEEP_LAST + 1} 条对话才能压缩`, 'warn'); return }
  setCompressNotice('开始压缩历史消息...', 'info')
  compressing.value = true
  fetch('/api/assistant/compress', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ chatHistory: raw.map(m => ({ role: m.role, content: m.content || m.answer || '' })), keepLast: COMPRESS_KEEP_LAST }) })
    .then(r => r.json()).then(d => {
      if (d.code !== 200 || !d.data) { setCompressNotice('压缩异常', 'error'); forceCompress.value = true; return }
      const s = (d.data.summary || '').trim(); if (!d.data.compressed || !s) { setCompressNotice('无可用的摘要', 'warn'); return }
      const kl = d.data.keep_last || COMPRESS_KEEP_LAST; conv.messages = [{ role: 'assistant', answer: '【对话摘要】\n' + s, events: [] }, ...conv.messages.slice(-kl)]
      conv.updatedAt = Date.now(); tokenStatus.value = buildTokenSnapshot(conv.messages)
      setCompressNotice('压缩完成', 'info'); persist(); nextTick(() => { const el = messagesRef.value; if (el) el.scrollTop = 0 })
    }).catch(e => { setCompressNotice('压缩失败: ' + e.message, 'error'); forceCompress.value = true })
    .finally(() => { compressing.value = false })
}

function formatToken(v) { if (!v) return '0'; return v >= 1000 ? (v / 1000).toFixed(1) + 'K' : String(v) }

</script>

<style scoped>
.ai-plan-page {
  display: flex;
  width: 100%;
  height: 100vh;
  background: var(--color-bg);
  font-family: var(--font-family);
  color: var(--color-body);
  overflow: hidden;
}

/* Center panel */
.center-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
}

.message-actions {
  display: flex;
  justify-content: flex-start;
  margin: 8px 0 14px;
}

.sync-knowledge-btn {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-body);
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.sync-knowledge-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.sync-knowledge-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 40px 20px;
}

.brand-greeting {
  text-align: center;
}

.brand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: var(--gradient-brand);
  color: white;
  margin-bottom: 16px;
}

.brand-greeting h1 {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-title);
  margin: 0 0 6px;
}

.brand-greeting p {
  font-size: 14px;
  color: var(--color-muted);
  margin: 0;
}

/* Conversation view */
.conversation-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.agent-content-wrapper {
  width: 100%;
  max-width: 60%;
  min-width: 0;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.agent-content-wrapper.arena {
  max-width: 100%;
}

.compact-input-area {
  padding: 12px 24px 16px;
  border-top: 1px solid var(--color-border);
  background: var(--color-bg);
}

.compress-notice { margin-top: 8px; font-size: 12px; color: var(--color-muted); line-height: 1.4; }

.workbench-trigger-wrapper {
  display: flex;
  justify-content: center;
  margin: 16px 0;
  animation: slideUp 0.4s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.workbench-trigger-btn {
  background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%);
  color: white;
  border: none;
  padding: 12px 28px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 50px;
  cursor: pointer;
  box-shadow: 0 4px 15px rgba(99, 102, 241, 0.4);
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.workbench-trigger-btn:hover {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.6);
}

.workbench-trigger-btn:active:not(:disabled) {
  transform: translateY(1px);
}

.workbench-trigger-btn:disabled {
  opacity: 0.7;
  cursor: wait;
  transform: none;
}

.btn-loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: btn-spin 0.6s infinite linear;
}

@keyframes btn-spin { to { transform: rotate(360deg); } }

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
