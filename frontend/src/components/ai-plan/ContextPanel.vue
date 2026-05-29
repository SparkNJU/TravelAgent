<template>
  <div 
    class="context-wrapper"
    :class="healthClass">
    <!-- icon button -->
    <button
      class="context-indicator"
      @click="toggleOpen"
      :title="tooltipText"
    >
      <svg viewBox="0 0 36 36">
        <path
          class="track"
          d="M18 2
            a 16 16 0 0 1 0 32
            a 16 16 0 0 1 0 -32"
        />

        <path
          class="progress"
          :stroke-dasharray="
            percent + ', 100'
          "
          d="M18 2
            a 16 16 0 0 1 0 32
            a 16 16 0 0 1 0 -32"
        />
      </svg>

      <div class="dot" />

      <span class="ring-percent" aria-hidden="true">
        {{ percent }}%
      </span>
    </button>

    <!-- popup -->
    <Transition name="context-pop">
      <div
        v-if="open"
        class="context-popup"
      >
        <div class="popup-header">
          <div class="title">
            上下文窗口
          </div>

          <div class="percent">
            {{ percent }}%
          </div>
        </div>

        <div class="status-row">
          <span class="status-pill" :class="statusClass">
            {{ statusLabel }}
          </span>
          <span class="status-hint">
            {{ warningText || '状态良好' }}
          </span>
        </div>

        <div class="progress-bar">
          <div
            class="progress-fill"
            :style="{ width: percent + '%' }"
          />
        </div>

        <div class="token-main">
          <span class="token-amount">
            {{ formatToken(tokenStatus?.input_tokens) }}
          </span>
          <span class="token-divider">/</span>
          <span class="token-total">
            {{ formatToken(tokenStatus?.max_context_tokens) }}
          </span>
          <span class="token-label">Tokens</span>
        </div>

        <div class="stats">
          <div class="stat-card">
            <span class="stat-label">历史对话</span>
            <span class="stat-value">{{ formatToken(tokenStatus?.history_tokens) }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">输出预算</span>
            <span class="stat-value">{{ formatToken(tokenStatus?.output_budget) }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">当前占用</span>
            <span class="stat-value">{{ percent }}%</span>
          </div>
        </div>

        <button
          class="compress-btn"
          :disabled="loading || compressing"
          @click="compress"
        >
          <span v-if="compressing" class="compress-spinner" aria-hidden="true" />
          <span>{{ compressing ? '正在压缩中...' : '立即压缩对话' }}</span>
        </button>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import {
  ref,
  computed,
} from 'vue'
import SvgIcon from '../SvgIcon.vue'

const props = defineProps({
  tokenStatus: Object,
  loading: Boolean,
  compressing: Boolean,
})

const emit = defineEmits([
  'compress',
])

const open = ref(false)

function toggleOpen() {
  open.value = !open.value
}

function compress() {
  emit('compress')
}

const percent = computed(() =>
  Math.round(
    (
      props.tokenStatus
        ?.utilization || 0
    ) * 100
  )
)

const warningText = computed(() => {
  const ratio =
    props.tokenStatus
      ?.utilization || 0

  if (ratio > 0.85) {
    return '上下文接近上限，建议压缩'
  }

  if (ratio > 0.65) {
    return '上下文逐渐变长'
  }

  return ''
})

const statusLabel = computed(() => {
  const ratio = props.tokenStatus?.utilization || 0
  if (ratio > 0.85) return '高占用'
  if (ratio > 0.65) return '偏高'
  return '健康'
})

const statusClass = computed(() => {
  const ratio = props.tokenStatus?.utilization || 0
  if (ratio > 0.85) return 'status-danger'
  if (ratio > 0.65) return 'status-warning'
  return 'status-safe'
})

const healthClass = computed(() => {
  const r =
    props.tokenStatus
      ?.utilization || 0

  if (r > 0.85)
    return 'danger'

  if (r > 0.65)
    return 'warning'

  return 'safe'
})

const tooltipText = computed(() => {
  return `上下文占用 ${percent.value}%`
})

function formatToken(v) {
  if (!v) return '0'

  if (v >= 1000) {
    return (
      (v / 1000).toFixed(1)
      + 'K'
    )
  }

  return String(v)
}
</script>

<style scoped>
.context-wrapper {
  position: relative;
}

.context-indicator {
  position: relative;
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: transparent;
  box-shadow: none;
}

.context-indicator svg {
  width: 24px;
  height: 24px;
  transform: rotate(-90deg);
}

.ring-percent {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 9px;
  font-weight: 700;
  color: var(--color-title);
  opacity: 0;
  transform: scale(0.96);
  transition: opacity 0.15s ease, transform 0.15s ease;
  pointer-events: none;
  letter-spacing: 0.1px;
}

.context-indicator:hover .ring-percent,
.context-indicator:focus-visible .ring-percent {
  opacity: 1;
  transform: scale(1);
}

.track {
  fill: none;
  stroke: rgba(17, 24, 39, 0.14);
  stroke-width: 2.8;
}

.progress {
  fill: none;
  stroke: currentColor;
  stroke-width: 2.8;
  stroke-linecap: round;
  transition: stroke-dasharray .3s ease, stroke .3s ease;
}

.dot {
  position: absolute;
  display: none;
}

.context-wrapper.safe {
  color: #111111;
}

.context-wrapper.warning {
  color: #f59e0b;
}

.context-wrapper.danger {
  color: #ef4444;
}

.context-wrapper.safe .progress {
  stroke: #111111;
}

.context-wrapper.warning .progress {
  stroke: #f59e0b;
}

.context-wrapper.danger .progress {
  stroke: #ef4444;
}

:root[data-theme="dark"] .track {
  stroke: rgba(255, 255, 255, 0.16);
}

:root[data-theme="dark"] .context-indicator {
  background: transparent;
  box-shadow: none;
}

:root[data-theme="dark"] .context-popup {
  background:
    linear-gradient(180deg, rgba(30, 30, 30, 0.98), rgba(20, 20, 20, 0.96));
  border-color: rgba(255, 255, 255, 0.08);
  box-shadow:
    0 24px 60px rgba(0, 0, 0, 0.5),
    0 1px 0 rgba(255, 255, 255, 0.04) inset;
}

:root[data-theme="dark"] .context-popup::before {
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.96), rgba(245, 158, 11, 0.92), rgba(239, 68, 68, 0.96));
}

:root[data-theme="dark"] .context-wrapper.safe {
  color: #f8fafc;
}

:root[data-theme="dark"] .status-pill.status-safe {
  color: #f8fafc;
  background: rgba(248, 250, 252, 0.08);
  border-color: rgba(248, 250, 252, 0.16);
}

:root[data-theme="dark"] .context-wrapper.safe .progress {
  stroke: #f8fafc;
}

:root[data-theme="dark"] .context-wrapper.warning {
  color: #f59e0b;
}

:root[data-theme="dark"] .context-wrapper.warning .progress {
  stroke: #f59e0b;
}

:root[data-theme="dark"] .context-wrapper.danger {
  color: #ef4444;
}

:root[data-theme="dark"] .context-wrapper.danger .progress {
  stroke: #ef4444;
}

:root[data-theme="dark"] .progress-bar {
  background: rgba(255, 255, 255, 0.08);
}

:root[data-theme="dark"] .stat-card {
  background: linear-gradient(180deg, rgba(36, 36, 36, 0.98), rgba(26, 26, 26, 0.96));
  border-color: rgba(255, 255, 255, 0.08);
}

:root[data-theme="dark"] .title,
:root[data-theme="dark"] .percent,
:root[data-theme="dark"] .stat-value,
:root[data-theme="dark"] .token-amount {
  color: #f4f4f5;
}

:root[data-theme="dark"] .status-hint,
:root[data-theme="dark"] .token-divider,
:root[data-theme="dark"] .token-total,
:root[data-theme="dark"] .token-label,
:root[data-theme="dark"] .stat-label {
  color: #a1a1aa;
}

:root[data-theme="dark"] .percent {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.12);
}

:root[data-theme="dark"] .progress-fill {
  background: rgba(220, 38, 38, 0.28);
}

:root[data-theme="dark"] .compress-btn {
  box-shadow: 0 12px 28px rgba(239, 68, 68, 0.22);
}

:root[data-theme="dark"] .compress-btn:hover {
  box-shadow: 0 14px 30px rgba(239, 68, 68, 0.28);
}

:root[data-theme="dark"] .compress-btn:active {
  box-shadow: 0 10px 22px rgba(239, 68, 68, 0.22);
}

.context-popup {
  position: absolute;
  right: -8px;
  bottom: 52px;
  width: 300px;
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(229, 231, 235, 0.9);
  box-shadow:
    0 24px 60px rgba(15, 23, 42, 0.12),
    0 1px 0 rgba(255, 255, 255, 0.82) inset,
    0 0 0 1px rgba(255, 255, 255, 0.22) inset;
  backdrop-filter: blur(18px) saturate(160%);
}

.context-popup::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 3px;
  border-radius: 18px 18px 0 0;
  background: linear-gradient(90deg, rgba(17, 24, 39, 0.95), rgba(245, 158, 11, 0.88), rgba(239, 68, 68, 0.94));
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.title {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.2px;
  color: var(--color-title);
}

.percent {
  font-weight: 700;
  color: var(--color-title);
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.05);
  border: 1px solid rgba(17, 24, 39, 0.08);
}

.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.status-pill {
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
}

.status-pill.status-safe {
  color: #111111;
  background: rgba(17, 24, 39, 0.06);
  border-color: rgba(17, 24, 39, 0.12);
}

.status-pill.status-warning {
  color: #f59e0b;
  background: rgba(245, 158, 11, 0.1);
  border-color: rgba(245, 158, 11, 0.2);
}

.status-pill.status-danger {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.12);
  border-color: rgba(239, 68, 68, 0.24);
}

.status-hint {
  font-size: 12px;
  color: var(--color-secondary);
}

.progress-bar {
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(239, 68, 68, 0.07);
  margin-bottom: 14px;
}

.progress-fill {
  height: 100%;
  background: rgba(200, 50, 58, 0.2);
}

.token-main {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 14px;
}

.token-amount {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-title);
}

.token-divider {
  font-size: 14px;
  color: var(--color-secondary);
}

.token-total {
  font-size: 14px;
  color: var(--color-secondary);
}

.token-label {
  font-size: 11px;
  color: var(--color-hint);
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(249, 250, 251, 0.98));
  border: 1px solid rgba(229, 231, 235, 0.9);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
}

.stat-label {
  font-size: 11px;
  color: var(--color-secondary);
}

.stat-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-title);
}

.compress-btn {
  width: 100%;
  height: 40px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(180deg, #d9485f, #c93f55);
  color: #fff;
  cursor: pointer;
  font-weight: 600;
  letter-spacing: 0.2px;
  box-shadow: 0 10px 24px rgba(217, 72, 95, 0.18);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.compress-spinner {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  animation: compress-spin 0.8s linear infinite;
  flex-shrink: 0;
}

.compress-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(217, 72, 95, 0.22);
}

.compress-btn:active {
  transform: translateY(0);
  box-shadow: 0 8px 18px rgba(217, 72, 95, 0.16);
}

.compress-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
  box-shadow: none;
}

@keyframes compress-spin {
  to {
    transform: rotate(360deg);
  }
}

.context-pop-enter-active,
.context-pop-leave-active {
  transition: all .2s ease;
}

.context-pop-enter-from,
.context-pop-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(.98);
}
</style>