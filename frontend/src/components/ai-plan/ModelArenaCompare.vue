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
      <ArenaTimeline :stages="stages" :loading="loading" compact />
      <div class="arena-loading-note">正在抽取匿名模型并并行生成回答，投票后再揭晓具体模型名。</div>
    </div>

    <template v-else>
      <ArenaTimeline v-if="stages.length" :stages="stages" :loading="false" class="arena-trace revealed" />

      <div class="arena-grid">
        <div class="arena-col">
          <div class="arena-col-header">
            <span class="arena-label">A</span>
            <span class="arena-model">{{ displayModelA }}</span>
            <button class="arena-expand-btn" @click="openFullscreen('A')">全屏</button>
          </div>
          <div class="arena-answer" v-html="renderedA" />
        </div>
        <div class="arena-col">
          <div class="arena-col-header">
            <span class="arena-label">B</span>
            <span class="arena-model">{{ displayModelB }}</span>
            <button class="arena-expand-btn" @click="openFullscreen('B')">全屏</button>
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

    <div v-if="fullscreen.open" class="arena-fullscreen-mask" @click.self="closeFullscreen">
      <section class="arena-fullscreen-panel">
        <header class="arena-fullscreen-head">
          <div class="arena-fullscreen-title">回答 {{ fullscreen.target }} · {{ fullscreen.target === 'A' ? displayModelA : displayModelB }}</div>
          <div class="arena-fullscreen-actions">
            <button class="arena-fullscreen-switch" @click="toggleFullscreenMode">{{ fullscreen.mode === 'split' ? '单栏' : '双栏' }}</button>
            <button class="arena-fullscreen-switch" @click="switchFullscreen('A')">看 A</button>
            <button class="arena-fullscreen-switch" @click="switchFullscreen('B')">看 B</button>
            <button class="arena-fullscreen-close" @click="closeFullscreen">关闭</button>
          </div>
        </header>
        <div v-if="fullscreen.mode === 'single'" class="arena-fullscreen-body" v-html="fullscreen.target === 'A' ? renderedA : renderedB"></div>
        <div v-else class="arena-fullscreen-split">
          <section class="split-col">
            <header class="split-col-head">A · {{ displayModelA }}</header>
            <div ref="fullscreenScrollA" class="split-col-body" @scroll="syncScroll('A')" v-html="renderedA"></div>
          </section>
          <section class="split-col">
            <header class="split-col-head">B · {{ displayModelB }}</header>
            <div ref="fullscreenScrollB" class="split-col-body" @scroll="syncScroll('B')" v-html="renderedB"></div>
          </section>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, nextTick } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import ArenaTimeline from './ArenaTimeline.vue'

const props = defineProps({
  modelA: { type: String, default: '' },
  modelB: { type: String, default: '' },
  answerA: { type: String, default: '' },
  answerB: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  voted: { type: String, default: '' },
  stages: { type: Array, default: () => [] },
})

const emit = defineEmits(['vote'])

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const fullscreen = ref({ open: false, target: 'A', mode: 'single' })
const fullscreenScrollA = ref(null)
const fullscreenScrollB = ref(null)
let syncingScroll = false

const renderedA = computed(() => DOMPurify.sanitize(md.render(props.answerA || '')))
const renderedB = computed(() => DOMPurify.sanitize(md.render(props.answerB || '')))
const revealed = computed(() => Boolean(props.voted))
const stages = computed(() => Array.isArray(props.stages) ? props.stages : [])
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

function openFullscreen(target) {
  fullscreen.value = { open: true, target, mode: 'single' }
}

function closeFullscreen() {
  fullscreen.value = { open: false, target: 'A', mode: 'single' }
}

function switchFullscreen(target) {
  fullscreen.value = { ...fullscreen.value, open: true, target }
}

function toggleFullscreenMode() {
  const nextMode = fullscreen.value.mode === 'single' ? 'split' : 'single'
  fullscreen.value = { ...fullscreen.value, mode: nextMode }
  if (nextMode === 'split') {
    nextTick(() => {
      if (fullscreenScrollA.value && fullscreenScrollB.value) {
        fullscreenScrollB.value.scrollTop = fullscreenScrollA.value.scrollTop
      }
    })
  }
}

function syncScroll(source) {
  if (fullscreen.value.mode !== 'split' || syncingScroll) return
  const fromEl = source === 'A' ? fullscreenScrollA.value : fullscreenScrollB.value
  const toEl = source === 'A' ? fullscreenScrollB.value : fullscreenScrollA.value
  if (!fromEl || !toEl) return
  syncingScroll = true
  toEl.scrollTop = fromEl.scrollTop
  requestAnimationFrame(() => {
    syncingScroll = false
  })
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

.arena-expand-btn {
  margin-left: auto;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-secondary);
  border-radius: 8px;
  padding: 2px 8px;
  font-size: 11px;
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

.arena-fullscreen-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.52);
  z-index: 2500;
  padding: 24px;
}

.arena-fullscreen-panel {
  width: min(1100px, 100%);
  height: min(88vh, 960px);
  margin: 0 auto;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
}

.arena-fullscreen-head {
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  gap: 10px;
}

.arena-fullscreen-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-title);
}

.arena-fullscreen-actions {
  margin-left: auto;
  display: inline-flex;
  gap: 8px;
}

.arena-fullscreen-switch,
.arena-fullscreen-close {
  border-radius: 8px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-secondary);
  padding: 4px 10px;
  font-size: 12px;
}

.arena-fullscreen-close {
  border-color: rgba(248, 113, 113, 0.45);
  color: #b91c1c;
}

.arena-fullscreen-body {
  flex: 1;
  overflow: auto;
  padding: 16px 18px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-body);
}

.arena-fullscreen-body :deep(p) {
  margin: 0 0 12px;
}

.arena-fullscreen-split {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
  min-height: 0;
}

.split-col {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.split-col + .split-col {
  border-left: 1px solid var(--color-border);
}

.split-col-head {
  font-size: 12px;
  color: var(--color-title);
  font-weight: 600;
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

.split-col-body {
  flex: 1;
  overflow: auto;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-body);
}

.split-col-body :deep(p) {
  margin: 0 0 12px;
}

@media (max-width: 960px) {
  .arena-fullscreen-split {
    grid-template-columns: 1fr;
  }

  .split-col + .split-col {
    border-left: none;
    border-top: 1px solid var(--color-border);
  }
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
