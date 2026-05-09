<template>
  <div :class="['event-block', type]">
    <button class="event-header" @click="toggle">
      <span class="event-icon"><SvgIcon :name="iconName" :size="14" /></span>
      <span class="event-label">{{ label }}</span>
      <span v-if="actionSummary" class="event-summary">{{ actionSummary }}</span>
      <span v-else-if="toolName" class="tool-tag">{{ toolName }}</span>
      <span v-if="observationSummary" class="event-summary">{{ observationSummary }}</span>
      <span class="expand-arrow" :class="{ open: expanded }">
        <SvgIcon name="chevron-right" :size="12" />
      </span>
    </button>
    <div ref="bodyRef" class="event-body">
      <div v-if="props.type === 'thought'" class="event-content rendered" v-html="rendered" />
      <pre v-else class="event-content">{{ content }}</pre>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SvgIcon from '../SvgIcon.vue'

const props = defineProps({
  type: { type: String, required: true },
  content: { type: String, default: '' },
  toolName: { type: String, default: '' },
  metadata: { type: Object, default: null },
  expanded: { type: Boolean, default: false },
})

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const rendered = computed(() => DOMPurify.sanitize(md.render(props.content)))

const expanded = ref(props.expanded)
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

const iconMap = {
  plan: 'sparkles',
  thought: 'brain',
  action: 'wrench',
  observation: 'eye',
  reflection: 'refresh',
}

const labelMap = {
  plan: '规划中...',
  thought: '思考中...',
  action: '调用工具',
  observation: '观察结果',
  reflection: '自我反思',
}

const iconName = computed(() => iconMap[props.type] || 'sparkles')

const actionSummary = computed(() => {
  if (props.type !== 'action') return ''
  const content = props.content || ''
  // Backend sends "Calling tool: tool_name({json_args})"
  const match = content.match(/Calling tool: (\w+)\((.*)\)/s)
    || content.match(/^(\w+)\((.*)\)$/s)
  if (!match) return ''
  const name = match[1]
  try {
    const args = JSON.parse(match[2])
    if (name === 'web_search') return `搜索: ${args.query || ''}`
    if (name === 'parse_file') return `解析文件: ${args.file_name || ''}`
    const firstStr = Object.values(args).find(v => typeof v === 'string')
    return firstStr || ''
  } catch {
    return ''
  }
})

const observationSummary = computed(() => {
  if (props.type !== 'observation') return ''
  try {
    const arr = JSON.parse(props.content || '')
    if (Array.isArray(arr)) return `搜索到 ${arr.length} 个相关结果`
  } catch {}
  return ''
})

const label = computed(() => {
  if (props.type === 'action' && props.toolName) {
    return `调用工具: ${props.toolName}`
  }
  if (props.type === 'thought' && props.metadata?.step) {
    return `思考中... (步骤 ${props.metadata.step})`
  }
  return labelMap[props.type] || '处理中...'
})
</script>

<style scoped>
.event-block {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 6px;
  background: var(--color-card);
  transition: border-color 0.2s;
}

.event-block:hover {
  border-color: var(--color-secondary);
}

.event-header {
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

.event-header:hover {
  background: var(--color-surface);
}

.event-icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.plan .event-icon { color: #3b82f6; }
.thought .event-icon { color: #a855f7; }
.action .event-icon { color: #f97316; }
.observation .event-icon { color: #22c55e; }
.reflection .event-icon { color: #06b6d4; }

.event-label {
  flex: 1;
  font-weight: 500;
}

.tool-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: rgba(249, 115, 22, 0.1);
  color: #f97316;
  white-space: nowrap;
}

.event-summary {
  font-size: 12px;
  color: var(--color-body);
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.event-body {
  overflow: hidden;
  max-height: 0;
  opacity: 0;
  transition: max-height 0.3s ease-out, opacity 0.2s ease;
}

.event-content {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-body);
  background: var(--color-surface);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-family);
  border-top: 1px solid var(--color-border);
}

.event-content.rendered {
  white-space: normal;
  font-size: 13px;
  line-height: 1.6;
}

.event-content.rendered :deep(p) {
  margin: 0 0 8px;
}

.event-content.rendered :deep(p:last-child) {
  margin-bottom: 0;
}

.event-content.rendered :deep(ul),
.event-content.rendered :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}

.event-content.rendered :deep(code) {
  background: var(--color-card);
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 12px;
}

.event-content.rendered :deep(pre) {
  background: var(--color-card);
  padding: 10px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 12px;
}
</style>
