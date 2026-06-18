<template>
  <div :class="['event-block', type, { streaming }]">
    <button class="event-header" @click="toggle">
      <span class="event-icon"><SvgIcon :name="iconName" :size="14" /></span>
      <span class="event-label">{{ label }}</span>
      <span v-if="actionSummary" class="event-summary">{{ actionSummary }}</span>
      <span v-else-if="toolName" class="tool-tag">{{ toolDisplayName }}</span>
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
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SvgIcon from '../SvgIcon.vue'

const props = defineProps({
  type: { type: String, required: true },
  content: { type: String, default: '' },
  toolName: { type: String, default: '' },
  metadata: { type: Object, default: null },
  expanded: { type: Boolean, default: false },
  streaming: { type: Boolean, default: false },
})

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const rendered = computed(() => DOMPurify.sanitize(md.render(props.content)))

const expanded = ref(props.expanded || props.streaming)
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

watch(() => props.expanded, (value) => {
  expanded.value = value
  updateHeight()
})

watch(() => props.content, () => {
  updateHeight()
})

onMounted(updateHeight)

const iconMap = {
  plan: 'sparkles',
  thought: 'brain',
  action: 'wrench',
  observation: 'eye',
  reflection: 'refresh',
  ask_user: 'message',
}

const labelMap = {
  plan: '规划中...',
  thought: '思考中...',
  action: '调用工具',
  observation: '观察结果',
  reflection: '自我反思',
  ask_user: '等待用户确认',
}

const iconName = computed(() => iconMap[props.type] || 'sparkles')

const toolNameMap = {
  web_search: '联网搜索',
  parse_file: '文件解析',
}

const toolDisplayName = computed(() => toolNameMap[props.toolName] || props.toolName)

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
    if (name === 'web_search') return `联网搜索：${args.query || ''}`
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
    return `调用工具: ${toolDisplayName.value}`
  }
  if (props.type === 'thought' && props.metadata?.step) {
    return `思考中... (步骤 ${props.metadata.step})`
  }
  return labelMap[props.type] || '处理中...'
})
</script>

<style scoped>
.event-block {
  border: 1px solid rgba(17, 24, 39, 0.08);
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 4px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(17, 24, 39, 0.04);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.event-block:hover {
  border-color: rgba(255, 36, 66, 0.2);
  box-shadow: 0 12px 30px rgba(17, 24, 39, 0.055);
}

/* Streaming 状态动画 */
.event-block.streaming {
  border-color: rgba(59, 130, 246, 0.3);
  animation: streaming-pulse 2s ease-in-out infinite;
}

@keyframes streaming-pulse {
  0%, 100% { border-color: rgba(59, 130, 246, 0.2); }
  50% { border-color: rgba(59, 130, 246, 0.5); }
}

.event-block.streaming .event-header {
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.05), transparent);
}

.event-header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-family: var(--font-family);
  font-size: 12px;
  color: var(--color-body);
  text-align: left;
  transition: background 0.15s;
}

.event-header:hover {
  background: #fff7f8;
}

.event-icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.plan .event-icon,
.thought .event-icon,
.action .event-icon,
.observation .event-icon,
.reflection .event-icon,
.ask_user .event-icon {
  color: var(--color-red);
}

.event-label {
  flex: 1;
  font-weight: 900;
  color: var(--color-title);
}

.tool-tag {
  font-size: 11px;
  font-weight: 900;
  padding: 3px 8px;
  border-radius: var(--radius-pill);
  background: #fff1f3;
  color: var(--color-red);
  white-space: nowrap;
}

.event-summary {
  font-size: 12px;
  color: var(--color-secondary);
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
  padding: 12px 14px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-body);
  background: #fbfbfc;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-family);
  border-top: 1px solid rgba(17, 24, 39, 0.08);
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
  background: #fff1f3;
  color: var(--color-red);
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 12px;
}

.event-content.rendered :deep(pre) {
  border: 1px solid var(--color-border);
  background: #fffafa;
  color: var(--color-title);
  padding: 10px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 12px;
}

:root[data-theme="dark"] .event-block {
  background: var(--color-card);
  border-color: var(--color-border);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.28);
}

:root[data-theme="dark"] .event-block:hover {
  border-color: rgba(255, 36, 66, 0.24);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.34);
}

:root[data-theme="dark"] .event-header:hover {
  background: var(--color-soft-red);
}

:root[data-theme="dark"] .tool-tag,
:root[data-theme="dark"] .event-content.rendered :deep(code) {
  background: rgba(255, 36, 66, 0.14);
  color: #ff8fa3;
}

:root[data-theme="dark"] .event-content {
  background: rgba(255, 255, 255, 0.04);
  border-top-color: rgba(255, 255, 255, 0.08);
}

:root[data-theme="dark"] .event-content.rendered :deep(pre) {
  background: rgba(255, 255, 255, 0.04);
  color: var(--color-body);
}
</style>
