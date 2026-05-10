<template>
  <div class="arena-card">
    <div class="arena-header">
      <div class="arena-title">Auto Mode · 模型对比</div>
      <div class="arena-subtitle">随机选取两种模型生成回答</div>
    </div>

    <div v-if="loading" class="arena-loading">正在生成对比回答...</div>

    <div v-else class="arena-grid">
      <div class="arena-col">
        <div class="arena-col-header">
          <span class="arena-label">A</span>
          <span class="arena-model">{{ modelA }}</span>
        </div>
        <div class="arena-answer" v-html="renderedA" />
      </div>
      <div class="arena-col">
        <div class="arena-col-header">
          <span class="arena-label">B</span>
          <span class="arena-model">{{ modelB }}</span>
        </div>
        <div class="arena-answer" v-html="renderedB" />
      </div>
    </div>

    <div class="arena-actions">
      <button class="arena-btn" :disabled="isDisabled" @click="emitVote('A')">A 更好</button>
      <button class="arena-btn" :disabled="isDisabled" @click="emitVote('B')">B 更好</button>
      <button class="arena-btn ghost" :disabled="isDisabled" @click="emitVote('BOTH_GOOD')">都好</button>
      <button class="arena-btn ghost" :disabled="isDisabled" @click="emitVote('BOTH_BAD')">都不好</button>
      <span v-if="votedLabel" class="arena-voted">已投票：{{ votedLabel }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const props = defineProps({
  modelA: { type: String, default: '' },
  modelB: { type: String, default: '' },
  answerA: { type: String, default: '' },
  answerB: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  voted: { type: String, default: '' },
})

const emit = defineEmits(['vote'])

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const renderedA = computed(() => DOMPurify.sanitize(md.render(props.answerA || '')))
const renderedB = computed(() => DOMPurify.sanitize(md.render(props.answerB || '')))

const votedLabel = computed(() => {
  if (!props.voted) return ''
  if (props.voted === 'A') return 'A 更好'
  if (props.voted === 'B') return 'B 更好'
  if (props.voted === 'BOTH_GOOD') return '都好'
  if (props.voted === 'BOTH_BAD') return '都不好'
  return ''
})

const isDisabled = computed(() => {
  return Boolean(props.voted) || props.loading
})

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
</style>