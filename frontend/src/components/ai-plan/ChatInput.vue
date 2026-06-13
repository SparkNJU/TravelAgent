<template>
  <div :class="['chat-input-wrapper', { compact, centered: !compact && !hasMessages }]">
    <!-- Suggestion chips: only in empty state -->
    <div v-if="!compact && !hasMessages" class="suggestions-row">
      <button
        v-for="tag in tags"
        :key="tag"
        type="button"
        class="suggestion-chip"
        @click="appendTag(tag)"
      >{{ tag }}</button>
    </div>

    <div class="chat-input-box">
      <textarea
        ref="textareaRef"
        v-model="localQuery"
        :rows="compact ? 1 : 2"
        :placeholder="placeholder"
        class="chat-textarea"
        @keydown.enter.exact.prevent="handleSubmit"
        @input="autoResize"
      />

      <div class="chat-input-toolbar">
        <!-- Left group: upload + mode + model -->
        <div class="toolbar-left">
          <label class="tool-btn upload-btn" title="上传文件">
            <input type="file" @change="onFileChange" hidden />
            <SvgIcon name="upload" :size="16" />
          </label>
          <span v-if="file" class="file-chip">{{ file.name }}</span>

          <div class="mode-select-wrap">
            <select
              :value="mode"
              class="chip-select"
              :disabled="arenaMode"
              @change="onModeChange"
            >
              <option v-for="m in modes" :key="m.value" :value="m.value">{{ m.label }}</option>
            </select>
          </div>

          <div class="model-chip">
            <select
              :value="modelName"
              class="chip-select"
              :disabled="arenaMode"
              @change="$emit('update:selectedModel', $event.target.value)"
            >
              <option v-for="m in models" :key="m.value" :value="m.value">{{ m.label }}</option>
            </select>
          </div>
        </div>

        <!-- Right group: arena + context + send -->
        <div class="toolbar-right">
          <button
            type="button"
            class="arena-chip"
            :class="{ active: arenaMode }"
            title="竞技场模式"
            @click="emit('toggleArena')"
          >
            <SvgIcon :name="arenaMode ? 'close' : 'trophy'" :size="13" />
          </button>

          <ContextPanel
            :tokenStatus="tokenStatus"
            :loading="loading"
            :compressing="compressing"
            :canCompress="canCompress"
            :compressHint="compressHint"
            @compress="emit('compress')"
          />

          <button
            v-if="loading"
            class="stop-btn"
            @click="emit('stop')"
          >
            <SvgIcon name="close" :size="14" />
          </button>

          <button
            v-else
            class="send-btn"
            :disabled="!canSend"
            @click="handleSubmit"
            title="发送"
          >
            <SvgIcon name="send" :size="16" />
          </button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import SvgIcon from '../SvgIcon.vue'
import ContextPanel from './ContextPanel.vue'

const props = defineProps({
  compact: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  compressing: { type: Boolean, default: false },
  placeholder: { type: String, default: '描述你的旅行想法，例如：帮我做一个东京5天旅行计划...' },
  hasMessages: { type: Boolean, default: false },
  modelValue: { type: String, default: 'agent' },
  arenaMode: { type: Boolean, default: false },
  selectedModel: { type: String, default: 'qwen3.6-plus' },
  canCompress: { type: Boolean, default: true },
  compressHint: { type: String, default: '' },
  tokenStatus: {
    type: Object,
    default: () => null,
  },
  initialQuery: { type: String, default: '' },
})

const emit = defineEmits(['submit', 'update:modelValue', 'update:selectedModel', 'stop', 'toggleArena', 'compress'])

const mode = ref(props.modelValue)
const modelName = ref(props.selectedModel)

watch(() => props.modelValue, (v) => { mode.value = v })
watch(() => props.selectedModel, (v) => { modelName.value = v })

const modes = [
  { value: 'agent', label: 'Agent' },
  { value: 'plan', label: 'Plan' },
  { value: 'reflection', label: 'Reflection' },
]

const models = [
  { value: 'qwen3.6-plus', label: 'Qwen 3.6 Plus' },
  { value: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash' },
  { value: 'kimi-k2.6', label: 'Kimi K2.6' },
  { value: 'MiniMax-M2.5', label: 'MiniMax M2.5' },
  { value: 'glm-5.1', label: 'GLM 5.1' },
]

const tags = ['3天短途', '5天深度游', '美食为主', '亲子游', '情侣出行']
const localQuery = ref('')
const file = ref(null)
const textareaRef = ref(null)

const canSend = computed(() => Boolean(localQuery.value.trim()) && !props.loading)

const tokenPercent = computed(() => {
  const ratio = props.tokenStatus?.utilization || 0
  return Math.round(ratio * 100)
})

const tokenLevel = computed(() => {
  const ratio = props.tokenStatus?.utilization || 0
  if (ratio >= 0.85) return 'danger'
  if (ratio >= 0.65) return 'warning'
  return 'safe'
})

watch(() => props.initialQuery, (value) => {
  if (!value || props.loading) return
  if (!localQuery.value.trim()) {
    localQuery.value = value
    nextTick(autoResize)
  }
}, { immediate: true })

function appendTag(tag) {
  if (!localQuery.value.includes(tag)) {
    localQuery.value = localQuery.value.trim() + (localQuery.value ? '，' : '') + tag
  }
  textareaRef.value?.focus()
}

function onFileChange(e) {
  file.value = e.target.files?.[0] || null
}

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 200) + 'px'
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
/* ── Wrapper ── */
.chat-input-wrapper {
  width: 100%;
}

.chat-input-wrapper.centered {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
}

.chat-input-wrapper.centered .chat-input-box {
  max-width: 680px;
  width: 100%;
}

/* ── Suggestions row (empty state) ── */
.suggestions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 680px;
  width: 100%;
}

.suggestion-chip {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: 20px;
  background: var(--color-card);
  color: var(--color-secondary);
  font-size: 13px;
  font-family: var(--font-family);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.suggestion-chip:hover {
  border-color: var(--color-red);
  color: var(--color-red);
  background: #fff7f8;
}

/* ── Input box ── */
.chat-input-box {
  position: relative;
  border: 1.5px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-card);
  overflow: visible;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.chat-input-box:focus-within {
  border-color: var(--color-red);
  box-shadow: 0 0 0 4px rgba(255, 36, 66, 0.06);
}

/* ── Textarea ── */
.chat-textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  padding: 14px 16px 6px;
  font-size: 15px;
  font-family: var(--font-family);
  background: transparent;
  color: var(--color-title);
  line-height: 1.55;
}

.chat-textarea::placeholder {
  color: #9ca3af;
}

.compact .chat-textarea {
  padding: 10px 14px 4px;
  font-size: 14px;
}

/* ── Toolbar ── */
.chat-input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px 10px;
  gap: 8px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ── Tool button ── */
.tool-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: var(--color-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.tool-btn:hover {
  background: var(--color-surface);
  color: var(--color-title);
}

.file-chip {
  padding: 3px 10px;
  border-radius: 6px;
  background: rgba(255, 36, 66, 0.06);
  color: var(--color-red);
  font-size: 12px;
  font-weight: 600;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Token pill ── */
.token-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: transparent;
  font-size: 11px;
  font-weight: 700;
  color: var(--color-secondary);
  cursor: pointer;
  font-family: var(--font-family);
  transition: all 0.15s;
}

.token-pill:hover {
  border-color: var(--color-red);
}

.token-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
}

.token-pill.warning .token-dot { background: #f59e0b; }
.token-pill.danger .token-dot { background: var(--color-red); }
.token-pill.danger { color: var(--color-red); border-color: rgba(255, 36, 66, 0.3); }

/* ── Chip selects (mode / model) ── */
.mode-select-wrap,
.model-chip {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.chip-select {
  padding: 4px 22px 4px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 12px;
  font-family: var(--font-family);
  color: var(--color-secondary);
  background: transparent;
  cursor: pointer;
  outline: none;
  appearance: none;
  -webkit-appearance: none;
  transition: all 0.15s;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='10' viewBox='0 0 24 24' fill='none' stroke='%23999' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 5px center;
}

.chip-select:hover {
  border-color: var(--color-red);
  color: var(--color-title);
}

.chip-select:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

/* ── Arena chip ── */
.arena-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: transparent;
  color: var(--color-muted);
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.arena-chip:hover {
  border-color: var(--color-red);
  color: var(--color-red);
}

.arena-chip.active {
  border-color: rgba(255, 36, 66, 0.3);
  background: rgba(255, 36, 66, 0.06);
  color: var(--color-red);
}

/* ── Model chip (kept for wrapper, select uses .chip-select above) ── */
.model-chip {
  position: relative;
  display: inline-flex;
  align-items: center;
}

/* ── Send button ── */
.send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 10px;
  background: var(--color-red);
  color: #fff;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s;
}

.send-btn:hover:not(:disabled) {
  filter: brightness(1.1);
  transform: scale(1.04);
}

.send-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

/* ── Stop button ── */
.stop-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1.5px solid rgba(255, 36, 66, 0.25);
  border-radius: 10px;
  background: rgba(255, 36, 66, 0.08);
  color: var(--color-red);
  cursor: pointer;
  transition: all 0.15s;
}

.stop-btn:hover {
  background: rgba(255, 36, 66, 0.16);
}

</style>
