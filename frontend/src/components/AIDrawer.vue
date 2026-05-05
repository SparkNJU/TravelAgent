<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="visible" class="drawer-overlay" @click="$emit('close')">
        <div class="drawer-panel" @click.stop>
          <div class="drawer-header">
            <div class="drawer-title">
              <SvgIcon name="sparkles" :size="18" class="ai-badge" />
              <span>AI 旅行规划</span>
            </div>
            <button class="close-btn" @click="$emit('close')">
              <SvgIcon name="close" :size="16" />
            </button>
          </div>

          <div class="drawer-body" v-if="!result">
            <form @submit.prevent="generatePlan" class="plan-form">
              <div class="form-group">
                <label class="form-label">旅行需求</label>
                <textarea
                  v-model="query"
                  rows="4"
                  placeholder="说说你的旅行想法，比如：帮我做一个东京 5 天旅行计划，偏美食和城市观光..."
                  class="form-textarea"
                />
              </div>

              <div class="quick-tags">
                <span class="quick-label">快捷标签</span>
                <div class="quick-list">
                  <button type="button" class="quick-tag" @click="appendTag('3天短途')">3天短途</button>
                  <button type="button" class="quick-tag" @click="appendTag('5天深度游')">5天深度游</button>
                  <button type="button" class="quick-tag" @click="appendTag('情侣出行')">情侣出行</button>
                  <button type="button" class="quick-tag" @click="appendTag('亲子游')">亲子游</button>
                  <button type="button" class="quick-tag" @click="appendTag('美食为主')">美食为主</button>
                  <button type="button" class="quick-tag" @click="appendTag('预算5000')">预算5000</button>
                </div>
              </div>

              <div class="form-group">
                <label class="form-label">参考文件</label>
                <label class="upload-area">
                  <input type="file" @change="handleFileChange" hidden />
                  <SvgIcon name="upload" :size="16" />
                  <span>{{ file ? file.name : '选择文件' }}</span>
                </label>
              </div>

              <button type="submit" class="submit-btn" :disabled="loading">
                <SvgIcon v-if="loading" name="loader" :size="16" spin />
                <SvgIcon v-else name="sparkles" :size="16" />
                {{ loading ? '生成中...' : '生成旅行计划' }}
              </button>

              <p v-if="errorMessage" class="error-msg">{{ errorMessage }}</p>
            </form>
          </div>

          <div class="drawer-body result-body" v-else>
            <div class="result-summary">
              <div class="summary-card">
                <span class="summary-label">目的地</span>
                <span class="summary-value">{{ result.destination || '-' }}</span>
              </div>
              <div class="summary-card">
                <span class="summary-label">天数</span>
                <span class="summary-value">{{ result.days || '-' }}天</span>
              </div>
            </div>

            <div class="result-content markdown-body" v-html="renderedMarkdown"></div>

            <div class="result-images" v-if="result.images?.length">
              <h4>图片参考</h4>
              <div class="image-grid">
                <a v-for="(img, i) in result.images" :key="i" :href="img.sourceUrl || img.imageUrl" target="_blank" rel="noreferrer" class="img-item">
                  <img :src="img.imageUrl" :alt="img.title" />
                </a>
              </div>
            </div>

            <div class="result-sources" v-if="result.sources?.length">
              <h4>参考来源</h4>
              <ul>
                <li v-for="(s, i) in result.sources" :key="i">
                  <a :href="s.link" target="_blank" rel="noreferrer">{{ s.title }}</a>
                  <p v-if="s.snippet">{{ s.snippet }}</p>
                </li>
              </ul>
            </div>

            <div class="result-actions">
              <button class="action-btn share-btn" @click="shareToCommunity">
                <SvgIcon name="share" :size="14" />
                分享到社区
              </button>
              <button class="action-btn reset-btn" @click="resetPlan">重新规划</button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import SvgIcon from './SvgIcon.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  prefill: { type: Object, default: () => null }
})

const emit = defineEmits(['close', 'share'])

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

watch(() => props.prefill, (val) => {
  if (val) { query.value = val.query || ''; result.value = null }
}, { immediate: true })

watch(() => props.visible, (val) => {
  if (val && !props.prefill) { result.value = null; errorMessage.value = '' }
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
    if (data.code === 200) { result.value = data.data }
    else { errorMessage.value = data.message || '生成失败' }
  } catch { errorMessage.value = '服务器错误' }
  finally { loading.value = false }
}

const shareToCommunity = () => { if (result.value) emit('share', result.value) }
const resetPlan = () => { result.value = null; errorMessage.value = ''; file.value = null }
</script>

<style scoped>
.drawer-overlay {
  position: fixed;
  inset: 0;
  background: var(--color-overlay);
  z-index: 1100;
  display: flex;
  justify-content: flex-end;
}

.drawer-panel {
  width: var(--drawer-width);
  max-width: 100vw;
  height: 100vh;
  background: var(--color-surface);
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-drawer);
  border-left: 1px solid var(--color-border);
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 15px;
  font-weight: 700;
  color: var(--color-title);
}

.ai-badge { color: var(--color-red-light); }

.close-btn {
  width: 28px; height: 28px;
  border: none;
  background: var(--color-card);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  color: var(--color-hint);
  transition: all 0.2s;
}
.close-btn:hover { background: var(--color-border); color: var(--color-title); }

.drawer-body { flex: 1; overflow-y: auto; padding: 18px; }

.plan-form { display: flex; flex-direction: column; gap: 14px; }
.form-group { display: flex; flex-direction: column; gap: 5px; }
.form-label { font-size: 12px; font-weight: 600; color: var(--color-secondary); }

.form-textarea {
  resize: vertical;
  min-height: 90px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-input);
  padding: 10px 12px;
  font-size: 13px;
  font-family: var(--font-family);
  background: var(--color-card);
  color: var(--color-title);
  transition: border-color 0.2s;
}
.form-textarea:focus {
  outline: none;
  border-color: var(--color-red);
  box-shadow: 0 0 0 3px rgba(230, 57, 70, 0.1);
}
.form-textarea::placeholder { color: var(--color-muted); }

.quick-tags { display: flex; flex-direction: column; gap: 6px; }
.quick-label { font-size: 11px; color: var(--color-muted); }
.quick-list { display: flex; flex-wrap: wrap; gap: 6px; }

.quick-tag {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  padding: 4px 10px;
  font-size: 11px;
  color: var(--color-hint);
  background: var(--color-card);
  cursor: pointer;
  transition: all 0.2s;
  font-family: var(--font-family);
}
.quick-tag:hover {
  border-color: var(--color-red);
  color: var(--color-red-light);
}

.upload-area {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 12px;
  border: 1.5px dashed var(--color-border);
  border-radius: var(--radius-input);
  color: var(--color-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.upload-area:hover { border-color: var(--color-red); color: var(--color-red-light); }

.submit-btn {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  width: 100%; padding: 10px;
  border: none; border-radius: var(--radius-pill);
  background: var(--gradient-brand);
  color: white;
  font-size: 13px; font-weight: 600; font-family: var(--font-family);
  cursor: pointer;
  box-shadow: var(--shadow-button);
  transition: all 0.2s;
}
.submit-btn:hover:not(:disabled) { filter: brightness(1.1); transform: translateY(-1px); }
.submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.error-msg { margin: 0; font-size: 12px; color: var(--color-red-light); }

/* Result */
.result-body { display: flex; flex-direction: column; gap: 16px; }

.result-summary { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.summary-card {
  padding: 10px 14px;
  background: var(--color-card);
  border-radius: var(--radius-input);
  border-left: 3px solid var(--color-red);
}
.summary-label { display: block; font-size: 10px; color: var(--color-muted); margin-bottom: 3px; }
.summary-value { font-size: 14px; font-weight: 600; color: var(--color-title); }

.markdown-body { line-height: 1.7; color: var(--color-body); font-size: 13px; }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) {
  margin-top: 16px; margin-bottom: 8px; font-weight: 700; color: var(--color-title);
}
.markdown-body :deep(h1) { font-size: 18px; }
.markdown-body :deep(h2) { font-size: 15px; }
.markdown-body :deep(h3) { font-size: 14px; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 18px; margin: 6px 0; }
.markdown-body :deep(li) { margin-bottom: 4px; }
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--color-red);
  margin: 10px 0; padding: 8px 12px;
  background: rgba(230, 57, 70, 0.06);
  border-radius: 0 var(--radius-input) var(--radius-input) 0;
  color: var(--color-secondary);
}
.markdown-body :deep(a) { color: var(--color-red-light); text-decoration: none; }
.markdown-body :deep(a):hover { text-decoration: underline; }
.markdown-body :deep(strong) { color: var(--color-title); }

.result-images h4, .result-sources h4 {
  font-size: 13px; font-weight: 600; margin: 0 0 8px; color: var(--color-title);
}
.image-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.img-item img { width: 100%; border-radius: var(--radius-image); aspect-ratio: 4/3; object-fit: cover; display: block; }
.result-sources ul { list-style: none; padding: 0; margin: 0; }
.result-sources li { margin-bottom: 8px; }
.result-sources a { font-size: 12px; font-weight: 500; color: var(--color-red-light); text-decoration: none; }
.result-sources p { margin: 3px 0 0; font-size: 11px; color: var(--color-hint); }

.result-actions { display: flex; gap: 8px; padding-top: 14px; border-top: 1px solid var(--color-border); }
.action-btn {
  display: flex; align-items: center; gap: 5px;
  padding: 8px 16px;
  border: none; border-radius: var(--radius-pill);
  font-size: 12px; font-weight: 600; font-family: var(--font-family);
  cursor: pointer; transition: all 0.2s;
}
.share-btn { background: var(--gradient-brand); color: white; }
.share-btn:hover { filter: brightness(1.1); }
.reset-btn { background: var(--color-card); color: var(--color-secondary); border: 1px solid var(--color-border); }
.reset-btn:hover { background: var(--color-border); }

/* Transition */
.drawer-enter-active, .drawer-leave-active { transition: opacity 0.2s; }
.drawer-enter-active .drawer-panel, .drawer-leave-active .drawer-panel { transition: transform 0.3s ease; }
.drawer-enter-from, .drawer-leave-to { opacity: 0; }
.drawer-enter-from .drawer-panel, .drawer-leave-to .drawer-panel { transform: translateX(100%); }

@media (max-width: 520px) { .drawer-panel { width: 100vw; } }
</style>
