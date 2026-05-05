<template>
  <div class="ai-page">
    <div class="ai-inner">
      <!-- Input Section -->
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

      <!-- Result Section -->
      <section v-if="result" class="result-section">
        <div class="result-header">
          <div class="result-title-area">
            <h2>{{ result.title }}</h2>
            <p class="result-meta">{{ result.destination }} · {{ result.days }}天</p>
          </div>
          <div class="result-actions">
            <button class="action-btn" @click="shareToCommunity">
              <SvgIcon name="share" :size="14" />
              分享到社区
            </button>
            <button class="action-btn secondary" @click="resetPlan">重新规划</button>
          </div>
        </div>

        <div class="result-body">
          <div class="markdown-body" v-html="renderedMarkdown"></div>

          <div class="result-sidebar" v-if="result.images?.length || result.sources?.length">
            <div class="sidebar-block" v-if="result.images?.length">
              <h4>图片参考</h4>
              <div class="image-grid">
                <a v-for="(img, i) in result.images" :key="i" :href="img.sourceUrl || img.imageUrl" target="_blank" rel="noreferrer">
                  <img :src="img.imageUrl" :alt="img.title" />
                </a>
              </div>
            </div>

            <div class="sidebar-block" v-if="result.sources?.length">
              <h4>参考来源</h4>
              <ul class="source-list">
                <li v-for="(s, i) in result.sources" :key="i">
                  <a :href="s.link" target="_blank" rel="noreferrer">{{ s.title }}</a>
                  <p v-if="s.snippet">{{ s.snippet }}</p>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SvgIcon from '../components/SvgIcon.vue'

const route = useRoute()
const query = ref('')
const file = ref(null)
const result = ref(null)
const loading = ref(false)
const errorMessage = ref('')

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const renderedMarkdown = computed(() => {
  if (!result.value?.markdown) return ''
  return DOMPurify.sanitize(md.render(result.value.markdown))
})

onMounted(() => {
  if (route.query.q) {
    query.value = route.query.q
  }
})

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

const resetPlan = () => { result.value = null; errorMessage.value = ''; file.value = null }
</script>

<style scoped>
.ai-page {
  background: var(--color-bg);
  font-family: var(--font-family);
  color: var(--color-body);
}

.ai-inner {
  max-width: 960px;
  margin: 0 auto;
  padding: 28px 32px;
}

/* Input Section */
.input-section {
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 24px;
  border: 1px solid var(--color-border);
  margin-bottom: 24px;
}

.input-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.header-icon { color: var(--color-red-light); }

.input-header h2 {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-title);
  margin: 0;
}

.input-header p {
  font-size: 13px;
  color: var(--color-hint);
  margin: 3px 0 0;
}

.input-form { display: flex; flex-direction: column; gap: 14px; }

.query-input {
  resize: vertical;
  min-height: 80px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-input);
  padding: 12px 14px;
  font-size: 14px;
  font-family: var(--font-family);
  background: var(--color-bg);
  color: var(--color-title);
  transition: border-color 0.2s;
}
.query-input:focus { outline: none; border-color: var(--color-red); box-shadow: 0 0 0 3px rgba(230,57,70,0.08); }
.query-input::placeholder { color: var(--color-muted); }

.input-row { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 12px; }

.quick-tags { display: flex; flex-wrap: wrap; gap: 6px; }

.quick-tag {
  border: 1px solid var(--color-border); border-radius: var(--radius-pill);
  padding: 5px 12px; font-size: 12px; color: var(--color-hint);
  background: var(--color-bg); cursor: pointer; transition: all 0.2s; font-family: var(--font-family);
}
.quick-tag:hover { border-color: var(--color-red); color: var(--color-red-light); }

.input-actions { display: flex; gap: 10px; align-items: center; }

.upload-btn {
  display: flex; align-items: center; gap: 5px;
  padding: 7px 14px; border: 1.5px dashed var(--color-border);
  border-radius: var(--radius-input); color: var(--color-muted);
  font-size: 12px; cursor: pointer; transition: all 0.2s;
}
.upload-btn:hover { border-color: var(--color-red); color: var(--color-red-light); }

.gen-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 20px; border: none; border-radius: var(--radius-pill);
  background: var(--gradient-brand); color: white;
  font-size: 13px; font-weight: 600; font-family: var(--font-family);
  cursor: pointer; box-shadow: var(--shadow-button); transition: all 0.2s;
}
.gen-btn:hover:not(:disabled) { filter: brightness(1.1); transform: translateY(-1px); }
.gen-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.error-msg { margin: 0; font-size: 13px; color: var(--color-red-light); }

/* Result Section */
.result-section {
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 24px;
  border: 1px solid var(--color-border);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}

.result-title-area h2 { font-size: 20px; font-weight: 700; color: var(--color-title); margin: 0 0 4px; }
.result-meta { font-size: 13px; color: var(--color-hint); margin: 0; }

.result-actions { display: flex; gap: 8px; flex-shrink: 0; }

.action-btn {
  display: flex; align-items: center; gap: 5px;
  padding: 7px 16px; border: none; border-radius: var(--radius-pill);
  font-size: 12px; font-weight: 600; font-family: var(--font-family);
  cursor: pointer; transition: all 0.2s;
}
.action-btn:not(.secondary) { background: var(--gradient-brand); color: white; }
.action-btn.secondary { background: var(--color-bg); color: var(--color-secondary); border: 1px solid var(--color-border); }
.action-btn:hover { filter: brightness(1.1); }

.result-body { display: flex; gap: 24px; }

.markdown-body {
  flex: 1;
  line-height: 1.8;
  color: var(--color-body);
  font-size: 14px;
}

.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) {
  margin-top: 20px; margin-bottom: 10px; font-weight: 700; color: var(--color-title);
}
.markdown-body :deep(h1) { font-size: 18px; }
.markdown-body :deep(h2) { font-size: 16px; }
.markdown-body :deep(h3) { font-size: 14px; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 8px 0; }
.markdown-body :deep(li) { margin-bottom: 6px; }
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--color-red); margin: 12px 0; padding: 10px 14px;
  background: rgba(230,57,70,0.06); border-radius: 0 var(--radius-input) var(--radius-input) 0;
  color: var(--color-secondary);
}
.markdown-body :deep(a) { color: var(--color-red-light); text-decoration: none; }
.markdown-body :deep(a):hover { text-decoration: underline; }
.markdown-body :deep(strong) { color: var(--color-title); }

.result-sidebar { width: 260px; flex-shrink: 0; display: flex; flex-direction: column; gap: 20px; }

.sidebar-block h4 { font-size: 14px; font-weight: 600; margin: 0 0 10px; color: var(--color-title); }

.image-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.image-grid img {
  width: 100%; border-radius: var(--radius-image);
  aspect-ratio: 4/3; object-fit: cover; display: block;
}

.source-list { list-style: none; padding: 0; margin: 0; }
.source-list li { margin-bottom: 8px; }
.source-list a { font-size: 12px; font-weight: 500; color: var(--color-red-light); text-decoration: none; }
.source-list p { margin: 3px 0 0; font-size: 11px; color: var(--color-hint); }

@media (max-width: 768px) {
  .ai-inner { padding: 16px; }
  .result-body { flex-direction: column; }
  .result-sidebar { width: 100%; }
}
</style>
