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
          <div class="mode-select-wrap">
            <select
              :value="mode"
              class="mode-select"
              @change="onModeChange"
            >
              <option v-for="m in modes" :key="m.value" :value="m.value">{{ m.label }}</option>
            </select>
          </div>
          <select
            v-if="mode !== 'auto'"
            :value="modelName"
            class="model-select"
            @change="$emit('update:selectedModel', $event.target.value)"
          >
            <option v-for="m in models" :key="m.value" :value="m.value">{{ m.label }}</option>
          </select>
          <div v-else class="model-auto-pill">随机模型对比</div>
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
          v-if="loading"
          class="stop-btn"
          @click="emit('stop')"
        >
          <SvgIcon name="close" :size="14" />
          <span>停止</span>
        </button>

        <button
          v-else
          class="send-btn"
          :disabled="!canSend"
          @click="handleSubmit"
        >
          <SvgIcon name="send" :size="16" />
        </button>

      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import SvgIcon from '../SvgIcon.vue'

const props = defineProps({
  compact: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  placeholder: { type: String, default: '描述你的旅行想法，例如：帮我做一个东京5天旅行计划...' },
  hasMessages: { type: Boolean, default: false },
  modelValue: { type: String, default: 'agent' },
  selectedModel: { type: String, default: 'deepseek-v4-flash' },
})

const emit = defineEmits(['submit', 'update:modelValue', 'update:selectedModel', 'stop'])

const mode = ref(props.modelValue)
const modelName = ref(props.selectedModel)

watch(() => props.modelValue, (v) => { mode.value = v })
watch(() => props.selectedModel, (v) => { modelName.value = v })

const modes = [
  { value: 'agent', label: 'Agent' },
  { value: 'plan', label: 'Plan' },
  { value: 'reflection', label: 'Reflection' },
  { value: 'auto', label: 'Auto' },
]

const models = [
  { value: 'deepseek-v4-flash', label: 'DeepSeek V4' },
  { value: 'kimi-k2.6', label: 'Kimi K2.6' },
  { value: 'MiniMax-M2.5', label: 'MiniMax M2.5' },
  { value: 'qwen3.6-plus', label: 'Qwen 3.6+' },
  { value: 'glm-5.1', label: 'GLM 5.1' },
]

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

function onModeChange(e) {
  mode.value = e.target.value
  emit('update:modelValue', mode.value)
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
  color: var(--color-title);
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

.mode-select-wrap {
  display: block;
  width: 160px;
  flex-shrink: 0;
}

.mode-select {
  width: 100%;
  padding: 3px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-family: var(--font-family);
  color: var(--color-title);
  background: transparent;
  cursor: pointer;
  outline: none;
  -webkit-appearance: none;
  appearance: none;
  padding-right: 20px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='10' viewBox='0 0 24 24' fill='none' stroke='%23999' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 6px center;
}

.mode-select:hover {
  border-color: var(--color-secondary);
}

.model-select {
  width: 160px;
  padding: 3px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-family: var(--font-family);
  color: var(--color-title);
  background: transparent;
  cursor: pointer;
  outline: none;
  flex-shrink: 0;
  -webkit-appearance: none;
  appearance: none;
  padding-right: 20px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='10' viewBox='0 0 24 24' fill='none' stroke='%23999' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 6px center;
}

.model-select:hover {
  border-color: var(--color-secondary);
}

.model-auto-pill {
  padding: 3px 10px;

  border: 1px dashed var(--color-border);
  border-radius: var(--radius-pill);
  font-size: 11px;
  color: var(--color-secondary);
  background: var(--color-surface);
  white-space: nowrap;
}



.stop-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: var(--radius-pill);
  background: rgba(230, 57, 70, 0.12);
  color: var(--color-red-light);
  font-size: 12px;
  font-weight: 600;
  border: 1px solid rgba(230, 57, 70, 0.2);
  transition: all 0.15s;
}

.stop-btn:hover {
  background: rgba(230, 57, 70, 0.2);
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
