<template>
  <div class="plan-block">
    <button class="plan-header" @click="toggle">
      <span class="plan-icon"><SvgIcon name="sparkles" :size="14" /></span>
      <span class="plan-label">
        {{ streaming ? '规划中' : '执行计划' }}
        <span v-if="streaming" class="streaming-dots">
          <span class="dot" /><span class="dot" /><span class="dot" />
        </span>
      </span>
      <span class="expand-arrow" :class="{ open: expanded }">
        <SvgIcon name="chevron-right" :size="12" />
      </span>
    </button>
    <div ref="bodyRef" class="plan-body">
      <div class="plan-content" v-html="rendered" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SvgIcon from '../SvgIcon.vue'

const props = defineProps({
  content: { type: String, default: '' },
  streaming: { type: Boolean, default: false },
})

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const expanded = ref(true)
const bodyRef = ref(null)

function updateHeight() {
  nextTick(() => {
    const el = bodyRef.value
    if (!el) return
    el.style.maxHeight = expanded.value ? el.scrollHeight + 'px' : '0px'
    el.style.opacity = expanded.value ? '1' : '0'
  })
}

function toggle() {
  expanded.value = !expanded.value
  updateHeight()
}

// Auto-expand when streaming starts, auto-collapse when it ends
watch(() => props.streaming, (val) => {
  expanded.value = true
  updateHeight()
  if (!val) {
    // Collapse after a short delay once streaming ends
    setTimeout(() => {
      expanded.value = false
      updateHeight()
    }, 400)
  }
}, { immediate: true })

// Re-measure height when content changes during streaming
watch(() => props.content, () => {
  if (expanded.value) updateHeight()
})

const rendered = computed(() => DOMPurify.sanitize(md.render(props.content)))
</script>

<style scoped>
.plan-block {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 6px;
  background: var(--color-card);
  transition: border-color 0.2s;
}

.plan-block:hover {
  border-color: var(--color-secondary);
}

.plan-header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-family: var(--font-family);
  font-size: 13px;
  color: var(--color-body);
  text-align: left;
  transition: background 0.15s;
}

.plan-header:hover {
  background: var(--color-surface);
}

.plan-icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  color: #3b82f6;
}

.plan-label {
  flex: 1;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.streaming-dots {
  display: inline-flex;
  gap: 3px;
  align-items: center;
}

.streaming-dots .dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #3b82f6;
  animation: dotPulse 1.2s ease-in-out infinite;
}

.streaming-dots .dot:nth-child(2) {
  animation-delay: 0.2s;
}

.streaming-dots .dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes dotPulse {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.1); }
}

.expand-arrow {
  color: var(--color-muted);
  display: flex;
  align-items: center;
  transition: transform 0.25s ease;
}

.expand-arrow.open {
  transform: rotate(90deg);
}

.plan-body {
  overflow: hidden;
  max-height: 0;
  opacity: 0;
  transition: max-height 0.3s ease-out, opacity 0.2s ease;
}

.plan-content {
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-body);
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
}

.plan-content :deep(p) {
  margin: 0 0 8px;
}

.plan-content :deep(p:last-child) {
  margin-bottom: 0;
}

.plan-content :deep(ul),
.plan-content :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}

.plan-content :deep(code) {
  background: var(--color-card);
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 12px;
}

.plan-content :deep(pre) {
  background: var(--color-card);
  padding: 10px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 12px;
}
</style>
