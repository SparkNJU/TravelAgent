<template>
  <div class="settings-page">
    <header class="settings-header">
      <div class="header-left">
        <h1>个人设置与工具</h1>
      </div>
    </header>

    <!-- Tab Switcher -->
    <nav class="tab-bar" aria-label="设置分类">
      <button
        :class="['tab-btn', { active: activeTab === 'skills' }]"
        @click="activeTab = 'skills'"
      >
        <SvgIcon name="wrench" :size="18" />
        <span>智能技能</span>
      </button>
      <button
        :class="['tab-btn', { active: activeTab === 'memory' }]"
        @click="activeTab = 'memory'"
      >
        <SvgIcon name="brain" :size="18" />
        <span>偏好记忆</span>
      </button>
      <button
        :class="['tab-btn', { active: activeTab === 'knowledge' }]"
        @click="activeTab = 'knowledge'"
      >
        <SvgIcon name="book" :size="18" />
        <span>知识库</span>
      </button>
    </nav>

    <div :class="['settings-container', activeTab === 'skills' ? 'show-left' : 'show-right']">
      <!-- Left Column: Skill Studio -->
      <div v-if="activeTab === 'skills'" class="settings-column">
        <div class="pane-card">
          <div class="pane-header">
            <h2 class="pane-title">
              <span class="icon-indicator skill-color">
                <SvgIcon name="wrench" :size="18" />
              </span>
              智能技能工坊
            </h2>
          </div>

          <div v-if="loadingSkills" class="loading-state">
            <SvgIcon name="loader" :size="28" :spin="true" color="var(--color-primary)" />
            <span>正在同步技能库数据...</span>
          </div>

          <div v-else class="pane-content skill-split">
            <!-- ===== LEFT: 已有技能（元技能 + 系统内置） ===== -->
            <div class="skill-list-side">
              <div v-if="!creatorSkill && systemSkills.length === 0" class="sub-section stretch-section">
                <h3 class="sub-title">系统内置技能</h3>
                <div class="empty-placeholder">
                  <div class="empty-icon">
                    <SvgIcon name="sparkles" :size="24" />
                  </div>
                  <h5>暂无系统内置技能</h5>
                  <p>系统技能将在初始化后自动加载。</p>
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
                        <input type="checkbox" :checked="creatorSkill.isEnabled" @change="toggleSkill(creatorSkill)" :disabled="updatingSkill === creatorSkill.id" />
                        <span class="slider round"></span>
                      </label>
                    </div>
                  </div>
                </div>

                <!-- System Skills -->
                <div v-if="systemSkills.length > 0" class="sub-section">
                  <h3 class="sub-title">系统内置技能</h3>
                  <div class="list-grid">
                    <div v-for="skill in systemSkills" :key="skill.id" class="tool-item skill-item system-item" :class="{ disabled: !skill.isEnabled }">
                      <div class="item-header">
                        <span class="tool-icon skill-icon"><SvgIcon name="sparkles" :size="16" /></span>
                        <div class="item-meta"><h4>{{ skill.title }}</h4></div>
                        <label class="switch">
                          <input type="checkbox" :checked="skill.isEnabled" @change="toggleSkill(skill)" :disabled="updatingSkill === skill.id" />
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
              </template>
            </div>

            <!-- ===== RIGHT: 自定义技能 + 编辑面板 ===== -->
            <div class="skill-editor-side">
              <!-- 自定义技能列表 -->
              <div class="sub-section" :class="{ 'stretch-section': customSkills.length === 0 }">
                <div class="custom-head">
                  <h3 class="sub-title">我的自定义技能</h3>
                  <button class="action-btn skill-btn" @click="openSkillEditor()">
                    <SvgIcon name="plus" :size="14" />
                    新建
                  </button>
                </div>
                <div v-if="customSkills.length === 0 && !skillEditorActive" class="empty-placeholder" @click="openSkillEditor()">
                  <div class="empty-icon"><SvgIcon name="plus-circle" :size="24" /></div>
                  <h5>创建专属规划套路</h5>
                  <p>点击此处添加你的个性化规划法则</p>
                </div>
                <div v-else class="list-grid">
                  <div v-for="skill in customSkills" :key="skill.id" class="tool-item skill-item custom-item" :class="{ disabled: !skill.isEnabled, editing: editingSkillId === skill.id }">
                    <div class="item-header">
                      <span class="tool-icon skill-icon custom"><SvgIcon name="wrench" :size="16" /></span>
                      <div class="item-meta"><h4>{{ skill.title }}</h4></div>
                      <label class="switch">
                        <input type="checkbox" :checked="skill.isEnabled" @change="toggleSkill(skill)" :disabled="updatingSkill === skill.id" />
                        <span class="slider round"></span>
                      </label>
                    </div>
                    <p class="item-desc"><strong>激活条件：</strong>{{ formatDescription(skill.description) }}</p>
                    <div class="item-footer">
                      <button class="delete-btn" @click="confirmDeleteSkill(skill)"><SvgIcon name="trash" :size="12" /> 删除</button>
                      <button class="text-link-btn" @click="editSkill(skill)">编辑技能</button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 编辑面板（在列表下方） -->
              <div v-if="skillEditorActive" class="editor-panel">
                <div class="editor-panel-head">
                  <h3>{{ isEditSkill ? '编辑自定义技能' : '新建自定义技能' }}</h3>
                  <button class="editor-cancel-btn" type="button" @click="cancelSkillEdit">取消</button>
                </div>

                <form class="editor-form" @submit.prevent="saveSkill">
                  <div class="form-row">
                    <div class="form-group flex-1">
                      <label>技能名称 (中文)</label>
                      <input v-model="skillForm.title" class="form-input" placeholder="例如：蜜月浪漫顾问" required />
                    </div>
                    <div class="form-group flex-1">
                      <label>唯一英文标识</label>
                      <input v-model="skillForm.name" class="form-input" placeholder="如: honeymoon-specialist" :disabled="isEditSkill" required />
                      <span class="hint">用于系统唯一标识，创建后不可更改，仅限小写英文与连字符</span>
                    </div>
                  </div>

                  <div class="form-group">
                    <label>激活条件</label>
                    <textarea v-model="skillForm.description" class="form-textarea desc-textarea" placeholder="极度关键！告知 Agent 应该在什么对话场景下激活此技能。例如：用户提到度蜜月、情侣游、求婚或浪漫旅游。" required></textarea>
                  </div>

                  <div class="form-group">
                    <label>技能指令手册</label>
                    <textarea v-model="skillForm.instructions" class="form-textarea code-textarea" placeholder="# 浪漫度蜜月专家规划准则\n1. 必须优先推荐海景/江景房并在备注中要求蜜月布置...\n2. 每日傍晚留出看日落的浪漫专属时间...\n3. 推荐富有情调的景观露台或米其林餐厅用餐..." required></textarea>
                    <span class="hint">输入具体的 Prompt 规划准则，支持 Markdown 格式。激活技能时将动态注入 AI 上下文。</span>
                  </div>

                  <footer class="editor-footer">
                    <button type="submit" class="submit-btn" :disabled="savingSkill">
                      <span v-if="savingSkill">正在保存...</span>
                      <span v-else>{{ isEditSkill ? '确认修改' : '确认保存' }}</span>
                    </button>
                  </footer>
                </form>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column: Memory Space -->
      <div v-if="activeTab === 'memory'" class="settings-column">
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
            <!-- Memories grouped by category -->
            <div v-for="cat in CATEGORY_OPTIONS" :key="cat">
              <div v-if="getMemoriesByCategory(cat).length > 0" class="sub-section memory-category-section">
                <h3 class="category-title">{{ cat }}</h3>
                <div class="list-grid flex-list">
                  <div
                    v-for="mem in getMemoriesByCategory(cat)"
                    :key="mem.id"
                    class="tool-item memory-item"
                    :class="{ disabled: !mem.isEnabled }"
                  >
                    <div class="memory-card-content">
                      <div class="memory-main">
                        <span class="memory-key" v-if="parseMemoryContent(mem.content).key">{{ parseMemoryContent(mem.content).key }}</span>
                        <p class="memory-value">{{ parseMemoryContent(mem.content).value }}</p>
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
            <!-- Empty state when no memories at all -->
            <div v-if="memories.length === 0" class="sub-section stretch-section">
              <div class="empty-placeholder" @click="openMemoryModal()">
                <div class="empty-icon">
                  <SvgIcon name="brain" :size="24" color="#457b9d" />
                </div>
                <h5>让 Agent 更懂您</h5>
                <p>在此输入您的个人喜好、忌口、预算倾向或住宿要求，Agent 在生成规划时将默默遵循。</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Knowledge Column -->
      <div v-if="activeTab === 'knowledge'" class="settings-column">
        <div class="pane-card">
          <div class="pane-header">
            <h2 class="pane-title">
              <span class="icon-indicator knowledge-color">
                <SvgIcon name="book" :size="18" />
              </span>
              知识库管理
            </h2>
            <button class="action-btn knowledge-btn" @click="openKnowledgeModal()">
              <SvgIcon name="plus" :size="14" />
              添加知识
            </button>
          </div>

          <div v-if="loadingKnowledge" class="loading-state">
            <SvgIcon name="loader" :size="28" :spin="true" color="#4caf50" />
            <span>正在加载知识库数据...</span>
          </div>

          <div v-else class="pane-content">
            <div v-if="knowledgeDocuments.length > 0" class="list-grid">
              <div v-for="doc in knowledgeDocuments" :key="doc.doc_id" class="tool-item knowledge-item">
                <div class="item-header">
                  <span class="tool-icon knowledge-icon"><SvgIcon name="book" :size="16" /></span>
                  <div class="item-meta">
                    <h4>{{ doc.title }}</h4>
                    <span class="item-tag">{{ doc.source_type }}</span>
                  </div>
                  <span class="chunk-badge">{{ doc.chunk_count }} 片段</span>
                </div>
                <p class="item-desc">{{ doc.source_ref || '手动添加的知识文档' }}</p>
                <div class="item-footer">
                  <span class="badge">{{ formatDate(doc.created_at) }}</span>
                  <button class="delete-btn" @click="confirmDeleteKnowledge(doc)">
                    <SvgIcon name="trash" :size="12" /> 删除
                  </button>
                </div>
              </div>
            </div>

            <div v-else class="sub-section stretch-section">
              <div class="empty-placeholder" @click="openKnowledgeModal()">
                <div class="empty-icon">
                  <SvgIcon name="book" :size="24" color="#4caf50" />
                </div>
                <h5>构建专属知识库</h5>
                <p>上传旅行攻略、游记或文档，Agent 将在规划时自动检索相关知识，提升规划质量。</p>
              </div>
            </div>
          </div>
        </div>
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
            <label>分类</label>
            <div class="category-tabs">
              <button
                v-for="cat in CATEGORY_OPTIONS"
                :key="cat"
                type="button"
                class="category-tab"
                :class="{ active: memoryForm.category === cat }"
                @click="memoryForm.category = cat"
              >{{ cat }}</button>
            </div>
          </div>
          <div class="form-group">
            <label>偏好记忆内容</label>
            <textarea
              v-model="memoryForm.content"
              class="form-textarea desc-textarea memory-textarea"
              required
            ></textarea>
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

    <!-- Knowledge Upload Modal -->
    <div v-if="knowledgeModalVisible" class="modal-backdrop" @click.self="closeKnowledgeModal">
      <div class="settings-modal-content knowledge-modal">
        <header class="modal-header">
          <h2>添加知识文档</h2>
          <button class="close-btn" @click="closeKnowledgeModal">
            <SvgIcon name="close" :size="16" />
          </button>
        </header>

        <form class="modal-form" @submit.prevent="saveKnowledge">
          <div class="form-group">
            <label>文档标题</label>
            <input v-model="knowledgeForm.title" class="form-input" placeholder="例如：北京三日游攻略" required />
          </div>

          <div class="form-group">
            <label>添加方式</label>
            <div class="category-tabs">
              <button type="button" class="category-tab" :class="{ active: knowledgeUploadMode === 'text' }" @click="knowledgeUploadMode = 'text'">手动输入</button>
              <button type="button" class="category-tab" :class="{ active: knowledgeUploadMode === 'file' }" @click="knowledgeUploadMode = 'file'">上传文件</button>
            </div>
          </div>

          <div v-if="knowledgeUploadMode === 'text'" class="form-group">
            <label>知识内容</label>
            <textarea v-model="knowledgeForm.content" class="form-textarea knowledge-textarea" placeholder="输入旅行攻略、景点介绍、美食推荐等知识内容..." required></textarea>
          </div>

          <div v-else class="form-group">
            <label>选择文件</label>
            <div class="file-drop-area" @click="$refs.knowledgeFileInput.click()">
              <SvgIcon name="upload" :size="24" color="var(--color-hint)" />
              <p v-if="!knowledgeForm.fileName">点击选择文件（支持 PDF、DOCX、TXT、MD）</p>
              <p v-else class="file-selected">已选择：{{ knowledgeForm.fileName }}</p>
              <input ref="knowledgeFileInput" type="file" accept=".pdf,.doc,.docx,.txt,.md" style="display:none" @change="handleKnowledgeFileSelect" />
            </div>
          </div>

          <footer class="modal-footer">
            <button type="button" class="cancel-btn" @click="closeKnowledgeModal">取消</button>
            <button type="submit" class="submit-btn knowledge-submit" :disabled="savingKnowledge || (knowledgeUploadMode === 'file' && !knowledgeForm.fileBase64)">
              <span v-if="savingKnowledge">正在上传...</span>
              <span v-else>确认保存</span>
            </button>
          </footer>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import SvgIcon from '../components/SvgIcon.vue'
import { useAuth } from '../composables/useAuth'
import { parseMemoryMarkdown, buildMemoryMarkdown, sectionsToCards, cardsToSections, parseMemoryContent } from '../utils/markdownParser.js'

const { userId, isLoggedIn } = useAuth()
const currentUserId = computed(() => Number(userId.value) || null)

const activeTab = ref('skills')

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
const skillEditorActive = ref(false)
const isEditSkill = ref(false)
const editingSkillId = ref(null)
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
const disabledKeys = ref(new Set())
const memoryModalVisible = ref(false)
const isEditMemory = ref(false)

const CATEGORY_OPTIONS = ['个人信息', '旅游偏好', '口味偏好', '其他']

const memoryForm = reactive({
  id: null,
  category: '个人信息',
  content: '',
  key: '',
  isEnabled: true
})

// Knowledge Data
const knowledgeDocuments = ref([])
const loadingKnowledge = ref(false)
const savingKnowledge = ref(false)
const knowledgeModalVisible = ref(false)
const knowledgeUploadMode = ref('text')
const knowledgeForm = reactive({
  title: '',
  content: '',
  fileName: '',
  fileBase64: ''
})

// ------------------- LOAD FUNCTIONS -------------------

const loadSkills = async () => {
  loadingSkills.value = true
  try {
    const res = await fetch(`/api/skills?userId=${currentUserId.value}`)
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
    const res = await fetch(`/api/agent/memory/${currentUserId.value}`)
    const data = await res.json()
    if (data.code === 200) {
      const memory = data.data?.memory
      const markdown = memory?.memoryMarkdown || ''
      const sections = parseMemoryMarkdown(markdown)

      let disabled = new Set()
      if (memory?.memoryJson) {
        try {
          const parsed = JSON.parse(memory.memoryJson)
          if (parsed.disabledKeys) {
            disabled = new Set(parsed.disabledKeys)
          }
        } catch {}
      }
      disabledKeys.value = disabled

      memories.value = sectionsToCards(sections, disabled)
    }
  } catch (e) {
    console.error("加载偏好记忆失败:", e)
  }
  loadingMemories.value = false
}

const loadKnowledgeDocuments = async () => {
  loadingKnowledge.value = true
  try {
    const res = await fetch('/api/knowledge/documents')
    const data = await res.json()
    if (data.code === 200) {
      knowledgeDocuments.value = data.data?.documents || []
    }
  } catch (e) {
    console.error("加载知识库失败:", e)
  }
  loadingKnowledge.value = false
}

onMounted(() => {
  loadSkills()
  loadMemories()
  loadKnowledgeDocuments()
})

// ------------------- SKILL ACTIONS -------------------

const toggleSkill = async (skill) => {
  updatingSkill.value = skill.id
  const targetStatus = !skill.isEnabled
  try {
    const res = await fetch(`/api/skills/${skill.id}/toggle?userId=${currentUserId.value}&isEnabled=${targetStatus}`, {
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

const openSkillEditor = () => {
  isEditSkill.value = false
  editingSkillId.value = null
  skillForm.id = null
  skillForm.name = ''
  skillForm.title = ''
  skillForm.description = ''
  skillForm.instructions = ''
  skillForm.isEnabled = true
  skillEditorActive.value = true
}

const cancelSkillEdit = () => {
  skillEditorActive.value = false
  editingSkillId.value = null
  isEditSkill.value = false
  skillForm.id = null
  skillForm.name = ''
  skillForm.title = ''
  skillForm.description = ''
  skillForm.instructions = ''
}

const editSkill = (skill) => {
  isEditSkill.value = true
  editingSkillId.value = skill.id
  skillForm.id = skill.id
  skillForm.name = skill.name
  skillForm.title = skill.title
  skillForm.description = skill.description
  skillForm.instructions = skill.instructions
  skillForm.isEnabled = skill.isEnabled
  skillEditorActive.value = true
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
    const res = await fetch(`/api/skills/${skill.id}?userId=${currentUserId.value}`, {
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
    const url = isEditSkill.value ? `/api/skills/${skillForm.id}?userId=${currentUserId.value}` : `/api/skills?userId=${currentUserId.value}`
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
      cancelSkillEdit()
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

const syncMemoriesToLongTermMemory = async () => {
  const categoryOrder = CATEGORY_OPTIONS
  const groups = {}
  for (const cat of categoryOrder) groups[cat] = []
  for (const m of memories.value) {
    if (!m.isEnabled) continue
    const cat = m.category || '其他'
    if (!groups[cat]) groups[cat] = []
    const colonIdx = m.content.indexOf(':')
    const key = colonIdx > 0 ? m.content.substring(0, colonIdx).trim() : ''
    const value = colonIdx > 0 ? m.content.substring(colonIdx + 1).trim() : m.content.trim()
    groups[cat].push({ key, value, evidence: '' })
  }

  const sections = []
  for (const cat of categoryOrder) {
    if (groups[cat] && groups[cat].length > 0) {
      sections.push({ title: cat, items: groups[cat] })
    }
  }

  const username = currentUserId.value ? 'user-' + currentUserId.value : 'guest'
  const markdown = buildMemoryMarkdown(username, sections, '')
  const keys = []
  for (const m of memories.value) {
    if (!m.isEnabled) {
      const colonIdx = m.content.indexOf(':')
      const key = colonIdx > 0 ? m.content.substring(0, colonIdx).trim() : ''
      keys.push(key)
    }
  }

  const res = await fetch('/api/agent/memory/sync', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userId: currentUserId.value,
      memoryMarkdown: markdown,
      disabledKeys: keys
    })
  })
  const data = await res.json()
  if (data.code !== 200) {
    throw new Error(data.message || '同步长期记忆失败')
  }
}

const toggleMemory = async (mem) => {
  updatingMemory.value = mem.id
  const targetStatus = !mem.isEnabled
  try {
    mem.isEnabled = targetStatus
    await syncMemoriesToLongTermMemory()
  } catch (e) {
    mem.isEnabled = !targetStatus
    alert("修改状态失败: " + e.message)
    console.error("切换记忆状态失败:", e)
  }
  updatingMemory.value = null
}

const openMemoryModal = (mem = null) => {
  if (mem) {
    isEditMemory.value = true
    memoryForm.id = mem.id
    memoryForm.content = mem.content
    memoryForm.category = mem.category || '其他'
    memoryForm.isEnabled = mem.isEnabled
  } else {
    isEditMemory.value = false
    memoryForm.id = null
    memoryForm.content = ''
    memoryForm.category = '个人信息'
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
    memories.value = memories.value.filter(m => m.id !== mem.id)
    await syncMemoriesToLongTermMemory()
  } catch (e) {
    alert("删除记忆失败: " + e.message)
    console.error("删除记忆失败:", e)
  }
}

const saveMemory = async () => {
  savingMemory.value = true
  try {
    if (isEditMemory.value) {
      const idx = memories.value.findIndex(m => m.id === memoryForm.id)
      if (idx !== -1) {
        memories.value[idx] = {
          ...memories.value[idx],
          content: memoryForm.content,
          category: memoryForm.category,
          isEnabled: memoryForm.isEnabled
        }
      }
    } else {
      const colonIdx = memoryForm.content.indexOf(':')
      const key = colonIdx > 0 ? memoryForm.content.substring(0, colonIdx).trim() : ''
      const newCard = {
        id: Date.now(),
        key,
        content: memoryForm.content,
        category: memoryForm.category,
        isEnabled: memoryForm.isEnabled,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      }
      memories.value.push(newCard)
    }
    await syncMemoriesToLongTermMemory()
    memoryModalVisible.value = false
  } catch (e) {
    alert("保存记忆失败: " + e.message)
    console.error("保存记忆失败:", e)
  }
  savingMemory.value = false
}

// Helper: Get memories filtered by category
const getMemoriesByCategory = (category) => {
  return memories.value.filter(m => (m.category || '其他') === category)
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

// ------------------- KNOWLEDGE ACTIONS -------------------

const openKnowledgeModal = () => {
  knowledgeForm.title = ''
  knowledgeForm.content = ''
  knowledgeForm.fileName = ''
  knowledgeForm.fileBase64 = ''
  knowledgeUploadMode.value = 'text'
  knowledgeModalVisible.value = true
}

const closeKnowledgeModal = () => {
  knowledgeModalVisible.value = false
}

const handleKnowledgeFileSelect = (event) => {
  const file = event.target.files[0]
  if (!file) return
  knowledgeForm.fileName = file.name
  const reader = new FileReader()
  reader.onload = () => {
    const base64 = reader.result.split(',')[1]
    knowledgeForm.fileBase64 = base64
  }
  reader.readAsDataURL(file)
}

const saveKnowledge = async () => {
  if (!knowledgeForm.title.trim()) {
    alert('请输入文档标题')
    return
  }
  savingKnowledge.value = true
  try {
    if (knowledgeUploadMode.value === 'text') {
      if (!knowledgeForm.content.trim()) {
        alert('请输入知识内容')
        savingKnowledge.value = false
        return
      }
      const res = await fetch('/api/knowledge/documents', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: knowledgeForm.title,
          content: knowledgeForm.content,
          source_type: 'manual_text'
        })
      })
      const data = await res.json()
      if (data.doc_id) {
        closeKnowledgeModal()
        await loadKnowledgeDocuments()
      } else {
        alert('保存失败: ' + (data.detail || '未知错误'))
      }
    } else {
      if (!knowledgeForm.fileBase64) {
        alert('请选择文件')
        savingKnowledge.value = false
        return
      }
      const res = await fetch('/api/knowledge/documents/upload', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: knowledgeForm.title,
          fileName: knowledgeForm.fileName,
          fileBase64: knowledgeForm.fileBase64,
          sourceType: 'uploaded_file'
        })
      })
      const data = await res.json()
      if (data.code === 200) {
        closeKnowledgeModal()
        await loadKnowledgeDocuments()
      } else {
        alert('上传失败: ' + (data.message || '未知错误'))
      }
    }
  } catch (e) {
    console.error("保存知识文档失败:", e)
    alert('保存失败: ' + e.message)
  }
  savingKnowledge.value = false
}

const confirmDeleteKnowledge = async (doc) => {
  if (!confirm(`确认要删除知识文档「${doc.title}」吗？该文档的 ${doc.chunk_count} 个片段将被永久移除。`)) return
  try {
    const res = await fetch(`/api/knowledge/documents/${doc.doc_id}`, { method: 'DELETE' })
    const data = await res.json()
    if (data.code === 200) {
      knowledgeDocuments.value = knowledgeDocuments.value.filter(d => d.doc_id !== doc.doc_id)
    } else {
      alert('删除失败: ' + data.message)
    }
  } catch (e) {
    console.error("删除知识文档失败:", e)
    alert('删除失败: ' + e.message)
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

/* ── Tab Switcher ── */

.tab-bar {
  display: flex;
  gap: 6px;
  margin-bottom: 28px;
  padding: 4px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  width: fit-content;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border: none;
  border-radius: 11px;
  background: transparent;
  color: var(--color-secondary);
  font-size: 14px;
  font-weight: 700;
  font-family: var(--font-family);
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-btn:hover {
  color: var(--color-title);
  background: var(--color-card-hover);
}

.tab-btn.active {
  background: var(--color-soft-red);
  color: var(--color-red);
  box-shadow: 0 1px 3px rgba(255, 36, 66, 0.12);
}

/* ── Container Layout ── */
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

/* When only one column is visible, let it fill the full width */
.settings-container.show-left,
.settings-container.show-right {
  grid-template-columns: 1fr;
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

/* ── Skill two-column split ── */

.skill-split {
  display: grid !important;
  grid-template-columns: minmax(300px, 1fr) minmax(440px, 1.5fr);
  gap: 0;
  padding: 0;
}

.skill-list-side {
  padding: 24px;
  overflow-y: auto;
  max-height: calc(100vh - 200px);
  border-right: 1px solid var(--color-border);
}

.skill-editor-side {
  padding: 24px;
  overflow-y: auto;
  max-height: calc(100vh - 200px);
  background: var(--color-surface);
}

/* Editor panel */
.editor-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid var(--color-border);
}

.editor-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.editor-panel-head h3 {
  font-size: 15px;
  font-weight: 800;
  color: var(--color-title);
  margin: 0;
}

.editor-cancel-btn {
  font-size: 12.5px;
  color: var(--color-secondary);
  background: var(--color-card);
  border: 1px solid var(--color-border);
  padding: 5px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  font-family: var(--font-family);
}

.editor-cancel-btn:hover {
  color: var(--color-red);
  border-color: rgba(255, 36, 66, 0.3);
}

.editor-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.editor-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
  border-top: 1px solid var(--color-border);
}

/* Custom skills header row */
.custom-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.custom-head .sub-title {
  margin-bottom: 0;
}

/* Highlight the custom skill item being edited */
.tool-item.custom-item.editing {
  border-color: var(--color-red);
  background: var(--color-soft-red);
}

/* Responsive: stack on narrow screens */
@media (max-width: 960px) {
  .skill-split {
    grid-template-columns: 1fr;
  }

  .skill-list-side {
    border-right: none;
    border-bottom: 1px solid var(--color-border);
    max-height: none;
  }

  .skill-editor-side {
    max-height: none;
  }
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

.memory-key {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  color: var(--color-hint);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  line-height: 1;
}

.memory-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-title);
  line-height: 1.4;
  margin: 0;
}

.memory-time {
  font-size: 10.5px;
  color: var(--color-hint);
}

.memory-category-section {
  margin-bottom: 24px;
}

.category-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-title);
  margin: 0 0 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--color-border);
  display: flex;
  align-items: center;
  gap: 8px;
}

.category-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.category-tab {
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-hint);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
  line-height: 1.4;
}

.category-tab:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.category-tab.active {
  color: var(--color-accent);
  background: var(--color-accent-bg, rgba(69, 123, 157, 0.08));
  border-color: var(--color-accent);
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

/* Knowledge Tab Styles */
.knowledge-color {
  background: rgba(76, 175, 80, 0.1);
  color: #4caf50;
}

.knowledge-btn {
  background: linear-gradient(135deg, #4caf50, #2e7d32);
  box-shadow: 0 4px 12px rgba(76, 175, 80, 0.2);
}

.knowledge-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(76, 175, 80, 0.35);
}

.knowledge-icon {
  background: rgba(76, 175, 80, 0.08);
  color: #4caf50;
}

.chunk-badge {
  font-size: 11px;
  color: #4caf50;
  background: rgba(76, 175, 80, 0.08);
  padding: 3px 8px;
  border-radius: 4px;
  font-weight: 600;
  white-space: nowrap;
}

.knowledge-item input:checked + .slider {
  background-color: #4caf50;
}

.file-drop-area {
  border: 2px dashed var(--color-border);
  border-radius: 8px;
  padding: 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.file-drop-area:hover {
  border-color: #4caf50;
  background: rgba(76, 175, 80, 0.02);
}

.file-drop-area p {
  margin: 0;
  font-size: 13px;
  color: var(--color-secondary);
}

.file-drop-area .file-selected {
  color: #4caf50;
  font-weight: 600;
}

.knowledge-textarea {
  height: 200px;
}

.knowledge-submit {
  background: linear-gradient(135deg, #4caf50, #2e7d32);
  box-shadow: 0 4px 10px rgba(76, 175, 80, 0.15);
}

.knowledge-submit:hover {
  box-shadow: 0 6px 15px rgba(76, 175, 80, 0.25);
}

.knowledge-modal {
  max-width: 550px;
}
</style>
