<template>
  <div class="arena-card">
    <div class="arena-header">
      <div class="arena-title">Auto Mode · 模型对比</div>
      <div class="arena-subtitle">随机选取两种模型生成回答</div>
    </div>

    <div v-if="loading" class="arena-loading">
      <div class="arena-loading-head">
        <span class="arena-loading-title">正在生成对比回答</span>
        <span class="arena-loading-dots"><span class="dot" /><span class="dot" /><span class="dot" /></span>
      </div>
      <div v-if="traceEvents.length" class="arena-trace">
        <AgentEventBlock
          v-for="(ev, index) in traceEvents"
          :key="`${ev.type}-${index}`"
          :type="ev.type"
          :content="ev.content"
          :metadata="ev.metadata"
        />
      </div>
      <div v-else class="arena-loading-note">正在抽取匿名模型并并行生成回答，投票后再揭晓具体模型名。</div>
    </div>

    <template v-else>
      <div v-if="traceEvents.length" class="arena-trace revealed">
        <AgentEventBlock
          v-for="(ev, index) in traceEvents"
          :key="`${ev.type}-${index}`"
          :type="ev.type"
          :content="ev.content"
          :metadata="ev.metadata"
        />
      </div>

      <div class="arena-grid">
        <div class="arena-col">
          <div class="arena-col-header">
            <span class="arena-label">A</span>
            <span class="arena-model">{{ displayModelA }}</span>
          </div>
          <div class="arena-answer" v-html="renderedA" />
        </div>
        <div class="arena-col">
          <div class="arena-col-header">
            <span class="arena-label">B</span>
            <span class="arena-model">{{ displayModelB }}</span>
          </div>
          <div class="arena-answer" v-html="renderedB" />
        </div>
      </div>
    </template>

    <div class="arena-actions">
      <button class="arena-btn" :disabled="isDisabled" @click="emitVote('A')">A 更好</button>
      <button class="arena-btn" :disabled="isDisabled" @click="emitVote('B')">B 更好</button>
      <button class="arena-btn ghost" :disabled="isDisabled" @click="emitVote('BOTH_GOOD')">都好</button>
      <button class="arena-btn ghost" :disabled="isDisabled" @click="emitVote('BOTH_BAD')">都不好</button>
      <span v-if="votedLabel" class="arena-voted">已投票：{{ votedLabel }}</span>
      <span v-else-if="!loading" class="arena-voted muted">投票后揭晓具体模型名</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import AgentEventBlock from './AgentEventBlock.vue'

const props = defineProps({
  modelA: { type: String, default: '' },
  modelB: { type: String, default: '' },
  answerA: { type: String, default: '' },
  answerB: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  voted: { type: String, default: '' },
  events: { type: Array, default: () => [] },
})

const emit = defineEmits(['vote'])

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const renderedA = computed(() => DOMPurify.sanitize(md.render(props.answerA || '')))
const renderedB = computed(() => DOMPurify.sanitize(md.render(props.answerB || '')))
const traceEvents = computed(() => Array.isArray(props.events) ? props.events : [])
const revealed = computed(() => Boolean(props.voted))
const displayModelA = computed(() => (revealed.value ? props.modelA || '模型 A' : '模型 A'))
const displayModelB = computed(() => (revealed.value ? props.modelB || '模型 B' : '模型 B'))

const votedLabel = computed(() => {
  if (!props.voted) return ''
  if (props.voted === 'A') return 'A 更好'
  if (props.voted === 'B') return 'B 更好'
  if (props.voted === 'BOTH_GOOD') return '都好'
  if (props.voted === 'BOTH_BAD') return '都不好'
  return ''
})

const isDisabled = computed(() => Boolean(props.voted) || props.loading)

function emitVote(result) {
  if (props.loading || props.voted) return
  emit('vote', result)
}
</script>

<style scoped>
.arena-card {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-card);
  box-shadow: var(--shadow-card);
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.arena-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.arena-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-title);
}

.arena-subtitle {
  font-size: 12px;
  color: var(--color-muted);
}

.arena-loading {
  padding: 16px;
  border-radius: 10px;
  background: var(--color-surface);
  color: var(--color-secondary);
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.arena-loading-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.arena-loading-title {
  color: var(--color-title);
  font-weight: 600;
}

.arena-loading-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.arena-loading-dots .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-red-light);
  animation: arenaPulse 1.2s ease-in-out infinite;
}

.arena-loading-dots .dot:nth-child(2) {
  animation-delay: 0.2s;
}

.arena-loading-dots .dot:nth-child(3) {
  animation-delay: 0.4s;
}

.arena-loading-note {
  color: var(--color-muted);
  font-size: 12px;
}

.arena-trace {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.arena-trace.revealed {
  margin-bottom: 2px;
}

.arena-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.arena-col {
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.arena-col-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-card);
}

.arena-label {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--gradient-brand);
  color: white;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.arena-model {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-title);
}

.arena-answer {
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-body);
  overflow-y: auto;
  max-height: 320px;
}

.arena-answer :deep(p) {
  margin: 0 0 8px;
}

.arena-answer :deep(p:last-child) {
  margin-bottom: 0;
}

.arena-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.arena-btn {
  padding: 6px 14px;
  border-radius: var(--radius-pill);
  background: var(--gradient-brand);
  color: white;
  font-size: 12px;
  font-weight: 600;
  border: none;
  transition: all 0.15s;
}

.arena-btn:hover:not(:disabled) {
  filter: brightness(1.05);
}

.arena-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.arena-btn.ghost {
  background: transparent;
  color: var(--color-secondary);
  border: 1px solid var(--color-border);
}

.arena-voted {
  font-size: 12px;
  color: var(--color-secondary);
  font-weight: 600;
}

.arena-voted.muted {
  color: var(--color-muted);
  font-weight: 500;
}

@keyframes arenaPulse {
  0%, 80%, 100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1.1);
  }
}
</style>