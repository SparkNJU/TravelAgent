<template>
  <div class="home-view">
    <HomeGlobeHero
      @start-plan="startPlan"
      @open-explore="openExplore"
    />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import HomeGlobeHero from '../components/HomeGlobeHero.vue'

const router = useRouter()

function planFromCity(city) {
  const tags = city.tags?.length ? `，偏好${city.tags.join('、')}` : ''
  const spots = city.spots?.length ? `，重点包含${city.spots.slice(0, 3).join('、')}` : ''
  router.push({
    path: '/ai-plan',
    query: {
      q: `帮我规划${city.name}${city.days || '3天'}旅行${tags}${spots}`,
      auto: '1',
    },
  })
}

function startPlan(query) {
  if (!query) {
    router.push('/ai-plan')
    return
  }
  router.push({
    path: '/ai-plan',
    query: {
      q: query,
      auto: '1',
    },
  })
}

function openExplore(channel) {
  router.push({
    path: '/discover',
    query: channel && channel !== 'all' ? { channel } : {},
  })
}
</script>

<style scoped>
.home-view {
  min-height: 100%;
  background: #ffffff;
}
</style>
