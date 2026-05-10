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
        <ChatInput :loading="loading" v-model="selectedMode" :selectedModel="selectedModel" @update:selectedModel="selectedModel = $event" @submit="handleSend" @stop="stopActiveRequest" />
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
                  :events="msg.arena.events || []"
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
                    :toolName="ev.metadata?.tool_name || ''"
                    :metadata="ev.metadata"
                  />
                  <MessageBubble
                    v-if="msg.answer"
                    role="assistant"
                    :content="msg.answer"
                  />
                </template>
              </div>
            </template>
          </template>

          <StreamingIndicator v-if="loading" />
        </div>

        <div class="compact-input-area">
          <ChatInput
            compact
            :loading="loading"
            :hasMessages="true"
            v-model="selectedMode"
            :selectedModel="selectedModel"
            @update:selectedModel="selectedModel = $event"
            @submit="handleSend"
            @stop="stopActiveRequest"
          />
        </div>
      </div>
    </div>

    <!-- Right panel: map + itinerary (shown after result) -->
    <div v-if="activeConversation?.result" class="right-panel">
      <div class="panel-header">
        <h2>{{ activeConversation.result.title }}</h2>
        <p class="panel-meta">{{ activeConversation.result.destination }} · {{ activeConversation.result.days }}天</p>
        <div class="panel-actions">
          <button class="panel-btn primary" @click="saveToMyPlans">保存规划</button>
        </div>
      </div>
      <div class="panel-map">
        <MapComponent
          :destinations="[activeConversation.result.destination]"
          :itinerary="parsedItinerary"
        />
      </div>
      <div class="panel-itinerary">
        <ItineraryPanel
          :title="activeConversation.result.title"
          :destination="activeConversation.result.destination"
          :days="activeConversation.result.days"
          :itinerary="parsedItinerary"
          :summary="renderedSummary"
          @update:itinerary="handleUpdateItinerary"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, watch, onMounted, inject } from 'vue'
import { useRoute } from 'vue-router'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { useAuth } from '../composables/useAuth'
import { useSSE } from '../composables/useSSE'
import { useConversation } from '../composables/useConversation'
import SvgIcon from '../components/SvgIcon.vue'
import MapComponent from '../components/MapComponent.vue'
import ItineraryPanel from '../components/ItineraryPanel.vue'
import ChatInput from '../components/ai-plan/ChatInput.vue'
import MessageBubble from '../components/ai-plan/MessageBubble.vue'
import AgentEventBlock from '../components/ai-plan/AgentEventBlock.vue'
import AgentPlanBlock from '../components/ai-plan/AgentPlanBlock.vue'
import StreamingIndicator from '../components/ai-plan/StreamingIndicator.vue'
import ConversationSidebar from '../components/ai-plan/ConversationSidebar.vue'
import ModelArenaCompare from '../components/ai-plan/ModelArenaCompare.vue'

const route = useRoute()
const { isLoggedIn } = useAuth()
const showLogin = inject('showLoginModal')

const {
  conversations, activeId, activeConversation,
  newConversation, selectConversation, deleteConversation,
  addMessage, setResult, persist, loadFromBackend,
} = useConversation()

const { streamPost } = useSSE()

const sidebarCollapsed = ref(false)
const loading = ref(false)
const messagesRef = ref(null)
const activeController = ref(null)
const selectedMode = ref('agent')
const selectedModel = ref('deepseek-v4-flash')
const arenaTraceTimers = []

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const renderedSummary = computed(() => {
  const result = activeConversation.value?.result
  if (!result?.markdown) return ''
  return DOMPurify.sanitize(md.render(result.markdown))
})

const parsedItinerary = computed(() => {
  const result = activeConversation.value?.result
  if (!result) return []
  if (result.itinerary && Array.isArray(result.itinerary)) return result.itinerary

  const itinerary = []
  const lines = (result.markdown || '').split('\n')
  let currentDay = null

  lines.forEach((line) => {
    if (line.includes('第') && line.includes('天') && line.startsWith('#')) {
      currentDay = {
        day: parseInt(line.match(/\d+/)?.[0] || itinerary.length + 1),
        activities: [],
        date: '',
        summary: '',
      }
      itinerary.push(currentDay)
    }
    if (currentDay && line.trim().startsWith('-')) {
      const trimmed = line.trim().substring(1).trim()
      const parts = trimmed.split(/[-–]/).map(s => s.trim())
      if (parts.length >= 1) {
        const firstPart = parts[0]
        const colonIdx = firstPart.search(/[：:]/)
        let time = '', location = ''
        if (colonIdx > -1) {
          time = firstPart.substring(0, colonIdx).trim()
          location = firstPart.substring(colonIdx + 1).trim()
        } else {
          location = firstPart
        }
        currentDay.activities.push({
          time: time || '全天',
          location,
          description: parts.slice(1).join(' - '),
          coordinates: mockCoords(result.destination, location),
        })
      }
    }
  })

  if (!itinerary.length) {
    for (let i = 1; i <= (result.days || 1); i++) {
      itinerary.push({
        day: i,
        activities: [{
          location: result.destination,
          description: `第${i}天行程`,
          time: '全天',
          coordinates: mockCoords(result.destination),
        }],
      })
    }
  }
  return itinerary
})

function mockCoords(dest) {
  const cities = {
    '北京': [39.9042, 116.4074], '上海': [31.2304, 121.4737],
    '东京': [35.6762, 139.6503], '大阪': [34.6937, 135.5023],
    '京都': [35.0116, 135.7681], '首尔': [37.5665, 126.9780],
    '曼谷': [13.7563, 100.5018], '新加坡': [1.3521, 103.8198],
    '南京': [32.0603, 118.7969], '杭州': [30.2741, 120.1551],
  }
  const base = cities[dest] || [30, 110]
  const off = Math.random() * 0.5 - 0.25
  return [base[0] + off, base[1] + off]
}

function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function clearArenaTraceTimers() {
  while (arenaTraceTimers.length) {
    clearTimeout(arenaTraceTimers.pop())
  }
}

function pushArenaTrace(msg, type, content, metadata = {}) {
  if (!msg?.arena) return
  if (!Array.isArray(msg.arena.events)) msg.arena.events = []
  msg.arena.events.push({ type, content, metadata })
  activeConversation.value.messages = [...activeConversation.value.messages]
  scrollToBottom()
}

function scheduleArenaTrace(msg) {
  clearArenaTraceTimers()
  const steps = [
    { delay: 0, type: 'thought', content: '从候选池中随机抽取两个匿名模型进行对比。', metadata: { step: 1 } },
    { delay: 800, type: 'action', content: '并行发起两路回答请求，保持同一输入和上下文。', metadata: { step: 2 } },
    { delay: 1700, type: 'observation', content: '正在等待两路输出返回，开始整理对比结果。', metadata: { step: 3 } },
    { delay: 2600, type: 'reflection', content: '回答已经生成完毕，投票后会揭晓具体模型名。', metadata: { step: 4 } },
  ]

  steps.forEach((step) => {
    const timer = setTimeout(() => {
      if (!msg?.arena?.loading) return
      pushArenaTrace(msg, step.type, step.content, step.metadata)
    }, step.delay)
    arenaTraceTimers.push(timer)
  })
}

watch(() => activeConversation.value?.messages?.length, scrollToBottom)

onMounted(() => {
  if (route.query.planId) loadSavedPlan(route.query.planId)
  loadFromBackend()
})

function handleNewConversation() {
  newConversation()
  sidebarCollapsed.value = false
}

function handleSend({ query, file }) {
  if (!query) return
  if (!activeConversation.value) newConversation()

  if (selectedMode.value === 'auto') {
    handleAutoSend({ query, file })
    return
  }

  addMessage({ role: 'user', content: query })
  addMessage({ role: 'assistant', content: '', events: [], planContent: '' })

  loading.value = true
  scrollToBottom()

  const formData = new FormData()
  formData.append('query', query)
  formData.append('userId', localStorage.getItem('userId') || '1')
  formData.append('mode', selectedMode.value)
  formData.append('generatePlanFirst', selectedMode.value === 'plan' ? 'false' : 'true')
  formData.append('model', selectedModel.value)

  // Append history (excluding the two we just added for this current turn)
  const historyRaw = activeConversation.value.messages.slice(0, -2).filter(m => m.role === 'user' || m.role === 'assistant')
  const historyToSent = historyRaw.map(m => ({ role: m.role, content: m.content || m.answer || '' }))
  formData.append('chatHistoryJson', JSON.stringify(historyToSent))

  if (file) formData.append('file', file)

  const agentMsg = () => activeConversation.value?.messages.at(-1)

  activeController.value = streamPost(
    '/api/assistant/chat/stream',
    formData,
    (event) => {
      const msg = agentMsg()
      if (!msg) return
      if (event.type === 'answer') {
        msg.answer = (msg.answer || '') + event.content
      } else if (event.type === 'plan') {
        if (!msg.planContent) msg.planContent = ''
        msg.planContent += event.content
      } else if (event.type === 'done') {
        return
      } else if (['thought', 'action', 'observation', 'reflection'].includes(event.type)) {
        msg.events.push({ type: event.type, content: event.content, metadata: event.metadata })
      } else if (event.type === 'error') {
        msg.events.push({ type: 'observation', content: `Error: ${event.content}`, metadata: {} })
      }
      scrollToBottom()
      // Force reactivity update
      activeConversation.value.messages = [...activeConversation.value.messages]
    },
    () => {
      loading.value = false
      activeController.value = null
      const msg = agentMsg()
      if (msg?.answer) {
        try {
          const parsed = JSON.parse(msg.answer)
          if (parsed.destination || parsed.markdown) {
            setResult(parsed)
          }
        } catch {
          // Not JSON — keep as text answer
        }
      }
      persist()
    },
    (err) => {
      loading.value = false
      activeController.value = null
      console.error('SSE error:', err)
      addMessage({ role: 'assistant', content: `请求失败: ${err.message}`, events: [] })
    },
  )
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
      events: [],
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

  if (file) formData.append('file', file)

  const arenaMsg = () => activeConversation.value?.messages.at(-1)
  scheduleArenaTrace(arenaMsg())

  const controller = new AbortController()
  activeController.value = controller

  try {
    const res = await fetch('/api/arena/auto', {
      method: 'POST',
      body: formData,
      signal: controller.signal,
    })
    const data = await res.json()
    if (data.code !== 200 || !data.data) {
      throw new Error(data.message || '请求失败')
    }
    const msg = arenaMsg()
    if (msg?.arena) {
      msg.arena.loading = false
      msg.arena.modelA = data.data.modelA
      msg.arena.modelB = data.data.modelB
      msg.arena.answerA = data.data.answerA
      msg.arena.answerB = data.data.answerB
      msg.content = 'Auto对比已完成，等待投票后揭晓模型名。'
      activeConversation.value.messages = [...activeConversation.value.messages]
    }
  } catch (err) {
    const errorMessage = err instanceof Error ? err.message : String(err)
    const aborted = err && err.name === 'AbortError'
    const msg = arenaMsg()
    if (msg?.arena) {
      msg.arena.loading = false
      msg.arena.answerA = aborted ? '已停止' : `请求失败：${errorMessage}`
      msg.arena.answerB = ''
      msg.content = aborted ? 'Auto对比已停止' : 'Auto对比失败'
      activeConversation.value.messages = [...activeConversation.value.messages]
    }
  } finally {
    clearArenaTraceTimers()
    loading.value = false
    activeController.value = null
    persist()
  }
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
    clearArenaTraceTimers()
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

async function saveToMyPlans() {
  if (!isLoggedIn.value) { showLogin(); return }
  const result = activeConversation.value?.result
  if (!result?.markdown) { alert('没有可保存的行程'); return }
  try {
    const res = await fetch('/api/travel/plan/save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: Number(localStorage.getItem('userId')) || 1,
        title: result.title,
        destination: result.destination,
        days: result.days,
        itinerary: JSON.stringify(parsedItinerary.value),
      }),
    })
    const data = await res.json()
    alert(data.code === 200 ? '保存成功！' : '保存失败: ' + data.message)
  } catch {
    alert('保存失败，请重试')
  }
}

function handleUpdateItinerary(updated) {
  const result = activeConversation.value?.result
  if (result) {
    setResult({ ...result, itinerary: updated })
  }
}
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

/* Right panel */
.right-panel {
  width: 480px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--color-border);
  background: var(--color-card);
  height: 100%;
  overflow: hidden;
}

.panel-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.panel-header h2 {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-title);
  margin: 0 0 4px;
}

.panel-meta {
  font-size: 12px;
  color: var(--color-muted);
  margin: 0 0 12px;
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.panel-btn {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 500;
  font-family: var(--font-family);
  background: transparent;
  color: var(--color-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.panel-btn:hover {
  background: var(--color-surface);
}

.panel-btn.primary {
  background: var(--gradient-brand);
  color: white;
  border: none;
}

.panel-btn.primary:hover {
  filter: brightness(1.1);
}

.panel-map {
  height: 240px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--color-border);
}

.panel-itinerary {
  flex: 1;
  overflow-y: auto;
}

/* Responsive */
@media (max-width: 1200px) {
  .right-panel {
    width: 380px;
  }
}

@media (max-width: 900px) {
  .right-panel {
    display: none;
  }
}
</style>
