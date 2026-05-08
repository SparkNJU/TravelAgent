<template>
  <div class="itinerary-panel">
    <div class="itinerary-header">
      <div class="header-content">
        <h3>{{ title }}</h3>
        <p class="itinerary-meta">{{ destination }} · {{ days }}天行程</p>
      </div>
      <div class="header-actions">
        <button class="edit-toggle" :class="{ active: isEditing }" @click="toggleEditMode">
          {{ isEditing ? '保存' : '编辑' }}
        </button>
        <button class="summary-btn" @click="showSummaryModal = true" v-if="summary">
          行程概览
        </button>
      </div>
    </div>

    <div class="itinerary-content">
      <div class="day-card" v-for="(day, index) in (isEditing ? editableItinerary : parsedItinerary)" :key="index">
        <div class="day-label">
          <span class="day-number">第{{ day.day }}天</span>
          <span v-if="day.date" class="day-date">{{ day.date }}</span>
          <div v-if="isEditing" class="day-actions">
            <button class="action-btn add-day" @click="addDay">+</button>
            <button v-if="(isEditing ? editableItinerary : parsedItinerary).length > 1" class="action-btn remove-day" @click="removeDay(index)">-</button>
          </div>
        </div>

        <div class="day-activities">
          <div
            class="activity-item"
            v-for="(activity, aIdx) in day.activities"
            :key="aIdx"
            :class="{ active: activeActivityId === `${index}-${aIdx}` }"
            @click="!isEditing && (activeActivityId = `${index}-${aIdx}`)"
          >
            <div v-if="isEditing" class="activity-edit">
              <input
                v-model="activity.time"
                type="text"
                placeholder="时间（如：上午 9:00）"
                class="edit-input time-input"
              />
              <input
                v-model="activity.location"
                type="text"
                placeholder="地点"
                class="edit-input location-input"
              />
              <textarea
                v-model="activity.description"
                placeholder="活动描述"
                class="edit-textarea"
              ></textarea>
              <input
                v-model="activity.tips"
                type="text"
                placeholder="小贴士"
                class="edit-input tips-input"
              />
              <input
                v-model="activity.cost"
                type="number"
                placeholder="预估消费"
                class="edit-input cost-input"
              />
              <button class="remove-activity" @click.stop="removeActivity(index, aIdx)">删除</button>
            </div>
            <template v-else>
              <div class="activity-time" v-if="activity.time">{{ activity.time }}</div>
              <div class="activity-content">
                <div class="activity-location">
                  <SvgIcon v-if="activity.location" name="mapPin" :size="14" />
                  {{ activity.location || activity.title }}
                </div>
                <div class="activity-description" v-if="activity.description">
                  {{ activity.description }}
                </div>
                <div class="activity-tips" v-if="activity.tips">
                  💡 {{ activity.tips }}
                </div>
                <div v-if="activity.cost" class="activity-cost">
                  预估消费：¥{{ activity.cost }}
                </div>
              </div>
              <div class="activity-indicator"></div>
            </template>
          </div>
          <button v-if="isEditing" class="add-activity" @click="addActivity(index)">
            + 添加活动
          </button>
        </div>

        <div class="day-summary" v-if="day.summary">
          <p>{{ day.summary }}</p>
        </div>
      </div>
    </div>



    <!-- 行程概览弹窗 -->
    <div class="summary-modal-overlay" v-if="showSummaryModal" @click="showSummaryModal = false">
      <div class="summary-modal" @click.stop>
        <div class="modal-header">
          <h3>行程概览</h3>
          <button class="modal-close" @click="showSummaryModal = false">✕</button>
        </div>
        <div class="modal-body">
          <div v-html="summary"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import SvgIcon from './SvgIcon.vue'

const props = defineProps({
  title: String,
  destination: String,
  days: Number,
  itinerary: Array,
  summary: String
})

const emit = defineEmits(['update:itinerary'])

const activeActivityId = ref(null)
const showSummaryModal = ref(false)
const isEditing = ref(false)
const editableItinerary = ref([])

// 初始化可编辑行程
const initEditableItinerary = () => {
  editableItinerary.value = JSON.parse(JSON.stringify(parsedItinerary.value))
}

// 切换编辑模式
const toggleEditMode = () => {
  if (isEditing.value) {
    // 保存修改
    emit('update:itinerary', editableItinerary.value)
  } else {
    // 进入编辑模式，复制一份数据
    initEditableItinerary()
  }
  isEditing.value = !isEditing.value
}

// 添加活动
const addActivity = (dayIndex) => {
  const newActivity = {
    time: '',
    location: '',
    description: '',
    tips: '',
    cost: ''
  }
  editableItinerary.value[dayIndex].activities.push(newActivity)
}

// 删除活动
const removeActivity = (dayIndex, activityIndex) => {
  editableItinerary.value[dayIndex].activities.splice(activityIndex, 1)
}

// 添加天数
const addDay = () => {
  const newDay = {
    day: editableItinerary.value.length + 1,
    date: '',
    activities: [],
    summary: ''
  }
  editableItinerary.value.push(newDay)
}

// 删除天数
const removeDay = (dayIndex) => {
  if (editableItinerary.value.length > 1) {
    editableItinerary.value.splice(dayIndex, 1)
    // 更新天数序号
    editableItinerary.value.forEach((day, idx) => {
      day.day = idx + 1
    })
  }
}

// 解析行程数据
const parsedItinerary = computed(() => {
  if (!props.itinerary) return []

  // 如果已经是结构化数据，直接返回
  if (Array.isArray(props.itinerary) && props.itinerary[0]?.day !== undefined) {
    return props.itinerary
  }

  // 否则尝试从markdown中解析（备选方案）
  const days = []
  let currentDay = null

  const parseActivities = (text) => {
    const activities = []
    const lines = text.split('\n')

    lines.forEach(line => {
      if (line.includes('--') || line.includes('：')) {
        const parts = line.split(/--|：/)
        if (parts.length >= 2) {
          activities.push({
            time: parts[0]?.trim(),
            location: parts[1]?.trim() || '',
            description: parts.slice(2).join('：').trim()
          })
        }
      }
    })

    return activities
  }

  // 简单的行程结构化
  for (let i = 1; i <= (props.days || 1); i++) {
    days.push({
      day: i,
      activities: [
        {
          location: `${props.destination}`,
          description: `第${i}天行程`,
          time: '全天'
        }
      ]
    })
  }

  return days
})
</script>

<style scoped>
.itinerary-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  overflow-y: auto;
}

.itinerary-header {
  position: sticky;
  top: 0;
  background: var(--color-card);
  border-bottom: 1px solid var(--color-border);
  padding: 16px 20px;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-content {
  flex: 1;
}

.itinerary-header h3 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-title);
}

.itinerary-meta {
  margin: 0;
  font-size: 13px;
  color: var(--color-hint);
}

.itinerary-content {
  flex: 1;
  padding: 16px 20px;
  overflow-y: auto;
}

.day-card {
  margin-bottom: 20px;
  border-left: 3px solid var(--color-red-light);
  padding-left: 16px;
}

.day-label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.day-actions {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.action-btn {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s;
}

.add-day {
  background: rgba(34, 197, 94, 0.1);
  color: #22c55e;
}

.add-day:hover {
  background: rgba(34, 197, 94, 0.2);
}

.remove-day {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.remove-day:hover {
  background: rgba(239, 68, 68, 0.2);
}

.day-number {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-title);
}

.day-date {
  font-size: 12px;
  color: var(--color-hint);
}

.day-activities {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.activity-item {
  position: relative;
  padding: 12px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  cursor: pointer;
  transition: all 0.2s;
}

.activity-item:hover {
  border-color: var(--color-red-light);
  background: rgba(255, 107, 107, 0.02);
}

.activity-item.active {
  border-color: var(--color-red-light);
  background: rgba(255, 107, 107, 0.08);
  box-shadow: 0 2px 8px rgba(255, 107, 107, 0.12);
}

.activity-edit {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.edit-input {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 12px;
  background: white;
  color: #0a0a0a;
}

.edit-input:focus {
  outline: none;
  border-color: var(--color-red-light);
}

.edit-textarea {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 12px;
  background: white;
  color: #0a0a0a;
  min-height: 60px;
  resize: vertical;
}

.edit-textarea:focus {
  outline: none;
  border-color: var(--color-red-light);
}

.remove-activity {
  align-self: flex-end;
  padding: 6px 12px;
  border: none;
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.remove-activity:hover {
  background: rgba(239, 68, 68, 0.2);
}

.add-activity {
  margin-top: 8px;
  padding: 10px;
  border: 1px dashed var(--color-border);
  background: transparent;
  color: var(--color-hint);
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}

.add-activity:hover {
  border-color: var(--color-red-light);
  color: var(--color-red-light);
}

.activity-time {
  font-size: 11px;
  color: var(--color-red-light);
  font-weight: 600;
  margin-bottom: 4px;
}

.activity-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.activity-location {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-title);
}

.activity-description {
  font-size: 12px;
  color: var(--color-body);
  line-height: 1.5;
}

.activity-tips {
  font-size: 11px;
  color: var(--color-hint);
  padding: 4px 8px;
  background: rgba(255, 200, 50, 0.1);
  border-radius: 4px;
  margin-top: 2px;
}

.activity-cost {
  font-size: 11px;
  color: var(--color-red-light);
  font-weight: 500;
  margin-top: 4px;
}

.activity-indicator {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-red-light);
  opacity: 0;
  transition: opacity 0.2s;
}

.activity-item:hover .activity-indicator,
.activity-item.active .activity-indicator {
  opacity: 1;
}

.day-summary {
  margin-top: 8px;
  padding: 8px 12px;
  background: rgba(255, 107, 107, 0.05);
  border-radius: 4px;
  font-size: 12px;
  color: var(--color-body);
  line-height: 1.6;
}

.day-summary p {
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.edit-toggle {
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-title);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.edit-toggle:hover {
  background: var(--color-card);
}

.edit-toggle.active {
  background: var(--color-red-light);
  border-color: var(--color-red-light);
  color: white;
}

.summary-btn {
  padding: 8px 16px;
  border: 1.5px solid var(--color-red-light);
  background: #0a0a0a;
  color: #f8fafc;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.summary-btn:hover {
  background: #1a1a1a;
  border-color: var(--color-red);
}

/* 行程概览弹窗 */
.summary-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.summary-modal {
  background: white;
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  max-height: 70vh;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid var(--color-border);
  background: #f8fafc;
}

.modal-header h3 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #0a0a0a;
}

.modal-close {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: #e2e8f0;
  color: #64748b;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.modal-close:hover {
  background: #cbd5e1;
  color: #334155;
}

.modal-body {
  padding: 20px;
  max-height: calc(70vh - 80px);
  overflow-y: auto;
  color: #0a0a0a;
}

.modal-body :deep(h1),
.modal-body :deep(h2),
.modal-body :deep(h3),
.modal-body :deep(h4),
.modal-body :deep(h5),
.modal-body :deep(h6),
.modal-body :deep(strong),
.modal-body :deep(b) {
  font-size: 15px !important;
  font-weight: 600 !important;
  color: #0a0a0a !important;
  margin: 8px 0 4px !important;
  line-height: 1.4 !important;
}

.modal-body :deep(p),
.modal-body :deep(li) {
  margin: 6px 0;
  font-size: 14px;
  color: #0a0a0a !important;
  line-height: 1.8;
}

.modal-body :deep(ul) {
  padding-left: 20px;
  margin: 0;
}

.modal-body :deep(*) {
  color: #0a0a0a !important;
}
</style>