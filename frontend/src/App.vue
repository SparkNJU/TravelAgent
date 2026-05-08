<script setup>
import { ref, provide } from 'vue'
import { RouterView } from 'vue-router'
import Sidebar from './components/Sidebar.vue'
import LoginModal from './components/LoginModal.vue'

const loginModalVisible = ref(false)

const showLoginModal = () => {
  loginModalVisible.value = true
}
provide('showLoginModal', showLoginModal)

const closeLoginModal = () => {
  loginModalVisible.value = false
}
</script>

<template>
  <div class="app-layout">
    <Sidebar />
    <main class="main-area">
      <RouterView />
    </main>
  </div>
  <LoginModal v-if="loginModalVisible" @close="closeLoginModal" @success="closeLoginModal" />
</template>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.main-area {
  flex: 1;
  margin-left: 72px;
  height: 100vh;
  overflow-y: auto;
  overscroll-behavior: none;
}
</style>
