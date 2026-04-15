<template>
  <div class="ai-plan-container">
    <div class="plan-header">
      <h2>AI 智能规划</h2>
      <p>由我们的 AgentLLM 为你定制完美的旅行计划</p>
    </div>

    <div class="plan-form-section">
      <form @submit.prevent="generatePlan" class="plan-form">
        <div class="form-row">
          <div class="form-group">
            <label>目的地 *</label>
            <input 
              v-model="formData.destination" 
              type="text" 
              placeholder="e.g., 巴黎、新加坡、京都" 
              required 
            />
          </div>
          <div class="form-group">
            <label>旅游天数 *</label>
            <input 
              v-model.number="formData.days" 
              type="number" 
              min="1" 
              max="30" 
              placeholder="天数" 
              required 
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>预算范围 (CNY) *</label>
            <input 
              v-model.number="formData.budget" 
              type="number" 
              min="1000" 
              placeholder="预算" 
              required 
            />
          </div>
          <div class="form-group">
            <label>旅游风格 *</label>
            <select v-model="formData.travelStyle" required>
              <option value="">选择风格</option>
              <option value="relaxed">轻松休闲</option>
              <option value="active">活跃探险</option>
              <option value="luxury">奢华享受</option>
            </select>
          </div>
        </div>

        <div class="form-group">
          <label>兴趣偏好 (多选)</label>
          <div class="interests-checkboxes">
            <label><input type="checkbox" value="culture" v-model="interests"> 文化艺术</label>
            <label><input type="checkbox" value="food" v-model="interests"> 美食品尝</label>
            <label><input type="checkbox" value="nature" v-model="interests"> 自然风景</label>
            <label><input type="checkbox" value="adventure" v-model="interests"> 冒险运动</label>
          </div>
        </div>

        <button type="submit" class="generate-btn" :disabled="loading">
          {{ loading ? 'AI 正在思考中...' : '生成行程' }}
        </button>

        <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
      </form>
    </div>

    <!-- Display generated plan -->
    <div v-if="generatedPlan" class="plan-result">
      <div class="result-header">
        <h3>{{ generatedPlan.title }}</h3>
        <div class="confidence">
          <span class="score">AI 评分: {{ (generatedPlan.aiConfidenceScore * 100).toFixed(0) }}%</span>
        </div>
      </div>

      <div class="plan-details">
        <div class="detail-item">
          <strong>目的地:</strong> {{ generatedPlan.destination }}
        </div>
        <div class="detail-item">
          <strong>行程天数:</strong> {{ generatedPlan.days }} 天
        </div>
        <div class="detail-item">
          <strong>预计费用:</strong> ¥{{ generatedPlan.estimatedBudget }}
        </div>
      </div>

      <div class="itinerary">
        <h4>详细行程</h4>
        <p>{{ generatedPlan.itinerary }}</p>
      </div>

      <div class="highlights">
        <h4>行程亮点</h4>
        <div class="highlight-items">
          <div v-for="(highlight, index) in generatedPlan.highlights" :key="index" class="highlight-item">
            {{ highlight }}
          </div>
        </div>
      </div>

      <div class="action-buttons">
        <button class="save-btn">保存行程</button>
        <button class="share-btn">分享好友</button>
        <button @click="resetForm" class="reset-btn">重新生成</button>
      </div>
    </div>

    <!-- Sample plans display -->
    <div v-else class="sample-plans">
      <h3>推荐行程模板</h3>
      <div class="plans-grid">
        <div v-for="sample in samplePlans" :key="sample.planId" class="sample-card">
          <h4>{{ sample.title }}</h4>
          <p><strong>目的地:</strong> {{ sample.destination }}</p>
          <p><strong>天数:</strong> {{ sample.days }} 天</p>
          <p><strong>预算:</strong> ¥{{ sample.estimatedBudget }}</p>
          <button @click="useSamplePlan(sample)" class="use-btn">使用此模板</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const formData = ref({
  destination: '',
  days: 5,
  budget: 10000,
  travelStyle: ''
})

const interests = ref([])
const generatedPlan = ref(null)
const samplePlans = ref([])
const loading = ref(false)
const errorMessage = ref('')

onMounted(async () => {
  // Fetch sample plans
  try {
    const response = await fetch('/api/travel/plans/samples')
    const data = await response.json()
    if (data.code === 200) {
      samplePlans.value = data.data
    }
  } catch (error) {
    console.error('Failed to load sample plans:', error)
  }
})

const generatePlan = async () => {
  if (!formData.value.destination || !formData.value.days) {
    errorMessage.value = '请填写必填项'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    // Get userId from localStorage 
    const userId = localStorage.getItem('userId') || 1;
    
    const response = await fetch('/api/travel/plan/generate', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        userId: userId,
        destination: formData.value.destination,
        days: formData.value.days,
        budget: formData.value.budget,
        travelStyle: formData.value.travelStyle,
        interests: interests.value.join(',')
      })
    })

    const data = await response.json()

    if (data.code === 200) {
      generatedPlan.value = data.data
    } else {
      errorMessage.value = data.message || '生成失败，请重试'
    }
  } catch (error) {
    errorMessage.value = '服务器错误'
  } finally {
    loading.value = false
  }
}

const useSamplePlan = (sample) => {
  formData.value.destination = sample.destination
  formData.value.days = sample.days
  generatedPlan.value = sample
}

const resetForm = () => {
  generatedPlan.value = null
  formData.value = { destination: '', days: 5, budget: 10000, travelStyle: '' }
  interests.value = []
  errorMessage.value = ''
}
</script>

<style scoped>
.ai-plan-container {
  padding: 40px;
  background-color: #f5f7fa;
  min-height: 100vh;
  max-width: 1200px;
  margin: 0 auto;
}

.plan-header {
  text-align: center;
  margin-bottom: 40px;
}

.plan-header h2 {
  font-size: 28px;
  color: #333;
  margin-bottom: 10px;
  font-weight: 600;
}

.plan-header p {
  color: #666;
  font-size: 16px;
}

.plan-form-section {
  background: white;
  border-radius: 8px;
  padding: 30px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  margin-bottom: 40px;
}

.form-row {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}

.form-group {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.form-group label {
  font-weight: bold;
  margin-bottom: 8px;
  color: #333;
}

.form-group input,
.form-group select {
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 16px;
}

.form-group input:focus,
.form-group select:focus {
  border-color: #4CAF50;
  outline: none;
}

.interests-checkboxes {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.interests-checkboxes label {
  display: flex;
  align-items: center;
  font-weight: normal;
  margin: 0;
}

.interests-checkboxes input {
  margin-right: 8px;
}

.generate-btn {
  width: 100%;
  padding: 12px;
  background-color: #1976d2;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s;
  margin-top: 20px;
}

.generate-btn:hover {
  background-color: #1565c0;
}

.generate-btn:disabled {
  background-color: #90caf9;
  cursor: not-allowed;
}

.error-message {
  color: #f44336;
  margin-top: 15px;
  text-align: center;
}

.plan-result {
  background: white;
  border-radius: 8px;
  padding: 30px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  border-bottom: 2px solid #f0f0f0;
  padding-bottom: 15px;
}

.result-header h3 {
  color: #333;
  margin: 0;
}

.confidence {
  background-color: #fff3e0;
  padding: 8px 16px;
  border-radius: 4px;
  font-weight: 600;
  color: #e65100;
}

.plan-details {
  background-color: #f8f9fa;
  padding: 15px;
  border-radius: 4px;
  margin-bottom: 20px;
}

.detail-item {
  padding: 8px 0;
  color: #555;
}

.detail-item strong {
  color: #333;
}

.itinerary {
  margin: 30px 0;
  padding: 20px;
  background-color: #e3f2fd;
  border-left: 4px solid #1976d2;
  border-radius: 4px;
}

.itinerary h4 {
  margin-top: 0;
  color: #333;
}

.itinerary p {
  white-space: pre-wrap;
  line-height: 1.6;
  color: #555;
}

.highlights {
  margin: 30px 0;
}

.highlights h4 {
  color: #333;
  margin-bottom: 15px;
}

.highlight-items {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.highlight-item {
  padding: 12px;
  background-color: #f3e5f5;
  border-radius: 4px;
  color: #333;
  border-left: 3px solid #7b1fa2;
}

.action-buttons {
  display: flex;
  gap: 12px;
  margin-top: 30px;
}

.save-btn, .share-btn, .reset-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-weight: bold;
  transition: background-color 0.2s;
}

.save-btn {
  background-color: #4CAF50;
  color: white;
}

.save-btn:hover {
  background-color: #45a049;
}

.share-btn {
  background-color: #f57c00;
  color: white;
}

.share-btn:hover {
  background-color: #e65100;
}

.reset-btn {
  background-color: #f0f0f0;
  color: #333;
}

.reset-btn:hover {
  background-color: #e0e0e0;
}

.sample-plans {
  background: white;
  border-radius: 8px;
  padding: 30px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.sample-plans h3 {
  color: #333;
  margin-bottom: 20px;
}

.plans-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.sample-card {
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 20px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.sample-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 16px rgba(0,0,0,0.1);
}

.sample-card h4 {
  margin-top: 0;
  color: #333;
}

.sample-card p {
  color: #666;
  margin: 8px 0;
}

.use-btn {
  width: 100%;
  padding: 8px;
  background-color: #1976d2;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  margin-top: 12px;
  font-weight: 600;
  transition: background-color 0.2s;
}

.use-btn:hover {
  background-color: #1565c0;
}
</style>
