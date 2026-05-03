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
  padding: var(--space-6);
  overflow-x: hidden;
  background: linear-gradient(135deg, var(--color-white) 0%, var(--color-gray-50) 100%);
}

.page-shell {
  max-width: var(--max-width);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 100%;
  padding-left: var(--sidebar-width);
  transition: padding var(--transition-normal);
}

.page-shell.collapsed {
  padding-left: var(--sidebar-collapsed);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.page-header h2 {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--color-gray-900);
}

.page-header p {
  margin: var(--space-1) 0 0;
  color: var(--color-gray-500);
  font-size: 14px;
}

.page-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-5);
  width: 100%;
}

.main-column {
  display: grid;
  gap: var(--space-5);
}

.back-link,
.primary-btn,
.secondary-btn {
  border: none;
  border-radius: var(--radius-full);
  padding: 10px 20px;
  text-decoration: none;
  cursor: pointer;
  font-weight: 600;
  font-size: 14px;
  transition: all var(--transition-fast);
}

.back-link,
.secondary-btn {
  background: var(--color-gray-100);
  color: var(--color-gray-700);
}

.back-link:hover,
.secondary-btn:hover {
  background: var(--color-gray-200);
}

.primary-btn {
  background: var(--color-primary);
  color: var(--color-white);
  box-shadow: var(--shadow-primary);
}

.primary-btn:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.composer-section,
.result-section,
.source-section,
.image-section {
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--color-gray-200);
}

.composer-section {
  padding-top: 0;
}

.composer-form {
  display: grid;
  gap: var(--space-5);
}

.field {
  display: grid;
  gap: var(--space-3);
}

.field span {
  font-weight: 600;
  font-size: 14px;
  color: var(--color-gray-800);
}

textarea,
input[type='file'] {
  width: 100%;
}

textarea {
  resize: vertical;
  min-height: 120px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-gray-200);
  padding: var(--space-4);
  font-size: 15px;
  font-family: var(--font-sans);
  background: var(--color-white);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-lighter);
}

input[type='file'] {
  padding: var(--space-3);
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-size: 14px;
  cursor: pointer;
}

input[type='file']:hover {
  border-color: var(--color-gray-300);
}

small {
  font-size: 12px;
  color: var(--color-gray-500);
}

.actions {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.error-text {
  color: var(--color-error);
  font-size: 14px;
  margin: 0;
}

.result-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: var(--space-5);
  min-width: 0;
}

.result-section {
  padding-right: var(--space-3);
  min-width: 0;
}

.result-aside {
  display: grid;
  gap: var(--space-5);
  align-content: start;
  min-width: 0;
}

.result-header {
  display: flex;
  justify-content: space-between;
  gap: var(--space-4);
  align-items: flex-start;
  margin-bottom: var(--space-5);
}

.eyebrow {
  text-transform: uppercase;
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--color-primary);
  font-weight: 600;
  margin: 0 0 var(--space-2);
}

.result-header h3 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--color-gray-900);
  letter-spacing: -0.02em;
}

.subtitle {
  margin: var(--space-2) 0 0;
  color: var(--color-gray-500);
  font-size: 14px;
}

.meta-badges {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.tag {
  background: var(--color-primary);
  color: var(--color-white);
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 600;
}

.tag.soft {
  background: var(--color-primary-lighter);
  color: var(--color-primary-dark);
}

.result-highlight {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.highlight-card {
  padding: var(--space-3) var(--space-4);
  background: var(--color-gray-50);
  border-radius: var(--radius-md);
  border-left: 3px solid var(--color-primary);
}

.label {
  margin: 0 0 var(--space-2);
  font-size: 12px;
  color: var(--color-gray-500);
  font-weight: 500;
}

.value {
  margin: 0;
  font-weight: 600;
  color: var(--color-gray-900);
  font-size: 15px;
}

.value.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.markdown-body {
  line-height: 1.8;
  color: var(--color-gray-800);
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3 {
  margin-top: var(--space-5);
  margin-bottom: var(--space-3);
  font-weight: 700;
  color: var(--color-gray-900);
}

.markdown-body h1 { font-size: 24px; }
.markdown-body h2 { font-size: 20px; }
.markdown-body h3 { font-size: 18px; }

.markdown-body p {
  margin-bottom: var(--space-3);
}

.markdown-body ul,
.markdown-body ol {
  padding-left: var(--space-5);
  margin-bottom: var(--space-3);
}

.markdown-body li {
  margin-bottom: var(--space-2);
}

.markdown-body blockquote {
  border-left: 3px solid var(--color-primary);
  margin: var(--space-4) 0;
  padding: var(--space-3) var(--space-4);
  color: var(--color-gray-600);
  background: var(--color-gray-50);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
}

.markdown-body code {
  background: var(--color-gray-100);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-size: 0.9em;
  font-family: var(--font-mono);
}

.markdown-body pre {
  background: var(--color-gray-900);
  color: var(--color-white);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  overflow-x: auto;
}

.markdown-body pre code {
  background: none;
  padding: 0;
  color: inherit;
}

.image-list,
.source-list {
  display: grid;
  gap: var(--space-4);
}

.image-item {
  display: grid;
  gap: var(--space-2);
  text-decoration: none;
  color: inherit;
}

.image-item img {
  width: 100%;
  border-radius: var(--radius-lg);
  object-fit: cover;
  aspect-ratio: 4 / 3;
  display: block;
  transition: transform var(--transition-normal);
}

.image-item:hover img {
  transform: scale(1.02);
}

.image-item span {
  font-size: 12px;
  color: var(--color-gray-500);
}

.source-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-width: 100%;
}

.source-list li {
  padding: var(--space-3);
  background: var(--color-gray-50);
  border-radius: var(--radius-md);
}

.source-list a {
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 500;
}

.source-list a:hover {
  text-decoration: underline;
}

.source-list p {
  margin: var(--space-2) 0 0;
  color: var(--color-gray-500);
  font-size: 13px;
}

/* History Sidebar */
.history-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: var(--sidebar-width);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-4);
  background: var(--color-white);
  border-right: 1px solid var(--color-gray-200);
  overflow-y: auto;
  transition: width var(--transition-normal);
  z-index: 10;
}

.history-sidebar.collapsed {
  width: var(--sidebar-collapsed);
}

.history-top {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  align-items: flex-start;
}

.history-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  width: 100%;
}

.history-heading h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-gray-900);
}

.history-heading p {
  margin: var(--space-1) 0 0;
  color: var(--color-gray-500);
  font-size: 12px;
}

.history-toolbar {
  display: flex;
  gap: var(--space-2);
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
  gap: var(--space-2);
}

.history-sidebar.collapsed .history-title-row {
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-1);
}

.history-sidebar.collapsed .history-heading h3 {
  font-size: 13px;
}

.history-sidebar.collapsed .history-heading p {
  font-size: 10px;
}

.history-sidebar.collapsed .history-toolbar {
  flex-direction: column;
  align-items: flex-start;
}

.history-footer {
  margin-top: auto;
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-gray-200);
}

.history-actions {
  display: flex;
  justify-content: flex-end;
}

.empty-state {
  padding: var(--space-6) var(--space-3);
  text-align: center;
  color: var(--color-gray-400);
}

.empty-state p {
  font-size: 14px;
  margin-bottom: var(--space-2);
}

.empty-state span {
  font-size: 12px;
}

.image-list.masonry {
  display: block;
  column-count: auto;
  column-width: 180px;
  column-gap: var(--space-4);
  width: 100%;
  max-width: 100%;
}

.image-list.masonry .image-item {
  break-inside: avoid;
  margin-bottom: var(--space-4);
}

.history-list {
  display: grid;
  gap: var(--space-3);
}

.history-item {
  display: grid;
  gap: var(--space-2);
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  padding: var(--space-3);
  text-align: left;
  transition: background-color var(--transition-fast);
  position: relative;
}

.history-item:hover {
  background: var(--color-gray-100);
}

.history-item.active {
  background: var(--color-primary-lighter);
}

.history-title {
  font-weight: 600;
  margin-bottom: var(--space-1);
  color: var(--color-gray-900);
  font-size: 14px;
}

.history-meta {
  display: flex;
  gap: var(--space-3);
  font-size: 12px;
  color: var(--color-gray-500);
  margin-bottom: var(--space-2);
}

.history-main {
  background: transparent;
  border: none;
  text-align: left;
  padding: 0 60px 0 0;
  cursor: pointer;
}

.history-actions-inline {
  display: flex;
  gap: var(--space-2);
  position: absolute;
  bottom: var(--space-2);
  right: var(--space-2);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--transition-fast);
}

.history-item:hover .history-actions-inline,
.history-item.active .history-actions-inline {
  opacity: 1;
  pointer-events: auto;
}

.inline-btn {
  border: none;
  background: var(--color-white);
  color: var(--color-primary);
  padding: 4px 10px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  box-shadow: var(--shadow-xs);
}

.inline-btn:hover {
  background: var(--color-primary);
  color: var(--color-white);
}

.inline-btn.danger {
  background: var(--color-white);
  color: var(--color-error);
}

.inline-btn.danger:hover {
  background: var(--color-error);
  color: var(--color-white);
}

.history-snippet {
  margin: 0 0 var(--space-2);
  color: var(--color-gray-600);
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}

.history-date {
  font-size: 11px;
  color: var(--color-gray-400);
}

/* Section Headers */
h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-gray-900);
  margin-bottom: var(--space-4);
}

/* Responsive */
@media (max-width: 1100px) {
  .history-sidebar {
    position: static;
    width: 100%;
    height: auto;
    border-right: none;
    border-bottom: 1px solid var(--color-gray-200);
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
