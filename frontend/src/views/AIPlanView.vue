<template>
  <div :class="['ai-plan-page', { 'has-result': result }]">
    <!-- Input Modal (固定在顶部或侧边) -->
    <div v-if="!result" class="input-modal">
      <section class="input-section">
        <div class="input-header">
          <SvgIcon name="sparkles" :size="20" class="header-icon" />
          <div>
            <h2>AI 旅行规划</h2>
            <p>输入你的旅行想法，智能生成个性化行程方案</p>
          </div>
        </div>

        <form @submit.prevent="generatePlan" class="input-form">
          <textarea
            v-model="query"
            rows="3"
            placeholder="比如：帮我做一个东京 5 天旅行计划，偏美食和城市观光，预算 10000 元..."
            class="query-input"
          />

          <div class="input-row">
            <div class="quick-tags">
              <button type="button" class="quick-tag" @click="appendTag('3天短途')">3天短途</button>
              <button type="button" class="quick-tag" @click="appendTag('5天深度游')">5天深度游</button>
              <button type="button" class="quick-tag" @click="appendTag('情侣出行')">情侣出行</button>
              <button type="button" class="quick-tag" @click="appendTag('亲子游')">亲子游</button>
              <button type="button" class="quick-tag" @click="appendTag('美食为主')">美食为主</button>
              <button type="button" class="quick-tag" @click="appendTag('预算5000')">预算5000</button>
            </div>
            <div class="input-actions">
              <label class="upload-btn">
                <input type="file" @change="handleFileChange" hidden />
                <SvgIcon name="upload" :size="16" />
                <span>{{ file ? file.name : '上传文件' }}</span>
              </label>
              <button type="submit" class="gen-btn" :disabled="loading">
                <SvgIcon v-if="loading" name="loader" :size="16" spin />
                <SvgIcon v-else name="sparkles" :size="16" />
                {{ loading ? '生成中...' : '生成计划' }}
              </button>
            </div>
          </div>

          <p v-if="errorMessage" class="error-msg">{{ errorMessage }}</p>
        </form>
      </section>
    </div>

    <!-- Result Layout (Left: Map, Right: Itinerary) -->
    <div v-if="result" class="result-layout">
      <!-- 左侧地图 -->
      <div class="map-section">
        <MapComponent :destinations="[result.destination]" :itinerary="parsedItinerary" />
      </div>

      <!-- 右侧：行程 + AI 对话 -->
      <div class="itinerary-section">
        <!-- 上面：行程详情 -->
        <div class="itinerary-top">
          <!-- 顶部操作栏 -->
          <div class="result-top-bar">
            <div class="result-title-area">
              <h2>{{ result.title }}</h2>
              <p class="result-meta">{{ result.destination }} · {{ result.days }}天</p>
            </div>
            <div class="result-actions">
              <button class="action-btn" @click="saveToMyPlans">
                保存规划
              </button>
              <button class="action-btn secondary" @click="resetPlan">重新规划</button>
              <button class="action-btn" @click="shareToCommunity">
                <SvgIcon name="share" :size="14" />
                分享到社区
              </button>
            </div>
          </div>

          <!-- 行程详情面板 -->
          <ItineraryPanel
            :title="result.title"
            :destination="result.destination"
            :days="result.days"
            :itinerary="parsedItinerary"
            :summary="renderedMarkdown"
            @update:itinerary="handleUpdateItinerary"
          />
        </div>

        <!-- 下面：AI 对话框 -->
        <div class="ai-chat-panel">
          <div class="chat-header">
            <h3>行程助手</h3>
            <p class="chat-subtitle">有问题？快速咨询</p>
          </div>

          <div class="chat-messages">
            <div class="message bot-message">
              <div class="message-content">{{ result.title }}已生成！有任何问题可以随时咨询我。</div>
            </div>
          </div>

          <div class="chat-input-area">
            <input
              v-model="chatQuery"
              @keyup.enter="sendChat"
              placeholder="如：改成美食为主、增加夜生活..."
              class="chat-input"
            />
            <button @click="sendChat" class="chat-send-btn">发送</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SvgIcon from '../components/SvgIcon.vue'
import MapComponent from '../components/MapComponent.vue'
import ItineraryPanel from '../components/ItineraryPanel.vue'

const route = useRoute()
const query = ref('')
const file = ref(null)

// 默认示例数据
const defaultResult = {
  title: '北京 5 天深度游',
  destination: '北京',
  days: 5,
  markdown: `
## 第1天：古都文化之旅
- 上午：天安门广场 - 游览中国国旗升旗仪式和广场的宏伟景观
- 中午：故宫博物院 - 探索960年的古宫廷建筑与文化
- 下午：景山公园 - 俯瞰紫禁城全景，拍摄绝美照片

## 第2天：长城壮美体验
- 上午：慕田峪长城 - 游览保存完好的明代长城
- 中午：长城脚下农家乐 - 品尝地道北京风味
- 下午：鸟巢水立方 - 参观奥运场馆建筑

## 第3天：皇家园林赏析
- 上午：颐和园 - 欣赏世界最大皇家园林
- 下午：清华大学 - 感受顶尖学府氛围
- 晚上：三里屯 - 体验北京夜生活

## 第4天：胡同文化品鉴
- 上午：南锣鼓巷 - 游走古老的胡同街道
- 中午：黑芝麻胡同的传统美食
- 下午：什刹海酒吧街 - 享受休闲时光

## 第5天：购物与美食之旅
- 上午：王府井大街 - 逛街购物
- 中午：全聚德烤鸭 - 品尝北京烤鸭
- 下午：798艺术区 - 探索现代艺术
  `,
  images: []
}

const result = ref(defaultResult)
const loading = ref(false)
const errorMessage = ref('')
const chatQuery = ref('')

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const renderedMarkdown = computed(() => {
  if (!result.value?.markdown) return ''
  return DOMPurify.sanitize(md.render(result.value.markdown))
})

// 从返回结果中解析行程数据
const parsedItinerary = computed(() => {
  if (!result.value) return []

  // 优先使用已编辑的行程数据
  if (result.value.itinerary && Array.isArray(result.value.itinerary)) {
    return result.value.itinerary
  }

  // 尝试从markdown中解析行程信息
  const itinerary = []
  const markdown = result.value.markdown || ''
  const lines = markdown.split('\n')

  let currentDay = null
  lines.forEach((line, index) => {
    // 匹配 "第X天" 的标题
    if (line.includes('第') && line.includes('天') && line.startsWith('#')) {
      currentDay = {
        day: parseInt(line.match(/\d+/)?.[0] || itinerary.length + 1),
        activities: [],
        date: '',
        summary: ''
      }
      itinerary.push(currentDay)
    }

    // 匹配活动 (- 上午：天安门广场 - 游览...)
    if (currentDay && line.trim().startsWith('-')) {
      const trimmed = line.trim().substring(1).trim() // 去掉 "- "
      const parts = trimmed.split(/[-–]/).map(s => s.trim()) // 分割 "-" 或 "–"
      
      if (parts.length >= 1) {
        // 第一部分是 "时间：地点" 的格式
        const firstPart = parts[0]
        const colonIndex = firstPart.indexOf('：') > -1 ? firstPart.indexOf('：') : firstPart.indexOf(':')
        
        let time = ''
        let location = ''
        
        if (colonIndex > -1) {
          time = firstPart.substring(0, colonIndex).trim()
          location = firstPart.substring(colonIndex + 1).trim()
        } else {
          location = firstPart
        }

        currentDay.activities.push({
          time: time || '全天',
          location: location || '',
          description: parts.slice(1).join(' - '),
          coordinates: generateMockCoordinates(result.value.destination, location)
        })
      }
    }
  })

  // 如果没有解析到行程，生成默认结构
  if (itinerary.length === 0) {
    for (let i = 1; i <= (result.value.days || 1); i++) {
      itinerary.push({
        day: i,
        activities: [
          {
            location: result.value.destination,
            description: `第${i}天行程`,
            time: '全天',
            coordinates: generateMockCoordinates(result.value.destination)
          }
        ]
      })
    }
  }

  return itinerary
})

// 生成模拟坐标（生产环境应该从AI返回的数据中获取真实坐标）
const generateMockCoordinates = (destination, location = '') => {
  // 主要城市坐标
  const cityCoords = {
    '北京': [39.9042, 116.4074],
    '上海': [31.2304, 121.4737],
    '广州': [23.1291, 113.2644],
    '深圳': [22.5431, 114.0579],
    '杭州': [30.2741, 120.1551],
    '西安': [34.3416, 108.9398],
    '成都': [30.5728, 104.0668],
    '南京': [32.0603, 118.7969],
    '苏州': [31.2989, 120.5954],
    '武汉': [30.5928, 114.3055],
    '东京': [35.6762, 139.6503],
    '大阪': [34.6937, 135.5023],
    '京都': [35.0116, 135.7681],
    '首尔': [37.5665, 126.9780],
    '曼谷': [13.7563, 100.5018],
    '新加坡': [1.3521, 103.8198]
  }

  // 随机偏移以显示不同的位置
  const baseCoord = cityCoords[destination] || [30, 110]
  const offset = Math.random() * 0.5 - 0.25
  return [baseCoord[0] + offset, baseCoord[1] + offset]
}

onMounted(async () => {
  if (route.query.q) {
    query.value = route.query.q
  }
  // 如果有 planId 参数，加载已保存的规划
  if (route.query.planId) {
    await loadSavedPlan(route.query.planId)
  }
})

const loadSavedPlan = async (planId) => {
  try {
    const response = await fetch(`/api/travel/plan/${planId}`)
    const data = await response.json()
    if (data.code === 200) {
      const plan = data.data
      result.value = {
        title: plan.title,
        destination: plan.destination,
        days: plan.days,
        markdown: plan.itinerary || '',
        highlights: plan.highlights || []
      }
    }
  } catch (error) {
    console.error('加载规划失败:', error)
  }
}

const appendTag = (tag) => {
  if (!query.value.includes(tag)) {
    query.value = query.value.trim() + (query.value ? '，' : '') + tag
  }
}

const handleFileChange = (e) => { file.value = e.target.files?.[0] || null }

const generatePlan = async () => {
  if (!query.value.trim()) { errorMessage.value = '请先输入旅行需求'; return }
  loading.value = true
  errorMessage.value = ''
  try {
    const formData = new FormData()
    formData.append('query', query.value.trim())
    formData.append('userId', localStorage.getItem('userId') || '1')
    if (file.value) formData.append('file', file.value)
    const res = await fetch('/api/assistant/chat', { method: 'POST', body: formData })
    const data = await res.json()
    if (data.code === 200) result.value = data.data
    else errorMessage.value = data.message || '生成失败'
  } catch { errorMessage.value = '服务器错误' }
  finally { loading.value = false }
}

const shareToCommunity = () => {
  if (!result.value) return
  fetch('/api/community/posts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User-Id': localStorage.getItem('userId') || '1' },
    body: JSON.stringify({
      title: result.value.title,
      description: result.value.summary || '',
      images: result.value.images?.map(i => i.imageUrl).filter(Boolean) || [],
      avatar: '', nickname: localStorage.getItem('username') || '用户', bio: '',
      tags: [result.value.destination, 'AI规划'].filter(Boolean)
    })
  })
}

const resetPlan = () => { result.value = null; errorMessage.value = ''; file.value = null; query.value = '' }

const saveToMyPlans = async () => {
  if (!result.value) {
    alert('没有可保存的行程')
    return
  }
  
  // 检查是否有实际的行程数据（不是默认占位数据）
  if (!result.value.markdown || result.value.markdown.trim() === '') {
    alert('请先生成行程规划再保存')
    return
  }
  
  console.log('开始保存规划:', result.value)
  
  try {
    // 使用结构化的行程数据
    const itineraryData = parsedItinerary.value
    
    // 检查是否有实际景点（不是默认的目的地占位）
    const hasRealLocations = itineraryData.some(day => 
      day.activities.some(activity => 
        activity.location && activity.location !== result.value.destination
      )
    )
    
    if (!hasRealLocations) {
      alert('行程中没有实际景点，请先生成详细行程')
      return
    }
    
    const response = await fetch('/api/travel/plan/save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: Number(localStorage.getItem('userId')) || 1,
        title: result.value.title,
        destination: result.value.destination,
        days: result.value.days,
        budget: calculateBudget(result.value.destination, result.value.days),
        itinerary: JSON.stringify(itineraryData),
        interests: '',
        travelStyle: '',
        highlights: ['✨ 景点体验', '🍽️ 美食品尝', '📸 打卡推荐']
      })
    })
    
    console.log('响应状态:', response.status)
    const data = await response.json()
    console.log('响应数据:', data)
    
    if (data.code === 200) {
      alert('保存成功！已添加到我的规划')
    } else {
      alert('保存失败: ' + data.message)
    }
  } catch (error) {
    console.error('保存失败:', error)
    alert('保存失败，请重试')
  }
}

const sendChat = async () => {
  if (!chatQuery.value.trim()) return
  // TODO: 调用后端 API 更新行程
  console.log('用户问题:', chatQuery.value)
  chatQuery.value = ''
}

// 处理行程更新
const handleUpdateItinerary = async (updatedItinerary) => {
  // 更新本地数据
  result.value = {
    ...result.value,
    itinerary: updatedItinerary
  }
  
  // 如果是从已保存的规划加载的（有 planId），自动保存到后端
  if (route.query.planId) {
    await saveUpdatedPlan(route.query.planId, updatedItinerary)
  }
}

// 保存更新后的规划到后端
const saveUpdatedPlan = async (planId, itinerary) => {
  try {
    const response = await fetch(`/api/travel/plan/${planId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        itinerary: JSON.stringify(itinerary)
      })
    })
    const data = await response.json()
    if (data.code === 200) {
      console.log('规划已更新')
    } else {
      console.error('更新失败:', data.message)
    }
  } catch (error) {
    console.error('更新规划失败:', error)
  }
}
</script>

<style scoped>
.ai-plan-page {
  width: 100%;
  min-height: 100vh;
  background: var(--color-bg);
  font-family: var(--font-family);
  color: var(--color-body);
}

/* Input Modal (无结果时) */
.input-modal {
  width: 100%;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.input-section {
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 32px;
  border: 1px solid var(--color-border);
  width: 100%;
  max-width: 640px;
  box-shadow: var(--shadow-card);
}

.input-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.header-icon { color: var(--color-red-light); }

.input-header h2 {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-title);
  margin: 0;
}

.input-header p {
  font-size: 14px;
  color: var(--color-hint);
  margin: 4px 0 0;
}

.input-form { display: flex; flex-direction: column; gap: 16px; }

.query-input {
  resize: vertical;
  min-height: 100px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-input);
  padding: 14px 16px;
  font-size: 14px;
  font-family: var(--font-family);
  background: var(--color-bg);
  color: var(--color-title);
  transition: border-color 0.2s;
}
.query-input:focus { outline: none; border-color: var(--color-red); box-shadow: 0 0 0 3px rgba(230,57,70,0.08); }
.query-input::placeholder { color: var(--color-muted); }

.input-row { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 12px; }

.quick-tags { display: flex; flex-wrap: wrap; gap: 8px; }

.quick-tag {
  border: 1px solid var(--color-border); border-radius: var(--radius-pill);
  padding: 6px 14px; font-size: 12px; color: var(--color-hint);
  background: var(--color-bg); cursor: pointer; transition: all 0.2s; font-family: var(--font-family);
}
.quick-tag:hover { border-color: var(--color-red); color: var(--color-red-light); }

.input-actions { display: flex; gap: 12px; align-items: center; }

.upload-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 16px; border: 1.5px dashed var(--color-border);
  border-radius: var(--radius-input); color: var(--color-muted);
  font-size: 12px; cursor: pointer; transition: all 0.2s; font-family: var(--font-family);
}
.upload-btn:hover { border-color: var(--color-red); color: var(--color-red-light); }

.gen-btn {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 24px; border: none; border-radius: var(--radius-pill);
  background: var(--gradient-brand); color: white;
  font-size: 14px; font-weight: 600; font-family: var(--font-family);
  cursor: pointer; box-shadow: var(--shadow-button); transition: all 0.2s;
}
.gen-btn:hover:not(:disabled) { filter: brightness(1.1); transform: translateY(-2px); }
.gen-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.error-msg { margin: 0; font-size: 13px; color: var(--color-red-light); }

/* Result Layout (Map + Itinerary) */
.result-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: 100%;
  height: 100vh;
  gap: 0;
}

.map-section {
  position: relative;
  background: #f5f5f5;
  border-right: 2px solid var(--color-border);
  overflow: hidden;
  box-shadow: inset -2px 0 8px rgba(0, 0, 0, 0.05);
}

.itinerary-section {
  display: flex;
  flex-direction: column;
  background: #0a0a0a;
  overflow: hidden;
}

.itinerary-top {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-bottom: 2px solid #333333;
  background: #0a0a0a;
}

.ai-chat-panel {
  flex: 0 0 280px;
  display: flex;
  flex-direction: column;
  background: #0a0a0a;
  border-top: 2px solid #333333;
  overflow: hidden;
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.3);
}

.chat-header {
  padding: 16px;
  border-bottom: 1px solid #333333;
  flex-shrink: 0;
  background: #111111;
}

.chat-header h3 {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 700;
  color: #f8fafc;
}

.chat-subtitle {
  margin: 0;
  font-size: 11px;
  color: var(--color-hint);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #0a0a0a;
}

.message {
  font-size: 12px;
  line-height: 1.5;
}

.message-content {
  padding: 8px 12px;
  border-radius: 6px;
  word-break: break-word;
}

.bot-message .message-content {
  background: rgba(255, 107, 107, 0.15);
  color: #e2e8f0;
}

.user-message .message-content {
  background: var(--color-red-light);
  color: white;
  margin-left: 20px;
}

.chat-input-area {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #333333;
  flex-shrink: 0;
  background: #0a0a0a;
}

.chat-input {
  flex: 1;
  border: 1px solid #333333;
  border-radius: 4px;
  padding: 8px 12px;
  font-size: 12px;
  font-family: var(--font-family);
  background: #1a1a1a;
  color: #f8fafc;
}

.chat-input::placeholder {
  color: #64748b;
  opacity: 0.6;
}

.chat-input:focus {
  outline: none;
  border-color: var(--color-red-light);
  box-shadow: 0 0 0 2px rgba(255, 107, 107, 0.1);
}

.chat-send-btn {
  padding: 8px 16px;
  border: none;
  background: var(--color-red-light);
  color: white;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  font-family: var(--font-family);
  cursor: pointer;
  transition: all 0.2s;
}

.chat-send-btn:hover {
  background: var(--color-red);
}

.result-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-card);
  flex-shrink: 0;
}

.result-title-area h2 {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-title);
  margin: 0 0 4px;
}

.result-meta {
  font-size: 13px;
  color: var(--color-body);
  opacity: 0.7;
  margin: 0;
}

.result-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  flex-shrink: 0;
  width: 100%;
  max-width: 380px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: 100%;
  min-width: 0;
  padding: 8px 0;
  border: none;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 600;
  font-family: var(--font-family);
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  box-sizing: border-box;
}

.action-btn:not(.secondary) {
  background: var(--gradient-brand);
  color: white;
}

.action-btn.secondary {
  background: var(--color-bg);
  color: var(--color-secondary);
  border: 1px solid var(--color-border);
}

.action-btn:hover {
  filter: brightness(1.1);
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .result-layout {
    grid-template-columns: 1fr;
    height: auto;
  }

  .map-section {
    height: 400px;
    border-right: none;
    border-bottom: 1px solid var(--color-border);
  }

  .itinerary-section {
    height: auto;
    max-height: none;
  }

  .ai-chat-panel {
    flex: 0 0 200px;
  }
}
</style>