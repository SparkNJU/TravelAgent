<template>
  <Teleport to="body">
    <Transition name="modal">
      <div class="publish-overlay" @click="$emit('close')">
        <div class="publish-panel" @click.stop>
          <div class="publish-header">
            <h3>发布帖子</h3>
            <button class="close-btn" @click="$emit('close')">
              <SvgIcon name="close" :size="18" />
            </button>
          </div>

          <div class="publish-body">
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

            <textarea v-model="description" rows="4" placeholder="分享你的旅行故事..." class="field" />

            <div class="tags-section">
              <div class="preset-tags">
                <button
                  v-for="tag in presetTags" :key="tag"
                  :class="['tag-btn', { selected: selectedTags.includes(tag) }]"
                  @click="toggleTag(tag)"
                >{{ tag }}</button>
              </div>
              <div class="custom-tag-row">
                <input v-model="customTag" placeholder="自定义标签" class="field small" @keydown.enter="addCustomTag" />
              </div>
            </div>
          </div>

          <div class="publish-footer">
            <button class="cancel-btn" @click="$emit('close')">取消</button>
            <button class="submit-btn" @click="submit">发布</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref } from 'vue'
import SvgIcon from './SvgIcon.vue'

const emit = defineEmits(['close', 'success'])

const title = ref('')
const description = ref('')
const images = ref([])
const selectedTags = ref([])
const customTag = ref('')

const presetTags = ['美食', '景点', '住宿', '路线', '攻略', '自然风光', '城市', '亲子', '情侣', '出境游']

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
  if (!title.value.trim() || !description.value.trim() || !selectedTags.value.length) return
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
      emit('success')
    }
  } catch { /* ignore */ }
}
</script>

<style scoped>
.publish-overlay {
  position: fixed; inset: 0; background: var(--color-overlay);
  display: flex; align-items: center; justify-content: center;
  z-index: 1200; padding: 20px;
}

.publish-panel {
  background: var(--color-surface); border-radius: var(--radius-modal);
  max-width: 520px; width: 100%; max-height: 85vh; overflow-y: auto;
  border: 1px solid var(--color-border); scrollbar-width: none;
}
.publish-panel::-webkit-scrollbar { display: none; }

.publish-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; border-bottom: 1px solid var(--color-border);
}
.publish-header h3 { font-size: 16px; font-weight: 700; color: var(--color-title); margin: 0; }

.close-btn {
  width: 30px; height: 30px; border: none; background: var(--color-card);
  border-radius: 50%; display: flex; align-items: center; justify-content: center;
  cursor: pointer; color: var(--color-hint); transition: all 0.2s;
}
.close-btn:hover { background: var(--color-border); color: var(--color-title); }

.publish-body { padding: 20px; display: flex; flex-direction: column; gap: 14px; }

.field {
  width: 100%; padding: 10px 12px; border: 1.5px solid var(--color-border);
  border-radius: var(--radius-input); font-size: 14px; font-family: var(--font-family);
  outline: none; background: var(--color-bg); color: var(--color-title);
  transition: border-color 0.2s;
}
.field:focus { border-color: var(--color-red); }
.field::placeholder { color: var(--color-muted); }
.field.small { padding: 7px 10px; font-size: 12px; }
.title-field { font-weight: 600; font-size: 15px; }

.upload-area { display: flex; gap: 10px; align-items: flex-start; flex-wrap: wrap; }

.upload-trigger {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 4px; width: 72px; height: 72px; border: 1.5px dashed var(--color-border);
  border-radius: var(--radius-input); cursor: pointer; color: var(--color-muted);
  font-size: 11px; transition: all 0.2s;
}
.upload-trigger:hover { border-color: var(--color-red); color: var(--color-red-light); }

.preview-row { display: flex; gap: 6px; flex-wrap: wrap; }

.preview-item { position: relative; width: 72px; height: 72px; border-radius: 8px; overflow: hidden; }
.preview-item img { width: 100%; height: 100%; object-fit: cover; }

.remove-img {
  position: absolute; top: 2px; right: 2px; width: 18px; height: 18px;
  background: rgba(0,0,0,0.6); border: none; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: white; cursor: pointer;
}

.preset-tags { display: flex; flex-wrap: wrap; gap: 6px; }

.tag-btn {
  padding: 5px 12px; border: 1px solid var(--color-border); border-radius: var(--radius-pill);
  background: none; font-size: 12px; color: var(--color-hint);
  cursor: pointer; transition: all 0.2s; font-family: var(--font-family);
}
.tag-btn:hover { border-color: var(--color-red); color: var(--color-red-light); }
.tag-btn.selected { background: var(--color-red); border-color: var(--color-red); color: white; }

.custom-tag-row { margin-top: 6px; }

.publish-footer {
  display: flex; justify-content: flex-end; gap: 10px;
  padding: 14px 20px; border-top: 1px solid var(--color-border);
}

.cancel-btn {
  padding: 8px 20px; border: 1px solid var(--color-border); border-radius: var(--radius-pill);
  background: none; color: var(--color-hint); font-size: 13px; cursor: pointer;
  font-family: var(--font-family); transition: all 0.2s;
}
.cancel-btn:hover { background: var(--color-card); }

.submit-btn {
  padding: 8px 20px; border: none; border-radius: var(--radius-pill);
  background: var(--gradient-brand); color: white; font-size: 13px;
  font-weight: 600; cursor: pointer; font-family: var(--font-family); transition: all 0.2s;
}
.submit-btn:hover { filter: brightness(1.1); }

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>