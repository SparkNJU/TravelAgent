<template>
  <div class="app-shell">
    <aside class="left-rail">
      <div class="brand-block">
        <div class="brand-mark">TA</div>
        <div class="brand-text">
          <span class="brand-name">TripAgent</span>
          <span class="brand-tag">EVALUATE</span>
        </div>
      </div>

      <div class="rail-search" role="search">
        <span class="rail-search-icon" aria-hidden="true">⌕</span>
        <input type="text" placeholder="Search" readonly aria-label="搜索 (占位)" />
        <span class="rail-search-kbd">⌘K</span>
      </div>

      <p class="rail-group-title">评测中心</p>
      <nav class="rail-nav">
        <RouterLink to="/overview" class="rail-item">
          <span class="rail-icon" aria-hidden="true">⌂</span>
          <span class="rail-label">首页</span>
        </RouterLink>
        <RouterLink to="/tasks" class="rail-item">
          <span class="rail-icon" aria-hidden="true">▤</span>
          <span class="rail-label">评测任务</span>
        </RouterLink>
        <RouterLink to="/history" class="rail-item">
          <span class="rail-icon" aria-hidden="true">⟲</span>
          <span class="rail-label">评测历史</span>
        </RouterLink>
        <RouterLink to="/strategies" class="rail-item">
          <span class="rail-icon" aria-hidden="true">⚙</span>
          <span class="rail-label">AI 评测配置</span>
        </RouterLink>
        <RouterLink to="/models" class="rail-item">
          <span class="rail-icon" aria-hidden="true">◎</span>
          <span class="rail-label">模型管理</span>
        </RouterLink>
        <RouterLink to="/datasets" class="rail-item">
          <span class="rail-icon" aria-hidden="true">⛁</span>
          <span class="rail-label">数据集管理</span>
        </RouterLink>
      </nav>

      <div class="rail-user-block" aria-label="当前用户">
        <span class="rail-user-avatar">P</span>
        <div class="rail-user-meta">
          <span class="rail-user-name">Personal</span>
          <span class="rail-user-sub">tripagent@local</span>
        </div>
      </div>
    </aside>

    <main class="workspace">
      <RouterView />
    </main>

    <CreateTaskModal v-model:open="createModalOpen" :initial-preset="createModalPreset" @created="onTaskCreated" />
    <HelpDocPanel v-if="showHelpDoc" @close="showHelpDoc = false" />
  </div>
</template>

<script setup lang="ts">
import { provide, ref, watch } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';

import CreateTaskModal from './components/CreateTaskModal.vue';
import HelpDocPanel from './components/HelpDocPanel.vue';

const route = useRoute();
const router = useRouter();

const createModalOpen = ref(false);
const createModalPreset = ref<string | null>(null);
const showHelpDoc = ref(false);

function openCreateTask(preset?: string): void {
  createModalPreset.value = preset ?? null;
  createModalOpen.value = true;
}

function openHelpDoc(): void {
  showHelpDoc.value = true;
}

function onTaskCreated(): void {
  window.dispatchEvent(new Event('tripagent-tasks-reload'));
}

provide('openCreateTask', openCreateTask);
provide('openHelpDoc', openHelpDoc);

watch(
  () => route.query,
  (q) => {
    if (q.create === '1') {
      createModalPreset.value = typeof q.preset === 'string' ? q.preset : null;
      createModalOpen.value = true;
      router.replace({ path: route.path, query: { ...q, create: undefined, preset: undefined } });
    }
  },
  { immediate: true },
);
</script>

<style>
.rail-item.router-link-active {
  color: var(--brand);
  border-color: var(--brand);
  background: var(--brand-faint);
  font-weight: 600;
}

.rail-item.router-link-active .rail-icon {
  color: var(--brand);
}
</style>
