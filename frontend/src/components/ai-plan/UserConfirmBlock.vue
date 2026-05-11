<template>
  <div class="confirm-block">
    <div class="confirm-header">
      <SvgIcon name="message" :size="14" />
      <span class="confirm-label">需要确认信息</span>
      <span class="confirm-progress">{{ currentIndex + 1 }}/{{ questions.length }}</span>
    </div>
    <div v-if="message && currentIndex === 0" class="confirm-message">{{ message }}</div>

    <div class="question-panel">
      <div class="question-text">{{ currentQuestion.question }}</div>
      <div v-if="currentQuestion.options && currentQuestion.options.length" class="options-list">
        <label
          v-for="(opt, j) in currentQuestion.options"
          :key="j"
          :class="['option-item', { selected: answers[currentIndex] === opt }]"
        >
          <input
            type="radio"
            :name="`q_${currentIndex}`"
            :value="opt"
            v-model="answers[currentIndex]"
            class="option-radio"
          />
          <span class="option-label">{{ opt }}</span>
        </label>
      </div>
      <div class="custom-input-row">
        <span class="custom-input-label">其他回答：</span>
        <input
          type="text"
          v-model="otherTexts[currentIndex]"
          class="custom-input"
          placeholder="输入自定义回答..."
          @input="onCustomInput"
        />
      </div>
    </div>

    <div class="confirm-nav">
      <button
        v-if="currentIndex > 0"
        class="nav-btn prev-btn"
        @click="prevQuestion"
      >
        &lt; 上一题
      </button>
      <span v-else />
      <button
        v-if="currentIndex < questions.length - 1"
        class="nav-btn next-btn"
        :disabled="!canProceed"
        @click="nextQuestion"
      >
        下一题 &gt;
      </button>
      <button
        v-else
        class="nav-btn submit-btn"
        :disabled="!canProceed"
        @click="handleSubmit"
      >
        确认并发送
      </button>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import SvgIcon from '../SvgIcon.vue'

const props = defineProps({
  message: { type: String, default: '' },
  questions: { type: Array, default: () => [] },
})

const emit = defineEmits(['confirm'])

const currentIndex = ref(0)
const answers = reactive({})
const otherTexts = reactive({})

const currentQuestion = computed(() => props.questions[currentIndex.value] || { question: '', options: [] })

const canProceed = computed(() => {
  const other = otherTexts[currentIndex.value]
  if (other && other.trim().length > 0) return true
  return !!answers[currentIndex.value]
})

function onCustomInput() {
  if (otherTexts[currentIndex.value]?.trim().length > 0) {
    answers[currentIndex.value] = ''
  }
}

function prevQuestion() {
  if (currentIndex.value > 0) currentIndex.value--
}

function nextQuestion() {
  if (currentIndex.value < props.questions.length - 1) {
    currentIndex.value++
  }
}

function handleSubmit() {
  const result = props.questions.map((q, i) => {
    const other = otherTexts[i]?.trim()
    const answer = other || answers[i] || ''
    return { question: q.question, answer }
  })
  emit('confirm', { answers: result })
}
</script>

<style scoped>
.confirm-block {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-card);
}

.confirm-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-border);
  color: #3b82f6;
  font-size: 13px;
  font-weight: 500;
}

.confirm-label {
  color: var(--color-body);
  flex: 1;
}

.confirm-progress {
  font-size: 12px;
  color: var(--color-muted);
  font-weight: 400;
}

.confirm-message {
  padding: 10px 14px 0;
  font-size: 13px;
  color: var(--color-muted);
}

.question-panel {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.question-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-title);
  line-height: 1.5;
}

.options-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  transition: all 0.15s;
  font-size: 13px;
  color: var(--color-body);
}

.option-item:hover {
  border-color: var(--color-secondary);
  background: var(--color-surface);
}

.option-item.selected {
  border-color: var(--color-red);
  background: rgba(230, 57, 70, 0.06);
  color: var(--color-red-light);
}

.option-radio {
  display: none;
}

.option-label {
  flex: 1;
}

.custom-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.custom-input-label {
  font-size: 12px;
  color: var(--color-muted);
  white-space: nowrap;
}

.custom-input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 13px;
  font-family: var(--font-family);
  background: var(--color-surface);
  color: var(--color-title);
  outline: none;
  transition: border-color 0.15s;
}

.custom-input:focus {
  border-color: var(--color-red);
}

.confirm-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-top: 1px solid var(--color-border);
}

.nav-btn {
  padding: 6px 16px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 13px;
  font-family: var(--font-family);
  background: transparent;
  color: var(--color-body);
  cursor: pointer;
  transition: all 0.15s;
}

.nav-btn:hover:not(:disabled) {
  background: var(--color-surface);
  border-color: var(--color-secondary);
}

.nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.submit-btn {
  background: var(--gradient-brand);
  color: white;
  border: none;
}

.submit-btn:hover:not(:disabled) {
  filter: brightness(1.1);
}
</style>
