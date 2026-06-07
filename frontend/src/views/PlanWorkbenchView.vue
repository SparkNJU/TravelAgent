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
          <input
            v-model="plan.title"
            class="title-input"
            placeholder="输入行程标题"
            @change="triggerAutoSave"
          />
          <span class="destination-badge">{{ plan.destinationName }} · {{ plan.days }}天</span>
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
          :destinations="[plan.destinationName]"
          :itinerary="mapItinerary"
          :selectedDay="selectedDay"
          :pickingMode="pickingMode"
          @mapClick="onMapClick"
        />
      </div>

      <!-- Right editor panel -->
      <div v-show="selectedDay !== null" class="right-editor">
          <div class="editor-header">
            <h3>第 {{ selectedDay }} 天</h3>
            <div class="editor-header-actions">
              <button class="icon-btn" @click="addActivity(selectedDay)" title="添加活动">+</button>
              <button class="icon-btn close-btn" @click="closeEditor" title="关闭">✕</button>
            </div>
          </div>

          <div class="editor-body">
            <!-- Remove day button -->
            <div class="editor-day-actions" v-if="plan.days > 1">
              <button class="text-btn danger" @click="removeSelectedDay">
                🗑 删除本天所有行程
              </button>
            </div>

            <!-- Activity cards -->
            <div
              v-for="(act, idx) in getActivitiesByDay(selectedDay)"
              :key="act.id || idx"
              class="activity-edit-card"
              :class="{ highlighted: highlightedActivityId === act.id, picking: pickingActivityId === act.id }"
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
                  <button
                    class="mini-btn"
                    :disabled="idx === 0"
                    @click="moveActivity(act, -1)"
                    title="上移"
                  >▲</button>
                  <button
                    class="mini-btn"
                    :disabled="idx === getActivitiesByDay(selectedDay).length - 1"
                    @click="moveActivity(act, 1)"
                    title="下移"
                  >▼</button>
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
                <div class="field-group">
                  <span class="field-label">💡</span>
                  <input
                    v-model="act.tips"
                    class="field-tips"
                    placeholder="小贴士/注意事项"
                    @input="triggerAutoSave"
                  />
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
                    v-if="pickingActivityId !== act.id"
                    class="pick-location-btn"
                    @click="startPicking(act.id)"
                  >📍 重选位置</button>
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

      <!-- Placeholder when no day selected -->
      <div v-show="selectedDay === null" class="no-selection-hint">
        <span>👆 点击上方日轴或地图标记以编辑行程</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
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

// Picking mode state
const pickingActivityId = ref(null)
const pickingMode = computed(() => pickingActivityId.value !== null)

// Auto-save state
const syncState = ref('saved')
const syncText = ref('所有修改已自动保存')
let autoSaveTimer = null

// Computed
// Day color palette — must match MapComponent
const DAY_COLORS = ['#6366f1', '#10b981', '#f59e0b', '#f43f5e', '#06b6d4', '#8b5cf6', '#ec4899', '#84cc16']
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
      location: act.locationName,
      time: act.activityTime,
      description: act.description,
      coordinates: [Number(act.latitude), Number(act.longitude)]
    })
  })
  return Object.values(daysMap).sort((a, b) => a.day - b.day)
})

// Helpers
function stepStatus(step) {
  if (currentStep.value > step) return 'completed'
  if (currentStep.value === step) return 'active'
  return 'pending'
}

function getActivitiesByDay(day) {
  return activities.value.filter(a => a.dayNumber === day)
}

// Day selection
function selectDay(day) {
  if (selectedDay.value === day) {
    closeEditor()
  } else {
    selectedDay.value = day
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
}

function cancelPicking() {
  pickingActivityId.value = null
}

async function onMapClick({ lng, lat }) {
  if (!pickingActivityId.value) return
  const act = activities.value.find(a => a.id === pickingActivityId.value)
  if (!act) return

  act.latitude = lat
  act.longitude = lng

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

function onKeydown(e) {
  if (e.key === 'Escape' && pickingActivityId.value) {
    cancelPicking()
  }
}

// Navigation
function goBack() {
  router.push('/ai-plan')
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
      destination: plan.value.destinationName,
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
  const conversationId = route.query.c
  const planId = route.query.planId

  if (planId) {
    try {
      const res = await fetch(`/api/travel/plan/${planId}`)
      const data = await res.json()
      if (data.code === 200 && data.data) {
        plan.value = data.data
        activities.value = data.data.activities || []
        loading.value = false
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
          router.replace({ query: { planId: data.data.planId } })
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
    goBack()
  }
}

onMounted(() => {
  initWorkspace()
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
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
  background: transparent; border: none; font-size: 16px; font-weight: 700;
  color: #1f2937; padding: 4px 8px; border-radius: 4px; width: 260px;
  transition: background 0.2s;
}
.title-input:hover, .title-input:focus { background: #f3f4f6; outline: none; }
.destination-badge {
  background: #fef3c7; color: #92400e; padding: 3px 10px;
  border-radius: 10px; font-size: 12px; font-weight: 600; white-space: nowrap;
}

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
  padding: 6px 14px;
  border-radius: 20px;
  border: 1px solid #e5e7eb;
  background: white;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  font-size: 13px;
  color: #6b7280;
}
.axis-day-chip:hover { border-color: var(--chip-color, #a5b4fc); color: var(--chip-color, #4f46e5); }
.axis-day-chip.active {
  background: var(--chip-color, #6366f1);
  color: white;
  border-color: transparent;
}
.axis-day-chip.active .chip-count { color: rgba(255,255,255,0.7); }
.chip-dot {
  width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0;
  border: 1.5px solid rgba(255,255,255,0.6);
}
.chip-num { font-weight: 700; font-size: 12px; }
.chip-label { font-weight: 500; }
.chip-count { font-size: 11px; color: #9ca3af; }
.axis-add-btn {
  background: transparent; border: 1px dashed #d1d5db;
  color: #9ca3af; padding: 6px 14px; border-radius: 20px;
  font-size: 12px; cursor: pointer; transition: all 0.2s; white-space: nowrap;
}
.axis-add-btn:hover { border-color: #6366f1; color: #6366f1; }

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
.editor-header h3 {
  margin: 0; font-size: 15px; font-weight: 700; color: #1f2937;
}
.editor-header-actions { display: flex; gap: 6px; }
.icon-btn {
  width: 28px; height: 28px; border-radius: 6px;
  border: 1px solid #e5e7eb; background: white;
  cursor: pointer; display: flex; align-items: center;
  justify-content: center; font-size: 16px; color: #6b7280;
  transition: all 0.15s;
}
.icon-btn:hover { background: #f3f4f6; color: #1f2937; }
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
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99,102,241,0.08);
}
.activity-edit-card.highlighted {
  border-color: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245,158,11,0.12);
}
.activity-edit-card.picking {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99,102,241,0.15);
  animation: pickingGlow 1.2s ease-in-out infinite alternate;
}
@keyframes pickingGlow {
  from { box-shadow: 0 0 0 3px rgba(99,102,241,0.15); }
  to { box-shadow: 0 0 0 6px rgba(99,102,241,0.08); }
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.activity-index {
  width: 22px; height: 22px; border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: white; display: flex; align-items: center;
  justify-content: center; font-size: 11px; font-weight: 700;
  flex-shrink: 0;
}
.card-header-inputs { flex: 1; display: flex; gap: 4px; min-width: 0; }
.field-time {
  width: 68px; flex-shrink: 0; border: none; background: white; padding: 6px 4px;
  border-radius: 6px; font-size: 12px; font-weight: 600; color: #1f2937; text-align: center;
}
.field-location {
  flex: 1; min-width: 0; border: none; background: white; padding: 6px 8px;
  border-radius: 6px; font-size: 13px; font-weight: 600; color: #1f2937;
}
.field-time:focus, .field-location:focus { outline: none; box-shadow: 0 0 0 2px rgba(99,102,241,0.2); }

.card-move-actions { display: flex; gap: 3px; }
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
  padding: 8px 10px; font-size: 12px; color: #4b5563;
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
.field-label { font-size: 12px; color: #9ca3af; flex-shrink: 0; }
.field-tips, .field-cost {
  background: transparent; border: none; font-size: 12px;
  color: #6b7280; width: 100%; min-width: 0;
}
.field-tips:focus, .field-cost:focus { outline: none; }
.coords-row {
  display: flex; align-items: center; gap: 8px; width: 100%;
}
.coords-readout { font-size: 10px; color: #9ca3af; white-space: nowrap; flex: 1; }
.pick-location-btn {
  background: transparent; border: 1px solid #d1d5db; color: #6366f1;
  padding: 2px 10px; border-radius: 10px; font-size: 11px; font-weight: 500;
  cursor: pointer; transition: all 0.15s; white-space: nowrap; flex-shrink: 0;
}
.pick-location-btn:hover { background: #eef2ff; border-color: #6366f1; }
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
.add-activity-btn:hover { border-color: #6366f1; color: #6366f1; background: rgba(99,102,241,0.04); }

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
  width: 48px; height: 48px; border: 4px solid rgba(99,102,241,0.1);
  border-top-color: #6366f1; border-radius: 50%;
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
  width: 16px; height: 16px; border: 2px solid rgba(99,102,241,0.1);
  border-top-color: #6366f1; border-radius: 50%; animation: spin 1s infinite linear;
}
.step.completed { color: #10b981; font-weight: 500; }
.step.active { color: #6366f1; font-weight: 600; }
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
