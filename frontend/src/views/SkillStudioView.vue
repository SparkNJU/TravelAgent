<template>
  <div class="skills-page">
    <header class="skills-header">
      <div class="header-left">
        <h1>技能工坊</h1>
        <p class="description">在此动态开启或添加特殊的 AI 规划技能。技能基于 Anthropic Skills 范式，赋予 Agent 专属领域的决策与推理本领。</p>
      </div>
      <button class="create-btn" @click="openCreateModal">
        <SvgIcon name="plus" :size="16" />
        新建自定义技能
      </button>
    </header>

    <div v-if="loading" class="loading-state">
      <SvgIcon name="loader" :size="32" :spin="true" color="var(--color-primary)" />
      <span>正在同步技能库数据...</span>
    </div>

    <div v-else class="skills-content">
      <!-- Meta Controller Skill -->
      <section v-if="creatorSkill" class="skills-section meta-section">
        <h2 class="section-title">
          <SvgIcon name="sparkles" :size="18" />
          元智能技能
        </h2>
        <div class="meta-skill-banner">
          <div class="meta-banner-left">
            <div class="meta-icon-wrap">
              <SvgIcon name="wrench" :size="20" />
            </div>
            <div class="meta-info">
              <div class="meta-header-row">
                <h3>{{ creatorSkill.title }}</h3>
              </div>
              <p class="skill-desc"><strong>激活条件：</strong>{{ formatDescription(creatorSkill.description) }}</p>
            </div>
          </div>
          <div class="meta-banner-right">
            <button class="view-btn" @click="viewSkill(creatorSkill)">查看指令手册</button>
            <label class="switch">
              <input 
                type="checkbox" 
                :checked="creatorSkill.isEnabled" 
                @change="toggleSkill(creatorSkill)"
                :disabled="updating === creatorSkill.id"
              />
              <span class="slider round"></span>
            </label>
          </div>
        </div>
      </section>

      <!-- System Built-in Skills -->
      <section class="skills-section">
        <h2 class="section-title">
          <SvgIcon name="brain" :size="18" />
          系统内置技能
        </h2>
        <div class="skills-grid">
          <div 
            v-for="skill in systemSkills" 
            :key="skill.id" 
            class="skill-card system-card"
            :class="{ disabled: !skill.isEnabled }"
          >
            <div class="card-header">
              <span class="skill-icon-wrap">
                <SvgIcon name="sparkles" :size="18" />
              </span>
              <div class="skill-meta">
                <h3>{{ skill.title }}</h3>
              </div>
              <label class="switch">
                <input 
                  type="checkbox" 
                  :checked="skill.isEnabled" 
                  @change="toggleSkill(skill)"
                  :disabled="updating === skill.id"
                />
                <span class="slider round"></span>
              </label>
            </div>
            <p class="skill-desc"><strong>激活条件：</strong>{{ formatDescription(skill.description) }}</p>
            <div class="card-footer">
              <span class="skill-type">通用推荐</span>
              <button class="view-btn" @click="viewSkill(skill)">查看指令手册</button>
            </div>
          </div>
        </div>
      </section>

      <!-- Custom Private Skills -->
      <section class="skills-section">
        <h2 class="section-title">
          <SvgIcon name="wrench" :size="18" />
          我的自定义技能
        </h2>
        <div v-if="customSkills.length === 0" class="empty-custom-card" @click="openCreateModal">
          <div class="empty-icon-wrap">
            <SvgIcon name="plus-circle" :size="24" />
          </div>
          <h3>开启您的专属业务套路</h3>
          <p>点击这里录入您专属的攻略法则，让 Agent 成为您个人的特化助手。</p>
        </div>
        <div v-else class="skills-grid">
          <div 
            v-for="skill in customSkills" 
            :key="skill.id" 
            class="skill-card custom-card"
            :class="{ disabled: !skill.isEnabled }"
          >
            <div class="card-header">
              <span class="skill-icon-wrap custom-icon">
                <SvgIcon name="wrench" :size="18" />
              </span>
              <div class="skill-meta">
                <h3>{{ skill.title }}</h3>
              </div>
              <label class="switch">
                <input 
                  type="checkbox" 
                  :checked="skill.isEnabled" 
                  @change="toggleSkill(skill)"
                  :disabled="updating === skill.id"
                />
                <span class="slider round"></span>
              </label>
            </div>
            <p class="skill-desc"><strong>激活条件：</strong>{{ formatDescription(skill.description) }}</p>
            <div class="card-footer">
              <button class="delete-btn" @click="confirmDelete(skill)">
                <SvgIcon name="trash" :size="14" />
                删除
              </button>
              <div class="footer-right">
                <button class="edit-btn" @click="editSkill(skill)">编辑</button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- Skill Create/Edit Modal -->
    <div v-if="modalVisible" class="modal-backdrop" @click.self="closeModal">
      <div class="skill-modal-content">
        <header class="modal-header">
          <h2>{{ isEdit ? '编辑自定义技能' : '新建自定义技能' }}</h2>
          <button class="close-btn" @click="closeModal">
            <SvgIcon name="close" :size="16" />
          </button>
        </header>

        <form class="modal-form" @submit.prevent="saveSkill">
          <div class="form-row">
            <div class="form-group flex-1">
              <label>技能名称 (中文)</label>
              <input 
                v-model="form.title" 
                class="form-input" 
                placeholder="例如：蜜月浪漫顾问" 
                required 
              />
            </div>
            <div class="form-group flex-1">
              <label>唯一英文标识</label>
              <input 
                v-model="form.name" 
                class="form-input" 
                placeholder="如: honeymoon-specialist" 
                :disabled="isEdit"
                required 
              />
              <span class="hint">用于代码唯一辨别，创建后不可修改，仅限小写与连字符</span>
            </div>
          </div>

          <div class="form-group">
            <label>激活条件</label>
            <textarea 
              v-model="form.description" 
              class="form-textarea desc-textarea" 
              placeholder="极度关键！告知 Agent 应该在什么对话场景下激活此技能。例如：用户提到度蜜月、情侣游、求婚或浪漫旅游。"
              required
            ></textarea>
          </div>

          <div class="form-group">
            <label>技能指令手册</label>
            <textarea 
              v-model="form.instructions" 
              class="form-textarea code-textarea" 
              placeholder="# 浪漫度蜜月专家规划准则\n1. 必须优先推荐海景/江景房并在备注中要求蜜月布置...\n2. 每日傍晚留出看日落的浪漫专属时间...\n3. 推荐富有情调的景观露台或米其林餐厅用餐..."
              required
            ></textarea>
            <span class="hint">输入具体的 Prompt 规划准则，支持 Markdown 格式。激活技能时将动态注入 AI 上下文。</span>
          </div>

          <footer class="modal-footer">
            <button type="button" class="cancel-btn" @click="closeModal">取消</button>
            <button type="submit" class="submit-btn" :disabled="saving">
              <span v-if="saving">正在保存...</span>
              <span v-else>确认保存</span>
            </button>
          </footer>
        </form>
      </div>
    </div>

    <!-- Instructions View-Only Modal -->
    <div v-if="viewModalVisible" class="modal-backdrop" @click.self="closeViewModal">
      <div class="skill-modal-content view-modal">
        <header class="modal-header">
          <h2>【{{ selectedSkill?.title }}】指令手册</h2>
          <button class="close-btn" @click="closeViewModal">
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

const userId = 1 // Standard local user

const loading = ref(false)
const saving = ref(false)
const updating = ref(null)

const creatorSkill = ref(null)
const systemSkills = ref([])
const customSkills = ref([])

const modalVisible = ref(false)
const isEdit = ref(false)
const viewModalVisible = ref(false)
const selectedSkill = ref(null)

const form = reactive({
  id: null,
  name: '',
  title: '',
  description: '',
  instructions: '',
  isEnabled: true
})

const loadSkills = async () => {
  loading.value = true
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
  loading.value = false
}

onMounted(loadSkills)

const toggleSkill = async (skill) => {
  updating.value = skill.id
  const targetStatus = !skill.isEnabled
  try {
    const res = await fetch(`/api/skills/${skill.id}/toggle?userId=${userId}&isEnabled=${targetStatus}`, {
      method: 'PUT'
    })
    const data = await res.json()
    if (data.code === 200) {
      skill.isEnabled = targetStatus
    } else {
      alert("修改失败: " + data.message)
    }
  } catch (e) {
    console.error("状态切换失败:", e)
  }
  updating.value = null
}

const openCreateModal = () => {
  isEdit.value = false
  form.id = null
  form.name = ''
  form.title = ''
  form.description = ''
  form.instructions = ''
  form.isEnabled = true
  modalVisible.value = true
}

const closeModal = () => {
  modalVisible.value = false
}

const editSkill = (skill) => {
  isEdit.value = true
  form.id = skill.id
  form.name = skill.name
  form.title = skill.title
  form.description = skill.description
  form.instructions = skill.instructions
  form.isEnabled = skill.isEnabled
  modalVisible.value = true
}

const viewSkill = (skill) => {
  selectedSkill.value = skill
  viewModalVisible.value = true
}

const closeViewModal = () => {
  viewModalVisible.value = false
  selectedSkill.value = null
}

const confirmDelete = async (skill) => {
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
      alert("删除失败: " + data.message)
    }
  } catch (e) {
    console.error("删除失败:", e)
  }
}

const saveSkill = async () => {
  saving.value = true
  try {
    const url = isEdit.value ? `/api/skills/${form.id}?userId=${userId}` : `/api/skills?userId=${userId}`
    const method = isEdit.value ? 'PUT' : 'POST'
    
    // Clean identifier format
    if (!isEdit.value) {
      form.name = form.name.trim().toLowerCase().replace(/[^a-z0-9-]/g, '-')
    }

    const res = await fetch(url, {
      method: method,
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        name: form.name,
        title: form.title,
        description: form.description,
        instructions: form.instructions,
        isEnabled: form.isEnabled
      })
    })

    const data = await res.json()
    if (data.code === 200) {
      modalVisible.value = false
      await loadSkills()
    } else {
      alert("保存失败: " + data.message)
    }
  } catch (e) {
    console.error("保存失败:", e)
  }
  saving.value = false
}
</script>

<style scoped>
.skills-page {
  background: var(--color-bg);
  min-height: 100%;
  padding: 30px;
  font-family: var(--font-family);
  color: var(--color-body);
}

.skills-header {
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

.skills-header h1 {
  font-size: 24px;
  color: var(--color-title);
  margin: 0 0 8px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.skills-header .subtitle {
  font-size: 14px;
  color: var(--color-hint);
  font-weight: 400;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.skills-header p.description {
  font-size: 13px;
  color: var(--color-secondary);
  margin: 0;
  max-width: 600px;
  line-height: 1.5;
}

.create-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: var(--radius-pill);
  border: none;
  background: var(--gradient-brand);
  color: white;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 15px rgba(230, 57, 70, 0.2);
  transition: all 0.25s;
}

.create-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(230, 57, 70, 0.35);
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 100px 0;
  color: var(--color-secondary);
}

.skills-content {
  display: flex;
  flex-direction: column;
  gap: 40px;
}

.skills-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.section-title {
  font-size: 16px;
  color: var(--color-title);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}

.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.skill-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  position: relative;
  overflow: hidden;
}

.skill-card::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--gradient-brand);
  opacity: 0.8;
}

.skill-card.custom-card::after {
  background: linear-gradient(90deg, #457b9d, #1d3557);
}

.skill-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.05);
  border-color: rgba(230, 57, 70, 0.3);
}

.skill-card.custom-card:hover {
  border-color: rgba(69, 123, 157, 0.4);
}

.skill-card.disabled {
  opacity: 0.65;
}

.skill-card.disabled::after {
  background: #a8dadc;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.skill-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: rgba(230, 57, 70, 0.08);
  color: var(--color-red-light);
  display: flex;
  align-items: center;
  justify-content: center;
}

.skill-icon-wrap.custom-icon {
  background: rgba(69, 123, 157, 0.1);
  color: #457b9d;
}

.skill-meta {
  flex: 1;
}

.skill-meta h3 {
  font-size: 15px;
  color: var(--color-title);
  margin: 0 0 2px;
  font-weight: 600;
}

.skill-name-tag {
  font-size: 11px;
  color: var(--color-hint);
  font-family: monospace;
}

.skill-desc {
  font-size: 12.5px;
  color: var(--color-secondary);
  line-height: 1.5;
  margin: 0;
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.skill-type {
  font-size: 11px;
  color: var(--color-hint);
  background: var(--color-surface);
  padding: 4px 8px;
  border-radius: 6px;
}

.view-btn,
.edit-btn {
  font-size: 12px;
  color: var(--color-secondary);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s;
}

.view-btn:hover,
.edit-btn:hover {
  background: var(--color-card);
  color: var(--color-title);
  border-color: var(--color-hint);
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
}

.delete-btn:hover {
  color: var(--color-red-light);
}

/* Switch styling */
.switch {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 20px;
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
  transition: .25s;
}

.slider:before {
  position: absolute;
  content: "";
  height: 14px;
  width: 14px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  transition: .25s;
}

input:checked + .slider {
  background-color: var(--color-red-light);
}

input:checked + .slider:before {
  transform: translateX(16px);
}

.slider.round {
  border-radius: 20px;
}

.slider.round:before {
  border-radius: 50%;
}

/* Custom empty card */
.empty-custom-card {
  border: 2px dashed var(--color-border);
  border-radius: var(--radius-card);
  padding: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  cursor: pointer;
  background: var(--color-card);
  opacity: 0.8;
  transition: all 0.25s;
}

.empty-custom-card:hover {
  border-color: var(--color-hint);
  opacity: 1;
  background: rgba(230, 57, 70, 0.01);
}

.empty-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--color-surface);
  color: var(--color-hint);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  box-shadow: 0 4px 10px rgba(0,0,0,0.02);
}

.empty-custom-card h3 {
  font-size: 14.5px;
  color: var(--color-title);
  margin: 0 0 6px;
}

.empty-custom-card p {
  font-size: 12px;
  color: var(--color-secondary);
  margin: 0;
  max-width: 320px;
}

/* Modal Backgrop */
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

.skill-modal-content {
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

.skill-modal-content.view-modal {
  max-width: 600px;
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

.hint {
  font-size: 11px;
  color: var(--color-hint);
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
  margin-bottom: 10px;
}

.meta-skill-banner {
  background: linear-gradient(135deg, rgba(230, 57, 70, 0.04), rgba(69, 123, 157, 0.04));
  border: 1px solid rgba(230, 57, 70, 0.15);
  border-radius: var(--radius-card);
  padding: 20px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(230, 57, 70, 0.02);
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
  width: 44px;
  height: 44px;
  border-radius: 10px;
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

.meta-header-row h3 {
  font-size: 15px;
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
