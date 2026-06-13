<template>
  <div class="settings-page">
    <header class="settings-header">
      <div class="header-left">
        <h1>个人设置与工具</h1>
      </div>
    </header>

    <div class="settings-container">
      <!-- Left Column: Skill Studio -->
      <div class="settings-column">
        <div class="pane-card">
          <div class="pane-header">
            <h2 class="pane-title">
              <span class="icon-indicator skill-color">
                <SvgIcon name="wrench" :size="18" />
              </span>
              智能技能工坊
            </h2>
            <button class="action-btn skill-btn" @click="openSkillModal">
              <SvgIcon name="plus" :size="14" />
              新建自定义技能
            </button>
          </div>

          <div v-if="loadingSkills" class="loading-state">
            <SvgIcon name="loader" :size="28" :spin="true" color="var(--color-primary)" />
            <span>正在同步技能库数据...</span>
          </div>

          <div v-else class="pane-content">
            <!-- If both lists are empty, show a single clean empty placeholder -->
            <div v-if="systemSkills.length === 0 && customSkills.length === 0" class="sub-section stretch-section">
              <h3 class="sub-title">我的专属智能技能</h3>
              <div class="empty-placeholder" @click="openSkillModal">
                <div class="empty-icon">
                  <SvgIcon name="plus-circle" :size="24" />
                </div>
                <h5>创建专属智能技能</h5>
                <p>点击此处添加您的个性化规划法则，使 Agent 具有针对您的特化领域知识。</p>
              </div>
            </div>

            <template v-else>
              <!-- Meta Controller Skill -->
              <div v-if="creatorSkill" class="sub-section meta-section">
                <h3 class="sub-title">元智能技能</h3>
                <div class="meta-skill-banner">
                  <div class="meta-banner-left">
                    <div class="meta-icon-wrap">
                      <SvgIcon name="wrench" :size="18" />
                    </div>
                    <div class="meta-info">
                      <div class="meta-header-row">
                        <h4>{{ creatorSkill.title }}</h4>
                      </div>
                      <p class="item-desc"><strong>激活条件：</strong>{{ formatDescription(creatorSkill.description) }}</p>
                    </div>
                  </div>
                  <div class="meta-banner-right">
                    <button class="text-link-btn" @click="viewSkill(creatorSkill)">查看指令手册</button>
                    <label class="switch">
                      <input 
                        type="checkbox" 
                        :checked="creatorSkill.isEnabled" 
                        @change="toggleSkill(creatorSkill)"
                        :disabled="updatingSkill === creatorSkill.id"
                      />
                      <span class="slider round"></span>
                    </label>
                  </div>
                </div>
              </div>

              <!-- System Skills -->
              <div v-if="systemSkills.length > 0" class="sub-section">
                <h3 class="sub-title">系统内置技能</h3>
                <div class="list-grid">
                  <div 
                    v-for="skill in systemSkills" 
                    :key="skill.id" 
                    class="tool-item skill-item system-item"
                    :class="{ disabled: !skill.isEnabled }"
                  >
                    <div class="item-header">
                      <span class="tool-icon skill-icon">
                        <SvgIcon name="sparkles" :size="16" />
                      </span>
                      <div class="item-meta">
                        <h4>{{ skill.title }}</h4>
                      </div>
                      <label class="switch">
                        <input 
                          type="checkbox" 
                          :checked="skill.isEnabled" 
                          @change="toggleSkill(skill)"
                          :disabled="updatingSkill === skill.id"
                        />
                        <span class="slider round"></span>
                      </label>
                    </div>
                    <p class="item-desc"><strong>激活条件：</strong>{{ formatDescription(skill.description) }}</p>
                    <div class="item-footer">
                      <span class="badge">通用推荐</span>
                      <button class="text-link-btn" @click="viewSkill(skill)">查看指令手册</button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Custom Skills -->
              <div class="sub-section" :class="{ 'stretch-section': customSkills.length === 0 }">
                <h3 class="sub-title">我的自定义技能</h3>
                <div v-if="customSkills.length === 0" class="empty-placeholder" @click="openSkillModal">
                  <div class="empty-icon">
                    <SvgIcon name="plus-circle" :size="24" />
                  </div>
                  <h5>创建专属规划套路</h5>
                  <p>点击此处添加您的个性化规划法则，使 Agent 具有针对您的特化领域知识。</p>
                </div>
                <div v-else class="list-grid">
                  <div 
                    v-for="skill in customSkills" 
                    :key="skill.id" 
                    class="tool-item skill-item custom-item"
                    :class="{ disabled: !skill.isEnabled }"
                  >
                    <div class="item-header">
                      <span class="tool-icon skill-icon custom">
                        <SvgIcon name="wrench" :size="16" />
                      </span>
                      <div class="item-meta">
                        <h4>{{ skill.title }}</h4>
                      </div>
                      <label class="switch">
                        <input 
                          type="checkbox" 
                          :checked="skill.isEnabled" 
                          @change="toggleSkill(skill)"
                          :disabled="updatingSkill === skill.id"
                        />
                        <span class="slider round"></span>
                      </label>
                    </div>
                    <p class="item-desc"><strong>激活条件：</strong>{{ formatDescription(skill.description) }}</p>
                    <div class="item-footer">
                      <button class="delete-btn" @click="confirmDeleteSkill(skill)">
                        <SvgIcon name="trash" :size="12" />
                        删除
                      </button>
                      <button class="text-link-btn" @click="editSkill(skill)">编辑技能</button>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- Right Column: Memory Space -->
      <div class="settings-column">
        <div class="pane-card">
          <div class="pane-header">
            <h2 class="pane-title">
              <span class="icon-indicator memory-color">
                <SvgIcon name="brain" :size="18" />
              </span>
              个性化偏好记忆
            </h2>
            <button class="action-btn memory-btn" @click="openMemoryModal()">
              <SvgIcon name="plus" :size="14" />
              添加偏好记忆
            </button>
          </div>

          <div v-if="loadingMemories" class="loading-state">
            <SvgIcon name="loader" :size="28" :spin="true" color="#457b9d" />
            <span>正在加载您的偏好记忆...</span>
          </div>

          <div v-else class="pane-content">
            <div class="sub-section" :class="{ 'stretch-section': memories.length === 0 }">
              <h3 class="sub-title">我的个人特征与偏好习惯</h3>
              <div v-if="memories.length === 0" class="empty-placeholder" @click="openMemoryModal()">
                <div class="empty-icon">
                  <SvgIcon name="brain" :size="24" color="#457b9d" />
                </div>
                <h5>让 Agent 更懂您</h5>
                <p>在此输入您的个人喜好、忌口、预算倾向或住宿要求，Agent 在生成规划时将默默遵循。</p>
              </div>
              <div v-else class="list-grid flex-list">
                <div 
                  v-for="mem in memories" 
                  :key="mem.id" 
                  class="tool-item memory-item"
                  :class="{ disabled: !mem.isEnabled }"
                >
                  <div class="memory-card-content">
                    <div class="memory-main">
                      <p class="memory-text">{{ mem.content }}</p>
                      <span class="memory-time">保存于 {{ formatDate(mem.createdAt || mem.updatedAt) }}</span>
                    </div>
                    
                    <div class="memory-actions">
                      <label class="switch small">
                        <input 
                          type="checkbox" 
                          :checked="mem.isEnabled" 
                          @change="toggleMemory(mem)"
                          :disabled="updatingMemory === mem.id"
                        />
                        <span class="slider round"></span>
                      </label>
                      <button class="icon-action-btn edit" title="编辑" @click="openMemoryModal(mem)">
                        <SvgIcon name="edit" :size="14" />
                      </button>
                      <button class="icon-action-btn delete" title="删除" @click="confirmDeleteMemory(mem)">
                        <SvgIcon name="trash" :size="14" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Skill Create/Edit Modal -->
    <div v-if="skillModalVisible" class="modal-backdrop" @click.self="closeSkillModal">
      <div class="settings-modal-content">
        <header class="modal-header">
          <h2>{{ isEditSkill ? '编辑自定义技能' : '新建自定义技能' }}</h2>
          <button class="close-btn" @click="closeSkillModal">
            <SvgIcon name="close" :size="16" />
          </button>
        </header>

        <form class="modal-form" @submit.prevent="saveSkill">
          <div class="form-row">
            <div class="form-group flex-1">
              <label>技能名称 (中文)</label>
              <input 
                v-model="skillForm.title" 
                class="form-input" 
                placeholder="例如：蜜月浪漫顾问" 
                required 
              />
            </div>
            <div class="form-group flex-1">
              <label>唯一英文标识</label>
              <input 
                v-model="skillForm.name" 
                class="form-input" 
                placeholder="如: honeymoon-specialist" 
                :disabled="isEditSkill"
                required 
              />
              <span class="hint">用于系统唯一标识，创建后不可更改，仅限小写英文与连字符</span>
            </div>
          </div>

          <div class="form-group">
            <label>激活条件</label>
            <textarea 
              v-model="skillForm.description" 
              class="form-textarea desc-textarea" 
              placeholder="极度关键！告知 Agent 应该在什么对话场景下激活此技能。例如：用户提到度蜜月、情侣游、求婚或浪漫旅游。"
              required
            ></textarea>
          </div>

          <div class="form-group">
            <label>技能指令手册</label>
            <textarea 
              v-model="skillForm.instructions" 
              class="form-textarea code-textarea" 
              placeholder="# 浪漫度蜜月专家规划准则\n1. 必须优先推荐海景/江景房并在备注中要求蜜月布置...\n2. 每日傍晚留出看日落的浪漫专属时间...\n3. 推荐富有情调的景观露台或米其林餐厅用餐..."
              required
            ></textarea>
            <span class="hint">输入具体的 Prompt 规划准则，支持 Markdown 格式。激活技能时将动态注入 AI 上下文。</span>
          </div>

          <footer class="modal-footer">
            <button type="button" class="cancel-btn" @click="closeSkillModal">取消</button>
            <button type="submit" class="submit-btn" :disabled="savingSkill">
              <span v-if="savingSkill">正在保存...</span>
              <span v-else>确认保存</span>
            </button>
          </footer>
        </form>
      </div>
    </div>

    <!-- Skill Instructions View Modal -->
    <div v-if="skillViewModalVisible" class="modal-backdrop" @click.self="closeSkillViewModal">
      <div class="settings-modal-content view-modal">
        <header class="modal-header">
          <h2>【{{ selectedSkill?.title }}】指令手册</h2>
          <button class="close-btn" @click="closeSkillViewModal">
            <SvgIcon name="close" :size="16" />
          </button>
        </header>
        <div class="view-body">
          <div class="meta-info">
            <div class="meta-item">
              <strong>唯一标识：</strong> <code>@{{ selectedSkill?.name }}</code>
            </div>
            <div class="meta-item">
              <strong>激活条件：</strong> {{ formatDescription(selectedSkill?.description) }}
            </div>
          </div>
          <div class="instructions-content">
            <pre>{{ selectedSkill?.instructions }}</pre>
          </div>
        </div>
      </div>
    </div>

    <!-- Memory Create/Edit Modal -->
    <div v-if="memoryModalVisible" class="modal-backdrop" @click.self="closeMemoryModal">
      <div class="settings-modal-content memory-modal">
        <header class="modal-header">
          <h2>{{ isEditMemory ? '编辑偏好记忆' : '添加偏好记忆' }}</h2>
          <button class="close-btn" @click="closeMemoryModal">
            <SvgIcon name="close" :size="16" />
          </button>
        </header>

        <form class="modal-form" @submit.prevent="saveMemory">
          <div class="form-group">
            <label>偏好记忆内容</label>
            <textarea 
              v-model="memoryForm.content" 
              class="form-textarea desc-textarea memory-textarea" 
              placeholder="请输入您的习惯偏好、出行习惯或身体状态。例如：“对海鲜严重过敏，在规划食物时避开海鲜餐厅” 或 “喜欢早起看日出，每天的行程可以安排得早一些”"
              required
            ></textarea>
            <span class="hint">输入您个人的事实，这会固化为 Agent 的前置记忆，并在每一次规划时隐式遵循。</span>
          </div>

          <footer class="modal-footer">
            <button type="button" class="cancel-btn" @click="closeMemoryModal">取消</button>
            <button type="submit" class="submit-btn memory-submit" :disabled="savingMemory">
              <span v-if="savingMemory">正在保存...</span>
              <span v-else>确认保存</span>
            </button>
          </footer>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import SvgIcon from '../components/SvgIcon.vue'

const formatDescription = (desc) => {
  if (!desc) return ''
  let cleaned = desc.trim()
  if (cleaned.startsWith('当') && (cleaned.endsWith('时激活。') || cleaned.endsWith('时激活') || cleaned.endsWith('时触发。') || cleaned.endsWith('时触发'))) {
    cleaned = cleaned.substring(1)
    if (cleaned.endsWith('时激活。') || cleaned.endsWith('时触发。')) {
      cleaned = cleaned.substring(0, cleaned.length - 4)
    } else {
      cleaned = cleaned.substring(0, cleaned.length - 3)
    }
  }
  return cleaned
}

const userId = 1 // Standard local user ID

// Loading States
const loadingSkills = ref(false)
const loadingMemories = ref(false)
const updatingSkill = ref(null)
const updatingMemory = ref(null)
const savingSkill = ref(false)
const savingMemory = ref(false)

// Skills Data
const creatorSkill = ref(null)
const systemSkills = ref([])
const customSkills = ref([])
const skillModalVisible = ref(false)
const isEditSkill = ref(false)
const skillViewModalVisible = ref(false)
const selectedSkill = ref(null)

const skillForm = reactive({
  id: null,
  name: '',
  title: '',
  description: '',
  instructions: '',
  isEnabled: true
})

// Memories Data
const memories = ref([])
const memoryModalVisible = ref(false)
const isEditMemory = ref(false)

const memoryForm = reactive({
  id: null,
  content: '',
  isEnabled: true
})

// ------------------- LOAD FUNCTIONS -------------------

const loadSkills = async () => {
  loadingSkills.value = true
  try {
    const res = await fetch(`/api/skills?userId=${userId}`)
    const data = await res.json()
    if (data.code === 200) {
      const all = data.data || []
      creatorSkill.value = all.find(s => s.name === 'skill-creator')
      systemSkills.value = all.filter(s => s.userId === null && s.name !== 'skill-creator')
      customSkills.value = all.filter(s => s.userId !== null)
    }
  } catch (e) {
    console.error("加载技能库失败:", e)
  }
  loadingSkills.value = false
}

const loadMemories = async () => {
  loadingMemories.value = true
  try {
    const res = await fetch(`/api/memories?userId=${userId}`)
    const data = await res.json()
    if (data.code === 200) {
      memories.value = data.data || []
    }
  } catch (e) {
    console.error("加载偏好记忆失败:", e)
  }
  loadingMemories.value = false
}

onMounted(() => {
  loadSkills()
  loadMemories()
})

// ------------------- SKILL ACTIONS -------------------

const toggleSkill = async (skill) => {
  updatingSkill.value = skill.id
  const targetStatus = !skill.isEnabled
  try {
    const res = await fetch(`/api/skills/${skill.id}/toggle?userId=${userId}&isEnabled=${targetStatus}`, {
      method: 'PUT'
    })
    const data = await res.json()
    if (data.code === 200) {
      skill.isEnabled = targetStatus
    } else {
      alert("修改状态失败: " + data.message)
    }
  } catch (e) {
    console.error("切换技能状态失败:", e)
  }
  updatingSkill.value = null
}

const openSkillModal = () => {
  isEditSkill.value = false
  skillForm.id = null
  skillForm.name = ''
  skillForm.title = ''
  skillForm.description = ''
  skillForm.instructions = ''
  skillForm.isEnabled = true
  skillModalVisible.value = true
}

const closeSkillModal = () => {
  skillModalVisible.value = false
}

const editSkill = (skill) => {
  isEditSkill.value = true
  skillForm.id = skill.id
  skillForm.name = skill.name
  skillForm.title = skill.title
  skillForm.description = skill.description
  skillForm.instructions = skill.instructions
  skillForm.isEnabled = skill.isEnabled
  skillModalVisible.value = true
}

const viewSkill = (skill) => {
  selectedSkill.value = skill
  skillViewModalVisible.value = true
}

const closeSkillViewModal = () => {
  skillViewModalVisible.value = false
  selectedSkill.value = null
}

const confirmDeleteSkill = async (skill) => {
  if (!confirm(`确认要删除自定义技能 @${skill.name} 吗？此操作无法撤销。`)) {
    return
  }
  try {
    const res = await fetch(`/api/skills/${skill.id}?userId=${userId}`, {
      method: 'DELETE'
    })
    const data = await res.json()
    if (data.code === 200) {
      customSkills.value = customSkills.value.filter(s => s.id !== skill.id)
    } else {
      alert("删除技能失败: " + data.message)
    }
  } catch (e) {
    console.error("删除技能失败:", e)
  }
}

const saveSkill = async () => {
  savingSkill.value = true
  try {
    const url = isEditSkill.value ? `/api/skills/${skillForm.id}?userId=${userId}` : `/api/skills?userId=${userId}`
    const method = isEditSkill.value ? 'PUT' : 'POST'
    
    if (!isEditSkill.value) {
      skillForm.name = skillForm.name.trim().toLowerCase().replace(/[^a-z0-9-]/g, '-')
    }

    const res = await fetch(url, {
      method: method,
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        name: skillForm.name,
        title: skillForm.title,
        description: skillForm.description,
        instructions: skillForm.instructions,
        isEnabled: skillForm.isEnabled
      })
    })

    const data = await res.json()
    if (data.code === 200) {
      skillModalVisible.value = false
      await loadSkills()
    } else {
      alert("保存技能失败: " + data.message)
    }
  } catch (e) {
    console.error("保存技能失败:", e)
  }
  savingSkill.value = false
}

// ------------------- MEMORY ACTIONS -------------------

const toggleMemory = async (mem) => {
  updatingMemory.value = mem.id
  const targetStatus = !mem.isEnabled
  try {
    const res = await fetch(`/api/memories/${mem.id}/toggle?userId=${userId}&isEnabled=${targetStatus}`, {
      method: 'PUT'
    })
    const data = await res.json()
    if (data.code === 200) {
      mem.isEnabled = targetStatus
    } else {
      alert("修改状态失败: " + data.message)
    }
  } catch (e) {
    console.error("切换记忆状态失败:", e)
  }
  updatingMemory.value = null
}

const openMemoryModal = (mem = null) => {
  if (mem) {
    isEditMemory.value = true
    memoryForm.id = mem.id
    memoryForm.content = mem.content
    memoryForm.isEnabled = mem.isEnabled
  } else {
    isEditMemory.value = false
    memoryForm.id = null
    memoryForm.content = ''
    memoryForm.isEnabled = true
  }
  memoryModalVisible.value = true
}

const closeMemoryModal = () => {
  memoryModalVisible.value = false
}

const confirmDeleteMemory = async (mem) => {
  if (!confirm(`确认要删除该条偏好记忆吗？此操作将无法撤销。`)) {
    return
  }
  try {
    const res = await fetch(`/api/memories/${mem.id}?userId=${userId}`, {
      method: 'DELETE'
    })
    const data = await res.json()
    if (data.code === 200) {
      memories.value = memories.value.filter(m => m.id !== mem.id)
    } else {
      alert("删除记忆失败: " + data.message)
    }
  } catch (e) {
    console.error("删除记忆失败:", e)
  }
}

const saveMemory = async () => {
  savingMemory.value = true
  try {
    const url = isEditMemory.value ? `/api/memories/${memoryForm.id}?userId=${userId}` : `/api/memories?userId=${userId}`
    const method = isEditMemory.value ? 'PUT' : 'POST'

    const res = await fetch(url, {
      method: method,
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        content: memoryForm.content,
        isEnabled: memoryForm.isEnabled
      })
    })

    const data = await res.json()
    if (data.code === 200) {
      memoryModalVisible.value = false
      await loadMemories()
    } else {
      alert("保存记忆失败: " + data.message)
    }
  } catch (e) {
    console.error("保存记忆失败:", e)
  }
  savingMemory.value = false
}

// Helper: Format Dates
const formatDate = (dateString) => {
  if (!dateString) return '刚刚'
  try {
    const date = new Date(dateString)
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  } catch (e) {
    return dateString
  }
}
</script>

<style scoped>
.settings-page {
  background: var(--color-bg);
  min-height: 100%;
  padding: 30px;
  font-family: var(--font-family);
  color: var(--color-body);
}

.settings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 24px 30px;
  margin-bottom: 30px;
  backdrop-filter: blur(16px);
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.03);
}

.settings-header h1 {
  font-size: 24px;
  color: var(--color-title);
  margin: 0 0 8px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.settings-header .subtitle {
  font-size: 14px;
  color: var(--color-hint);
  font-weight: 400;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.settings-header p.description {
  font-size: 13px;
  color: var(--color-secondary);
  margin: 0;
  max-width: 800px;
  line-height: 1.6;
}

/* Container Layout */
.settings-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 30px;
  align-items: stretch;
}

.settings-column {
  display: flex;
  flex-direction: column;
  height: 100%;
}

@media (max-width: 1100px) {
  .settings-container {
    grid-template-columns: 1fr;
  }
}

/* Pane card */
.pane-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: 0 4px 25px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  min-height: 500px;
  height: 100%;
  flex: 1;
  overflow: hidden;
}

.pane-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.02);
}

.pane-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-title);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
}

.skill-color {
  background: rgba(230, 57, 70, 0.08);
  color: var(--color-red-light);
}

.memory-color {
  background: rgba(69, 123, 157, 0.1);
  color: #457b9d;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--radius-pill);
  border: none;
  color: white;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.skill-btn {
  background: var(--gradient-brand);
  box-shadow: 0 4px 12px rgba(230, 57, 70, 0.2);
}

.skill-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(230, 57, 70, 0.35);
}

.memory-btn {
  background: linear-gradient(135deg, #457b9d, #1d3557);
  box-shadow: 0 4px 12px rgba(69, 123, 157, 0.2);
}

.memory-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(69, 123, 157, 0.35);
}

.pane-content {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 30px;
  flex: 1;
}

.sub-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stretch-section {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.empty-section-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.sub-title {
  font-size: 13.5px;
  color: var(--color-hint);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 0;
  font-weight: 600;
}

.list-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Tool items / Card Styling */
.tool-item {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: all 0.25s ease;
  position: relative;
}

.tool-item:hover {
  border-color: var(--color-hint);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
}

.tool-item.disabled {
  opacity: 0.6;
}

.item-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tool-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.skill-icon {
  background: rgba(230, 57, 70, 0.08);
  color: var(--color-red-light);
}

.skill-icon.custom {
  background: rgba(69, 123, 157, 0.1);
  color: #457b9d;
}

.item-meta {
  flex: 1;
}

.item-meta h4 {
  font-size: 14.5px;
  color: var(--color-title);
  margin: 0 0 2px;
  font-weight: 600;
}

.item-tag {
  font-size: 11px;
  color: var(--color-hint);
  font-family: monospace;
}

.item-desc {
  font-size: 12.5px;
  color: var(--color-secondary);
  line-height: 1.5;
  margin: 0;
}

.item-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px dashed var(--color-border);
  padding-top: 10px;
  margin-top: 2px;
}

.badge {
  font-size: 11px;
  color: var(--color-hint);
  background: var(--color-card);
  padding: 3px 8px;
  border-radius: 4px;
}

.text-link-btn {
  font-size: 12px;
  color: var(--color-secondary);
  background: none;
  border: none;
  cursor: pointer;
  font-weight: 500;
  transition: color 0.15s;
  padding: 0;
}

.text-link-btn:hover {
  color: var(--color-title);
  text-decoration: underline;
}

.delete-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-hint);
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.2s;
  padding: 0;
}

.delete-btn:hover {
  color: var(--color-red-light);
}

/* Switch styling */
.switch {
  position: relative;
  display: inline-block;
  width: 34px;
  height: 18px;
  flex-shrink: 0;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: var(--color-border);
  transition: .2s;
}

.slider:before {
  position: absolute;
  content: "";
  height: 12px;
  width: 12px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  transition: .2s;
}

input:checked + .slider {
  background-color: var(--color-red-light);
}

.custom-item input:checked + .slider {
  background-color: #457b9d;
}

.memory-item input:checked + .slider {
  background-color: #457b9d;
}

input:checked + .slider:before {
  transform: translateX(16px);
}

.slider.round {
  border-radius: 18px;
}

.slider.round:before {
  border-radius: 50%;
}

/* Loading & Empty placeholders */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 80px 0;
  color: var(--color-secondary);
  flex: 1;
}

.empty-placeholder {
  border: 2px dashed var(--color-border);
  border-radius: 12px;
  padding: 36px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  cursor: pointer;
  background: var(--color-surface);
  transition: all 0.2s;
  flex: 1;
}

.empty-placeholder:hover {
  border-color: var(--color-hint);
  background: rgba(230, 57, 70, 0.005);
}

.empty-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--color-card);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  color: var(--color-hint);
}

.empty-placeholder h5 {
  font-size: 14px;
  color: var(--color-title);
  margin: 0 0 6px;
  font-weight: 600;
}

.empty-placeholder p {
  font-size: 12px;
  color: var(--color-secondary);
  margin: 0;
  max-width: 280px;
  line-height: 1.5;
}

/* Memory Section Layout */
.memory-card-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.memory-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.memory-text {
  font-size: 13px;
  color: var(--color-title);
  line-height: 1.5;
  margin: 0;
  font-weight: 500;
}

.memory-time {
  font-size: 10.5px;
  color: var(--color-hint);
}

.memory-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-action-btn {
  background: none;
  border: none;
  color: var(--color-hint);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.icon-action-btn:hover {
  color: var(--color-title);
  background: var(--color-card);
}

.icon-action-btn.delete:hover {
  color: var(--color-red-light);
}

/* Modals */
.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: fadeIn 0.2s ease-out;
}

.settings-modal-content {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  width: 90%;
  max-width: 680px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
  animation: scaleIn 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.settings-modal-content.view-modal {
  max-width: 600px;
}

.settings-modal-content.memory-modal {
  max-width: 500px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h2 {
  font-size: 16px;
  color: var(--color-title);
  margin: 0;
  font-weight: 700;
}

.close-btn {
  background: none;
  border: none;
  color: var(--color-hint);
  cursor: pointer;
  transition: color 0.15s;
}

.close-btn:hover {
  color: var(--color-title);
}

.modal-form {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-row {
  display: flex;
  gap: 16px;
}

.flex-1 {
  flex: 1;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-title);
}

.form-input,
.form-textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 8px 12px;
  background: var(--color-surface);
  color: var(--color-body);
  font-size: 13px;
  font-family: inherit;
  transition: border-color 0.15s;
}

.form-textarea {
  resize: vertical;
}

.desc-textarea {
  height: 70px;
}

.memory-textarea {
  height: 100px;
}

.code-textarea {
  height: 180px;
  font-family: monospace;
  font-size: 12px;
  line-height: 1.5;
  background: #1e1e1e;
  color: #d4d4d4;
  border-color: #333;
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--color-red-light);
}

.settings-modal-content.memory-modal .form-textarea:focus {
  border-color: #457b9d;
}

.hint {
  font-size: 11px;
  color: var(--color-hint);
  line-height: 1.4;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--color-border);
}

.cancel-btn {
  padding: 8px 16px;
  border-radius: 8px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-secondary);
  font-size: 13px;
  cursor: pointer;
}

.submit-btn {
  padding: 8px 20px;
  border-radius: 8px;
  border: none;
  background: var(--gradient-brand);
  color: white;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 10px rgba(230, 57, 70, 0.15);
}

.submit-btn:hover {
  box-shadow: 0 6px 15px rgba(230, 57, 70, 0.25);
}

.memory-submit {
  background: linear-gradient(135deg, #457b9d, #1d3557);
  box-shadow: 0 4px 10px rgba(69, 123, 157, 0.15);
}

.memory-submit:hover {
  box-shadow: 0 6px 15px rgba(69, 123, 157, 0.25);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* View modal body */
.view-body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.meta-info {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12.5px;
}

.meta-info code {
  color: var(--color-red-light);
  font-family: monospace;
}

.instructions-content {
  background: #1e1e1e;
  border-radius: 8px;
  padding: 16px;
  max-height: 360px;
  overflow-y: auto;
  border: 1px solid #333;
}

.instructions-content pre {
  margin: 0;
  font-family: monospace;
  font-size: 12px;
  color: #d4d4d4;
  white-space: pre-wrap;
  line-height: 1.6;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes scaleIn {
  from { transform: scale(0.95); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

/* Meta Skill Banner Styles */
.meta-section {
  margin-bottom: 24px;
}

.meta-skill-banner {
  background: linear-gradient(135deg, rgba(230, 57, 70, 0.03), rgba(69, 123, 157, 0.03));
  border: 1px solid rgba(230, 57, 70, 0.15);
  border-radius: var(--radius-card);
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 15px rgba(230, 57, 70, 0.01);
}

.meta-skill-banner::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: 4px;
  background: var(--gradient-brand);
}

.meta-banner-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
}

.meta-icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: rgba(230, 57, 70, 0.08);
  color: var(--color-red-light);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.meta-info {
  flex: 1;
}

.meta-header-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}

.meta-header-row h4 {
  font-size: 14px;
  color: var(--color-title);
  margin: 0;
  font-weight: 700;
}

.meta-banner-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

@media (max-width: 600px) {
  .meta-skill-banner {
    flex-direction: column;
    align-items: stretch;
    padding: 16px;
  }
  .meta-banner-right {
    justify-content: space-between;
    margin-top: 10px;
  }
}
</style>
