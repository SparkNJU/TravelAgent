<template>
  <div class="app-shell">
    <aside class="left-rail">
      <div class="brand-mark">TA</div>
      <nav class="rail-nav">
        <button
          class="rail-item"
          :class="{ active: activeSection === 'overview' }"
          @click="jumpTo('overview')"
        >概况</button>
        <button
          class="rail-item"
          :class="{ active: activeSection === 'tasks' }"
          @click="jumpTo('tasks')"
        >评测任务</button>
        <button
          class="rail-item"
          :class="{ active: activeSection === 'config' }"
          @click="jumpTo('config')"
        >AI评测配置</button>
        <button
          class="rail-item"
          :class="{ active: activeSection === 'detail' }"
          @click="jumpTo('detail')"
        >运行详情</button>
        <button
          class="rail-item"
          :class="{ active: activeSection === 'monitor' }"
          @click="jumpTo('monitor')"
        >样本监控</button>
      </nav>
    </aside>

    <main class="workspace">
      <header class="global-topbar">
        <div>
          <h1>TripAgent 评测控制台</h1>
          <p>任务一：任务管理、执行监控、指标聚合、策略配置</p>
        </div>
        <div class="topbar-actions">
          <button class="ghost" @click="openHelpDoc">帮助文档</button>
          <button class="primary" @click="openCreateTask">新建任务</button>
        </div>
      </header>

      <DashboardView ref="dashboardRef" />

        <HelpDocPanel v-if="showHelpDoc" @close="showHelpDoc = false" />
    </main>
  </div>
</template>

<script setup lang="ts">
  import { ref } from 'vue';

import DashboardView from './views/DashboardView.vue';
  import HelpDocPanel from './components/HelpDocPanel.vue';

type DashboardExpose = {
  scrollToSection: (section: 'overview' | 'tasks' | 'config' | 'detail' | 'monitor') => void;
  openCreateDialog: () => void;
  reloadDashboard: () => Promise<void>;
};

const dashboardRef = ref<DashboardExpose | null>(null);
const activeSection = ref<'overview' | 'tasks' | 'config' | 'detail' | 'monitor'>('tasks');
const showHelpDoc = ref(false);

function jumpTo(section: 'overview' | 'tasks' | 'config' | 'detail' | 'monitor'): void {
  activeSection.value = section;
  dashboardRef.value?.scrollToSection(section);
}

function openCreateTask(): void {
  activeSection.value = 'tasks';
  dashboardRef.value?.scrollToSection('tasks');
  dashboardRef.value?.openCreateDialog();
}

function openHelpDoc(): void {
  showHelpDoc.value = true;
}
</script>
