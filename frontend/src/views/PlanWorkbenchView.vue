<template>
  <div class="plan-workbench">
    <!-- Loading overlay -->
    <div class="loading-overlay" v-if="loading">
      <div class="loading-card">
        <div class="spinner-premium"></div>
        <h2>正在构建可视化行程工作台</h2>
        <div class="loading-steps">
          <div class="step" :class="stepStatus(1)">
            <span class="step-icon">✔</span>
            <span class="step-text">提取对话文本行程大纲</span>
          </div>
          <div class="step" :class="stepStatus(2)">
            <span class="step-icon" v-if="currentStep > 2">✔</span>
            <span class="step-spinner" v-else-if="currentStep === 2"></span>
            <span class="step-text">调用高德 API 匹配地理坐标</span>
          </div>
          <div class="step" :class="stepStatus(3)">
            <span class="step-icon" v-if="currentStep > 3">✔</span>
            <span class="step-spinner" v-else-if="currentStep === 3"></span>
            <span class="step-text">结构化入库及数据同步</span>
          </div>
        </div>
        <p class="loading-hint">首次解析可能需要 5-10 秒，请稍后...</p>
      </div>
    </div>

    <!-- Top Bar -->
    <div class="top-bar">
      <div class="bar-left">
        <button class="back-btn" @click="goBack">← 返回对话</button>
        <div class="title-section" v-if="plan">
          <div class="plan-switcher" @click.stop="togglePlanDropdown">
            <input
              v-model="plan.title"
              class="title-input"
              :style="{ width: titleInputWidth }"
              placeholder="输入行程标题"
              @change="triggerAutoSave"
              readonly
            />
            <span class="switcher-arrow" :class="{ open: planDropdownOpen }">▾</span>
            <div class="plan-dropdown" :class="{ visible: planDropdownOpen }">
              <div
                v-for="item in planList"
                :key="item.planId"
                class="plan-dropdown-item"
                :class="{ active: item.planId === plan.planId }"
                @click="switchPlan(item)"
              >
                <span class="dropdown-title">{{ item.title || '未命名行程' }}</span>
                <span class="dropdown-meta">{{ item.destinationName }} · {{ item.days }}天</span>
              </div>
              <div v-if="!planList.length" class="plan-dropdown-empty">暂无规划记录</div>
            </div>
          </div>
          <span class="destination-badge">{{ planDestination }} · {{ plan.days }}天</span>
        </div>
      </div>
      <div class="bar-right">
        <div class="sync-status" :class="syncState">
          <span class="status-dot"></span>
          <span class="status-text">{{ syncText }}</span>
        </div>
      </div>
    </div>

    <!-- Day Axis -->
    <div class="day-axis" v-if="plan">
      <div class="axis-inner">
        <button
          v-for="day in planDays"
          :key="day"
          class="axis-day-chip"
          :class="{ active: selectedDay === day }"
          :style="{ '--chip-color': dayColor(day) }"
          @click="selectDay(day)"
        >
          <span class="chip-dot" :style="{ background: dayColor(day) }"></span>
          <span class="chip-num">D{{ day }}</span>
          <span class="chip-label">第{{ day }}天</span>
          <span class="chip-count">{{ getActivitiesByDay(day).length }}项</span>
        </button>
        <button class="axis-add-btn" @click="addNewDay" title="增加一天">
          <span>+ 增加一天</span>
        </button>
      </div>
    </div>

    <!-- Main workspace: map + right editor -->
    <div class="workspace-body" v-if="plan">
      <!-- Fullscreen map -->
      <div class="map-area">
        <MapComponent
          :destinations="[planDestination]"
          :itinerary="mapItinerary"
          :selectedDay="selectedDay"
          :pickingMode="pickingMode"
          :showLocationPanel="false"
          :showLocationDetail="false"
          @mapClick="onMapClick"
          @markerClick="onMarkerClick"
          @expandEdit="onExpandEdit"
        />
      </div>

      <!-- Right editor panel -->
      <div v-show="selectedDay !== null" class="right-editor">
          <div class="editor-header">
            <div class="editor-header-nav">
              <button class="day-nav-btn" :disabled="selectedDay <= 1" @click="selectDay(selectedDay - 1)" title="前一天">‹</button>
              <h3>第 {{ selectedDay }} 天</h3>
              <button class="day-nav-btn" :disabled="selectedDay >= plan.days" @click="selectDay(selectedDay + 1)" title="后一天">›</button>
            </div>
            <div class="editor-header-actions">
              <button v-if="plan.days > 1" class="icon-btn danger" @click="removeSelectedDay" title="删除本天">🗑</button>
              <button class="icon-btn" @click="addActivity(selectedDay)" title="添加活动">+</button>
              <button class="icon-btn close-btn" @click="closeEditor" title="关闭">✕</button>
            </div>
          </div>

          <div class="editor-body">

            <!-- Activity cards -->
            <div
              v-for="(act, idx) in getActivitiesByDay(selectedDay)"
              :key="act.id || idx"
              :ref="el => setActivityCardRef(act.id, el)"
              class="activity-edit-card"
              :class="{
                highlighted: isSameActivity(highlightedActivityId, act.id),
                picking: isSameActivity(pickingActivityId, act.id),
                'drag-ghost': dragSourceId === act.id,
              }"
            >
              <div class="card-header">
                <span class="activity-index">{{ idx + 1 }}</span>
                <div class="card-header-inputs">
                  <input
                    v-model="act.activityTime"
                    class="field-time"
                    placeholder="时间"
                    @input="triggerAutoSave"
                  />
                  <input
                    v-model="act.locationName"
                    class="field-location"
                    placeholder="地点名称"
                    @change="handleLocationChange(act)"
                  />
                </div>
                <div class="card-move-actions">
                  <span
                    class="drag-handle"
                    @mousedown.stop.prevent="onDragStart($event, act, idx)"
                    title="拖动排序"
                  >☰</span>
                  <button class="mini-btn danger" @click="deleteActivity(act)" title="删除">✕</button>
                </div>
              </div>

              <textarea
                v-model="act.description"
                class="field-desc"
                rows="2"
                placeholder="活动描述..."
                @input="triggerAutoSave"
              ></textarea>

              <div class="card-footer">
                <div class="field-group tips-group">
                  <span class="field-label">💡</span>
                  <input
                    v-model="act.tips"
                    class="field-tips"
                    placeholder="小贴士/注意事项"
                    @input="triggerAutoSave"
                  />
                  <button
                    v-if="(act.tips || '').length > 15"
                    class="tips-toggle"
                    @click.stop="toggleTips(act.id)"
                  >查看</button>
                  <div v-if="isTipsExpanded(act.id)" class="tips-popup" @click.stop>
                    <div class="tips-popup-content">{{ act.tips }}</div>
                    <button class="tips-popup-close" @click="toggleTips(act.id)">✕</button>
                  </div>
                </div>
                <div class="field-group">
                  <span class="field-label">¥</span>
                  <input
                    type="number"
                    v-model.number="act.cost"
                    class="field-cost"
                    placeholder="费用"
                    @input="triggerAutoSave"
                  />
                </div>
                <div class="coords-row" v-if="act.latitude || act.longitude || true">
                  <span v-if="act.latitude || act.longitude" class="coords-readout">
                    {{ Number(act.latitude).toFixed(4) }}, {{ Number(act.longitude).toFixed(4) }}
                  </span>
                  <button
                    v-if="!isSameActivity(pickingActivityId, act.id)"
                    class="pick-location-btn"
                    @click="startPicking(act.id)"
                  >重选位置</button>
                  <button
                    v-else
                    class="pick-location-btn cancel"
                    @click="cancelPicking"
                  >✕ 取消标点</button>
                </div>
              </div>
            </div>

            <!-- Empty state for a day with no activities -->
            <div v-if="getActivitiesByDay(selectedDay).length === 0" class="empty-day">
              <p>当天暂无行程安排</p>
            </div>

            <!-- Add activity at the bottom -->
            <button class="add-activity-btn" @click="addActivity(selectedDay)">
              + 添加条目
            </button>
          </div>
        </div>

        <!-- Drag ghost (follows mouse) -->
        <div
          v-if="dragSourceId && dragCardRect"
          class="drag-ghost-float"
          :style="{
            left: dragPos.x + 'px',
            top: dragPos.y + 'px',
            width: dragCardRect.width + 'px',
          }"
        >
          <span class="ghost-index">☰</span>
          <span class="ghost-text">{{ getActivityById(dragSourceId)?.locationName || '拖动中...' }}</span>
        </div>

      <!-- Placeholder when no day selected -->
      <div v-show="selectedDay === null" class="no-selection-hint">
        <span>👆 点击上方日轴或地图标记以编辑行程</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MapComponent from '../components/MapComponent.vue'

const route = useRoute()
const router = useRouter()

// Loading state
const loading = ref(true)
const currentStep = ref(1)

// Data state
const plan = ref(null)
const activities = ref([])
const selectedDay = ref(null)
const highlightedActivityId = ref(null)
const activityCardRefs = new Map()

// Picking mode state
const pickingActivityId = ref(null)
const pickingMode = computed(() => pickingActivityId.value !== null)

// Tips expand state
const expandedTips = ref(new Set())
function isTipsExpanded(id) { return expandedTips.value.has(id) }
function toggleTips(id) {
  if (expandedTips.value.has(id)) expandedTips.value.delete(id)
  else expandedTips.value.add(id)
}

// Plan switcher
const planList = ref([])
const planDropdownOpen = ref(false)

// Auto-save state
const syncState = ref('saved')
const syncText = ref('所有修改已自动保存')
let autoSaveTimer = null

// Computed
// Day color palette — must match MapComponent
const DAY_COLORS = ['#ff2442', '#10b981', '#f59e0b', '#ef4444', '#0891b2', '#f97316', '#ec4899', '#84cc16']
function dayColor(day) {
  return DAY_COLORS[(day - 1) % DAY_COLORS.length]
}

const planDays = computed(() => {
  if (!plan.value) return []
  const days = []
  for (let i = 1; i <= plan.value.days; i++) days.push(i)
  return days
})

const mapItinerary = computed(() => {
  const daysMap = {}
  activities.value.forEach(act => {
    const d = act.dayNumber || 1
    if (!daysMap[d]) daysMap[d] = { day: d, activities: [] }
    daysMap[d].activities.push({
      id: act.id,
      location: act.locationName,
      time: act.activityTime,
      description: act.description,
      coordinates: [Number(act.latitude), Number(act.longitude)]
    })
  })
  return Object.values(daysMap).sort((a, b) => a.day - b.day)
})

const planDestination = computed(() => plan.value?.destinationName || plan.value?.destination || '')

const titleInputWidth = computed(() => {
  const len = (plan.value?.title || '').length
  return Math.min(Math.max(len * 20 + 40, 120), 500) + 'px'
})

// Helpers
function stepStatus(step) {
  if (currentStep.value > step) return 'completed'
  if (currentStep.value === step) return 'active'
  return 'pending'
}

function queryValue(value) {
  return Array.isArray(value) ? value[0] : value
}

function isSameActivity(a, b) {
  return a != null && b != null && String(a) === String(b)
}

// Plan switcher
async function togglePlanDropdown() {
  if (planDropdownOpen.value) {
    planDropdownOpen.value = false
    return
  }
  // Fetch data first if needed
  if (planList.value.length === 0) {
    try {
      const uid = localStorage.getItem('userId') || '1'
      const res = await fetch(`/api/travel/plans/user/${uid}`)
      const data = await res.json()
      if (data.code === 200) planList.value = data.data || []
    } catch {}
  }
  planDropdownOpen.value = true
}

function switchPlan(item) {
  planDropdownOpen.value = false
  router.push({ name: 'planWorkbench', query: { planId: item.planId } })
}

function setActivityCardRef(id, el) {
  if (id == null) return
  const key = String(id)
  if (el) {
    activityCardRefs.set(key, el)
  } else {
    activityCardRefs.delete(key)
  }
}

async function focusActivityCard(id) {
  if (id == null) return
  await nextTick()
  const el = activityCardRefs.get(String(id))
  if (el?.scrollIntoView) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

function getActivitiesByDay(day) {
  return activities.value.filter(a => a.dayNumber === day)
}

function getActivityById(id) {
  return activities.value.find(a => a.id === id)
}

// Day selection
function selectDay(day) {
  if (selectedDay.value === day) {
    closeEditor()
  } else {
    cancelPicking()
    selectedDay.value = day
    highlightedActivityId.value = null
  }
}

function closeEditor() {
  selectedDay.value = null
  highlightedActivityId.value = null
  cancelPicking()
}

// Picking mode
function startPicking(activityId) {
  pickingActivityId.value = activityId
  highlightedActivityId.value = activityId
}

function cancelPicking() {
  pickingActivityId.value = null
}

async function onMapClick({ lng, lat }) {
  if (!pickingActivityId.value) return
  const act = activities.value.find(a => isSameActivity(a.id, pickingActivityId.value))
  if (!act) return

  act.latitude = lat
  act.longitude = lng
  highlightedActivityId.value = act.id

  // Reverse geocode to get address name
  try {
    const res = await fetch(`/api/map/regeocode?lng=${lng}&lat=${lat}`)
    const data = await res.json()
    if (data.code === 200 && data.data) {
      const addr = data.data.address || data.data.formatted_address || ''
      if (addr) {
        act.locationName = addr
      }
    }
  } catch (e) {
    console.error('逆地理编码失败:', e)
  }

  triggerAutoSave()
  cancelPicking()
}

function onMarkerClick(location) {
  if (pickingActivityId.value) return

  const activityId = location?.activityId
  const act = activities.value.find(a => isSameActivity(a.id, activityId))
  const day = act?.dayNumber || Number(location?.day)

  if (day && !Number.isNaN(day)) {
    selectedDay.value = day
  }
  highlightedActivityId.value = act?.id || activityId || null
  focusActivityCard(act?.id || activityId)
}

function onExpandEdit(day) {
  if (day === null) {
    selectedDay.value = null
  } else {
    selectedDay.value = day || 1
  }
}

function onKeydown(e) {
  if (e.key === 'Escape' && pickingActivityId.value) {
    cancelPicking()
  }
}

// Navigation
function goBack() {
  const conversationId = queryValue(route.query.c)
  if (conversationId) {
    router.push({ name: 'aiPlan', query: { c: conversationId } })
  } else {
    router.push({ name: 'aiPlan' })
  }
}

// Activity CRUD
function addNewDay() {
  if (!plan.value) return
  plan.value.days += 1
  triggerAutoSave()
}

function removeSelectedDay() {
  const day = selectedDay.value
  if (!plan.value || plan.value.days <= 1 || day === null) return
  if (!confirm(`确认要删除第 ${day} 天的所有行程吗？`)) return
  activities.value = activities.value.filter(a => a.dayNumber !== day)
  // Re-number days above the removed one
  activities.value.forEach(a => { if (a.dayNumber > day) a.dayNumber -= 1 })
  plan.value.days -= 1
  selectedDay.value = null
  triggerAutoSave()
}

function addActivity(day) {
  const newAct = {
    id: 'temp-' + Date.now() + Math.random().toString(36).slice(2, 6),
    dayNumber: day,
    activityTime: '09:00',
    locationName: '新地点',
    latitude: 39.9042,
    longitude: 116.4074,
    description: '新增活动描述',
    tips: '',
    cost: 0
  }
  activities.value.push(newAct)
  nextTick(() => triggerAutoSave())
}

function deleteActivity(act) {
  activities.value = activities.value.filter(a => a.id !== act.id)
  triggerAutoSave()
}

function moveActivity(act, direction) {
  const dayActs = getActivitiesByDay(act.dayNumber)
  const idx = dayActs.findIndex(a => a.id === act.id)
  if (idx === -1) return
  const targetIdx = idx + direction
  if (targetIdx < 0 || targetIdx >= dayActs.length) return

  const globalIdx = activities.value.findIndex(a => a.id === act.id)
  const globalTargetIdx = activities.value.findIndex(a => a.id === dayActs[targetIdx].id)
  if (globalIdx !== -1 && globalTargetIdx !== -1) {
    const temp = activities.value[globalIdx]
    activities.value[globalIdx] = activities.value[globalTargetIdx]
    activities.value[globalTargetIdx] = temp
    triggerAutoSave()
  }
}

// Drag and drop (mouse-based, fixed position follow)
const dragSourceId = ref(null)
const dragPos = ref({ x: 0, y: 0 })
const dragCardRect = ref(null)
let dragOffsetY = 0
let dragDayNumber = 1
let dropTargetIdx = -1

function onDragStart(e, act, idx) {
  dragSourceId.value = act.id
  dragDayNumber = act.dayNumber
  const cardEl = activityCardRefs.get(String(act.id))
  if (cardEl) {
    const rect = cardEl.getBoundingClientRect()
    dragCardRect.value = { width: rect.width, height: rect.height }
    dragOffsetY = e.clientY - rect.top
    dragPos.value = { x: rect.left, y: rect.top }
  }
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
}

function onDragMove(e) {
  if (!dragSourceId.value) return
  dragPos.value = { x: dragPos.value.x, y: e.clientY - dragOffsetY }

  // Find the closest card by midpoint distance, with edge detection
  const dayActs = getActivitiesByDay(dragDayNumber)
  const srcIdx = dayActs.findIndex(a => a.id === dragSourceId.value)
  dropTargetIdx = -1
  let minDist = Infinity

  // Get first and last card bounds for edge detection
  const firstEl = activityCardRefs.get(String(dayActs[0]?.id))
  const lastEl = activityCardRefs.get(String(dayActs[dayActs.length - 1]?.id))

  if (firstEl && e.clientY < firstEl.getBoundingClientRect().top) {
    // Above first card → target is first
    dropTargetIdx = 0
  } else if (lastEl && e.clientY > lastEl.getBoundingClientRect().bottom) {
    // Below last card → target is last
    dropTargetIdx = dayActs.length - 1
  } else {
    // Find closest by midpoint
    for (let i = 0; i < dayActs.length; i++) {
      if (i === srcIdx) continue
      const el = activityCardRefs.get(String(dayActs[i].id))
      if (!el) continue
      const rect = el.getBoundingClientRect()
      const mid = rect.top + rect.height / 2
      const dist = Math.abs(e.clientY - mid)
      if (dist < minDist) {
        minDist = dist
        dropTargetIdx = i
      }
    }
  }
}

function onDragEnd() {
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)

  if (dragSourceId.value && dropTargetIdx >= 0) {
    const dayActs = getActivitiesByDay(dragDayNumber)
    const srcIdx = dayActs.findIndex(a => a.id === dragSourceId.value)
    if (srcIdx !== -1 && srcIdx !== dropTargetIdx) {
      const srcGlobal = activities.value.findIndex(a => a.id === dragSourceId.value)
      const tgtGlobal = activities.value.findIndex(a => a.id === dayActs[dropTargetIdx].id)
      if (srcGlobal !== -1 && tgtGlobal !== -1) {
        const [removed] = activities.value.splice(srcGlobal, 1)
        activities.value.splice(tgtGlobal, 0, removed)
        triggerAutoSave()
      }
    }
  }

  dragSourceId.value = null
  dragCardRect.value = null
  dragPos.value = { x: 0, y: 0 }
  dropTargetIdx = -1
}

// Geocode on location change
async function handleLocationChange(act) {
  if (!act.locationName.trim()) return
  try {
    syncState.value = 'saving'
    syncText.value = `正在查询 ${act.locationName} 的坐标...`
    const response = await fetch(`/api/map/geocode?address=${encodeURIComponent(act.locationName)}`)
    const data = await response.json()
    if (data.code === 200 && data.data?.geocodes?.length > 0) {
      const loc = data.data.geocodes[0].location
      const [lng, lat] = loc.split(',').map(Number)
      act.longitude = lng
      act.latitude = lat
    }
  } catch (e) {
    console.error('Geocoding failed for', act.locationName, e)
  } finally {
    triggerAutoSave()
  }
}

// Auto save
function triggerAutoSave() {
  syncState.value = 'saving'
  syncText.value = '正在自动保存...'
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(performSave, 1500)
}

async function performSave() {
  if (!plan.value) return
  try {
      const payload = {
        title: plan.value.title,
        destination: planDestination.value,
      days: plan.value.days,
      budget: plan.value.estimatedBudget || 1000,
      activities: activities.value.map(a => {
        const item = {
          dayNumber: a.dayNumber,
          activityTime: a.activityTime,
          locationName: a.locationName,
          latitude: a.latitude,
          longitude: a.longitude,
          description: a.description,
          tips: a.tips,
          cost: a.cost || 0
        }
        // Include id for existing activities so backend updates instead of inserting
        if (a.id && !String(a.id).startsWith('temp-')) {
          item.id = a.id
        }
        return item
      })
    }
    const response = await fetch(`/api/travel/plan/${plan.value.planId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    const data = await response.json()
    if (data.code === 200) {
      syncState.value = 'saved'
      syncText.value = '所有修改已自动保存'
      if (data.data?.activities) {
        activities.value = data.data.activities
      }
    } else {
      syncState.value = 'error'
      syncText.value = '自动保存失败：' + (data.message || '服务器错误')
    }
  } catch (e) {
    syncState.value = 'error'
    syncText.value = '自动保存失败，请检查网络连接'
  }
}

// Initialization
async function initWorkspace() {
  const conversationId = queryValue(route.query.c)
  const planId = queryValue(route.query.planId)

  if (planId) {
    try {
      const res = await fetch(`/api/travel/plan/${planId}`)
      const data = await res.json()
      if (data.code === 200 && data.data) {
        plan.value = data.data
        activities.value = data.data.activities || []
        loading.value = false
        if (activities.value.length > 0) selectedDay.value = 1
      } else {
        alert('无法加载该行程计划')
        goBack()
      }
    } catch (e) {
      alert('加载行程出错，请检查网络')
      goBack()
    }
  } else if (conversationId) {
    try {
      currentStep.value = 1
      setTimeout(() => { currentStep.value = 2 }, 1500)
      setTimeout(() => { currentStep.value = 3 }, 3500)

      const res = await fetch('/api/travel/plan/parse-and-save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ conversationId })
      })
      const data = await res.json()

      if (data.code === 200 && data.data) {
        currentStep.value = 4
        setTimeout(() => {
          plan.value = data.data
          activities.value = data.data.activities || []
          loading.value = false
          if (activities.value.length > 0) selectedDay.value = 1
          router.replace({
            name: 'planWorkbench',
            query: {
              planId: data.data.planId,
              ...(conversationId ? { c: conversationId } : {})
            }
          })
        }, 800)
      } else {
        alert('行程大纲提取失败: ' + (data.message || '暂不支持解析该对话'))
        goBack()
      }
    } catch (e) {
      alert('解析行程出错: ' + e.message)
      goBack()
    }
  } else {
    // Check if there is an active conversation with a plan already generated
    const localConvsRaw = localStorage.getItem('travel_conversations')
    let latestBackendId = null
    if (localConvsRaw) {
      try {
        const localConvs = JSON.parse(localConvsRaw)
        const latest = localConvs.find(c => c.result && c.backendId)
        if (latest) {
          latestBackendId = latest.backendId
        }
      } catch (e) {}
    }

    if (latestBackendId) {
      router.replace({ name: 'planWorkbench', query: { c: latestBackendId } })
      initWorkspace()
      return
    }

    // Try to fetch the latest plan of the user from the backend
    try {
      const uid = localStorage.getItem('userId') || '1'
      const res = await fetch(`/api/travel/plans/user/${uid}`)
      const data = await res.json()
      if (data.code === 200 && data.data && data.data.length > 0) {
        const latestPlan = data.data[0]
        router.replace({
          name: 'planWorkbench',
          query: { planId: latestPlan.planId }
        })
        plan.value = latestPlan
        activities.value = latestPlan.activities || []
        loading.value = false
        if (activities.value.length > 0) selectedDay.value = 1
      } else {
        alert('您当前还没有任何行程规划记录，请先到 AI规划 页面生成！')
        goBack()
      }
    } catch (e) {
      goBack()
    }
  }
}

watch(() => route.query.planId, (newPlanId) => {
  if (newPlanId) {
    loading.value = true
    selectedDay.value = null
    planDropdownOpen.value = false
    initWorkspace()
  }
})

function onGlobalClick(e) {
  if (planDropdownOpen.value && !e.target.closest('.plan-switcher')) {
    planDropdownOpen.value = false
  }
  if (!e.target.closest('.tips-group')) {
    expandedTips.value.clear()
  }
}

onMounted(() => {
  initWorkspace()
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('click', onGlobalClick)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('click', onGlobalClick)
})
</script>

<style scoped>
/* === Base === */
.plan-workbench {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  overflow: hidden;
  background-color: #f5f5f7;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  color: #1f2937;
}

/* === Top Bar === */
.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  height: 52px;
  background: white;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
  z-index: 20;
}
.bar-left { display: flex; align-items: center; gap: 16px; }
.bar-right { display: flex; align-items: center; }
.back-btn {
  background: #f3f4f6; border: 1px solid #e5e7eb;
  color: #4b5563; padding: 6px 14px; border-radius: 18px;
  font-size: 13px; font-weight: 500; cursor: pointer;
  transition: all 0.2s; white-space: nowrap;
}
.back-btn:hover { background: #e5e7eb; color: #1f2937; }
.title-section { display: flex; align-items: center; gap: 10px; }
.title-input {
  background: transparent; border: none; font-size: 20px; font-weight: 700;
  color: #1f2937; padding: 6px 10px; border-radius: 6px;
  min-width: 120px; max-width: 500px; width: auto;
  transition: background 0.2s;
}
.title-input:hover, .title-input:focus { background: #f3f4f6; outline: none; }
.destination-badge {
  background: #fef3c7; color: #92400e; padding: 3px 10px;
  border-radius: 10px; font-size: 12px; font-weight: 600; white-space: nowrap;
}

/* === Plan Switcher === */
.plan-switcher { position: relative; }
.plan-switcher .title-input { cursor: pointer; padding-right: 24px; }
.switcher-arrow {
  position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
  font-size: 12px; color: #9ca3af; pointer-events: none;
  transition: transform 0.2s;
}
.switcher-arrow.open { transform: translateY(-50%) rotate(180deg); }
.plan-dropdown {
  position: absolute; top: calc(100% + 6px); left: 0;
  z-index: 9999;
  min-width: 260px; max-height: 240px; overflow-y: auto;
  background: white; border: 1px solid #e5e7eb; border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.15);
  opacity: 0; visibility: hidden; pointer-events: none;
  transition: opacity 0.15s, visibility 0.15s;
}
.plan-dropdown.visible {
  opacity: 1; visibility: visible; pointer-events: auto;
}
@keyframes dropdownIn {
  from { opacity: 0; transform: translateY(-6px); }
  to { opacity: 1; transform: translateY(0); }
}
.plan-dropdown-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; cursor: pointer; transition: background 0.15s;
  border-bottom: 1px solid #f3f4f6;
}
.plan-dropdown-item:last-child { border-bottom: none; }
.plan-dropdown-item:hover { background: #f9fafb; }
.plan-dropdown-item.active { background: #eff6ff; }
.dropdown-title { font-size: 13px; font-weight: 600; color: #1f2937; }
.dropdown-meta { font-size: 11px; color: #9ca3af; }
.plan-dropdown-empty { padding: 16px; text-align: center; color: #9ca3af; font-size: 14px; }

/* === Sync Status === */
.sync-status { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #9ca3af; }
.sync-status .status-dot { width: 6px; height: 6px; border-radius: 50%; transition: background 0.3s; }
.sync-status.saved .status-dot { background: #10b981; }
.sync-status.saving .status-dot { background: #f59e0b; animation: pulse 1s infinite alternate; }
.sync-status.error .status-dot { background: #ef4444; }
.sync-status.error { color: #ef4444; }

@keyframes pulse { from { opacity: 0.4; } to { opacity: 1; } }

/* === Day Axis === */
.day-axis {
  height: 48px;
  border-bottom: 1px solid #e5e7eb;
  background: white;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  z-index: 10;
  padding: 0 16px;
}
.axis-inner {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  width: 100%;
}
.axis-day-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 20px;
  border: 1px solid #e5e7eb;
  background: white;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  font-size: 14px;
  color: #6b7280;
}
.axis-day-chip:hover { border-color: var(--chip-color, #a5b4fc); color: var(--chip-color, #4f46e5); }
.axis-day-chip.active {
  background: var(--chip-color, #ff2442);
  color: white;
  border-color: transparent;
}
.axis-day-chip.active .chip-count { color: rgba(255,255,255,0.7); }
.chip-dot {
  width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0;
  border: 1.5px solid rgba(255,255,255,0.6);
}
.chip-num { font-weight: 700; font-size: 14px; }
.chip-label { font-weight: 500; font-size: 14px; }
.chip-count { font-size: 12px; color: #9ca3af; }
.axis-add-btn {
  background: transparent; border: 1px dashed #d1d5db;
  color: #9ca3af; padding: 6px 14px; border-radius: 20px;
  font-size: 12px; cursor: pointer; transition: all 0.2s; white-space: nowrap;
}
.axis-add-btn:hover { border-color: #ff2442; color: #ff2442; }

/* === Workspace Body === */
.workspace-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
}

/* === Map Area === */
.map-area {
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow: hidden;
  position: relative;
}

/* === No Selection Hint === */
.no-selection-hint {
  position: absolute;
  right: 440px;
  bottom: 24px;
  background: rgba(255,255,255,0.95);
  backdrop-filter: blur(8px);
  padding: 10px 20px;
  border-radius: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
  font-size: 13px;
  color: #6b7280;
  z-index: 5;
  pointer-events: none;
}

/* === Right Editor Panel === */
.right-editor {
  width: 420px;
  min-width: 420px;
  flex-shrink: 0;
  background: white;
  border-left: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  height: 100%;
  box-shadow: -2px 0 8px rgba(0,0,0,0.04);
  z-index: 10;
  overflow: hidden;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.editor-header-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}
.editor-header h3 {
  margin: 0; font-size: 18px; font-weight: 700; color: #1f2937;
  min-width: 80px;
  text-align: center;
}
.day-nav-btn {
  width: 28px; height: 28px; border-radius: 6px;
  border: 1px solid #e5e7eb; background: white;
  cursor: pointer; display: flex; align-items: center;
  justify-content: center; font-size: 18px; color: #6b7280;
  font-weight: 700; transition: all 0.15s;
}
.day-nav-btn:hover:not(:disabled) { background: #f3f4f6; color: #1f2937; }
.day-nav-btn:disabled { opacity: 0.3; cursor: default; }
.editor-header-actions { display: flex; gap: 6px; }
.icon-btn {
  width: 32px; height: 32px; border-radius: 6px;
  border: 1px solid #e5e7eb; background: white;
  cursor: pointer; display: flex; align-items: center;
  justify-content: center; font-size: 18px; color: #6b7280;
  transition: all 0.15s;
}
.icon-btn:hover { background: #f3f4f6; color: #1f2937; }
.icon-btn.danger { color: #ef4444; }
.icon-btn.danger:hover { background: #fee2e2; border-color: #fecaca; }
.close-btn:hover { background: #fee2e2; color: #ef4444; border-color: #fecaca; }

.editor-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.editor-day-actions { text-align: right; }
.text-btn.danger {
  background: transparent; border: none; color: #ef4444;
  font-size: 12px; cursor: pointer; padding: 4px 8px;
}
.text-btn.danger:hover { text-decoration: underline; }

/* === Activity Edit Card === */
.activity-edit-card {
  background: #fafafa;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.activity-edit-card:focus-within {
  border-color: #ff2442;
  box-shadow: 0 0 0 3px rgba(255,36,66,0.08);
}
.activity-edit-card.highlighted {
  border-color: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245,158,11,0.12);
}
.activity-edit-card.picking {
  border-color: #ff2442;
  box-shadow: 0 0 0 3px rgba(255,36,66,0.15);
  animation: pickingGlow 1.2s ease-in-out infinite alternate;
}
.activity-edit-card.drag-ghost {
  opacity: 0.3;
  border-style: dashed;
}
.drag-ghost-float {
  position: fixed; z-index: 9999; pointer-events: none;
  display: flex; align-items: center; gap: 10px;
  padding: 12px 14px; background: white;
  border: 2px solid #6366f1; border-radius: 10px;
  box-shadow: 0 12px 32px rgba(0,0,0,0.2);
  opacity: 0.9; font-size: 14px;
}
.ghost-index { font-size: 16px; color: #6366f1; }
.ghost-text { font-weight: 600; color: #1f2937; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
@keyframes pickingGlow {
  from { box-shadow: 0 0 0 3px rgba(255,36,66,0.15); }
  to { box-shadow: 0 0 0 6px rgba(255,36,66,0.08); }
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.activity-index {
  width: 24px; height: 24px; border-radius: 50%;
  background: linear-gradient(135deg, #ff2442, #ff5f73);
  color: white; display: flex; align-items: center;
  justify-content: center; font-size: 13px; font-weight: 700;
  flex-shrink: 0;
}
.card-header-inputs { flex: 1; display: flex; gap: 4px; min-width: 0; }
.field-time {
  width: 100px; flex-shrink: 0; border: none; background: white; padding: 6px 4px;
  border-radius: 6px; font-size: 14px; font-weight: 600; color: #1f2937; text-align: center;
}
.field-location {
  flex: 1; min-width: 0; border: none; background: white; padding: 6px 8px;
  border-radius: 6px; font-size: 14px; font-weight: 600; color: #1f2937;
}
.field-time:focus, .field-location:focus { outline: none; box-shadow: 0 0 0 2px rgba(255,36,66,0.2); }

.card-move-actions { display: flex; gap: 3px; align-items: center; }
.drag-handle {
  cursor: grab; font-size: 16px; color: #9ca3af; padding: 2px 4px;
  user-select: none; transition: color 0.15s;
}
.drag-handle:hover { color: #6b7280; }
.drag-handle:active { cursor: grabbing; }
.drag-handle.dragging { opacity: 0.5; }
.mini-btn {
  width: 24px; height: 24px; border-radius: 4px;
  border: 1px solid #e5e7eb; background: white;
  cursor: pointer; font-size: 10px; display: flex;
  align-items: center; justify-content: center; color: #6b7280;
  transition: all 0.15s;
}
.mini-btn:hover:not(:disabled) { background: #f3f4f6; color: #1f2937; }
.mini-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.mini-btn.danger { color: #ef4444; }
.mini-btn.danger:hover { background: #fee2e2; }

.field-desc {
  width: 100%; border: 1px solid #e5e7eb; border-radius: 6px;
  padding: 8px 10px; font-size: 14px; color: #4b5563;
  background: white; resize: vertical; font-family: inherit;
}
.field-desc:focus { outline: none; border-color: #a5b4fc; }

.card-footer {
  display: flex; flex-wrap: wrap; gap: 8px; align-items: center;
}
.field-group {
  display: flex; align-items: center; gap: 4px;
  background: white; border-radius: 6px; padding: 4px 8px; flex: 1;
  min-width: 0; border: 1px solid #f3f4f6;
}
.field-label { font-size: 13px; color: #9ca3af; flex-shrink: 0; }
.field-tips, .field-cost {
  background: transparent; border: none; font-size: 13px;
  color: #6b7280; width: 100%; min-width: 0;
}
.field-tips:focus, .field-cost:focus { outline: none; }
.tips-group { position: relative; }
.tips-toggle {
  flex-shrink: 0; background: none; border: none; color: #ff2442;
  font-size: 11px; cursor: pointer; padding: 0 4px; font-weight: 500;
}
.tips-toggle:hover { text-decoration: underline; }
.tips-popup {
  position: absolute; top: calc(100% + 6px); left: 0; z-index: 100;
  min-width: 200px; max-width: 280px; padding: 10px 12px;
  background: #1f2937; color: #f9fafb; border-radius: 8px;
  font-size: 13px; line-height: 1.6; white-space: normal;
  box-shadow: 0 4px 16px rgba(0,0,0,0.2);
  animation: popupIn 0.15s ease-out;
}
@keyframes popupIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}
.tips-popup-content { word-break: break-all; }
.tips-popup-close {
  position: absolute; top: 4px; right: 6px;
  background: none; border: none; color: #9ca3af; cursor: pointer;
  font-size: 12px; padding: 2px;
}
.tips-popup-close:hover { color: white; }
.coords-row {
  display: flex; align-items: center; gap: 8px; width: 100%;
}
.coords-readout { font-size: 11px; color: #9ca3af; white-space: nowrap; flex: 1; }
.pick-location-btn {
  background: transparent; border: 1px solid #d1d5db; color: #ff2442;
  padding: 3px 12px; border-radius: 10px; font-size: 12px; font-weight: 500;
  cursor: pointer; transition: all 0.15s; white-space: nowrap; flex-shrink: 0;
}
.pick-location-btn:hover { background: #fff1f3; border-color: #ff2442; }
.pick-location-btn.cancel { color: #ef4444; border-color: #fca5a5; }
.pick-location-btn.cancel:hover { background: #fef2f2; }

/* === Empty Day === */
.empty-day {
  text-align: center; padding: 32px 0; color: #d1d5db; font-size: 13px;
}

/* === Add Activity Button === */
.add-activity-btn {
  background: transparent; border: 1px dashed #d1d5db; color: #6b7280;
  padding: 10px; border-radius: 8px; font-size: 13px; cursor: pointer;
  transition: all 0.2s; text-align: center; width: 100%;
}
.add-activity-btn:hover { border-color: #ff2442; color: #ff2442; background: rgba(255,36,66,0.04); }

/* === Loading Overlay (unchanged) === */
.loading-overlay {
  position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
  background: rgba(255,255,255,0.85); backdrop-filter: blur(10px);
  z-index: 9999; display: flex; align-items: center; justify-content: center;
}
.loading-card {
  background: white; padding: 40px; border-radius: 20px;
  box-shadow: 0 20px 40px rgba(0,0,0,0.1); text-align: center;
  max-width: 450px; width: 90%; display: flex;
  flex-direction: column; align-items: center;
}
.spinner-premium {
  width: 48px; height: 48px; border: 4px solid rgba(255,36,66,0.1);
  border-top-color: #ff2442; border-radius: 50%;
  animation: spin 1s infinite linear; margin-bottom: 24px;
}
@keyframes spin { to { transform: rotate(360deg); } }
.loading-card h2 { font-size: 20px; font-weight: 700; margin-bottom: 24px; color: #1f2937; }
.loading-steps { display: flex; flex-direction: column; gap: 16px; width: 100%; text-align: left; margin-bottom: 30px; }
.step { display: flex; align-items: center; gap: 12px; font-size: 14px; color: #9ca3af; }
.step-icon {
  width: 20px; height: 20px; border-radius: 50%; background: #10b981;
  color: white; display: flex; align-items: center; justify-content: center; font-size: 11px;
}
.step-spinner {
  width: 16px; height: 16px; border: 2px solid rgba(255,36,66,0.1);
  border-top-color: #ff2442; border-radius: 50%; animation: spin 1s infinite linear;
}
.step.completed { color: #10b981; font-weight: 500; }
.step.active { color: #ff2442; font-weight: 600; }
.step.pending { color: #d1d5db; }
.loading-hint { font-size: 12px; color: #9ca3af; margin: 0; }

/* === Responsive === */
@media (max-width: 900px) {
  .right-editor {
    position: fixed; bottom: 0; left: 0; right: 0;
    width: 100%; height: 60vh; border-top-left-radius: 16px;
    border-top-right-radius: 16px; border-left: none; border-top: 1px solid #e5e7eb;
  }
  .no-selection-hint { right: 16px; bottom: 16px; }
  .title-input { width: 160px; }
}
</style>
