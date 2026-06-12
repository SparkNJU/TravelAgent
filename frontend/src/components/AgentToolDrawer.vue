<template>
  <Teleport to="body">
    <Transition name="tool-fade">
      <div v-if="drawerOpen" class="tool-overlay" @click="closeToolDrawer" />
    </Transition>

    <Transition name="tool-slide">
      <aside v-if="drawerOpen" class="tool-drawer" role="dialog" aria-modal="true" @click.stop>
        <header class="drawer-header">
          <div class="drawer-title-block">
            <div class="drawer-kicker">Agent Tools</div>
            <h2>{{ drawerTitle }}</h2>
            <p>{{ drawerDescription }}</p>
          </div>
          <button class="icon-button" type="button" title="关闭" @click="closeToolDrawer">
            <SvgIcon name="close" :size="16" />
          </button>
        </header>

        <div class="drawer-tabs">
          <button
            type="button"
            class="drawer-tab"
            :class="{ active: activeTab === 'skills' }"
            @click="openTab('skills')"
          >
            <SvgIcon name="wrench" :size="16" />
            <span>Skills</span>
            <small>{{ skillsSummary.enabled }}/{{ skillsSummary.total }}</small>
          </button>
          <button
            type="button"
            class="drawer-tab"
            :class="{ active: activeTab === 'memory' }"
            @click="openTab('memory')"
          >
            <SvgIcon name="brain" :size="16" />
            <span>Memory</span>
            <small>{{ memoriesSummary.enabled }}/{{ memoriesSummary.total }}</small>
          </button>
        </div>

        <div class="drawer-toolbar">
          <label class="search-field">
            <SvgIcon name="search" :size="15" />
            <input
              v-model="query"
              type="search"
              :placeholder="activeTab === 'skills' ? '搜索技能名称或描述' : '搜索记忆内容'"
            />
          </label>
        </div>

        <section v-if="activeTab === 'skills'" class="drawer-section">
          <div class="section-head">
            <div>
              <h3>技能管理</h3>
              <p>系统技能与自定义技能统一管理，启停状态会直接影响 Agent 规划行为。</p>
            </div>
            <button class="primary-action" type="button" @click="openSkillEditor()">
              <SvgIcon name="plus" :size="14" />
              新建技能
            </button>
          </div>

          <div class="filter-row">
            <button
              v-for="item in skillFilters"
              :key="item.value"
              type="button"
              class="filter-chip"
              :class="{ active: skillFilter === item.value }"
              @click="skillFilter = item.value"
            >
              {{ item.label }}
            </button>
          </div>

          <div v-if="skillLoading" class="loading-state">
            <SvgIcon name="loader" :size="24" :spin="true" />
            <span>正在加载技能库...</span>
          </div>

          <div v-else class="drawer-list">
            <div v-if="skillEditorVisible" class="editor-card">
              <div class="editor-head">
                <strong>{{ isEditingSkill ? '编辑技能' : '新建技能' }}</strong>
                <button class="ghost-btn" type="button" @click="closeSkillEditor">取消</button>
              </div>
              <form class="editor-form" @submit.prevent="saveSkill">
                <label class="field">
                  <span>技能名称</span>
                  <input v-model="skillForm.title" type="text" placeholder="蜜月行程顾问" required />
                </label>
                <label class="field">
                  <span>唯一标识</span>
                  <input
                    v-model="skillForm.name"
                    type="text"
                    placeholder="honeymoon-specialist"
                    :disabled="isEditingSkill"
                    required
                  />
                </label>
                <label class="field">
                  <span>触发场景</span>
                  <textarea v-model="skillForm.description" rows="3" placeholder="当用户提到..." required />
                </label>
                <label class="field">
                  <span>指令手册</span>
                  <textarea v-model="skillForm.instructions" rows="7" placeholder="# SKILL.md ..." required />
                </label>
                <label class="switch-row">
                  <input v-model="skillForm.isEnabled" type="checkbox" />
                  <span>启用该技能</span>
                </label>
                <button class="primary-action wide" type="submit" :disabled="savingSkill">
                  <SvgIcon name="check" :size="14" />
                  {{ savingSkill ? '保存中...' : '保存技能' }}
                </button>
              </form>
            </div>

            <article
              v-for="skill in filteredSkills"
              :key="skill.id"
              class="tool-card"
              :class="{ disabled: !skill.isEnabled }"
            >
              <div class="tool-card-head">
                <div class="tool-badge" :class="{ custom: skill.userId !== null }">
                  <SvgIcon :name="skill.userId === null ? 'sparkles' : 'wrench'" :size="14" />
                </div>
                <div class="tool-meta">
                  <strong>{{ skill.title }}</strong>
                  <span>@{{ skill.name }}</span>
                </div>
                <label class="toggle">
                  <input
                    type="checkbox"
                    :checked="skill.isEnabled"
                    :disabled="updatingSkill === skill.id"
                    @change="toggleSkill(skill)"
                  />
                  <span />
                </label>
              </div>
              <p class="tool-desc">{{ skill.description }}</p>
              <details class="detail-block">
                <summary>查看指令</summary>
                <pre>{{ skill.instructions }}</pre>
              </details>
              <div class="tool-actions">
                <button class="ghost-btn" type="button" @click="openSkillEditor(skill)">编辑</button>
                <button v-if="skill.userId !== null" class="danger-btn" type="button" @click="removeSkill(skill)">
                  删除
                </button>
              </div>
            </article>

            <div v-if="!filteredSkills.length" class="empty-state">
              <SvgIcon name="sparkles" :size="24" />
              <strong>没有匹配的技能</strong>
              <span>调整筛选或搜索条件。</span>
            </div>
          </div>
        </section>

        <section v-else class="drawer-section">
          <div class="section-head">
            <div>
              <h3>记忆管理</h3>
              <p>记忆会在对话和规划中作为稳定偏好输入使用。</p>
            </div>
            <button class="primary-action" type="button" @click="openMemoryEditor()">
              <SvgIcon name="plus" :size="14" />
              新增记忆
            </button>
          </div>

          <div class="filter-row">
            <button
              v-for="item in memoryFilters"
              :key="item.value"
              type="button"
              class="filter-chip"
              :class="{ active: memoryFilter === item.value }"
              @click="memoryFilter = item.value"
            >
              {{ item.label }}
            </button>
          </div>

          <div v-if="memoryLoading" class="loading-state">
            <SvgIcon name="loader" :size="24" :spin="true" />
            <span>正在加载记忆...</span>
          </div>

          <div v-else class="drawer-list">
            <div v-if="memoryEditorVisible" class="editor-card">
              <div class="editor-head">
                <strong>{{ isEditingMemory ? '编辑记忆' : '新增记忆' }}</strong>
                <button class="ghost-btn" type="button" @click="closeMemoryEditor">取消</button>
              </div>
              <form class="editor-form" @submit.prevent="saveMemory">
                <label class="field">
                  <span>记忆内容</span>
                  <textarea v-model="memoryForm.content" rows="7" placeholder="请输入长期偏好..." required />
                </label>
                <label class="switch-row">
                  <input v-model="memoryForm.isEnabled" type="checkbox" />
                  <span>启用该记忆</span>
                </label>
                <button class="primary-action wide" type="submit" :disabled="savingMemory">
                  <SvgIcon name="check" :size="14" />
                  {{ savingMemory ? '保存中...' : '保存记忆' }}
                </button>
              </form>
            </div>

            <article
              v-for="memory in filteredMemories"
              :key="memory.id"
              class="tool-card memory-card"
              :class="{ disabled: !memory.isEnabled }"
            >
              <div class="tool-card-head">
                <div class="tool-badge memory">
                  <SvgIcon name="brain" :size="14" />
                </div>
                <div class="tool-meta">
                  <strong>偏好记忆</strong>
                  <span>{{ formatDate(memory.createdAt || memory.updatedAt) }}</span>
                </div>
                <label class="toggle">
                  <input
                    type="checkbox"
                    :checked="memory.isEnabled"
                    :disabled="updatingMemory === memory.id"
                    @change="toggleMemory(memory)"
                  />
                  <span />
                </label>
              </div>
              <p class="tool-desc">{{ memory.content }}</p>
              <div class="tool-actions">
                <button class="ghost-btn" type="button" @click="openMemoryEditor(memory)">编辑</button>
                <button class="danger-btn" type="button" @click="removeMemory(memory)">删除</button>
              </div>
            </article>

            <div v-if="!filteredMemories.length" class="empty-state">
              <SvgIcon name="brain" :size="24" />
              <strong>没有匹配的记忆</strong>
              <span>调整筛选或搜索条件。</span>
            </div>
          </div>
        </section>
      </aside>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import SvgIcon from './SvgIcon.vue'
import { useAgentTools } from '../composables/useAgentTools'

const {
  activeToolDrawer,
  drawerOpen,
  skillsSummary,
  memoriesSummary,
  closeToolDrawer,
  updateSkillsSummary,
  updateMemoriesSummary,
} = useAgentTools()

const skillLoading = ref(false)
const memoryLoading = ref(false)
const updatingSkill = ref(null)
const updatingMemory = ref(null)
const savingSkill = ref(false)
const savingMemory = ref(false)
const query = ref('')
const skillFilter = ref('all')
const memoryFilter = ref('all')
const skills = ref([])
const memories = ref([])
const skillEditorVisible = ref(false)
const memoryEditorVisible = ref(false)
const isEditingSkill = ref(false)
const isEditingMemory = ref(false)

const skillForm = reactive({
  id: null,
  name: '',
  title: '',
  description: '',
  instructions: '',
  isEnabled: true,
})

const memoryForm = reactive({
  id: null,
  content: '',
  isEnabled: true,
})

const activeTab = computed(() => activeToolDrawer.value || 'skills')
const drawerTitle = computed(() => (activeTab.value === 'skills' ? '技能管理' : '记忆管理'))
const drawerDescription = computed(() => (
  activeTab.value === 'skills'
    ? '把可复用的决策规则放在这里，Agent 会在规划时自动引用。'
    : '把稳定偏好和约束放在这里，让规划结果更贴近个人习惯。'
))

const skillFilters = [
  { value: 'all', label: '全部' },
  { value: 'enabled', label: '已启用' },
  { value: 'custom', label: '自定义' },
]

const memoryFilters = [
  { value: 'all', label: '全部' },
  { value: 'enabled', label: '已启用' },
  { value: 'disabled', label: '已停用' },
]

function openTab(type) {
  activeToolDrawer.value = type
}

const userId = computed(() => {
  if (typeof window === 'undefined') return '1'
  return window.localStorage.getItem('userId') || '1'
})

const filteredSkills = computed(() => {
  const needle = query.value.trim().toLowerCase()
  return skills.value.filter((item) => {
    if (skillFilter.value === 'enabled' && !item.isEnabled) return false
    if (skillFilter.value === 'custom' && item.userId === null) return false
    if (needle) {
      const text = [item.title, item.name, item.description, item.instructions].join(' ').toLowerCase()
      if (!text.includes(needle)) return false
    }
    return true
  })
})

const filteredMemories = computed(() => {
  const needle = query.value.trim().toLowerCase()
  return memories.value.filter((item) => {
    if (memoryFilter.value === 'enabled' && !item.isEnabled) return false
    if (memoryFilter.value === 'disabled' && item.isEnabled) return false
    if (needle && !String(item.content || '').toLowerCase().includes(needle)) return false
    return true
  })
})

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options)
  const data = await response.json()
  if (data.code !== 200) {
    throw new Error(data.message || '请求失败')
  }
  return data.data
}

async function loadSkills() {
  skillLoading.value = true
  try {
    const data = await fetchJson(`/api/skills?userId=${userId.value}`)
    skills.value = Array.isArray(data) ? data : []
    updateSkillsSummary(skills.value)
  } catch (error) {
    console.error('加载技能失败:', error)
  } finally {
    skillLoading.value = false
  }
}

async function loadMemories() {
  memoryLoading.value = true
  try {
    const data = await fetchJson(`/api/memories?userId=${userId.value}`)
    memories.value = Array.isArray(data) ? data : []
    updateMemoriesSummary(memories.value)
  } catch (error) {
    console.error('加载记忆失败:', error)
  } finally {
    memoryLoading.value = false
  }
}

function openSkillEditor(skill = null) {
  skillEditorVisible.value = true
  isEditingSkill.value = Boolean(skill)
  skillForm.id = skill?.id ?? null
  skillForm.name = skill?.name || ''
  skillForm.title = skill?.title || ''
  skillForm.description = skill?.description || ''
  skillForm.instructions = skill?.instructions || ''
  skillForm.isEnabled = skill?.isEnabled ?? true
}

function closeSkillEditor() {
  skillEditorVisible.value = false
}

function openMemoryEditor(memory = null) {
  memoryEditorVisible.value = true
  isEditingMemory.value = Boolean(memory)
  memoryForm.id = memory?.id ?? null
  memoryForm.content = memory?.content || ''
  memoryForm.isEnabled = memory?.isEnabled ?? true
}

function closeMemoryEditor() {
  memoryEditorVisible.value = false
}

async function toggleSkill(skill) {
  updatingSkill.value = skill.id
  try {
    const targetStatus = !skill.isEnabled
    await fetchJson(
      `/api/skills/${skill.id}/toggle?userId=${userId.value}&isEnabled=${targetStatus}`,
      { method: 'PUT' },
    )
    skill.isEnabled = targetStatus
    updateSkillsSummary(skills.value)
  } catch (error) {
    console.error('切换技能状态失败:', error)
  } finally {
    updatingSkill.value = null
  }
}

async function saveSkill() {
  savingSkill.value = true
  try {
    const isEdit = isEditingSkill.value
    const url = isEdit ? `/api/skills/${skillForm.id}?userId=${userId.value}` : `/api/skills?userId=${userId.value}`
    const method = isEdit ? 'PUT' : 'POST'
    const payload = {
      name: isEdit ? skillForm.name : skillForm.name.trim().toLowerCase().replace(/[^a-z0-9-]/g, '-'),
      title: skillForm.title,
      description: skillForm.description,
      instructions: skillForm.instructions,
      isEnabled: skillForm.isEnabled,
    }
    await fetchJson(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    await loadSkills()
    skillEditorVisible.value = false
  } catch (error) {
    console.error('保存技能失败:', error)
    alert(error.message || '保存技能失败')
  } finally {
    savingSkill.value = false
  }
}

async function removeSkill(skill) {
  if (!confirm(`确认删除自定义技能 @${skill.name} 吗？`)) return
  try {
    await fetchJson(`/api/skills/${skill.id}?userId=${userId.value}`, { method: 'DELETE' })
    await loadSkills()
  } catch (error) {
    console.error('删除技能失败:', error)
    alert(error.message || '删除技能失败')
  }
}

async function toggleMemory(memory) {
  updatingMemory.value = memory.id
  try {
    const targetStatus = !memory.isEnabled
    await fetchJson(
      `/api/memories/${memory.id}/toggle?userId=${userId.value}&isEnabled=${targetStatus}`,
      { method: 'PUT' },
    )
    memory.isEnabled = targetStatus
    updateMemoriesSummary(memories.value)
  } catch (error) {
    console.error('切换记忆状态失败:', error)
  } finally {
    updatingMemory.value = null
  }
}

async function saveMemory() {
  savingMemory.value = true
  try {
    const isEdit = isEditingMemory.value
    const url = isEdit ? `/api/memories/${memoryForm.id}?userId=${userId.value}` : `/api/memories?userId=${userId.value}`
    const method = isEdit ? 'PUT' : 'POST'
    await fetchJson(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        content: memoryForm.content,
        isEnabled: memoryForm.isEnabled,
      }),
    })
    await loadMemories()
    memoryEditorVisible.value = false
  } catch (error) {
    console.error('保存记忆失败:', error)
    alert(error.message || '保存记忆失败')
  } finally {
    savingMemory.value = false
  }
}

async function removeMemory(memory) {
  if (!confirm('确认删除这条偏好记忆吗？')) return
  try {
    await fetchJson(`/api/memories/${memory.id}?userId=${userId.value}`, { method: 'DELETE' })
    await loadMemories()
  } catch (error) {
    console.error('删除记忆失败:', error)
    alert(error.message || '删除记忆失败')
  }
}

function formatDate(dateString) {
  if (!dateString) return '刚刚'
  try {
    const date = new Date(dateString)
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  } catch {
    return dateString
  }
}

watch(drawerOpen, (open) => {
  if (!open) {
    query.value = ''
    skillFilter.value = 'all'
    memoryFilter.value = 'all'
    skillEditorVisible.value = false
    memoryEditorVisible.value = false
    return
  }
  if (!skills.value.length && !skillLoading.value) loadSkills()
  if (!memories.value.length && !memoryLoading.value) loadMemories()
})

onMounted(() => {
  loadSkills()
  loadMemories()
})
</script>

<style scoped>
.tool-overlay {
  position: fixed;
  inset: 0;
  z-index: 2400;
  background: rgba(17, 24, 39, 0.24);
  backdrop-filter: blur(2px);
}

.tool-drawer {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 2410;
  display: flex;
  flex-direction: column;
  width: min(520px, 100vw);
  height: 100vh;
  padding: 18px 18px 16px;
  border-left: 1px solid rgba(17, 24, 39, 0.08);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(255, 247, 248, 0.96));
  box-shadow: -22px 0 60px rgba(17, 24, 39, 0.14);
  overflow: hidden;
}

:root[data-theme="dark"] .tool-drawer {
  border-left-color: rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, rgba(18, 18, 20, 0.98), rgba(14, 14, 16, 0.98));
  box-shadow: -22px 0 60px rgba(0, 0, 0, 0.35);
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(17, 24, 39, 0.08);
}

:root[data-theme="dark"] .drawer-header {
  border-bottom-color: rgba(255, 255, 255, 0.08);
}

.drawer-title-block {
  min-width: 0;
}

.drawer-kicker {
  margin-bottom: 6px;
  color: var(--color-red);
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.drawer-title-block h2 {
  margin: 0 0 6px;
  color: var(--color-title);
  font-size: 20px;
  line-height: 1.2;
}

.drawer-title-block p {
  margin: 0;
  color: var(--color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

:root[data-theme="dark"] .drawer-title-block p,
:root[data-theme="dark"] .section-head p,
:root[data-theme="dark"] .tool-desc,
:root[data-theme="dark"] .empty-state {
  color: var(--color-secondary);
}

.icon-button,
.ghost-btn,
.danger-btn,
.primary-action,
.filter-chip {
  font-family: var(--font-family);
}

.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-card);
  color: var(--color-secondary);
}

:root[data-theme="dark"] .icon-button,
:root[data-theme="dark"] .drawer-tab,
:root[data-theme="dark"] .search-field,
:root[data-theme="dark"] .filter-chip,
:root[data-theme="dark"] .editor-card,
:root[data-theme="dark"] .tool-card,
:root[data-theme="dark"] .field input,
:root[data-theme="dark"] .field textarea,
:root[data-theme="dark"] .ghost-btn,
:root[data-theme="dark"] .danger-btn {
  background: var(--color-card);
  border-color: var(--color-border);
  color: var(--color-title);
}

.drawer-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 14px 0 12px;
}

.drawer-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-card);
  color: var(--color-secondary);
  font-size: 13px;
  font-weight: 900;
}

.drawer-tab small {
  margin-left: auto;
  color: var(--color-hint);
  font-size: 11px;
  font-weight: 800;
}

.drawer-tab.active {
  border-color: rgba(255, 36, 66, 0.24);
  background: #fff1f3;
  color: var(--color-red);
}

:root[data-theme="dark"] .drawer-tab.active,
:root[data-theme="dark"] .filter-chip.active {
  background: rgba(255, 36, 66, 0.14);
  border-color: rgba(255, 36, 66, 0.24);
  color: var(--color-red-light);
}

.drawer-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 14px;
}

.search-field {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-hint);
}

:root[data-theme="dark"] .search-field {
  background: var(--color-surface);
}

.search-field input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--color-title);
  font-size: 13px;
}

.drawer-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 12px;
  overflow: hidden;
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.section-head h3 {
  margin: 0 0 4px;
  color: var(--color-title);
  font-size: 16px;
}

.section-head p {
  margin: 0;
  color: var(--color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.primary-action {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  flex-shrink: 0;
  min-height: 38px;
  padding: 0 13px;
  border: 0;
  border-radius: 999px;
  background: var(--gradient-brand);
  color: #fff;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
  box-shadow: 0 10px 24px rgba(255, 36, 66, 0.2);
}

:root[data-theme="dark"] .primary-action {
  box-shadow: 0 10px 24px rgba(255, 36, 66, 0.25);
}

.primary-action.wide {
  width: 100%;
  justify-content: center;
}

.filter-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.filter-chip {
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.filter-chip.active {
  border-color: rgba(255, 36, 66, 0.24);
  background: #fff1f3;
  color: var(--color-red);
}

.drawer-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
  display: grid;
  gap: 12px;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 220px;
  text-align: center;
  color: var(--color-secondary);
}

.empty-state strong {
  color: var(--color-title);
  font-size: 14px;
}

.empty-state span {
  font-size: 12px;
}

.editor-card,
.tool-card {
  border: 1px solid rgba(17, 24, 39, 0.08);
  border-radius: 16px;
  background: var(--color-card);
  box-shadow: 0 10px 28px rgba(17, 24, 39, 0.05);
  padding: 14px;
}

.editor-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.editor-head strong {
  color: var(--color-title);
  font-size: 14px;
}

.editor-form {
  display: grid;
  gap: 10px;
}

.field {
  display: grid;
  gap: 6px;
}

.field span {
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 800;
}

.field input,
.field textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: #fff;
  color: var(--color-title);
  font-size: 13px;
  padding: 10px 12px;
  outline: none;
  resize: vertical;
}

.field input:focus,
.field textarea:focus {
  border-color: rgba(255, 36, 66, 0.32);
  box-shadow: 0 0 0 3px rgba(255, 36, 66, 0.08);
}

:root[data-theme="dark"] .field input:focus,
:root[data-theme="dark"] .field textarea:focus {
  border-color: rgba(255, 36, 66, 0.38);
  box-shadow: 0 0 0 3px rgba(255, 36, 66, 0.14);
}

.switch-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 800;
}

.switch-row input {
  accent-color: var(--color-red);
}

.tool-card {
  display: grid;
  gap: 10px;
}

.tool-card.disabled {
  opacity: 0.68;
}

.tool-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tool-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  background: #fff1f3;
  color: var(--color-red);
  flex-shrink: 0;
}

.tool-badge.custom,
.tool-badge.memory {
  background: #fff1f3;
  color: var(--color-red);
}

.tool-meta {
  min-width: 0;
  display: grid;
}

.tool-meta strong {
  color: var(--color-title);
  font-size: 13px;
}

.tool-meta span {
  color: var(--color-hint);
  font-size: 11px;
}

.tool-desc {
  margin: 0;
  color: var(--color-secondary);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-block {
  border-top: 1px dashed rgba(17, 24, 39, 0.12);
  padding-top: 10px;
}

.detail-block summary {
  cursor: pointer;
  color: var(--color-red);
  font-size: 12px;
  font-weight: 900;
  list-style: none;
}

.detail-block summary::-webkit-details-marker {
  display: none;
}

.detail-block pre {
  margin: 10px 0 0;
  padding: 12px;
  border-radius: 12px;
  background: #fff8f8;
  color: var(--color-title);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

:root[data-theme="dark"] .detail-block pre {
  background: rgba(255, 255, 255, 0.04);
  color: var(--color-body);
}

.tool-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.ghost-btn,
.danger-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  padding: 0 11px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 800;
}

.danger-btn {
  border-color: rgba(255, 36, 66, 0.18);
  color: var(--color-red);
}

.toggle {
  position: relative;
  width: 38px;
  height: 22px;
  flex-shrink: 0;
}

.toggle input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle span {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  background: #e5e7eb;
  transition: 0.2s;
}

.toggle span::before {
  content: '';
  position: absolute;
  width: 16px;
  height: 16px;
  top: 3px;
  left: 3px;
  border-radius: 50%;
  background: #fff;
  transition: 0.2s;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.14);
}

.toggle input:checked + span {
  background: var(--color-red);
}

.toggle input:checked + span::before {
  transform: translateX(16px);
}

:root[data-theme="dark"] .toggle span {
  background: #313137;
}

:root[data-theme="dark"] .toggle input:checked + span {
  background: var(--color-red);
}

:root[data-theme="dark"] .tool-badge,
:root[data-theme="dark"] .tool-badge.memory {
  background: rgba(255, 36, 66, 0.14);
  color: var(--color-red-light);
}

@media (max-width: 720px) {
  .tool-drawer {
    width: 100vw;
    height: 86vh;
    top: auto;
    bottom: 0;
    border-left: 0;
    border-top: 1px solid rgba(17, 24, 39, 0.08);
    border-radius: 18px 18px 0 0;
  }

  .drawer-toolbar {
    flex-wrap: wrap;
  }

}

.tool-fade-enter-active,
.tool-fade-leave-active,
.tool-slide-enter-active,
.tool-slide-leave-active {
  transition: all 0.2s ease;
}

.tool-fade-enter-from,
.tool-fade-leave-to {
  opacity: 0;
}

.tool-slide-enter-from,
.tool-slide-leave-to {
  opacity: 0;
  transform: translateX(16px);
}
</style>
