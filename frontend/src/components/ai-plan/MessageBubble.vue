<template>
  <div :class="['message-row', role]">
    <div v-if="role === 'agent'" class="avatar agent-avatar">
      <SvgIcon name="sparkles" :size="16" />
    </div>
    <div class="bubble" v-html="rendered" />
    <div v-if="role === 'user'" class="avatar user-avatar">
      <SvgIcon name="user" :size="16" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SvgIcon from '../SvgIcon.vue'

const props = defineProps({
  role: { type: String, required: true }, // 'user' | 'agent'
  content: { type: String, default: '' },
})

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const rendered = computed(() => {
  if (props.role === 'user') {
    return escapeHtml(props.content)
  }
  return DOMPurify.sanitize(md.render(props.content))
})

function escapeHtml(str) {
  const div = document.createElement('div')
  div.textContent = str
  return div.innerHTML
}
</script>

<style scoped>
.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 4px;
}

.message-row.user {
  max-width: 80%;
  margin-left: auto;
  flex-direction: row-reverse;
}

.message-row.agent {
  max-width: 100%;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.agent-avatar {
  background: var(--gradient-brand);
  color: white;
}

.user-avatar {
  background: var(--color-surface);
  color: var(--color-secondary);
  border: 1px solid var(--color-border);
}

.bubble {
  padding: 10px 14px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.user .bubble {
  background: var(--gradient-brand);
  color: white;
  border-bottom-right-radius: 4px;
}

.agent .bubble {
  background: var(--color-surface);
  color: var(--color-body);
  border-bottom-left-radius: 4px;
}

.agent .bubble :deep(p) {
  margin: 0 0 8px;
}

.agent .bubble :deep(p:last-child) {
  margin-bottom: 0;
}

.agent .bubble :deep(ul),
.agent .bubble :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}

.agent .bubble :deep(code) {
  background: var(--color-card);
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 13px;
}

.agent .bubble :deep(pre) {
  background: var(--color-card);
  padding: 10px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 12px;
}
</style>
