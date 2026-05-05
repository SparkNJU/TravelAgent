<template>
  <div class="publish-page">
    <div class="publish-inner">
      <div class="publish-header">
        <SvgIcon name="plus" :size="20" class="header-icon" />
        <div>
          <h2>发布帖子</h2>
          <p>分享你的旅行故事和攻略</p>
        </div>
      </div>

      <div class="publish-form">
        <input v-model="title" placeholder="标题" class="field title-field" />

        <div class="upload-area">
          <label class="upload-trigger">
            <input type="file" accept="image/*" multiple @change="handleImages" hidden />
            <SvgIcon name="plus" :size="20" />
            <span>添加图片</span>
          </label>
          <div class="preview-row">
            <div v-for="(img, i) in images" :key="i" class="preview-item">
              <img :src="img" />
              <button class="remove-img" @click="images.splice(i, 1)">
                <SvgIcon name="close" :size="12" />
              </button>
            </div>
          </div>
        </div>

        <textarea v-model="description" rows="5" placeholder="分享你的旅行故事..." class="field" />

        <div class="tags-section">
          <label class="section-label">选择标签</label>
          <div class="preset-tags">
            <button
              v-for="tag in presetTags" :key="tag"
              :class="['tag-btn', { selected: selectedTags.includes(tag) }]"
              @click="toggleTag(tag)"
            >{{ tag }}</button>
          </div>
          <input v-model="customTag" placeholder="自定义标签，回车添加" class="field small" @keydown.enter="addCustomTag" />
        </div>

        <div class="form-actions">
          <button class="submit-btn" :disabled="!canSubmit" @click="submit">
            <SvgIcon name="check" :size="16" />
            发布
          </button>
        </div>

        <p v-if="successMsg" class="success-msg">{{ successMsg }}</p>
        <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import SvgIcon from '../components/SvgIcon.vue'

const router = useRouter()
const title = ref('')
const description = ref('')
const images = ref([])
const selectedTags = ref([])
const customTag = ref('')
const successMsg = ref('')
const errorMsg = ref('')

const presetTags = ['美食', '景点', '住宿', '路线', '攻略', '自然风光', '城市', '亲子', '情侣', '出境游']

const canSubmit = computed(() => title.value.trim() && description.value.trim() && selectedTags.value.length)

const handleImages = (e) => {
  const files = Array.from(e.target.files || []).slice(0, 9 - images.value.length)
  files.forEach(f => {
    const reader = new FileReader()
    reader.onload = (ev) => images.value.push(ev.target.result)
    reader.readAsDataURL(f)
  })
}

const toggleTag = (tag) => {
  const i = selectedTags.value.indexOf(tag)
  if (i > -1) selectedTags.value.splice(i, 1)
  else if (selectedTags.value.length < 5) selectedTags.value.push(tag)
}

const addCustomTag = () => {
  const t = customTag.value.trim()
  if (t && !selectedTags.value.includes(t) && selectedTags.value.length < 5) {
    selectedTags.value.push(t)
    customTag.value = ''
  }
}

const submit = async () => {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    const res = await fetch('/api/community/posts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-User-Id': localStorage.getItem('userId') || '1' },
      body: JSON.stringify({
        title: title.value,
        description: description.value,
        images: images.value,
        avatar: '', nickname: localStorage.getItem('username') || '用户', bio: '',
        tags: selectedTags.value
      })
    })
    const data = await res.json()
    if (data.code === 200) {
      successMsg.value = '发布成功！'
      setTimeout(() => router.push('/'), 1000)
    } else {
      errorMsg.value = data.message || '发布失败'
    }
  } catch {
    errorMsg.value = '发布失败，请重试'
  }
}
</script>

<style scoped>
.publish-page {
  background: var(--color-bg);
  font-family: var(--font-family);
  color: var(--color-body);
  min-height: 100%;
}

.publish-inner {
  max-width: 640px;
  margin: 0 auto;
  padding: 28px 32px;
}

.publish-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.header-icon { color: var(--color-red-light); }

.publish-header h2 {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-title);
  margin: 0;
}

.publish-header p {
  font-size: 13px;
  color: var(--color-hint);
  margin: 3px 0 0;
}

.publish-form {
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 24px;
  border: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-input);
  font-size: 14px;
  font-family: var(--font-family);
  outline: none;
  background: var(--color-bg);
  color: var(--color-title);
  transition: border-color 0.2s;
  resize: vertical;
}
.field:focus { border-color: var(--color-red); }
.field::placeholder { color: var(--color-muted); }
.field.small { padding: 7px 12px; font-size: 13px; }
.title-field { font-weight: 600; font-size: 16px; }

.upload-area { display: flex; gap: 10px; align-items: flex-start; flex-wrap: wrap; }

.upload-trigger {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 4px; width: 80px; height: 80px; border: 1.5px dashed var(--color-border);
  border-radius: var(--radius-input); cursor: pointer; color: var(--color-muted);
  font-size: 11px; transition: all 0.2s;
}
.upload-trigger:hover { border-color: var(--color-red); color: var(--color-red-light); }

.preview-row { display: flex; gap: 8px; flex-wrap: wrap; }

.preview-item { position: relative; width: 80px; height: 80px; border-radius: 8px; overflow: hidden; }
.preview-item img { width: 100%; height: 100%; object-fit: cover; }

.remove-img {
  position: absolute; top: 2px; right: 2px; width: 20px; height: 20px;
  background: rgba(0,0,0,0.6); border: none; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: white; cursor: pointer;
}

.tags-section { display: flex; flex-direction: column; gap: 10px; }
.section-label { font-size: 13px; font-weight: 600; color: var(--color-secondary); }

.preset-tags { display: flex; flex-wrap: wrap; gap: 6px; }

.tag-btn {
  padding: 6px 14px; border: 1px solid var(--color-border); border-radius: var(--radius-pill);
  background: none; font-size: 13px; color: var(--color-hint);
  cursor: pointer; transition: all 0.2s; font-family: var(--font-family);
}
.tag-btn:hover { border-color: var(--color-red); color: var(--color-red-light); }
.tag-btn.selected { background: var(--color-red); border-color: var(--color-red); color: white; }

.form-actions { display: flex; justify-content: flex-end; }

.submit-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 10px 28px; border: none; border-radius: var(--radius-pill);
  background: var(--gradient-brand); color: white;
  font-size: 14px; font-weight: 600; font-family: var(--font-family);
  cursor: pointer; box-shadow: var(--shadow-button); transition: all 0.2s;
}
.submit-btn:hover:not(:disabled) { filter: brightness(1.1); transform: translateY(-1px); }
.submit-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.success-msg { margin: 0; font-size: 13px; color: #34d399; }
.error-msg { margin: 0; font-size: 13px; color: var(--color-red-light); }
</style>
