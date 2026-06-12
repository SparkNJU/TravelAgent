<script setup>
import { computed, ref, provide } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppNavigation from './components/AppNavigation.vue'
import AgentToolDrawer from './components/AgentToolDrawer.vue'
import LoginModal from './components/LoginModal.vue'

const loginModalVisible = ref(false)
const route = useRoute()
const showSidebar = computed(() => route.path !== '/')

const showLoginModal = () => {
  loginModalVisible.value = true
}
provide('showLoginModal', showLoginModal)

const closeLoginModal = () => {
  loginModalVisible.value = false
}
</script>

<template>
  <div :class="['app-layout', { 'home-layout': !showSidebar }]">
    <AppNavigation v-if="showSidebar" />
    <main :class="['main-area', { 'home-main': !showSidebar }]">
      <RouterView />
    </main>
  </div>
  <AgentToolDrawer v-if="showSidebar" />
  <LoginModal v-if="loginModalVisible" @close="closeLoginModal" @success="closeLoginModal" />
</template>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: row;
  width: 100vw;
  max-width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg);
}

.main-area {
  flex: 0 0 calc(100vw - var(--sidebar-width));
  min-width: 0;
  width: calc(100vw - var(--sidebar-width));
  max-width: calc(100vw - var(--sidebar-width));
  min-height: 0;
  height: 100vh;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: none;
}

.main-area :deep(> *) {
  max-width: 100%;
}

@media (max-width: 860px) {
  .main-area {
    flex-basis: calc(100vw - var(--sidebar-width-compact));
    width: calc(100vw - var(--sidebar-width-compact));
    max-width: calc(100vw - var(--sidebar-width-compact));
  }
}

.main-area.home-main {
  flex-basis: 100vw;
  width: 100vw;
  max-width: 100vw;
}
</style>
