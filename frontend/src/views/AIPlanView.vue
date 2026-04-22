<template>
  <div class="assistant-page">
    <header class="page-header">
      <div>
        <h2>旅行计划工作台</h2>
        <p>输入旅行需求，上传参考文件，生成 Markdown 旅行计划</p>
      </div>
      <router-link to="/" class="back-link">返回首页</router-link>
    </header>

    <section class="composer-card">
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
          <small>支持常见文本 / 文档文件；如果解析失败，仍会基于 query 生成计划。</small>
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
      <article class="result-card markdown-card">
        <div class="card-title">
          <h3>{{ result.title }}</h3>
          <span class="tag">{{ result.destination }} · {{ result.days }} 天</span>
        </div>
        <pre class="markdown-output">{{ result.markdown }}</pre>
      </article>

      <article class="result-card side-card" v-if="result.images && result.images.length">
        <h3>图片资源</h3>
        <div class="image-list">
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

      <article class="result-card side-card" v-if="result.sources && result.sources.length">
        <h3>搜索来源</h3>
        <ul class="source-list">
          <li v-for="(source, index) in result.sources" :key="index">
            <a :href="source.link" target="_blank" rel="noreferrer">{{ source.title }}</a>
            <p>{{ source.snippet }}</p>
          </li>
        </ul>
      </article>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const query = ref('')
const file = ref(null)
const result = ref(null)
const loading = ref(false)
const errorMessage = ref('')

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
      body: formData,
    })
    const data = await response.json()

    if (data.code === 200) {
      result.value = data.data
    } else {
      errorMessage.value = data.message || '生成失败，请重试'
    }
  } catch (error) {
    errorMessage.value = '服务器错误'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.assistant-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);
  padding: 28px;
  color: #111827;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
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
}

.page-header p {
  margin: 6px 0 0;
  color: #64748b;
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
  background: white;
  color: #334155;
  border: 1px solid #cbd5e1;
}

.primary-btn {
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: white;
}

.composer-card,
.result-card {
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 24px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
}

.composer-card {
  padding: 24px;
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
  grid-template-columns: 2fr 1fr;
  gap: 20px;
  margin-top: 20px;
}

.result-card {
  padding: 22px;
}

.markdown-output {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: inherit;
  line-height: 1.7;
  color: #1e293b;
}

.card-title {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}

.tag {
  background: #e0e7ff;
  color: #3730a3;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 13px;
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
}

.source-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.source-list a {
  color: #2563eb;
  text-decoration: none;
}

.source-list p {
  margin: 6px 0 0;
  color: #64748b;
}

@media (max-width: 960px) {
  .result-grid {
    grid-template-columns: 1fr;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
