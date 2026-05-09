<template>
  <div :class="['chat-input-wrapper', { compact, centered: !compact && !hasMessages }]">
    <div class="chat-input-box">
      <textarea
        ref="textareaRef"
        v-model="localQuery"
        :rows="compact ? 1 : 3"
        :placeholder="placeholder"
        class="chat-textarea"
        @keydown.enter.exact.prevent="handleSubmit"
        @input="autoResize"
      />
      <div class="chat-input-footer">
        <div class="footer-left">
          <label class="upload-trigger">
            <input type="file" @change="onFileChange" hidden />
            <SvgIcon name="upload" :size="16" />
            <span v-if="file" class="file-name">{{ file.name }}</span>
            <span v-else>附件</span>
          </label>
          <div v-if="!compact" class="quick-tags">
            <button
              v-for="tag in tags"
              :key="tag"
              type="button"
              class="quick-tag"
              @click="appendTag(tag)"
            >{{ tag }}</button>
          </div>
        </div>
        <button
          class="send-btn"
          :disabled="!canSend"
          @click="handleSubmit"
        >
          <SvgIcon v-if="loading" name="loader" :size="16" spin />
          <SvgIcon v-else name="send" :size="16" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import SvgIcon from '../SvgIcon.vue'

const props = defineProps({
  compact: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  placeholder: { type: String, default: '描述你的旅行想法，例如：帮我做一个东京5天旅行计划...' },
  hasMessages: { type: Boolean, default: false },
})

const emit = defineEmits(['submit'])

const tags = ['3天短途', '5天深度游', '美食为主', '亲子游', '情侣出行']
const localQuery = ref('')
const file = ref(null)
const textareaRef = ref(null)

const canSend = ref(true)

function appendTag(tag) {
  if (!localQuery.value.includes(tag)) {
    localQuery.value = localQuery.value.trim() + (localQuery.value ? '，' : '') + tag
  }
}

function onFileChange(e) {
  file.value = e.target.files?.[0] || null
}

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = el.scrollHeight + 'px'
}

function handleSubmit() {
  if (!localQuery.value.trim() || props.loading) return
  emit('submit', { query: localQuery.value.trim(), file: file.value })
  localQuery.value = ''
  file.value = null
  nextTick(() => { if (textareaRef.value) textareaRef.value.style.height = 'auto' })
}
</script>

<style scoped>
.chat-input-wrapper {
  width: 100%;
}

.chat-input-wrapper.centered {
  display: flex;
  justify-content: center;
  padding: 0 20px;
}

.chat-input-wrapper.centered .chat-input-box {
  max-width: 680px;
  width: 100%;
}

.chat-input-box {
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-card);
  overflow: hidden;
  transition: border-color 0.2s;
}

.chat-input-box:focus-within {
  border-color: var(--color-red);
  box-shadow: 0 0 0 3px rgba(230, 57, 70, 0.08);
}

.chat-textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  padding: 14px 16px 8px;
  font-size: 14px;
  font-family: var(--font-family);
  background: transparent;
  color: var(--color-title);
  line-height: 1.5;
}

.chat-textarea::placeholder {
  color: var(--color-muted);
}

.compact .chat-textarea {
  padding: 10px 16px 6px;
  font-size: 13px;
}

.chat-input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  gap: 8px;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.upload-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  color: var(--color-hint);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.upload-trigger:hover {
  background: var(--color-surface);
  color: var(--color-secondary);
}

.file-name {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.quick-tags {
  display: flex;
  gap: 6px;
  overflow-x: auto;
}

.quick-tag {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  padding: 3px 10px;
  font-size: 11px;
  color: var(--color-hint);
  background: transparent;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  font-family: var(--font-family);
}

.quick-tag:hover {
  border-color: var(--color-red);
  color: var(--color-red-light);
}

.send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: var(--gradient-brand);
  color: white;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s;
}

.send-btn:hover:not(:disabled) {
  filter: brightness(1.1);
  transform: scale(1.05);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
