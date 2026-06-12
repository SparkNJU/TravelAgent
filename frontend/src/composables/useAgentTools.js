import { computed, ref, watch } from 'vue'

const WEB_SEARCH_KEY = 'travel_agent_web_search_enabled'

function readStoredWebSearch() {
  if (typeof window === 'undefined') return false
  return window.localStorage.getItem(WEB_SEARCH_KEY) === 'true'
}

const activeToolDrawer = ref('')
const webSearchEnabled = ref(readStoredWebSearch())
const skillsSummary = ref({ total: 0, enabled: 0 })
const memoriesSummary = ref({ total: 0, enabled: 0 })
let persistenceBound = false

function openToolDrawer(type) {
  activeToolDrawer.value = type
}

function closeToolDrawer() {
  activeToolDrawer.value = ''
}

function toggleWebSearch() {
  webSearchEnabled.value = !webSearchEnabled.value
}

function setWebSearchEnabled(value) {
  webSearchEnabled.value = Boolean(value)
}

function updateSkillsSummary(items = []) {
  skillsSummary.value = {
    total: items.length,
    enabled: items.filter((item) => item?.isEnabled).length,
  }
}

function updateMemoriesSummary(items = []) {
  memoriesSummary.value = {
    total: items.length,
    enabled: items.filter((item) => item?.isEnabled).length,
  }
}

function ensurePersistence() {
  if (persistenceBound || typeof window === 'undefined') return
  persistenceBound = true
  watch(webSearchEnabled, (value) => {
    window.localStorage.setItem(WEB_SEARCH_KEY, value ? 'true' : 'false')
  })
}

export function useAgentTools() {
  ensurePersistence()
  return {
    activeToolDrawer,
    drawerOpen: computed(() => Boolean(activeToolDrawer.value)),
    webSearchEnabled,
    skillsSummary,
    memoriesSummary,
    openToolDrawer,
    closeToolDrawer,
    toggleWebSearch,
    setWebSearchEnabled,
    updateSkillsSummary,
    updateMemoriesSummary,
  }
}
