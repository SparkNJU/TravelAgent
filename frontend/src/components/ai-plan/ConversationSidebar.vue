<template>
  <div :class="['conv-sidebar', { collapsed }]">
    <div class="sidebar-header">
      <button class="collapse-btn" :title="collapsed ? '展开对话记录' : '折叠对话记录'" @click="$emit('toggle')">
        <SvgIcon :name="collapsed ? 'chevron-right' : 'menu'" :size="18" />
      </button>
      <span v-if="!collapsed" class="sidebar-title">对话记录</span>
      <button class="new-btn" @click="$emit('new')">
        <SvgIcon name="plus-circle" :size="18" />
      </button>
    </div>

    <div v-if="!collapsed" class="conv-list">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        :class="['conv-item', { active: conv.id === activeId }]"
        @click="$emit('select', conv.id)"
      >
        <div class="conv-info">
          <div class="conv-title">{{ conv.title }}</div>
          <div class="conv-time">{{ formatTime(conv.updatedAt) }}</div>
        </div>
        <button class="delete-btn" @click.stop="$emit('delete', conv.id)">
          <SvgIcon name="trash" :size="14" />
        </button>
      </div>

      <div v-if="!conversations.length" class="empty-state">
        <p>暂无对话</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import SvgIcon from '../SvgIcon.vue'

defineProps({
  conversations: { type: Array, default: () => [] },
  activeId: { type: String, default: null },
  collapsed: { type: Boolean, default: false },
})

defineEmits(['toggle', 'new', 'select', 'delete'])

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}
</script>

<style scoped>
.conv-sidebar {
  width: 260px;
  background: var(--color-card);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  height: 100%;
  transition: width 0.2s;
}

.conv-sidebar.collapsed {
  width: 48px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid var(--color-border);
}

.sidebar-title {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-title);
  white-space: nowrap;
}

.collapse-btn,
.new-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.collapse-btn:hover,
.new-btn:hover {
  background: var(--color-surface);
  color: var(--color-title);
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
  margin-bottom: 2px;
}

.conv-item:hover {
  background: #fff7f8;
}

.conv-item.active {
  background: #fff1f3;
  border-left: 3px solid var(--color-red);
  padding-left: 9px;
}

.conv-info {
  flex: 1;
  min-width: 0;
}

.conv-title {
  font-size: 13px;
  font-weight: 850;
  color: var(--color-title);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-time {
  font-size: 11px;
  color: var(--color-muted);
  margin-top: 2px;
}

.delete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-muted);
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s;
  flex-shrink: 0;
}

.conv-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  background: rgba(230, 57, 70, 0.1);
  color: var(--color-red-light);
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: var(--color-muted);
  font-size: 13px;
}

:root[data-theme="dark"] .conv-sidebar {
  background: rgba(16, 16, 18, 0.96);
  border-right-color: rgba(255, 255, 255, 0.08);
}

:root[data-theme="dark"] .sidebar-header {
  border-bottom-color: rgba(255, 255, 255, 0.08);
}

:root[data-theme="dark"] .collapse-btn:hover,
:root[data-theme="dark"] .new-btn:hover,
:root[data-theme="dark"] .conv-item:hover {
  background: var(--color-soft-red);
}

:root[data-theme="dark"] .conv-item.active {
  background: var(--color-soft-red);
}
</style>
