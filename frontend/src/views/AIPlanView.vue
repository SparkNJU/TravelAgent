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
          v-model="selectedMode"
          :initialQuery="initialQuery"
          :selectedModel="selectedModel"
          :arenaMode="arenaMode"
          :tokenStatus="tokenStatus"
          :canCompress="canCompress"
          :compressHint="compressHint"
          @compress="triggerForceCompress"
          @toggleArena="toggleArenaMode"
          @update:selectedModel="selectedModel = $event"
          @submit="handleSend"
          @stop="stopActiveRequest"
        />
      </div>

      <!-- Active conversation: messages + compact input -->
      <div v-else class="conversation-view">
        <div class="messages-area" ref="messagesRef" @scroll="handleMessagesScroll">
          <template v-for="turn in conversationTurns" :key="turn.key">
            <div class="conversation-turn">
              <div
                v-if="turn.user"
                class="message-turn turn-user"
                :class="{ active: activeTurnIndex === turn.userIndex }"
                :data-message-index="turn.userIndex"
              >
              <!-- User message -->
                <MessageBubble role="user" :content="turn.user.content" />
              </div>

              <!-- Agent message: events + answer -->
              <div
                v-if="turn.assistant"
                class="message-turn turn-assistant"
                :class="{ active: activeTurnIndex === turn.assistantIndex }"
                :data-message-index="turn.assistantIndex"
              >
                <div class="agent-content-wrapper" :class="{ arena: turn.assistant.arena }">
                  <ModelArenaCompare
                    v-if="turn.assistant.arena"
                    :modelA="turn.assistant.arena.modelA"
                    :modelB="turn.assistant.arena.modelB"
                    :answerA="turn.assistant.arena.answerA"
                    :answerB="turn.assistant.arena.answerB"
                    :loading="turn.assistant.arena.loading"
                    :voted="turn.assistant.arena.voted"
                    :stages="turn.assistant.arena.stages || []"
                    @vote="handleArenaVote(turn.assistant, $event)"
                  />
                  <template v-else>
                    <AgentPlanBlock
                      v-if="turn.assistant.planContent"
                      :content="turn.assistant.planContent"
                      :streaming="isMessageStreaming(turn.assistantIndex)"
                    />
                    <!-- 流式输出时：显示最新的思考过程 -->
                    <div
                      v-if="isMessageStreaming(turn.assistantIndex) && getLatestEvent(turn.assistant.events)"
                      class="live-event-block"
                    >
                      <AgentEventBlock
                        :type="getLatestEvent(turn.assistant.events).type"
                        :content="getLatestEvent(turn.assistant.events).content"
                        :toolName="getLatestEvent(turn.assistant.events).metadata?.tool_name || ''"
                        :metadata="getLatestEvent(turn.assistant.events).metadata"
                        :streaming="true"
                      />
                    </div>
                    <!-- 完成后：显示所有事件的折叠面板 -->
                    <div
                      v-if="!isMessageStreaming(turn.assistantIndex) && turn.assistant.events?.length"
                      class="trace-panel"
                      :class="{ open: isTraceOpen(turn.assistantIndex) }"
                    >
                      <button
                        type="button"
                        class="trace-header"
                        :aria-expanded="isTraceOpen(turn.assistantIndex)"
                        @click="toggleTrace(turn.assistantIndex)"
                      >
                        <span class="trace-spark"><SvgIcon name="sparkles" :size="15" /></span>
                        <span class="trace-title">Thoughts</span>
                        <span class="trace-meta">{{ traceSummary(turn.assistant.events) }}</span>
                        <span class="trace-chevron">
                          <SvgIcon name="chevron-down" :size="15" />
                        </span>
                      </button>
                      <div v-if="isTraceOpen(turn.assistantIndex)" class="trace-body">
                        <AgentEventBlock
                          v-for="(ev, j) in turn.assistant.events"
                          :key="j"
                          :type="ev.type"
                          :content="ev.content"
                          :toolName="ev.metadata?.tool_name || ''"
                          :metadata="ev.metadata"
                        />
                      </div>
                    </div>
                    <MessageBubble
                      v-if="turn.assistant.answer"
                      role="assistant"
                      :content="turn.assistant.answer"
                    />
                    <div v-if="turn.assistant.answer && !loading" class="message-actions">
                      <button
                        class="sync-knowledge-btn"
                        :disabled="isSyncingKnowledgeTurn(turn.assistantIndex)"
                        @click="syncTurnToKnowledge(turn.assistantIndex)"
                      >
                        <SvgIcon name="sparkles" :size="13" />
                        {{ isSyncingKnowledgeTurn(turn.assistantIndex) ? '同步中...' : '同步到知识中心' }}
                      </button>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </template>

          <StreamingIndicator v-if="loading" />

          <!-- Workbench: parsing / ready / error -->
          <div v-if="activeConversation && activeConversation.result && !loading" class="workbench-trigger-wrapper">
            <!-- Parsing in progress -->
            <div v-if="workbenchParsing" class="workbench-parsing-indicator">
              <StreamingIndicator />
              <span class="parsing-text">正在构建可视化工作台…</span>
            </div>
            <!-- Parse complete — ready to enter -->
            <button v-else-if="workbenchPlanId" class="workbench-trigger-btn ready" @click="enterWorkbench">
              进入可视化工作台 ➜
            </button>
            <!-- Parse failed -->
            <div v-else-if="workbenchError" class="workbench-error">
              <span class="error-text">⚠ {{ workbenchError }}</span>
              <button class="retry-btn" @click="retryWorkbench">重试</button>
            </div>
            <!-- Not started -->
            <button v-else class="workbench-trigger-btn" @click="goToWorkbench">
              进入可视化工作台 ➜
            </button>
          </div>
        </div>

        <nav v-if="turnMarkers.length" class="scroll-jump-layer" aria-label="Conversation jump points">
          <button
            v-for="marker in turnMarkers"
            :key="marker.index"
            type="button"
            class="scroll-jump-dot"
            :class="[marker.role, { active: activeTurnIndex === marker.index }]"
            :style="{ top: marker.top }"
            :aria-label="marker.label"
            @click="scrollToTurn(marker.index)"
          />
        </nav>

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
            :hasMessages="true"
            v-model="selectedMode"
            :initialQuery="initialQuery"
            :selectedModel="selectedModel"
            :arenaMode="arenaMode"
            :tokenStatus="tokenStatus"
            :canCompress="canCompress"
            :compressHint="compressHint"
            @compress="triggerForceCompress"
            @toggleArena="toggleArenaMode"
            @update:selectedModel="selectedModel = $event"
            @submit="handleSend"
            @stop="stopActiveRequest"
          />
          <div v-if="compressNotice" class="compress-notice">{{ compressNotice }}</div>
        </div>
      </div>
    </div>

    <aside class="plan-inspector" :class="{ open: inspectorOpen, collapsed: inspectorCollapsed }" aria-label="Agent Run Panel">
      <button
        type="button"
        class="inspector-edge-toggle"
        :aria-expanded="!inspectorCollapsed"
        :title="inspectorCollapsed ? '展开运行面板' : '折叠运行面板'"
        @click="toggleInspectorCollapse"
      >
        <SvgIcon :name="inspectorCollapsed ? 'chevron-left' : 'chevron-right'" :size="16" />
      </button>
      <section class="run-card hero-card">
        <div class="run-card-head">
          <div>
            <div class="run-kicker">旅游规划Agent</div>
            <h2>运行参数</h2>
          </div>
          <div class="run-card-actions">
            <button
              type="button"
              class="inspector-toggle"
              :aria-expanded="inspectorOpen"
              @click="toggleInspector"
            >
              <SvgIcon :name="inspectorOpen ? 'chevron-down' : 'chevron-up'" :size="14" />
              <span>{{ inspectorOpen ? '收起' : '展开' }}</span>
            </button>
          </div>
        </div>
      </section>

      <section class="run-card">
        <div class="panel-title">
          <SvgIcon name="settings" :size="15" />
          <span>运行设置</span>
        </div>
        <div class="run-row">
          <span>模式</span>
          <strong>{{ selectedModeLabel }}</strong>
        </div>
        <div class="run-row">
          <span>模型</span>
          <strong>{{ selectedModelLabel }}</strong>
        </div>
        <div class="setting-switch-grid">
          <button
            type="button"
            class="setting-toggle"
            :class="{ active: webSearchEnabled }"
            :aria-pressed="webSearchEnabled"
            @click="toggleWebSearch"
          >
            <span>
              联网搜索
              <span
                class="setting-info-dot"
                title="查看说明"
                @click.stop="settingTooltip = settingTooltip === 'webSearch' ? null : 'webSearch'"
              >ⓘ</span>
            </span>
            <i />
          </button>
          <button
            type="button"
            class="setting-toggle"
            :class="{ active: knowledgeSearchEnabled }"
            :aria-pressed="knowledgeSearchEnabled"
            @click="toggleKnowledgeSearch"
          >
            <span>
              知识检索
              <span
                class="setting-info-dot"
                title="查看说明"
                @click.stop="settingTooltip = settingTooltip === 'knowledgeSearch' ? null : 'knowledgeSearch'"
              >ⓘ</span>
            </span>
            <i />
          </button>
          <button
            type="button"
            class="setting-toggle"
            :class="{ active: arenaMode }"
            :aria-pressed="arenaMode"
            @click="toggleArenaMode"
          >
            <span>
              竞技场
              <span
                class="setting-info-dot"
                title="查看说明"
                @click.stop="settingTooltip = settingTooltip === 'arena' ? null : 'arena'"
              >ⓘ</span>
            </span>
            <i />
          </button>

          <Transition name="tooltip-fade">
            <div v-if="settingTooltip" class="setting-tooltip">
              <p>{{ settingInfoMap[settingTooltip] }}</p>
              <button class="tooltip-close" @click.stop="settingTooltip = null">知道了</button>
            </div>
          </Transition>
        </div>
      </section>

      <section class="run-card">
        <div class="panel-title">
          <SvgIcon name="sparkles" :size="15" />
          <span>Agent工具管理</span>
        </div>
        <div class="asset-summary">
          <button type="button" class="asset-item" @click="openToolDrawer('skills')">
            <span>技能管理</span>
            <strong>{{ skillsSummary.enabled }}/{{ skillsSummary.total }}</strong>
          </button>
          <button type="button" class="asset-item" @click="openToolDrawer('memory')">
            <span>记忆管理</span>
            <strong>{{ memoriesSummary.enabled }}/{{ memoriesSummary.total }}</strong>
          </button>
        </div>
        <div class="run-row">
          <span>当前对话</span>
          <strong>{{ activeConversation?.messages?.length || 0 }} 条</strong>
        </div>
      </section>

      <section class="run-card context-card">
        <div class="panel-title">
          <SvgIcon name="brain" :size="15" />
          <span>上下文窗口</span>
          <strong :class="['context-badge', contextHealth.level]">{{ contextHealth.label }}</strong>
        </div>
        <div class="context-number">
          <span>{{ contextPercent }}%</span>
          <small>{{ contextHealth.message }}</small>
        </div>
        <div class="context-meter">
          <span :style="{ width: `${contextPercent}%` }" />
        </div>
        <div class="token-grid">
          <div v-for="item in contextStats" :key="item.label" class="token-cell">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <button class="inspector-action" :disabled="loading || compressing" @click="triggerForceCompress">
          <SvgIcon :name="compressing ? 'loader' : 'refresh'" :size="14" :spin="compressing" />
          {{ compressing ? '压缩中...' : '压缩历史' }}
        </button>
      </section>
    </aside>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSSE } from '../composables/useSSE'
import { useConversation } from '../composables/useConversation'
import { useAgentTools } from '../composables/useAgentTools'
import SvgIcon from '../components/SvgIcon.vue'
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
const initialQuery = computed(() => String(route.query.q || '').trim())

const {
  conversations, activeId, activeConversation,
  newConversation, selectConversation, deleteConversation,
  addMessage, setResult, persist, loadFromBackend, syncActiveToBackend,
} = useConversation()

const { streamPost } = useSSE()
const {
  webSearchEnabled,
  knowledgeSearchEnabled,
  toggleWebSearch,
  toggleKnowledgeSearch,
  openToolDrawer,
  skillsSummary,
  memoriesSummary,
} = useAgentTools()

const sidebarCollapsed = ref(false)
const messagesRef = ref(null)
const selectedMode = ref('agent')
const selectedModel = ref('deepseek-v4-pro')
const arenaMode = ref(false)
const navigatingToWorkbench = ref(false)

// Per-conversation stream state: convId -> { loading, controller, pendingAskUser, activeSuggestions }
const streamStates = reactive(new Map())

function getStreamState(convId) {
  if (!streamStates.has(convId)) {
    streamStates.set(convId, {
      loading: false,
      controller: null,
      pendingAskUser: null,
      activeSuggestions: [],
    })
  }
  return streamStates.get(convId)
}

// Template-facing computed refs (backward-compatible with existing template bindings)
const currentStreamState = computed(() => {
  const convId = activeConversation.value?.id
  if (!convId) return { loading: false, controller: null, pendingAskUser: null, activeSuggestions: [] }
  return getStreamState(convId)
})
const loading = computed(() => currentStreamState.value.loading)
const activeController = computed(() => currentStreamState.value.controller)
const pendingAskUser = computed({
  get: () => currentStreamState.value.pendingAskUser,
  set: (v) => { currentStreamState.value.pendingAskUser = v },
})
const activeSuggestions = computed({
  get: () => currentStreamState.value.activeSuggestions,
  set: (v) => { currentStreamState.value.activeSuggestions = v },
})
const workbenchParsing = ref(false)
const workbenchPlanId = ref(null)
const workbenchError = ref('')
const syncingKnowledgeTurns = ref(new Set())

const COMPRESS_KEEP_LAST = 6
const inspectorOpen = ref(false)
const inspectorCollapsed = ref(false)
const activeTurnIndex = ref(0)
const manualTraceOpen = ref({})
const turnMarkerPositions = ref({})

// Token / compress state — per-conversation tokenStatus stored in conversation object
const tokenStatus = computed({
  get: () => activeConversation.value?.tokenStatus || null,
  set: (val) => {
    if (activeConversation.value) {
      activeConversation.value.tokenStatus = val
    }
  },
})
const forceCompress = ref(false)
const compressing = ref(false)
const compressNotice = ref('')
let compressNoticeTimer = null

const contextHealth = computed(() => {
  const ratio = tokenStatus.value?.utilization || 0
  if (ratio >= 0.85) return { level: 'danger', label: '接近上限', message: '建议先压缩历史再继续规划。' }
  if (ratio >= 0.65) return { level: 'warning', label: '偏高', message: '上下文逐渐变长，可按需压缩。' }
  return { level: 'safe', label: '健康', message: '当前上下文状态良好。' }
})

const compressibleMessages = computed(() => {
  const msgs = activeConversation.value?.messages || []
  return msgs.filter(m => m.role === 'user' || m.role === 'assistant')
})

const canCompress = computed(() => {
  return compressibleMessages.value.length > COMPRESS_KEEP_LAST && !loading.value && !compressing.value
})

const compressHint = computed(() => {
  const count = compressibleMessages.value.length
  if (count <= COMPRESS_KEEP_LAST) {
    return `历史消息不足，至少需要 ${COMPRESS_KEEP_LAST + 1} 条对话才能压缩`
  }
  if (!canCompress.value) {
    if (loading.value) return '当前有请求在进行，等结束后再压缩'
  }
  const ratio = tokenStatus.value?.utilization || 0
  if (ratio >= 0.85) return '上下文接近上限，建议压缩'
  if (ratio >= 0.65) return '上下文较高，可压缩释放空间'
  return ''
})

const modeLabelMap = {
  agent: 'Agent',
  plan: 'Plan',
  reflection: 'Reflection',
}

const modelLabelMap = {
  'qwen3.7-plus': 'Qwen 3.7 Plus',
  'deepseek-v4-flash': 'DeepSeek V4 Flash',
  'deepseek-v4-pro': 'DeepSeek V4 Pro',
  'kimi-k2.6': 'Kimi K2.6',
  'MiniMax-M2.5': 'MiniMax M2.5',
  'glm-5.1': 'GLM 5.1',
}

const selectedModeLabel = computed(() => modeLabelMap[selectedMode.value] || selectedMode.value)
const selectedModelLabel = computed(() => modelLabelMap[selectedModel.value] || selectedModel.value)

const settingInfoMap = {
  webSearch: '开启后，Agent 可调用 Google 搜索引擎获取实时旅游信息，包括景点开放时间、最新评价、交通状况等。适合需要最新资讯的旅行规划。',
  knowledgeSearch: '开启后，Agent 可从知识库中检索相关旅行攻略和用户经验，增强规划的准确性和个性化程度。',
  arena: '开启后，可同时对比多个 AI 模型的规划结果，通过投票选出最优方案。适合需要横向评估模型表现的场景。',
}

const settingTooltip = ref(null)

function onDocClick(e) {
  if (!e.target.closest('.setting-info-dot') && !e.target.closest('.setting-tooltip')) {
    settingTooltip.value = null
  }
}

watch(settingTooltip, (val) => {
  if (val) nextTick(() => document.addEventListener('click', onDocClick))
  else document.removeEventListener('click', onDocClick)
})

onUnmounted(() => document.removeEventListener('click', onDocClick))

const contextPercent = computed(() => Math.round((tokenStatus.value?.utilization || 0) * 100))
const contextStats = computed(() => [
  { label: '历史对话', value: formatToken(tokenStatus.value?.history_tokens) },
  { label: '输入估算', value: formatToken(tokenStatus.value?.input_tokens) },
  { label: '输出预算', value: formatToken(tokenStatus.value?.output_budget) },
  { label: '最大窗口', value: formatToken(tokenStatus.value?.max_context_tokens) },
])
const runStatusLabel = computed(() => {
  if (loading.value) return '运行中'
  if (pendingAskUser.value) return '等待确认'
  if (navigatingToWorkbench.value) return '同步中'
  if (activeConversation.value?.result) return '已生成'
  return '空闲'
})
const runStatusClass = computed(() => {
  if (loading.value) return 'running'
  if (pendingAskUser.value) return 'waiting'
  if (activeConversation.value?.result) return 'done'
  return 'idle'
})
const conversationTurns = computed(() => {
  const messages = activeConversation.value?.messages || []
  const turns = []
  let current = null

  messages.forEach((message, index) => {
    if (message.role === 'user') {
      current = {
        key: `u-${index}`,
        user: message,
        userIndex: index,
        assistant: null,
        assistantIndex: -1,
      }
      turns.push(current)
      return
    }

    if (!current || current.assistant) {
      current = {
        key: `a-${index}`,
        user: null,
        userIndex: -1,
        assistant: message,
        assistantIndex: index,
      }
      turns.push(current)
      return
    }

    current.assistant = message
    current.assistantIndex = index
  })

  return turns
})
const turnMarkers = computed(() => {
  const messages = activeConversation.value?.messages || []
  const total = Math.max(1, messages.length - 1)
  return messages.map((message, index) => ({
    index,
    role: message.role,
    label: message.role === 'user' ? `Jump to question ${index + 1}` : `Jump to answer ${index + 1}`,
    top: turnMarkerPositions.value[index] || `${Math.max(3, Math.min(97, (index / total) * 100))}%`,
  }))
})

function isMessageStreaming(index) {
  return loading.value && index === (activeConversation.value?.messages?.length || 0) - 1
}

function isTraceManuallyOpen(index) {
  return Boolean(manualTraceOpen.value[index])
}

function isTraceOpen(index) {
  return isMessageStreaming(index) || isTraceManuallyOpen(index)
}

function toggleTrace(index) {
  manualTraceOpen.value = {
    ...manualTraceOpen.value,
    [index]: !manualTraceOpen.value[index],
  }
}

function traceSummary(events = []) {
  const counts = events.reduce((acc, ev) => {
    acc[ev.type] = (acc[ev.type] || 0) + 1
    return acc
  }, {})
  const parts = []
  if (counts.thought) parts.push(`${counts.thought} thoughts`)
  if (counts.action) parts.push(`${counts.action} tools`)
  if (counts.observation) parts.push(`${counts.observation} observations`)
  if (counts.reflection) parts.push(`${counts.reflection} reflections`)
  return parts.length ? parts.join(' · ') : `${events.length} events`
}

function getLatestEvent(events = []) {
  if (!events.length) return null
  // 从后往前找最新的事件
  return events[events.length - 1]
}

function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
      updateTurnMarkerPositions()
      handleMessagesScroll()
    }
  })
}

function updateTurnMarkerPositions() {
  const el = messagesRef.value
  if (!el) return
  const turns = Array.from(el.querySelectorAll('[data-message-index]'))
  const maxScroll = el.scrollHeight - el.clientHeight
  const total = Math.max(1, turns.length - 1)
  const next = {}
  turns.forEach((turn, order) => {
    const index = Number(turn.dataset.messageIndex || 0)
    const percent = maxScroll > 1 ? (turn.offsetTop / maxScroll) * 100 : (order / total) * 100
    next[index] = `${Math.max(3, Math.min(97, percent))}%`
  })
  turnMarkerPositions.value = next
}

function handleMessagesScroll() {
  const el = messagesRef.value
  if (!el) return

  const turns = Array.from(el.querySelectorAll('[data-message-index]'))
  if (!turns.length) {
    activeTurnIndex.value = 0
    return
  }

  const anchor = el.scrollTop + el.clientHeight * 0.34
  let nextIndex = Number(turns[0].dataset.messageIndex || 0)
  for (const turn of turns) {
    const top = turn.offsetTop
    if (top <= anchor) nextIndex = Number(turn.dataset.messageIndex || nextIndex)
    else break
  }
  activeTurnIndex.value = nextIndex
}

function scrollToTurn(index) {
  nextTick(() => {
    const el = messagesRef.value
    const target = el?.querySelector(`[data-message-index="${index}"]`)
    if (!el || !target) return
    el.scrollTo({
      top: Math.max(0, target.offsetTop - 24),
      behavior: 'smooth',
    })
    activeTurnIndex.value = index
  })
}

function syncJumpMarkers() {
  nextTick(() => {
    updateTurnMarkerPositions()
    handleMessagesScroll()
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

watch(() => activeConversation.value?.messages?.length, () => {
  scrollToBottom()
  syncJumpMarkers()
})

watch(activeId, () => {
  manualTraceOpen.value = {}
  turnMarkerPositions.value = {}
  syncJumpMarkers()
  // Reset workbench parsing state on conversation switch
  workbenchParsing.value = false
  workbenchError.value = ''
  // Restore workbenchPlanId from conversation result if available
  const savedPlanId = activeConversation.value?.result?.workbenchPlanId
  workbenchPlanId.value = savedPlanId || null
})

onMounted(() => {
  if (route.query.planId) loadSavedPlan(route.query.planId)
  loadFromBackend().then(() => {
    // Restore workbenchPlanId from conversation result
    const savedPlanId = activeConversation.value?.result?.workbenchPlanId
    if (savedPlanId) workbenchPlanId.value = savedPlanId
    scrollToBottom()
    if (route.query.auto === '1' && initialQuery.value) {
      if (!activeConversation.value || activeConversation.value.messages.length) {
        newConversation()
      }
      nextTick(() => {
        handleSend({ query: initialQuery.value, file: null })
      })
    }
  })
})

function handleNewConversation() {
  newConversation()
  sidebarCollapsed.value = false
}

function toggleArenaMode() {
  arenaMode.value = !arenaMode.value
}

function toggleInspector() {
  inspectorOpen.value = !inspectorOpen.value
}

function toggleInspectorCollapse() {
  inspectorCollapsed.value = !inspectorCollapsed.value
}

function startStream(query, mode = selectedMode.value, generatePlanFirst = null, file = null) {
  if (generatePlanFirst === null) {
    generatePlanFirst = false
  }

  // Snapshot target conversation at stream creation time
  const targetConv = activeConversation.value
  if (!targetConv) return
  const targetConvId = targetConv.id

  // Add messages directly to the target conversation (not via addMessage which writes to activeConversation)
  targetConv.messages.push({ role: 'user', content: query })
  targetConv.messages.push({ role: 'assistant', content: '', events: [], planContent: '' })
  if (targetConv.messages.length === 2 && targetConv.messages[0].role === 'user') {
    targetConv.title = query.slice(0, 40) || '新对话'
  }
  targetConv.updatedAt = Date.now()
  persist()

  // Per-conversation stream state
  const state = getStreamState(targetConvId)
  state.loading = true
  state.pendingAskUser = null
  state.activeSuggestions = []
  scrollToBottom()

  const formData = new FormData()
  formData.append('query', query)
  formData.append('userId', localStorage.getItem('userId') || '1')
  formData.append('mode', mode)
  formData.append('generatePlanFirst', String(generatePlanFirst))
  formData.append('model', selectedModel.value)
  formData.append('webSearchEnabled', String(webSearchEnabled.value))
  formData.append('knowledgeSearchEnabled', String(knowledgeSearchEnabled.value))
  const historyRaw = targetConv.messages.slice(0, -2).filter(m => m.role === 'user' || m.role === 'assistant')
  const relevantHistory = targetConv.result ? [] : historyRaw.slice(-10)
  const historyToSent = relevantHistory.map(m => ({ role: m.role, content: m.content || m.answer || '' }))
  formData.append('chatHistoryJson', JSON.stringify(historyToSent))

  if (forceCompress.value) { formData.append('forceCompress', 'true'); forceCompress.value = false }
  if (file) formData.append('file', file)

  // Snapshot the target message reference (not a closure over activeConversation)
  const targetMsg = targetConv.messages.at(-1)

  const controller = streamPost(
    '/api/assistant/chat/stream',
    formData,
    (event) => {
      if (!targetMsg) return
      if (event.type === 'token_status') {
        tokenStatus.value = {
          ...(tokenStatus.value || {}),
          ...(event.metadata || {}),
        }
        targetConv.messages = [...targetConv.messages]
        return
      }
      if (event.type === 'answer') {
        targetMsg.answer = (targetMsg.answer || '') + event.content
      } else if (event.type === 'plan') {
        if (!targetMsg.planContent) targetMsg.planContent = ''
        targetMsg.planContent += event.content
      } else if (event.type === 'done') {
        return
      } else if (event.type === 'ask_user') {
        state.pendingAskUser = {
          message: event.content,
          questions: event.metadata?.questions || [],
        }
        targetMsg.events.push({ type: 'ask_user', content: event.content, metadata: event.metadata })
      } else if (event.type === 'suggestions') {
        state.activeSuggestions = event.metadata?.questions || []
      } else if (event.type === 'action' && event.metadata?.tool === 'finish') {
        targetMsg._planFinished = true
        targetMsg.events.push({ type: event.type, content: event.content, metadata: event.metadata })
      } else if (['thought', 'action', 'observation', 'reflection'].includes(event.type)) {
        targetMsg.events.push({ type: event.type, content: event.content, metadata: event.metadata })
      } else if (event.type === 'error') {
        targetMsg.events.push({ type: 'observation', content: `Error: ${event.content}`, metadata: {} })
      }
      scrollToBottom()
      targetConv.messages = [...targetConv.messages]
    },
    () => finishStreamFor(targetConvId),
    (err) => {
      state.loading = false
      state.controller = null
      streamStates.delete(targetConvId)
      console.error('SSE error:', err)
      // Add error message directly to the target conversation
      targetConv.messages.push({ role: 'assistant', content: `请求失败: ${err.message}`, events: [] })
      persist()
    },
  )

  state.controller = controller
}

async function finishStreamFor(targetConvId) {
  const state = streamStates.get(targetConvId)
  if (state) {
    state.controller = null
  }

  // Find the target conversation from the conversations list
  const conv = conversations.value.find(c => c.id === targetConvId)
  if (!conv) {
    if (state) state.loading = false
    streamStates.delete(targetConvId)
    return
  }

  const msg = conv.messages.at(-1)
  if (msg?.answer) {
    try {
      const parsed = JSON.parse(msg.answer)
      if (parsed.destination || parsed.markdown) {
        conv.result = parsed
        conv.updatedAt = Date.now()
      }
    } catch {
      if (msg._planFinished) {
        conv.result = { markdown: msg.answer, source: 'markdown' }
        conv.updatedAt = Date.now()
      }
    }
  }
  // Sync the target conversation (may not be the active one)
  // Use syncActiveToBackend(conv) + saveConversations instead of persist()
  // to avoid syncing the wrong (active) conversation
  await syncActiveToBackend(conv)
  // persist() would also call syncActiveToBackend() for the active conversation,
  // so just save to localStorage directly
  try {
    localStorage.setItem('travel_conversations', JSON.stringify(conversations.value))
  } catch {}
  if (state) {
    state.loading = false
  }
  if (state?.pendingAskUser || state?.activeSuggestions?.length) {
    return
  }
  streamStates.delete(targetConvId)
}

// Legacy wrapper for template / other callers that don't have a convId
async function finishStream() {
  const convId = activeConversation.value?.id
  if (convId) await finishStreamFor(convId)
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
  // Snapshot target conversation
  const targetConv = activeConversation.value
  if (!targetConv) return
  const targetConvId = targetConv.id

  targetConv.messages.push({ role: 'user', content: query })
  targetConv.messages.push({
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
  if (targetConv.messages.length === 2 && targetConv.messages[0].role === 'user') {
    targetConv.title = query.slice(0, 40) || '新对话'
  }
  targetConv.updatedAt = Date.now()
  persist()

  const state = getStreamState(targetConvId)
  state.loading = true
  scrollToBottom()

  const formData = new FormData()
  formData.append('query', query)
  formData.append('userId', localStorage.getItem('userId') || '1')
  formData.append('webSearchEnabled', String(webSearchEnabled.value))
  formData.append('knowledgeSearchEnabled', String(knowledgeSearchEnabled.value))
  const historyRaw = targetConv.messages.slice(0, -2).filter(m => m.role === 'user' || m.role === 'assistant')
  const relevantHistory = targetConv.result ? [] : historyRaw.slice(-10)
  const historyToSent = relevantHistory.map(m => ({ role: m.role, content: m.content || m.answer || '' }))
  formData.append('chatHistoryJson', JSON.stringify(historyToSent))

  if (forceCompress.value) { formData.append('forceCompress', 'true'); forceCompress.value = false }
  if (file) formData.append('file', file)

  // Snapshot the arena message reference
  const arenaMsg = targetConv.messages.at(-1)
  if (arenaMsg?.arena) {
    arenaMsg.arena.stages = createInitialArenaStages(query)
    targetConv.messages = [...targetConv.messages]
  }

  const controller = streamPost(
    '/api/arena/auto/stream',
    formData,
    (event) => {
      handleArenaStreamEvent(arenaMsg, event)
    },
    () => {
      if (arenaMsg?.arena?.loading) {
        arenaMsg.arena.loading = false
        updateArenaStage(arenaMsg, 'merge', {
          status: 'done',
          detail: '流式连接结束，已停止接收新事件。',
        })
        targetConv.messages = [...targetConv.messages]
      }
      state.loading = false
      state.controller = null
      streamStates.delete(targetConvId)
      persist()
    },
    (err) => {
      if (arenaMsg?.arena) {
        arenaMsg.arena.loading = false
        arenaMsg.content = `Auto对比失败: ${err.message}`
        updateArenaStage(arenaMsg, 'reasoning', {
          status: 'error',
          time: timeTag(),
          detail: `流式事件处理失败：${err.message}`,
        })
        updateArenaStage(arenaMsg, 'merge', {
          status: 'error',
          time: timeTag(),
          detail: '对比流程中断。',
        })
        targetConv.messages = [...targetConv.messages]
      }
      state.loading = false
      state.controller = null
      streamStates.delete(targetConvId)
      persist()
    },
  )

  state.controller = controller
}

function stopActiveRequest() {
  const convId = activeConversation.value?.id
  if (!convId) return
  const state = streamStates.get(convId)
  if (!state?.loading || !state?.controller) return

  state.controller.abort()
  state.controller = null
  state.loading = false

  const conv = conversations.value.find(c => c.id === convId)
  if (!conv) {
    streamStates.delete(convId)
    return
  }
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
  streamStates.delete(convId)
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

  // If already parsed, go directly
  if (workbenchPlanId.value) {
    router.push({ name: 'planWorkbench', query: { planId: workbenchPlanId.value, c: conv.backendId } })
    return
  }

  if (!conv.backendId) {
    alert('会话尚未同步，请稍候重试...')
    return
  }

  // Start background parsing
  workbenchParsing.value = true
  workbenchError.value = ''

  try {
    const res = await fetch('/api/travel/plan/parse-and-save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ conversationId: conv.backendId })
    })
    const data = await res.json()

    if (data.code === 200 && data.data) {
      workbenchPlanId.value = data.data.planId
      // Persist planId in conversation result so it survives component re-creation
      const result = conv.result
      if (result) {
        setResult({ ...result, workbenchPlanId: data.data.planId })
      }
    } else {
      workbenchError.value = data.message || '暂不支持解析该对话'
    }
  } catch (e) {
    workbenchError.value = '解析失败：' + e.message
  } finally {
    workbenchParsing.value = false
  }
}

function enterWorkbench() {
  if (workbenchPlanId.value) {
    const conv = activeConversation.value
    router.push({ name: 'planWorkbench', query: { planId: workbenchPlanId.value, ...(conv?.backendId ? { c: conv.backendId } : {}) } })
  }
}

function retryWorkbench() {
  workbenchPlanId.value = null
  workbenchError.value = ''
  goToWorkbench()
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
  background: var(--color-page, #fafafa);
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
  background:
    linear-gradient(180deg, rgba(255, 241, 243, 0.5), transparent 180px),
    var(--color-page, #fafafa);
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 46px 20px;
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
  font-size: 28px;
  font-weight: 950;
  color: var(--color-title);
  margin: 0 0 6px;
  letter-spacing: 0;
}

.brand-greeting p {
  font-size: 14px;
  color: var(--color-secondary);
  margin: 0;
}

/* Conversation view */
.conversation-view {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 26px 34px 28px 28px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  scroll-behavior: smooth;
}

.message-turn {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 1px;
  gap: 10px;
  scroll-margin-top: 24px;
}

.conversation-turn {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 18px;
}

.turn-user {
  align-items: flex-end;
}

.turn-assistant {
  align-items: stretch;
}

.turn-user :deep(.message-row.user),
.turn-assistant :deep(.message-row.assistant) {
  margin-bottom: 0;
}

.scroll-jump-layer {
  position: absolute;
  top: 18px;
  right: 5px;
  bottom: 126px;
  z-index: 8;
  width: 16px;
  pointer-events: none;
}

.scroll-jump-layer::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 7px;
  width: 2px;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.07);
}

.scroll-jump-dot {
  position: relative;
  position: absolute;
  left: 4px;
  width: 8px;
  height: 8px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.18);
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.86);
  pointer-events: auto;
  transform: translateY(-50%);
  transition: width 0.16s ease, left 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
}

.scroll-jump-dot.user {
  background: rgba(255, 36, 66, 0.34);
}

.scroll-jump-dot:hover,
.scroll-jump-dot.active {
  left: 1px;
  width: 14px;
  background: var(--color-red);
  box-shadow: 0 0 0 4px rgba(255, 36, 66, 0.12);
}

.scroll-jump-dot::before {
  content: attr(aria-label);
  position: absolute;
  right: calc(100% + 12px);
  top: 50%;
  width: max-content;
  max-width: 220px;
  padding: 6px 9px;
  border-radius: 8px;
  background: rgba(17, 24, 39, 0.92);
  color: #ffffff;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.3;
  opacity: 0;
  pointer-events: none;
  transform: translateY(-50%) translateX(4px);
  transition: opacity 0.14s ease, transform 0.14s ease;
}

.scroll-jump-dot:hover::before {
  opacity: 1;
  transform: translateY(-50%) translateX(0);
}

/* 实时事件展示（流式输出时） */
.live-event-block {
  width: 100%;
  margin-bottom: 8px;
  animation: event-fade-in 0.3s ease;
}

@keyframes event-fade-in {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.trace-panel {
  position: relative;
  z-index: 0;
  width: 100%;
  flex-shrink: 0;
  margin: 0;
  overflow: hidden;
  border: 1px solid rgba(17, 24, 39, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 8px 22px rgba(17, 24, 39, 0.045);
}

.trace-header {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 46px;
  padding: 0 14px;
  border: 0;
  background: #ffffff;
  color: var(--color-title);
  text-align: left;
}

.trace-spark {
  display: inline-flex;
  color: var(--color-red);
}

.trace-title {
  font-size: 13px;
  font-weight: 950;
}

.trace-meta {
  overflow: hidden;
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-chevron {
  display: inline-flex;
  color: var(--color-muted);
  transition: transform 0.16s ease;
}

.trace-panel.open .trace-chevron {
  transform: rotate(180deg);
}

.trace-panel.streaming .trace-header {
  background: linear-gradient(90deg, #fff7f8, #ffffff);
}

.trace-body {
  display: grid;
  gap: 6px;
  padding: 8px;
  border-top: 1px solid rgba(17, 24, 39, 0.07);
  background: #fbfbfc;
}

.agent-content-wrapper {
  width: 100%;
  max-width: min(900px, calc(100% - 64px));
  min-width: 0;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.agent-content-wrapper.arena {
  max-width: min(1160px, calc(100% - 32px));
}

.compact-input-area {
  padding: 12px 28px 18px;
  border-top: 1px solid transparent;
  background: transparent;
}

.compress-notice { margin-top: 8px; font-size: 12px; color: var(--color-muted); line-height: 1.4; }

.message-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

.sync-knowledge-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-secondary);
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.18s ease;
  font-family: var(--font-family);
}

.sync-knowledge-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.sync-knowledge-btn:hover:not(:disabled) {
  border-color: var(--color-red);
  color: var(--color-red);
  background: #fff7f8;
}

:root[data-theme="dark"] .sync-knowledge-btn {
  background: var(--color-card);
  border-color: var(--color-border);
  color: var(--color-secondary);
}

:root[data-theme="dark"] .sync-knowledge-btn:hover:not(:disabled) {
  border-color: var(--color-red-light);
  color: var(--color-red-light);
  background: var(--color-soft-red);
}

.workbench-trigger-wrapper {
  display: flex;
  justify-content: center;
  margin: 16px 0;
  animation: slideUp 0.4s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.workbench-trigger-btn {
  background: var(--gradient-brand);
  color: white;
  border: none;
  padding: 12px 28px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 50px;
  cursor: pointer;
  box-shadow: 0 14px 30px rgba(255, 36, 66, 0.24);
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.workbench-trigger-btn:hover {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 18px 36px rgba(255, 36, 66, 0.3);
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

.workbench-parsing-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 24px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 50px;
  animation: slideUp 0.4s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.parsing-text {
  font-size: 13px;
  color: var(--color-secondary);
  font-weight: 500;
}

.workbench-trigger-btn.ready {
  background: linear-gradient(135deg, #10b981, #059669);
  box-shadow: 0 14px 30px rgba(16, 185, 129, 0.24);
}

.workbench-trigger-btn.ready:hover {
  box-shadow: 0 18px 36px rgba(16, 185, 129, 0.3);
}

.workbench-error {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 50px;
  animation: slideUp 0.4s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.error-text {
  font-size: 13px;
  color: var(--color-hint);
}

.retry-btn {
  padding: 6px 16px;
  font-size: 12px;
  font-weight: 600;
  border: none;
  border-radius: 20px;
  background: var(--color-red-light);
  color: white;
  cursor: pointer;
  transition: all 0.2s;
}

.retry-btn:hover {
  background: var(--color-red);
}

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

.plan-inspector {
  position: relative;
  display: flex;
  width: 318px;
  height: 100%;
  flex-shrink: 0;
  flex-direction: column;
  gap: 12px;
  padding: 18px 16px;
  border-left: 1px solid rgba(17, 24, 39, 0.08);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(255, 247, 248, 0.96));
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.25s ease, padding 0.25s ease;
}

/* ── 折叠状态 ────────────── */
.plan-inspector.collapsed {
  width: 48px;
  padding: 18px 8px;
  overflow: hidden;
}

.plan-inspector.collapsed > :not(.inspector-edge-toggle) {
  display: none;
}

/* ── 左侧边缘折叠按钮 ───── */
.inspector-edge-toggle {
  position: absolute;
  left: 10px;
  top: 50%;
  z-index: 10;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: 5px;
  background: var(--color-card);
  color: var(--color-secondary);
  cursor: pointer;
  transform: translateY(-50%);
  transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease;
}

.inspector-edge-toggle:hover {
  background: var(--color-soft-red);
  border-color: rgba(255, 36, 66, 0.22);
  color: var(--color-red);
}

.inspector-edge-toggle :deep(svg) {
  width: 12px;
  height: 12px;
}

.run-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid rgba(17, 24, 39, 0.08);
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 10px 28px rgba(17, 24, 39, 0.045);
}

.hero-card {
  background: linear-gradient(180deg, #fff7f8, #ffffff);
}

.run-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.run-card-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.inspector-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 28px;
  padding: 0 9px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-secondary);
  font-size: 11px;
  font-weight: 950;
}

.run-kicker {
  margin-bottom: 6px;
  color: var(--color-red);
  font-size: 11px;
  font-weight: 950;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.run-card h2 {
  margin: 0;
  color: var(--color-title);
  font-size: 22px;
  line-height: 1.1;
}

.run-card p {
  margin: 0;
  color: var(--color-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.run-status {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 950;
  white-space: nowrap;
}

.run-status.idle {
  background: #f3f4f6;
  color: #6b7280;
}

.run-status.running {
  background: #fff1f3;
  color: var(--color-red);
}

.run-status.waiting {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}

.run-status.done {
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
  color: var(--color-title);
  font-size: 11px;
  font-weight: 950;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.panel-title strong {
  margin-left: auto;
}

.run-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--color-secondary);
  font-size: 13px;
}

.run-row strong {
  color: var(--color-title);
  font-size: 13px;
  font-weight: 950;
  text-align: right;
}

.run-row strong.active {
  color: var(--color-red);
}

.setting-switch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  position: relative;
}

.setting-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-width: 0;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: #ffffff;
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 900;
}

.setting-toggle span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  white-space: nowrap;
}

.setting-toggle i {
  position: relative;
  width: 30px;
  height: 18px;
  border-radius: 999px;
  background: #e5e7eb;
  flex-shrink: 0;
  transition: background 0.15s ease;
}

.setting-toggle i::before {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(17, 24, 39, 0.14);
  transition: transform 0.15s ease;
}

.setting-toggle.active {
  border-color: rgba(255, 36, 66, 0.24);
  background: #fff1f3;
  color: var(--color-red);
}

.setting-toggle.active i {
  background: var(--color-red);
}

.setting-toggle.active i::before {
  transform: translateX(12px);
}

.panel-hint {
  margin: 0;
  color: var(--color-hint);
  font-size: 12px;
  line-height: 1.6;
}

/* ── Setting info dot & tooltip ── */
.setting-info-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  margin-left: 2px;
  margin-top: -5px;
  border-radius: 50%;
  background: transparent;
  color: var(--color-hint);
  font-size: 10px;
  font-weight: 800;
  line-height: 1;
  vertical-align: text-top;
  cursor: pointer;
  transition: all 0.15s ease;
}

.setting-info-dot:hover {
  background: var(--color-surface);
  color: var(--color-title);
}

.setting-tooltip {
  position: absolute;
  left: 0;
  right: 0;
  top: 100%;
  margin-top: 8px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-card);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.setting-tooltip p {
  margin: 0 0 12px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--color-body);
}

.tooltip-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 700;
  font-family: var(--font-family);
  cursor: pointer;
  transition: all 0.15s ease;
}

.tooltip-close:hover {
  border-color: var(--color-red);
  color: var(--color-red);
  background: var(--color-soft-red);
}

/* tooltip transition */
.tooltip-fade-enter-active,
.tooltip-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.tooltip-fade-enter-from,
.tooltip-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* Dark mode */
:root[data-theme="dark"] .setting-info-dot {
  color: var(--color-hint);
}
:root[data-theme="dark"] .setting-info-dot:hover {
  background: var(--color-card-hover);
  color: var(--color-title);
}
:root[data-theme="dark"] .setting-tooltip {
  background: var(--color-card);
  border-color: var(--color-border);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
}
:root[data-theme="dark"] .setting-tooltip p {
  color: var(--color-body);
}
:root[data-theme="dark"] .tooltip-close {
  background: var(--color-card-hover);
  border-color: var(--color-border);
  color: var(--color-secondary);
}
:root[data-theme="dark"] .tooltip-close:hover {
  border-color: var(--color-red-light);
  color: var(--color-red-light);
  background: var(--color-soft-red);
}

.context-card {
  display: grid;
  gap: 12px;
}

.context-number {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
}

.context-number span {
  color: var(--color-title);
  font-size: 28px;
  font-weight: 950;
  line-height: 1;
}

.context-number small {
  color: var(--color-secondary);
  font-size: 12px;
  line-height: 1.5;
  text-align: right;
}

.context-badge {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #fff1f3;
  color: var(--color-red);
  font-size: 11px;
  font-weight: 950;
}

.context-badge.safe {
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
}

.context-badge.warning {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}

.context-badge.danger {
  background: rgba(255, 36, 66, 0.12);
  color: var(--color-red);
}

.context-meter {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #fff1f3;
}

.context-meter span {
  display: block;
  width: 0;
  height: 100%;
  border-radius: inherit;
  background: var(--gradient-brand);
  transition: width 0.18s ease;
}

.token-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.token-cell {
  display: grid;
  gap: 4px;
  padding: 10px 11px;
  border: 1px solid rgba(17, 24, 39, 0.08);
  border-radius: 12px;
  background: #ffffff;
}

.token-cell span {
  color: var(--color-hint);
  font-size: 11px;
  font-weight: 800;
}

.token-cell strong {
  color: var(--color-title);
  font-size: 13px;
  font-weight: 950;
}

.inspector-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  width: 100%;
  border: none;
  border-radius: 999px;
  background: var(--gradient-brand);
  color: #ffffff;
  font-size: 12px;
  font-weight: 950;
  box-shadow: 0 10px 24px rgba(255, 36, 66, 0.2);
}

.inspector-action:hover:not(:disabled) {
  filter: brightness(1.03);
  transform: translateY(-1px);
}

.inspector-action:disabled {
  cursor: not-allowed;
  opacity: 0.56;
  box-shadow: none;
}

.asset-summary {
  display: grid;
  gap: 8px;
}

.asset-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 40px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: #ffffff;
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 900;
}

.asset-item strong {
  color: var(--color-title);
  font-size: 12px;
}

:root[data-theme="dark"] .ai-plan-page {
  background: var(--color-page);
  color: var(--color-body);
}

:root[data-theme="dark"] .center-panel {
  background: linear-gradient(180deg, rgba(255, 36, 66, 0.06), transparent 180px), var(--color-page);
}

:root[data-theme="dark"] .compact-input-area {
  border-top-color: transparent;
}

:root[data-theme="dark"] .scroll-jump-layer::before {
  background: rgba(255, 255, 255, 0.12);
}

:root[data-theme="dark"] .scroll-jump-dot {
  background: rgba(255, 255, 255, 0.26);
  box-shadow: 0 0 0 3px rgba(16, 16, 18, 0.9);
}

:root[data-theme="dark"] .scroll-jump-dot.user {
  background: rgba(255, 36, 66, 0.5);
}

:root[data-theme="dark"] .trace-panel {
  background: var(--color-card);
  border-color: var(--color-border);
}

:root[data-theme="dark"] .trace-header {
  background: var(--color-card);
}

:root[data-theme="dark"] .trace-panel.streaming .trace-header {
  background: linear-gradient(90deg, rgba(255, 36, 66, 0.12), var(--color-card));
}

:root[data-theme="dark"] .trace-body {
  border-top-color: var(--color-border);
  background: rgba(255, 255, 255, 0.03);
}

:root[data-theme="dark"] .plan-inspector {
  border-left-color: rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, rgba(18, 18, 20, 0.98), rgba(14, 14, 16, 0.98));
}

:root[data-theme="dark"] .inspector-edge-toggle {
  background: var(--color-card);
  border-color: var(--color-border);
  color: var(--color-secondary);
}

:root[data-theme="dark"] .inspector-edge-toggle:hover {
  background: var(--color-soft-red);
  border-color: rgba(255, 36, 66, 0.24);
  color: var(--color-red-light);
}

:root[data-theme="dark"] .run-card,
:root[data-theme="dark"] .tool-card,
:root[data-theme="dark"] .editor-card,
:root[data-theme="dark"] .token-cell,
:root[data-theme="dark"] .asset-item {
  background: var(--color-card);
  border-color: var(--color-border);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.3);
}

:root[data-theme="dark"] .hero-card {
  background: linear-gradient(180deg, rgba(255, 36, 66, 0.12), rgba(26, 26, 26, 0.98));
}

:root[data-theme="dark"] .setting-toggle {
  background: var(--color-card);
  border-color: var(--color-border);
  color: var(--color-secondary);
}

:root[data-theme="dark"] .setting-toggle i {
  background: #2e2e32;
}

:root[data-theme="dark"] .setting-toggle.active {
  background: rgba(255, 36, 66, 0.14);
  border-color: rgba(255, 36, 66, 0.24);
}

:root[data-theme="dark"] .setting-toggle.active i {
  background: var(--color-red);
}

:root[data-theme="dark"] .panel-title {
  color: var(--color-secondary);
}

:root[data-theme="dark"] .panel-hint,
:root[data-theme="dark"] .run-card p,
:root[data-theme="dark"] .run-row,
:root[data-theme="dark"] .context-number small,
:root[data-theme="dark"] .token-cell span,
:root[data-theme="dark"] .asset-item,
:root[data-theme="dark"] .tool-desc,
:root[data-theme="dark"] .section-head p {
  color: var(--color-secondary);
}

:root[data-theme="dark"] .run-card h2,
:root[data-theme="dark"] .run-row strong,
:root[data-theme="dark"] .token-cell strong,
:root[data-theme="dark"] .asset-item strong,
:root[data-theme="dark"] .context-number span {
  color: var(--color-title);
}

:root[data-theme="dark"] .context-meter {
  background: rgba(255, 255, 255, 0.08);
}

:root[data-theme="dark"] .context-badge.safe {
  background: rgba(34, 197, 94, 0.16);
  color: #7dd3a7;
}

:root[data-theme="dark"] .context-badge.warning {
  background: rgba(245, 158, 11, 0.16);
  color: #fbbf24;
}

:root[data-theme="dark"] .context-badge.danger {
  background: rgba(255, 36, 66, 0.18);
  color: #ff7a8d;
}

:root[data-theme="dark"] .inspector-action,
:root[data-theme="dark"] .workbench-trigger-btn {
  box-shadow: 0 12px 28px rgba(255, 36, 66, 0.24);
}

  :root[data-theme="dark"] .workbench-trigger-wrapper {
  filter: none;
}

:root[data-theme="dark"] .inspector-toggle {
  background: var(--color-card);
  border-color: var(--color-border);
  color: var(--color-secondary);
}

@media (max-width: 1240px) {
  .plan-inspector {
    position: fixed;
    right: 12px;
    bottom: 12px;
    width: min(420px, calc(100vw - 24px));
    max-height: calc(100vh - 100px);
    padding: 0;
    border: 1px solid rgba(17, 24, 39, 0.1);
    border-radius: 18px;
    box-shadow: 0 18px 40px rgba(17, 24, 39, 0.16);
    transform: translateY(calc(100% - 76px));
    transition: transform 0.22s ease;
    z-index: 1300;
    overflow-x: hidden;
    overflow-y: auto;
  }

  /* 防止固定定位的运行参数面板遮挡底部对话输入框 */
  .center-panel {
    padding-bottom: calc(76px + 12px + 2px);
  }

  .plan-inspector.collapsed {
    width: min(420px, calc(100vw - 24px));
    padding: 0;
    overflow-y: auto;
  }

  .plan-inspector.collapsed > :not(.inspector-edge-toggle) {
    display: revert;
  }

  .inspector-edge-toggle {
    display: none;
  }

  .plan-inspector.open {
    transform: translateY(0);
  }

  .plan-inspector .run-card {
    border-left: 0;
    border-right: 0;
    border-top: 0;
    border-radius: 0;
    box-shadow: none;
  }

  .plan-inspector .run-card + .run-card {
    border-top: 1px solid var(--color-border);
  }

  .run-card-head {
    align-items: center;
  }

  .inspector-toggle {
    display: inline-flex;
  }

  :root[data-theme="dark"] .plan-inspector {
    border-color: var(--color-border);
    box-shadow: 0 18px 40px rgba(0, 0, 0, 0.45);
  }
}

@media (max-width: 760px) {
  .messages-area {
    padding: 20px 14px 22px;
  }

  .scroll-jump-layer {
    display: none;
  }

  .agent-content-wrapper,
  .agent-content-wrapper.arena {
    max-width: 100%;
  }

  .compact-input-area {
    padding: 10px 12px 14px;
  }
}
</style>
