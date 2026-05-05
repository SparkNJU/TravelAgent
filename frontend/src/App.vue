<script setup>
import { RouterView } from 'vue-router'
import AppSidebar from './components/AppSidebar.vue'
</script>

<template>
  <div class="app-container">
    <AppSidebar />
    <main class="main-content">
      <RouterView />
    </main>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: #f8fafc;
}
</style>

<style scoped>
.app-container {
  min-height: 100vh;
  display: flex;
}

.main-content {
  flex: 1;
  margin-left: 220px;
  min-height: 100vh;
}
</style>