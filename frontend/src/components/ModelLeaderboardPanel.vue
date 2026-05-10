<template>
  <section class="leaderboard-card">
    <div class="leaderboard-header">
      <div>
        <h3>模型排行榜</h3>
      </div>
      <button class="refresh-btn" :disabled="loading" @click="$emit('refresh')">
        <SvgIcon name="refresh" :size="14" />
      </button>
    </div>

    <div v-if="loading" class="leaderboard-loading">加载中...</div>
    <div v-else-if="!entries.length" class="leaderboard-empty">暂无数据</div>
    <div v-else class="leaderboard-list">
      <div v-for="(item, idx) in entries.slice(0, 5)" :key="item.model" class="leaderboard-item">
        <div class="rank">{{ idx + 1 }}</div>
        <div class="model">
          <div class="model-name">{{ item.model }}</div>
          <div class="model-meta">胜 {{ item.wins }} · 负 {{ item.losses }} · 平 {{ item.ties }}</div>
        </div>
        <div class="score">{{ Math.round(item.score) }}</div>
      </div>
    </div>
  </section>
</template>

<script setup>
import SvgIcon from './SvgIcon.vue'

defineProps({
  entries: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

defineEmits(['refresh'])
</script>

<style scoped>
.leaderboard-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  padding: 16px;
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.leaderboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.leaderboard-header h3 {
  margin: 0 0 4px;
  font-size: 16px;
  color: var(--color-title);
}

.leaderboard-header p {
  margin: 0;
  font-size: 12px;
  color: var(--color-muted);
}

.refresh-btn {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.refresh-btn:hover {
  border-color: var(--color-red-light);
  color: var(--color-title);
}

.refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.leaderboard-loading,
.leaderboard-empty {
  font-size: 13px;
  color: var(--color-muted);
  padding: 8px 4px;
}

.leaderboard-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.leaderboard-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  background: var(--color-surface);
}

.rank {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: rgba(230, 57, 70, 0.12);
  color: var(--color-red-light);
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.model {
  flex: 1;
  min-width: 0;
}

.model-name {
  font-size: 13px;
  color: var(--color-title);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.model-meta {
  font-size: 11px;
  color: var(--color-muted);
}

.score {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-title);
  flex-shrink: 0;
}
</style>
