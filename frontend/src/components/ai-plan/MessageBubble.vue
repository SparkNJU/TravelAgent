<template>
  <div :class="['message-row', role]">
    <div v-if="role === 'assistant'" class="avatar agent-avatar">
      <SvgIcon name="sparkles" :size="16" />
    </div>
    <div class="bubble-col">
      <div class="bubble" v-html="rendered" />
      <div class="bubble-actions">
        <transition name="fade">
          <button
            v-if="content"
            class="copy-btn"
            :class="{ copied }"
            @click="handleCopy"
            :title="copied ? '已复制' : '复制'"
          >
            <SvgIcon :name="copied ? 'check' : 'copy'" :size="13" />
          </button>
        </transition>
        <slot name="actions" />
      </div>
    </div>
    <div v-if="role === 'user'" class="avatar user-avatar">
      <SvgIcon name="user" :size="16" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SvgIcon from '../SvgIcon.vue'

const props = defineProps({
  role: { type: String, required: true }, // 'user' | 'assistant'
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

const copied = ref(false)

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(props.content)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch {
    // fallback
    const textarea = document.createElement('textarea')
    textarea.value = props.content
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  }
}
</script>

<style scoped>
.message-row {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  margin-bottom: 2px;
}

.message-row.user {
  width: fit-content;
  max-width: min(72%, 720px);
  margin-left: auto;
  flex-direction: row-reverse;
}

.message-row.assistant {
  max-width: 100%;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.agent-avatar {
  background: var(--gradient-brand);
  color: white;
  box-shadow: 0 10px 24px rgba(255, 36, 66, 0.22);
}

.user-avatar {
  background: #ffffff;
  color: var(--color-secondary);
  border: 1px solid var(--color-border);
}

.bubble-col {
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-width: 100%;
}

.bubble {
  min-width: 0;
  padding: 11px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  cursor: text;
}

/* 选中文本高亮样式 */
.bubble ::selection {
  background: rgba(255, 36, 66, 0.25);
  color: inherit;
}

.user .bubble ::selection {
  background: rgba(255, 36, 66, 0.2);
}

.bubble-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 4px;
}

.copy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-tertiary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.copy-btn:hover {
  color: var(--color-red);
  background: #fff7f8;
}

.copy-btn.copied {
  color: #22c55e;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.user .bubble {
  background: #fff0f1;
  color: #333;
  border-bottom-right-radius: 6px;
  box-shadow: 0 2px 8px rgba(255, 36, 66, 0.08);
}

.assistant .bubble {
  width: 100%;
  border: 1px solid rgba(17, 24, 39, 0.08);
  background: #ffffff;
  color: var(--color-body);
  border-bottom-left-radius: 6px;
  box-shadow: 0 12px 34px rgba(17, 24, 39, 0.055);
}

.assistant .bubble :deep(p) {
  margin: 0 0 10px;
}

.assistant .bubble :deep(p:last-child) {
  margin-bottom: 0;
}

.assistant .bubble :deep(h1),
.assistant .bubble :deep(h2),
.assistant .bubble :deep(h3) {
  margin: 18px 0 9px;
  color: var(--color-title);
  font-weight: 900;
  line-height: 1.28;
  letter-spacing: 0;
}

.assistant .bubble :deep(h1:first-child),
.assistant .bubble :deep(h2:first-child),
.assistant .bubble :deep(h3:first-child) {
  margin-top: 0;
}

.assistant .bubble :deep(h1) {
  font-size: 22px;
}

.assistant .bubble :deep(h2) {
  padding-top: 14px;
  border-top: 1px solid rgba(17, 24, 39, 0.08);
  font-size: 18px;
}

.assistant .bubble :deep(h3) {
  font-size: 15px;
}

.assistant .bubble :deep(ul),
.assistant .bubble :deep(ol) {
  margin: 8px 0 12px;
  padding-left: 20px;
}

.assistant .bubble :deep(li) {
  margin: 4px 0;
}

.assistant .bubble :deep(strong) {
  color: var(--color-title);
  font-weight: 900;
}

.assistant .bubble :deep(a) {
  color: var(--color-red);
  font-weight: 800;
  text-decoration: none;
  border-bottom: 1px solid rgba(255, 36, 66, 0.28);
}

.assistant .bubble :deep(blockquote) {
  margin: 12px 0;
  padding: 10px 12px;
  border-left: 3px solid var(--color-red);
  border-radius: 8px;
  background: #fff7f8;
  color: var(--color-secondary);
}

.assistant .bubble :deep(code) {
  background: #fff1f3;
  color: var(--color-red);
  padding: 2px 5px;
  border-radius: 5px;
  font-size: 13px;
}

.assistant .bubble :deep(pre) {
  border: 1px solid var(--color-border);
  background: #fffafa;
  color: var(--color-title);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 12px;
}

.assistant .bubble :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
}

.assistant .bubble :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  overflow: hidden;
  border-radius: 8px;
}

.assistant .bubble :deep(th),
.assistant .bubble :deep(td) {
  padding: 9px 10px;
  border: 1px solid var(--color-border);
  text-align: left;
  vertical-align: top;
}

.assistant .bubble :deep(th) {
  background: #fff7f8;
  color: var(--color-title);
  font-weight: 900;
}

:root[data-theme="dark"] .agent-avatar {
  box-shadow: 0 10px 24px rgba(255, 36, 66, 0.28);
}

:root[data-theme="dark"] .user-avatar {
  background: var(--color-card);
  border-color: var(--color-border);
  color: var(--color-secondary);
}

:root[data-theme="dark"] .user .bubble {
  background: rgba(255, 36, 66, 0.12);
  color: var(--color-body);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

:root[data-theme="dark"] .assistant .bubble {
  background: var(--color-card);
  border-color: var(--color-border);
  color: var(--color-body);
  box-shadow: 0 12px 34px rgba(0, 0, 0, 0.32);
}

:root[data-theme="dark"] .copy-btn {
  background: transparent;
  color: var(--color-tertiary);
}

:root[data-theme="dark"] .copy-btn:hover {
  color: var(--color-red-light);
  background: rgba(255, 36, 66, 0.1);
}

:root[data-theme="dark"] .assistant .bubble :deep(h2) {
  border-top-color: rgba(255, 255, 255, 0.08);
}

:root[data-theme="dark"] .assistant .bubble :deep(blockquote) {
  background: rgba(255, 36, 66, 0.1);
  color: var(--color-body);
}

:root[data-theme="dark"] .assistant .bubble :deep(code) {
  background: rgba(255, 36, 66, 0.14);
  color: #ff8fa3;
}

:root[data-theme="dark"] .assistant .bubble :deep(pre) {
  background: rgba(255, 255, 255, 0.04);
  color: var(--color-body);
}

:root[data-theme="dark"] .assistant .bubble :deep(th) {
  background: rgba(255, 36, 66, 0.1);
  color: var(--color-title);
}

@media (max-width: 720px) {
  .message-row.user {
    max-width: 88%;
  }

  .avatar {
    width: 30px;
    height: 30px;
  }
}
</style>
