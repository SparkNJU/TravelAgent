<template>
  <div class="app-shell">
    <aside class="left-rail">
      <header class="rail-brand">
        <span class="brand-name">TripAgent_Evaluator</span>
      </header>

      <p class="rail-section-title">评测中心</p>
      <nav class="rail-nav" aria-label="评测导航">
        <RouterLink to="/overview" class="rail-item">
          <span class="rail-icon" aria-hidden="true">⌂</span>
          <span class="rail-label">首页</span>
        </RouterLink>
        <RouterLink to="/tasks" class="rail-item">
          <span class="rail-icon" aria-hidden="true">▤</span>
          <span class="rail-label">评测任务</span>
        </RouterLink>
        <RouterLink to="/history" class="rail-item">
          <span class="rail-icon" aria-hidden="true">↻</span>
          <span class="rail-label">评测历史</span>
        </RouterLink>
        <RouterLink to="/strategies" class="rail-item">
          <span class="rail-icon" aria-hidden="true">⚙</span>
          <span class="rail-label">AI 评测配置</span>
        </RouterLink>
        <RouterLink to="/models" class="rail-item">
          <span class="rail-icon" aria-hidden="true">⊙</span>
          <span class="rail-label">模型管理</span>
        </RouterLink>
        <RouterLink to="/datasets" class="rail-item">
          <span class="rail-icon" aria-hidden="true">▦</span>
          <span class="rail-label">数据集管理</span>
        </RouterLink>
      </nav>

      <footer class="rail-footer">
        <div class="rail-user-block" aria-label="当前用户">
          <span class="rail-user-avatar">P</span>
          <div class="rail-user-meta">
            <span class="rail-user-name">个人空间</span>
            <span class="rail-user-sub">tripagent@local</span>
          </div>
        </div>
      </footer>
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
  border-color: #ff6b73;
  background: #fff0f1;
  font-weight: 700;
}

.rail-item.router-link-active .rail-icon {
  color: var(--brand);
}
</style>
