<template>
  <section class="arena-timeline" :class="{ compact }">
    <button class="timeline-header" @click="toggleAll">
      <span class="timeline-title">对比执行时间线</span>
      <span class="timeline-status" :class="loading ? 'running' : 'done'">
        {{ loading ? '进行中' : '已完成' }}
      </span>
      <span class="timeline-toggle">{{ collapsedAll ? '展开全部' : '折叠全部' }}</span>
    </button>

    <div class="timeline-body">
      <article
        v-for="(stage, index) in localStages"
        :key="stage.id || index"
        class="timeline-stage"
      >
        <div class="timeline-marker" :class="stage.status"></div>
        <div class="timeline-content">
          <button class="stage-head" @click="toggleStage(index)">
            <span class="stage-title">{{ stage.title }}</span>
            <span class="stage-time">{{ stage.time || '--:--:--' }}</span>
            <span class="stage-state" :class="stage.status">{{ statusLabel(stage.status) }}</span>
            <span class="stage-toggle">{{ stage.expanded ? '收起' : '展开' }}</span>
          </button>
          <div v-if="stage.expanded" class="stage-detail">
            <p>{{ stage.detail || '暂无阶段详情' }}</p>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  stages: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  compact: { type: Boolean, default: false },
})

const localStages = ref([])
const collapsedAll = ref(false)

watch(
  () => props.stages,
  (value) => {
    const previousExpandState = new Map(localStages.value.map((item) => [item.id, item.expanded]))
    localStages.value = (Array.isArray(value) ? value : []).map((stage, index) => ({
      ...stage,
      id: stage.id || `stage-${index}`,
      expanded: previousExpandState.has(stage.id) ? previousExpandState.get(stage.id) : !!stage.expanded,
    }))
    collapsedAll.value = localStages.value.length > 0 && localStages.value.every((item) => !item.expanded)
  },
  { immediate: true, deep: true },
)

function statusLabel(status) {
  if (status === 'running') return '执行中'
  if (status === 'done') return '完成'
  if (status === 'error') return '失败'
  return '等待中'
}

function toggleStage(index) {
  const target = localStages.value[index]
  if (!target) return
  target.expanded = !target.expanded
  collapsedAll.value = localStages.value.length > 0 && localStages.value.every((item) => !item.expanded)
}

function toggleAll() {
  const nextExpanded = collapsedAll.value
  localStages.value = localStages.value.map((item) => ({ ...item, expanded: nextExpanded }))
  collapsedAll.value = !nextExpanded
}
</script>

<style scoped>
.arena-timeline {
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-card);
  overflow: hidden;
}

.timeline-header {
  width: 100%;
  border: none;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  color: var(--color-body);
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  text-align: left;
}

.timeline-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-title);
}

.timeline-status {
  margin-left: auto;
  font-size: 11px;
  border-radius: var(--radius-pill);
  padding: 2px 8px;
  border: 1px solid transparent;
}

.timeline-status.running {
  color: #b45309;
  background: rgba(251, 191, 36, 0.16);
  border-color: rgba(251, 191, 36, 0.36);
}

.timeline-status.done {
  color: #166534;
  background: rgba(134, 239, 172, 0.2);
  border-color: rgba(134, 239, 172, 0.38);
}

.timeline-toggle {
  font-size: 11px;
  color: var(--color-muted);
}

.timeline-body {
  padding: 8px 10px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.timeline-stage {
  display: grid;
  grid-template-columns: 16px 1fr;
  gap: 8px;
}

.timeline-marker {
  width: 10px;
  height: 10px;
  margin-top: 9px;
  border-radius: 50%;
  border: 2px solid var(--color-border);
  background: transparent;
}

.timeline-marker.pending {
  border-color: var(--color-border);
}

.timeline-marker.running {
  border-color: rgba(251, 191, 36, 0.65);
  background: rgba(251, 191, 36, 0.75);
}

.timeline-marker.done {
  border-color: rgba(74, 222, 128, 0.6);
  background: rgba(74, 222, 128, 0.9);
}

.timeline-marker.error {
  border-color: rgba(248, 113, 113, 0.6);
  background: rgba(248, 113, 113, 0.9);
}

.timeline-content {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  overflow: hidden;
  background: var(--color-surface);
}

.stage-head {
  width: 100%;
  border: none;
  background: transparent;
  color: var(--color-body);
  padding: 8px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  text-align: left;
}

.stage-title {
  font-size: 12px;
  color: var(--color-title);
  font-weight: 600;
}

.stage-time {
  font-size: 11px;
  color: var(--color-muted);
}

.stage-state {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-muted);
}

.stage-state.running {
  color: #b45309;
}

.stage-state.done {
  color: #166534;
}

.stage-state.error {
  color: #b91c1c;
}

.stage-toggle {
  font-size: 11px;
  color: var(--color-secondary);
}

.stage-detail {
  border-top: 1px solid var(--color-border);
  padding: 8px 10px;
}

.stage-detail p {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-body);
  white-space: pre-wrap;
}

.arena-timeline.compact .timeline-header {
  padding: 8px 10px;
}

.arena-timeline.compact .timeline-body {
  padding: 6px 8px 8px;
}
</style>
