<template>
  <div class="discover-page">
    <main class="discover-main">
      <!-- 搜索栏 -->
      <div class="search-bar">
        <SvgIcon name="search" :size="18" />
        <input
          v-model="searchQuery"
          type="search"
          placeholder="搜索目的地、标签或笔记标题..."
          @input="handleSearch"
        />
        <button v-if="searchQuery" class="search-clear" @click="resetFilters" title="清除搜索">
          <SvgIcon name="close" :size="14" />
        </button>
      </div>

      <section class="channel-row" aria-label="旅行频道">
        <button
          v-for="channel in channels"
          :key="channel.id"
          :class="['channel-chip', { active: activeChannel === channel.id }]"
          @click="activeChannel = channel.id"
        >
          {{ channel.name }}
        </button>
        <button class="publish-chip" @click="requireAuth(() => showPublishModal = true)">
          <SvgIcon name="plus" :size="14" />
          发布笔记
        </button>
      </section>

      <section class="note-grid" aria-label="旅行笔记">
        <article
          v-for="note in filteredNotes"
          :key="note.id"
          class="note-card"
          @click="openNote(note)"
        >
          <div class="note-cover">
            <img :src="note.cover" :alt="note.title" loading="lazy" />
            <span class="note-city">{{ note.city }}</span>
            <button class="plan-float" @click.stop="planFromNote(note)">用它规划</button>
          </div>
          <div class="note-body">
            <h2>{{ note.title }}</h2>
            <p>{{ note.summary }}</p>
            <div class="note-tags">
              <span v-for="tag in note.tags?.slice(0, 3)" :key="tag">#{{ tag }}</span>
            </div>
            <footer class="note-footer">
              <div class="note-author">
                <span v-if="!note.avatarUrl" class="avatar-text">{{ note.avatar }}</span>
                <img v-else :src="note.avatarUrl" class="avatar-img" alt="" />
                <span>{{ note.author }}</span>
              </div>
              <div class="note-stats">
                <span :class="{ liked: note.isLiked }">
                  <SvgIcon :name="note.isLiked ? 'heart-fill' : 'heart'" :size="13" />
                  {{ formatCount(note.likes) }}
                </span>
                <span>{{ formatCount(note.collections) }} 收藏</span>
              </div>
            </footer>
          </div>
        </article>
      </section>

      <div v-if="!filteredNotes.length" class="empty-state">
        <SvgIcon name="search" :size="32" />
        <p>没有找到相关旅行灵感</p>
        <button @click="resetFilters">查看推荐内容</button>
      </div>
    </main>

    <PublishModal
      v-if="showPublishModal"
      @close="closePublishModal"
      @success="handlePublishSuccess"
    />

    <Teleport to="body">
      <Transition name="modal">
        <div v-if="selectedNote" class="detail-overlay" @click="closeNote">
          <article class="detail-panel" @click.stop>
            <button class="detail-close" aria-label="关闭详情" @click="closeNote">
              <SvgIcon name="close" :size="18" />
            </button>

            <div class="detail-media">
              <img :src="selectedNote.cover" :alt="selectedNote.title" />
            </div>

            <div class="detail-content">
              <div class="detail-kicker">
                <span>{{ selectedNote.city }}</span>
                <span>{{ selectedNote.days || 3 }} 天建议</span>
                <span>{{ selectedNote.budget }}</span>
              </div>
              <h2>{{ selectedNote.title }}</h2>
              <p class="detail-summary">{{ selectedNote.summary }}</p>

              <div class="detail-tags">
                <span v-for="tag in selectedNote.tags" :key="tag">#{{ tag }}</span>
              </div>

              <div class="detail-section">
                <h3>代表景点</h3>
                <div class="spot-list">
                  <span v-for="spot in selectedNote.spots" :key="spot">{{ spot }}</span>
                </div>
              </div>

              <div class="detail-author">
                <span v-if="!selectedNote.avatarUrl" class="avatar-text large">{{ selectedNote.avatar }}</span>
                <img v-else :src="selectedNote.avatarUrl" class="avatar-img large" alt="" />
                <div>
                  <strong>{{ selectedNote.author }}</strong>
                  <p>收藏 {{ formatCount(selectedNote.collections) }} · 点赞 {{ formatCount(selectedNote.likes) }}</p>
                </div>
              </div>

              <div v-if="selectedNote.isCommunity" class="comment-box">
                <button class="comment-toggle" @click="toggleComments">
                  <SvgIcon name="message" :size="15" />
                  {{ showComments ? '收起评论' : `查看评论（${selectedNote.comments || 0}）` }}
                </button>
                <div v-if="showComments" class="comment-list">
                  <div v-for="comment in comments" :key="comment.id" class="comment-item">
                    <span class="avatar-text small">{{ (comment.username || '用').charAt(0) }}</span>
                    <div>
                      <strong>{{ comment.username }}</strong>
                      <p>{{ comment.content }}</p>
                    </div>
                  </div>
                  <p v-if="!comments.length" class="no-comments">暂无评论</p>
                  <div class="comment-input">
                    <input v-model="newComment" placeholder="写下你的评论..." @keydown.enter="submitComment" />
                    <button @click="submitComment"><SvgIcon name="send" :size="14" /></button>
                  </div>
                </div>
              </div>

              <div class="detail-actions">
                <button class="detail-primary" @click="planFromNote(selectedNote)">
                  <SvgIcon name="sparkles" :size="15" />
                  开始规划
                </button>
                <button
                  v-if="selectedNote.isCommunity"
                  class="detail-secondary"
                  :class="{ liked: selectedNote.isLiked }"
                  @click="handleLike(selectedNote)"
                >
                  <SvgIcon :name="selectedNote.isLiked ? 'heart-fill' : 'heart'" :size="15" />
                  {{ selectedNote.isLiked ? '已喜欢' : '喜欢' }}
                </button>
                <button class="detail-secondary" @click="handleShare(selectedNote)">
                  <SvgIcon name="share" :size="15" />
                  分享
                </button>
              </div>
            </div>
          </article>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SvgIcon from '../components/SvgIcon.vue'
import PublishModal from '../components/PublishModal.vue'
import { useAuth } from '../composables/useAuth'
import { channels, travelNotes } from '../data/travelData'

const router = useRouter()
const route = useRoute()
const { isLoggedIn } = useAuth()
const showLogin = inject('showLoginModal')

const searchQuery = ref('')
const activeChannel = ref('all')
const backendNotes = ref([])
const selectedNote = ref(null)
const showPublishModal = ref(false)
const showComments = ref(false)
const comments = ref([])
const newComment = ref('')

function requireAuth(action) {
  if (!isLoggedIn.value) {
    showLogin?.()
    return
  }
  action()
}

function clearPublishQuery() {
  if (!route.query.publish) return
  const nextQuery = { ...route.query }
  delete nextQuery.publish
  router.replace({ path: route.path, query: nextQuery })
}

function openPublishFromQuery() {
  if (route.query.publish !== '1') return
  requireAuth(() => {
    showPublishModal.value = true
  })
}

function formatCount(value) {
  const n = Number(value || 0)
  if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

function normalizePost(post) {
  const tags = Array.isArray(post.tags) ? post.tags : []
  return {
    id: `post-${post.id}`,
    postId: post.id,
    isCommunity: true,
    title: post.title || '旅行笔记',
    city: tags[0] || '旅行',
    channel: 'all',
    cover: post.images?.[0] || 'https://images.unsplash.com/photo-1488646953014-85cb44e25828?auto=format&fit=crop&w=900&q=80',
    tags,
    author: post.username || '旅行用户',
    avatar: (post.username || '旅').charAt(0),
    avatarUrl: post.avatar || '',
    likes: post.likes || 0,
    collections: post.shares || 0,
    comments: post.comments || 0,
    summary: post.description || '这是一篇来自社区的旅行分享。',
    days: 3,
    budget: '按需规划',
    spots: tags.slice(0, 4),
    isLiked: false,
    raw: post,
  }
}

const allNotes = computed(() => [
  ...backendNotes.value,
  ...travelNotes,
])

const filteredNotes = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  return allNotes.value.filter((note) => {
    const channelMatched = activeChannel.value === 'all' || note.channel === activeChannel.value
    if (!channelMatched) return false
    if (!keyword) return true
    return [
      note.title,
      note.city,
      note.summary,
      ...(note.tags || []),
      ...(note.spots || []),
    ].some(item => String(item || '').toLowerCase().includes(keyword))
  })
})

async function loadPosts() {
  try {
    const res = await fetch('/api/community/posts')
    const data = await res.json()
    backendNotes.value = data.code === 200 && Array.isArray(data.data)
      ? data.data.map(normalizePost)
      : []
  } catch {
    backendNotes.value = []
  }
}

function handleSearch() {
  activeChannel.value = 'all'
}

function resetFilters() {
  searchQuery.value = ''
  activeChannel.value = 'all'
}

function openNote(note) {
  selectedNote.value = note
  comments.value = []
  newComment.value = ''
  showComments.value = false
}

function closeNote() {
  selectedNote.value = null
  showComments.value = false
}

function buildPlanQuery(note) {
  const tags = note.tags?.length ? `，偏${note.tags.join('、')}` : ''
  return `参考《${note.title}》，帮我规划${note.city}${note.days || 3}天旅行${tags}`
}

function planFromNote(note) {
  router.push({ path: '/ai-plan', query: { q: buildPlanQuery(note), auto: '1' } })
}

async function toggleComments() {
  showComments.value = !showComments.value
  const note = selectedNote.value
  if (!showComments.value || !note?.isCommunity || comments.value.length) return
  try {
    const res = await fetch(`/api/community/posts/${note.postId}/comments`)
    const data = await res.json()
    comments.value = data.code === 200 && Array.isArray(data.data) ? data.data : []
  } catch {
    comments.value = []
  }
}

async function submitComment() {
  if (!selectedNote.value?.isCommunity) return
  if (!isLoggedIn.value) {
    showLogin?.()
    return
  }
  if (!newComment.value.trim()) return
  try {
    const res = await fetch(`/api/community/posts/${selectedNote.value.postId}/comments`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': localStorage.getItem('userId') || '1',
      },
      body: JSON.stringify({ content: newComment.value.trim() }),
    })
    const data = await res.json()
    if (data.code === 200) {
      comments.value.unshift(data.data || {
        id: Date.now(),
        username: localStorage.getItem('username') || '用户',
        content: newComment.value.trim(),
      })
      selectedNote.value.comments = (selectedNote.value.comments || 0) + 1
      newComment.value = ''
    }
  } catch {
    // 评论失败时保持弹窗状态，避免丢失用户正在查看的内容。
  }
}

function handleShare(note) {
  const url = `${window.location.origin}/ai-plan?q=${encodeURIComponent(buildPlanQuery(note))}`
  if (navigator.share) {
    navigator.share({ title: note.title, text: note.summary, url }).catch(() => {})
  } else {
    navigator.clipboard?.writeText(url).then(() => {
      alert('链接已复制到剪贴板')
    }).catch(() => {})
  }
}

async function handleLike(note) {
  if (!note?.isCommunity) return
  if (!isLoggedIn.value) {
    showLogin?.()
    return
  }
  try {
    const res = await fetch(`/api/community/posts/${note.postId}/like`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': localStorage.getItem('userId') || '1',
      },
    })
    const data = await res.json()
    if (data.code === 200) {
      note.isLiked = !note.isLiked
      note.likes += note.isLiked ? 1 : -1
    }
  } catch {
    // 点赞失败不做乐观更新。
  }
}

function handlePublishSuccess() {
  showPublishModal.value = false
  clearPublishQuery()
  loadPosts()
}

function closePublishModal() {
  showPublishModal.value = false
  clearPublishQuery()
}

watch(() => route.query.publish, openPublishFromQuery)

onMounted(() => {
  loadPosts()
  openPublishFromQuery()
})
</script>

<style scoped>
.discover-page {
  min-height: 100%;
  background: var(--color-page);
  color: var(--color-title);
}

.discover-main {
  width: 100%;
  max-width: 100%;
  padding: 12px 28px 56px;
  overflow-x: hidden;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 680px;
  margin: 0 auto 16px;
  padding: 0 16px;
  height: 48px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-hint);
  box-shadow: 0 4px 18px rgba(31, 31, 31, 0.05);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.search-bar:focus-within {
  border-color: rgba(255, 36, 66, 0.32);
  box-shadow: 0 0 0 4px rgba(255, 36, 66, 0.06), 0 6px 22px rgba(31, 31, 31, 0.08);
}

.search-bar input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-title);
  font-size: 15px;
}

.search-bar input::placeholder {
  color: var(--color-hint);
}

.search-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 50%;
  background: var(--color-surface);
  color: var(--color-hint);
  flex-shrink: 0;
  cursor: pointer;
}

.search-clear:hover {
  background: var(--color-border);
  color: var(--color-secondary);
}

.channel-row {
  position: sticky;
  top: 0;
  z-index: 90;
  display: flex;
  gap: 8px;
  align-items: center;
  max-width: 1440px;
  margin: 0 auto 20px;
  padding: 12px 0;
  overflow-x: auto;
  background: var(--color-page);
}

.channel-row::-webkit-scrollbar {
  display: none;
}

.channel-chip,
.publish-chip,
.plan-float,
.detail-close,
.detail-primary,
.detail-secondary,
.comment-toggle {
  font-family: var(--font-family);
}

.channel-chip,
.publish-chip {
  flex-shrink: 0;
  height: 34px;
  padding: 0 15px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-secondary);
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.channel-chip.active {
  border-color: var(--color-red);
  background: var(--color-red);
  color: #ffffff;
}

.publish-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  border-color: rgba(255, 36, 66, 0.22);
  color: var(--color-red);
}

.note-grid {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  column-count: 4;
  column-gap: 18px;
}

.note-card {
  display: inline-block;
  width: 100%;
  margin: 0 0 18px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-card);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.note-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 14px 36px rgba(31, 31, 31, 0.08);
}

.note-cover {
  position: relative;
  overflow: hidden;
  background: var(--color-surface);
}

.note-cover img {
  display: block;
  width: 100%;
  min-height: 210px;
  max-height: 360px;
  object-fit: cover;
}

.note-city {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 4px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-card) 92%, transparent);
  color: var(--color-title);
  font-size: 12px;
  font-weight: 900;
}

.plan-float {
  position: absolute;
  right: 10px;
  bottom: 10px;
  height: 30px;
  padding: 0 12px;
  border: 0;
  border-radius: 999px;
  background: var(--color-red);
  color: #ffffff;
  font-size: 12px;
  font-weight: 900;
  box-shadow: 0 8px 18px rgba(255, 36, 66, 0.24);
  cursor: pointer;
}

.note-body {
  padding: 14px 14px 13px;
}

.note-body h2 {
  margin: 0 0 8px;
  color: var(--color-title);
  font-size: 16px;
  line-height: 1.45;
}

.note-body p {
  display: -webkit-box;
  margin: 0 0 10px;
  overflow: hidden;
  color: var(--color-secondary);
  font-size: 13px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.note-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.note-tags span,
.detail-tags span {
  color: var(--color-red);
  font-size: 12px;
  font-weight: 800;
}

.note-footer,
.note-author,
.note-stats,
.detail-author,
.detail-actions,
.comment-input {
  display: flex;
  align-items: center;
}

.note-footer {
  justify-content: space-between;
  gap: 10px;
}

.note-author {
  gap: 7px;
  min-width: 0;
  color: var(--color-secondary);
  font-size: 12px;
  font-weight: 800;
}

.avatar-text {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--color-soft-red);
  color: var(--color-red);
  font-size: 12px;
  font-weight: 900;
}

.avatar-text.large,
.avatar-img.large {
  width: 40px;
  height: 40px;
  font-size: 16px;
}

.avatar-text.small {
  width: 28px;
  height: 28px;
}

.avatar-img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.note-stats {
  gap: 8px;
  color: var(--color-hint);
  font-size: 12px;
  white-space: nowrap;
}

.note-stats span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.liked {
  color: var(--color-red) !important;
}

.empty-state {
  max-width: 520px;
  margin: 60px auto;
  padding: 36px;
  border: 1px solid var(--color-border);
  border-radius: 18px;
  background: var(--color-card);
  text-align: center;
  color: var(--color-hint);
}

.empty-state p {
  margin: 12px 0;
  color: var(--color-secondary);
}

.empty-state button {
  height: 34px;
  padding: 0 15px;
  border: 0;
  border-radius: 999px;
  background: var(--color-red);
  color: #ffffff;
  font-weight: 800;
  cursor: pointer;
}

.detail-overlay {
  position: fixed;
  inset: 0;
  z-index: 1400;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24px;
  background: var(--color-overlay);
}

.detail-panel {
  position: relative;
  display: grid;
  grid-template-columns: minmax(280px, 0.95fr) minmax(320px, 1fr);
  width: min(980px, 100%);
  max-height: 88vh;
  overflow: hidden;
  border-radius: 20px;
  background: var(--color-card);
}

.detail-close {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 2;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: color-mix(in srgb, var(--color-card) 92%, transparent);
  color: var(--color-title);
  cursor: pointer;
}

.detail-media {
  min-height: 480px;
  background: var(--color-surface);
}

.detail-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-content {
  overflow-y: auto;
  padding: 34px 32px 28px;
}

.detail-kicker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.detail-kicker span {
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--color-soft-red);
  color: var(--color-red);
  font-size: 12px;
  font-weight: 900;
}

.detail-content h2 {
  margin: 0 0 12px;
  color: var(--color-title);
  font-size: 28px;
  line-height: 1.25;
}

.detail-summary {
  margin: 0 0 16px;
  color: var(--color-body);
  font-size: 15px;
  line-height: 1.8;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  margin-bottom: 22px;
}

.detail-section {
  padding: 16px 0;
  border-top: 1px solid var(--color-border);
}

.detail-section h3 {
  margin: 0 0 10px;
  color: var(--color-title);
  font-size: 14px;
}

.spot-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.spot-list span {
  padding: 7px 11px;
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-body);
  font-size: 13px;
}

.detail-author {
  gap: 10px;
  padding: 16px 0;
  border-top: 1px solid var(--color-border);
}

.detail-author strong {
  color: var(--color-title);
  font-size: 14px;
}

.detail-author p {
  margin: 2px 0 0;
  color: var(--color-hint);
  font-size: 12px;
}

.comment-box {
  margin: 8px 0 18px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  overflow: hidden;
}

.comment-toggle {
  display: inline-flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 40px;
  border: 0;
  background: var(--color-surface);
  color: var(--color-body);
  font-weight: 800;
  cursor: pointer;
}

.comment-list {
  padding: 12px;
}

.comment-item {
  display: flex;
  gap: 9px;
  padding: 8px 0;
}

.comment-item strong {
  color: var(--color-title);
  font-size: 12px;
}

.comment-item p {
  margin: 2px 0 0;
  color: var(--color-secondary);
  font-size: 13px;
}

.no-comments {
  margin: 8px 0 12px;
  color: var(--color-hint);
  font-size: 13px;
  text-align: center;
}

.comment-input {
  gap: 8px;
  margin-top: 8px;
}

.comment-input input {
  flex: 1;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-title);
  outline: 0;
}

.comment-input button {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: var(--color-red);
  color: #ffffff;
  cursor: pointer;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: nowrap;
}

.detail-primary,
.detail-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  padding: 0 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}

.detail-primary {
  flex: 1 1 auto;
  min-width: 0;
  border: 0;
  background: #ff2442;
  color: #ffffff;
}

.detail-secondary {
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-body);
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

@media (max-width: 1240px) {
  .note-grid {
    column-count: 3;
  }
}

@media (max-width: 860px) {
  .note-grid {
    column-count: 2;
  }

  .detail-panel {
    grid-template-columns: 1fr;
    overflow-y: auto;
  }

  .detail-media {
    min-height: 300px;
  }
}

@media (max-width: 560px) {
  .discover-main {
    padding: 10px 16px 42px;
  }

  .channel-row,
  .note-grid,
  .note-card {
    max-width: 100%;
  }

  .note-grid {
    column-count: 1;
  }

  .detail-overlay {
    padding: 12px;
  }

  .detail-content {
    padding: 24px 18px 20px;
  }
}
</style>
