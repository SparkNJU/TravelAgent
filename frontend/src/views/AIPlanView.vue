<template>
  <div class="assistant-page">
    <aside class="history-sidebar" :class="{ collapsed: isSidebarCollapsed }">
      <div class="history-top">
        <div class="history-heading">
          <div class="history-title-row">
            <h3>历史对话</h3>
            <div class="history-toolbar">
              <button class="secondary-btn" type="button" @click="toggleSidebar">
                {{ isSidebarCollapsed ? '展开' : '收起' }}
              </button>
            </div>
          </div>
          <p>快速回看已生成的行程记录</p>
        </div>
      </div>

      <div v-if="!isSidebarCollapsed" class="history-body">
        <div v-if="!history.length" class="empty-state">
          <p>还没有历史对话</p>
          <span>生成一次计划后会自动保存</span>
        </div>

        <div v-else class="history-list">
          <div
            v-for="item in history"
            :key="item.id"
            class="history-item"
            :class="{ active: item.id === selectedHistoryId }"
          >
            <button type="button" class="history-main" @click="selectHistory(item)">
              <div class="history-title">{{ item.result?.title || '未命名行程' }}</div>
              <div class="history-meta">
                <span>{{ item.result?.destination || '未知目的地' }}</span>
                <span>{{ item.result?.days ? item.result.days + ' 天' : '—' }}</span>
              </div>
              <p class="history-snippet">{{ item.query }}</p>
              <span class="history-date">{{ formatDateTime(item.createdAt) }}</span>
            </button>
            <div class="history-actions-inline">
              <button type="button" class="inline-btn" @click="editHistory(item)">编辑</button>
              <button type="button" class="inline-btn danger" @click="removeHistory(item.id)">删除</button>
            </div>
          </div>
        </div>
      </div>
      <div class="history-footer" v-if="!isSidebarCollapsed">
        <button class="secondary-btn" type="button" @click="clearHistory" :disabled="!history.length">
          清空
        </button>
      </div>
    </aside>

    <div class="page-shell" :class="{ collapsed: isSidebarCollapsed }">
      <header class="page-header">
        <div>
          <h2>旅行计划工作台</h2>
          <p>输入旅行需求，上传参考文件，获取渲染后的规划结果</p>
        </div>
        <router-link to="/" class="back-link">返回首页</router-link>
      </header>

      <div class="page-grid">
        <section class="main-column">
          <section class="composer-section">
            <form @submit.prevent="generatePlan" class="composer-form">
              <label class="field">
                <span>你的需求</span>
                <textarea
                  v-model="query"
                  rows="5"
                  placeholder="例如：帮我做一个日本东京 5 天旅行计划，偏美食和城市观光，预算 10000 元。"
                />
              </label>

              <label class="field">
                <span>上传参考文件</span>
                <input type="file" @change="handleFileChange" />
                <small>支持常见文本 / 文档文件；如果解析失败，仍会基于需求生成计划。</small>
              </label>

              <div class="actions">
                <button class="primary-btn" type="submit" :disabled="loading">
                  {{ loading ? '生成中...' : '生成旅行计划' }}
                </button>
                <button class="secondary-btn" type="button" @click="loadPreset('东京 5 天游')">东京 5 天</button>
                <button class="secondary-btn" type="button" @click="loadPreset('新加坡 4 天游，偏亲子和美食')">新加坡 4 天</button>
                <button class="secondary-btn" type="button" @click="resetAll">清空</button>
              </div>

              <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
            </form>
          </section>

          <section v-if="result" class="result-grid">
            <article class="result-section">
              <div class="result-header">
                <div>
                  <p class="eyebrow">AI 行程卡片</p>
                  <h3>{{ result.title }}</h3>
                  <p class="subtitle">{{ result.destination }} · {{ result.days }} 天 · {{ displayDate }}</p>
                </div>
                <div class="meta-badges">
                  <span class="tag">已生成</span>
                  <span class="tag soft">个性化</span>
                  <span class="tag soft">可编辑</span>
                </div>
              </div>

              <div class="result-highlight">
                <div class="highlight-card">
                  <p class="label">出行天数</p>
                  <p class="value">{{ result.days || '-' }} 天</p>
                </div>
                <div class="highlight-card">
                  <p class="label">目的地</p>
                  <p class="value">{{ result.destination || '-' }}</p>
                </div>
                <div class="highlight-card">
                  <p class="label">查询摘要</p>
                  <p class="value clamp">{{ query }}</p>
                </div>
              </div>

              <div class="markdown-body" v-html="renderedMarkdown"></div>
            </article>

            <aside class="result-aside">
              <article class="image-section" v-if="result.images && result.images.length">
                <h3>图片资源</h3>
                <div class="image-list masonry">
                  <a
                    v-for="(img, index) in result.images"
                    :key="index"
                    :href="img.sourceUrl || img.imageUrl"
                    target="_blank"
                    rel="noreferrer"
                    class="image-item"
                  >
                    <img :src="img.imageUrl" :alt="img.title || 'travel image'" />
                    <span>{{ img.title || '图片来源' }}</span>
                  </a>
                </div>
              </article>

              <article class="source-section" v-if="result.sources && result.sources.length">
                <h3>搜索来源</h3>
                <ul class="source-list">
                  <li v-for="(source, index) in result.sources" :key="index">
                    <a :href="source.link" target="_blank" rel="noreferrer">{{ source.title }}</a>
                    <p>{{ source.snippet }}</p>
                  </li>
                </ul>
              </article>
            </aside>
          </section>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const query = ref('')
const file = ref(null)
const result = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const history = ref([])
const selectedHistoryId = ref(null)
const isSidebarCollapsed = ref(false)

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true
})

const handleFileChange = (event) => {
  const selected = event.target.files?.[0] || null
  file.value = selected
}

const loadPreset = (text) => {
  query.value = text
}

const resetAll = () => {
  query.value = ''
  file.value = null
  result.value = null
  errorMessage.value = ''
  selectedHistoryId.value = null
}

const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

const formatDate = (isoString) => {
  if (!isoString) {
    return '—'
  }
  const date = new Date(isoString)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate()
  ).padStart(2, '0')}`
}

const formatDateTime = (isoString) => {
  if (!isoString) {
    return '—'
  }
  const date = new Date(isoString)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate()
  ).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(
    date.getMinutes()
  ).padStart(2, '0')}`
}

const stripMarkdownSections = (markdownText, sectionTitles) => {
  if (!markdownText) {
    return ''
  }
  const lines = markdownText.split('\n')
  const titleSet = new Set(sectionTitles)
  const output = []
  let skip = false
  let skipLevel = null

  lines.forEach((line) => {
    const headingMatch = /^(#{1,6})\s+(.*)$/.exec(line.trim())
    if (headingMatch) {
      const level = headingMatch[1].length
      const title = headingMatch[2].trim()
      if (titleSet.has(title)) {
        skip = true
        skipLevel = level
        return
      }
      if (skip && skipLevel !== null && level <= skipLevel) {
        skip = false
        skipLevel = null
      }
    }
    if (!skip) {
      output.push(line)
    }
  })

  return output.join('\n')
}

const renderedMarkdown = computed(() => {
  if (!result.value?.markdown) {
    return ''
  }
  const filteredMarkdown = stripMarkdownSections(result.value.markdown, ['图片参考', '联网参考来源'])
  return DOMPurify.sanitize(md.render(filteredMarkdown))
})

const displayDate = computed(() => {
  if (!selectedHistoryId.value) {
    return formatDate(new Date().toISOString())
  }
  const selected = history.value.find((item) => item.id === selectedHistoryId.value)
  return formatDate(selected?.createdAt)
})

const loadHistory = () => {
  const stored = localStorage.getItem('assistantHistory')
  if (!stored) {
    return
  }
  try {
    history.value = JSON.parse(stored)
  } catch (error) {
    history.value = []
  }
}

const saveHistory = () => {
  localStorage.setItem('assistantHistory', JSON.stringify(history.value))
}

const addHistory = (data) => {
  const entry = {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    query: query.value.trim(),
    createdAt: new Date().toISOString(),
    result: data
  }
  history.value.unshift(entry)
  history.value = history.value.slice(0, 20)
  selectedHistoryId.value = entry.id
  saveHistory()
}

const selectHistory = (entry) => {
  selectedHistoryId.value = entry.id
  result.value = entry.result
  query.value = entry.query
  errorMessage.value = ''
}

const editHistory = (entry) => {
  const nextQuery = window.prompt('修改这条历史的需求内容：', entry.query)
  if (nextQuery === null) {
    return
  }
  entry.query = nextQuery.trim()
  if (entry.id === selectedHistoryId.value) {
    query.value = entry.query
  }
  saveHistory()
}

const removeHistory = (id) => {
  const confirmed = window.confirm('确定要删除这条历史吗？')
  if (!confirmed) {
    return
  }
  history.value = history.value.filter((item) => item.id !== id)
  if (selectedHistoryId.value === id) {
    selectedHistoryId.value = null
    result.value = null
  }
  saveHistory()
}

const clearHistory = () => {
  if (!history.value.length) {
    return
  }
  const confirmed = window.confirm('确定要清空历史对话吗？')
  if (!confirmed) {
    return
  }
  history.value = []
  selectedHistoryId.value = null
  localStorage.removeItem('assistantHistory')
}

const generatePlan = async () => {
  if (!query.value.trim()) {
    errorMessage.value = '请先输入旅行需求'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const formData = new FormData()
    formData.append('query', query.value.trim())
    formData.append('userId', localStorage.getItem('userId') || '1')
    if (file.value) {
      formData.append('file', file.value)
    }

    const response = await fetch('/api/assistant/chat', {
      method: 'POST',
      body: formData
    })
    const data = await response.json()

    if (data.code === 200) {
      result.value = data.data
      addHistory(data.data)
    } else {
      errorMessage.value = data.message || '生成失败，请重试'
    }
  } catch (error) {
    errorMessage.value = '服务器错误'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.assistant-page {
  min-height: 100vh;
  padding: 28px;
  color: #111827;
  font-family: 'Space Grotesk', 'Noto Sans SC', 'Segoe UI', sans-serif;
  overflow-x: hidden;
  background:
    radial-gradient(circle at top left, rgba(14, 165, 233, 0.12), transparent 45%),
    radial-gradient(circle at 20% 20%, rgba(251, 146, 60, 0.16), transparent 50%),
    linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);
}

.page-shell {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: 100%;
  padding-left: 260px;
  transition: padding 0.2s ease;
}

.page-shell.collapsed {
  padding-left: 96px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.page-header p {
  margin: 6px 0 0;
  color: #64748b;
}

.page-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 22px;
  width: 100%;
}

.main-column {
  display: grid;
  gap: 20px;
}

.back-link,
.primary-btn,
.secondary-btn {
  border: none;
  border-radius: 999px;
  padding: 12px 18px;
  text-decoration: none;
  cursor: pointer;
  font-weight: 600;
}

.back-link,
.secondary-btn {
  background: #eef2ff;
  color: #334155;
}

.primary-btn {
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: white;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.2);
}

.primary-btn:hover {
  filter: brightness(1.02);
}

.secondary-btn:hover,
.back-link:hover {
  background: #e0e7ff;
}

.composer-section,
.result-section,
.source-section,
.image-section {
  padding: 18px 0;
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
}

.composer-section {
  padding-top: 0;
}

.composer-form {
  display: grid;
  gap: 18px;
}

.field {
  display: grid;
  gap: 10px;
}

.field span {
  font-weight: 600;
}

textarea,
input[type='file'] {
  width: 100%;
}

textarea {
  resize: vertical;
  min-height: 130px;
  border-radius: 18px;
  border: 1px solid #cbd5e1;
  padding: 14px;
  font-size: 15px;
  background: #f8fafc;
}

.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.error-text {
  color: #dc2626;
  margin: 0;
}

.result-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 20px;
  min-width: 0;
}

.result-section {
  padding-right: 12px;
  min-width: 0;
}

.result-aside {
  display: grid;
  gap: 20px;
  align-content: start;
  min-width: 0;
}

.result-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.eyebrow {
  text-transform: uppercase;
  font-size: 12px;
  letter-spacing: 0.12em;
  color: #94a3b8;
  margin: 0 0 6px;
}

.result-header h3 {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
}

.subtitle {
  margin: 6px 0 0;
  color: #64748b;
}

.meta-badges {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tag {
  background: #0f172a;
  color: #f8fafc;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.tag.soft {
  background: #e0f2fe;
  color: #0369a1;
}

.result-highlight {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.highlight-card {
  padding: 10px 12px;
  border-left: 3px solid rgba(148, 163, 184, 0.35);
}

.label {
  margin: 0 0 6px;
  font-size: 12px;
  color: #94a3b8;
}

.value {
  margin: 0;
  font-weight: 600;
  color: #0f172a;
}

.value.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.markdown-body {
  line-height: 1.8;
  color: #1e293b;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3 {
  margin-top: 18px;
  margin-bottom: 10px;
  font-weight: 700;
}

.markdown-body ul,
.markdown-body ol {
  padding-left: 20px;
}

.markdown-body blockquote {
  border-left: 3px solid #38bdf8;
  margin: 12px 0;
  padding-left: 12px;
  color: #475569;
  background: #f1f5f9;
}

.image-list,
.source-list {
  display: grid;
  gap: 14px;
}

.image-item {
  display: grid;
  gap: 8px;
  text-decoration: none;
  color: inherit;
}

.image-item img {
  width: 100%;
  border-radius: 16px;
  object-fit: cover;
  aspect-ratio: 4 / 3;
  display: block;
}

.source-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-width: 100%;
}

.source-list a {
  color: #0284c7;
  text-decoration: none;
}

.source-list p {
  margin: 6px 0 0;
  color: #64748b;
}

.history-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 240px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 16px;
  background: rgba(248, 250, 252, 0.96);
  border-right: 1px solid rgba(148, 163, 184, 0.2);
  overflow-y: auto;
  transition: width 0.2s ease;
  z-index: 10;
}

.history-sidebar.collapsed {
  width: 80px;
}

.history-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.history-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.history-heading h3 {
  font-size: 18px;
}

.history-heading p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.history-toolbar {
  display: flex;
  gap: 8px;
  margin-top: 0;
  width: auto;
  justify-content: flex-end;
}

.history-sidebar.collapsed .history-body {
  display: none;
}

.history-sidebar.collapsed .history-top {
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.history-sidebar.collapsed .history-title-row {
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.history-sidebar.collapsed .history-heading h3 {
  font-size: 14px;
}

.history-sidebar.collapsed .history-heading p {
  font-size: 11px;
}

.history-sidebar.collapsed .history-toolbar {
  flex-direction: column;
  align-items: flex-start;
}

.history-footer {
  margin-top: auto;
}

.history-actions {
  display: flex;
  justify-content: flex-end;
}

.empty-state {
  padding: 24px 12px;
  text-align: center;
  color: #94a3b8;
}

.empty-state span {
  font-size: 12px;
}

.image-list.masonry {
  display: block;
  column-count: auto;
  column-width: 180px;
  column-gap: 14px;
  width: 100%;
  max-width: 100%;
}

.image-list.masonry .image-item {
  break-inside: avoid;
  margin-bottom: 14px;
}

.history-list {
  display: grid;
  gap: 12px;
}

.history-item {
  display: grid;
  gap: 8px;
  border: none;
  background: transparent;
  border-radius: 10px;
  padding: 8px 8px 28px 4px;
  text-align: left;
  transition: background-color 0.2s ease;
  position: relative;
}

.history-item:hover {
  background: rgba(226, 232, 240, 0.4);
}

.history-item.active {
  background: rgba(191, 219, 254, 0.4);
}

.history-title {
  font-weight: 600;
  margin-bottom: 4px;
  color: #0f172a;
}

.history-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 8px;
}

.history-main {
  background: transparent;
  border: none;
  text-align: left;
  padding: 0 56px 0 0;
  cursor: pointer;
}

.history-actions-inline {
  display: flex;
  gap: 8px;
  position: absolute;
  bottom: 6px;
  right: 8px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s ease;
}

.history-item:hover .history-actions-inline,
.history-item.active .history-actions-inline {
  opacity: 1;
  pointer-events: auto;
}

.inline-btn {
  border: none;
  background: #eef2ff;
  color: #2563eb;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  cursor: pointer;
}

.inline-btn.danger {
  background: #fee2e2;
  color: #b91c1c;
}

.history-snippet {
  margin: 0 0 8px;
  color: #475569;
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.history-date {
  font-size: 11px;
  color: #94a3b8;
}

@media (max-width: 1100px) {
  .history-sidebar {
    position: static;
    width: 100%;
    height: auto;
    border-right: none;
    border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  }

  .history-sidebar.collapsed {
    width: 100%;
  }

  .page-shell,
  .page-shell.collapsed {
    padding-left: 0;
  }
}

@media (max-width: 960px) {
  .result-grid {
    grid-template-columns: 1fr;
  }

  .image-list.masonry {
    column-count: 1;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
