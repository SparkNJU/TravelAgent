<template>
  <div class="suggestions-container">
    <div class="suggestions-label">你可能还想问：</div>
    <div class="suggestions-list">
      <button
        v-for="(q, i) in questions"
        :key="i"
        class="suggestion-chip"
        @click="$emit('select', q)"
      >
        {{ q }}
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  questions: { type: Array, default: () => [] },
})

defineEmits(['select'])
</script>

<style scoped>
.suggestions-container {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-card);
  padding: 12px 14px;
}

.suggestions-label {
  font-size: 12px;
  color: var(--color-muted);
  margin-bottom: 8px;
}

.suggestions-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.suggestion-chip {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  color: var(--color-body);
  font-size: 13px;
  font-family: var(--font-family);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.suggestion-chip:hover {
  border-color: var(--color-red);
  color: var(--color-red-light);
  background: rgba(230, 57, 70, 0.06);
}
</style>
