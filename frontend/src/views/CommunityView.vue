<template>
  <div class="discover-page">
    <header class="discover-header">
      <button class="publish-btn" @click="requireAuth(() => showPublishModal = true)">
        <SvgIcon name="plus" :size="16" />
        <span>发布</span>
      </button>
      <div class="search-wrapper">
        <div class="search-box">
          <SvgIcon name="search" :size="15" class="search-icon" />
          <input
            v-model="searchQuery"
            placeholder="搜索旅行攻略、目的地..."
            class="search-input"
            @keydown.enter="handleSearch"
          />
        </div>
        <div class="category-bar">
          <button
            v-for="cat in categories"
            :key="cat.id"
            :class="['cat-item', { active: activeCategory === cat.id }]"
            @click="activeCategory = cat.id; handleCategoryChange()"
          >
            {{ cat.name }}
          </button>
        </div>
      </div>
    </header>

    <main class="feed-area">
      <div class="masonry">
        <article
          v-for="post in filteredPosts"
          :key="post.id"
          class="post-card"
          @click="openPost(post)"
        >
          <div
            v-if="post.images?.length"
            class="card-img"
            :style="{ backgroundImage: `url(${post.images[0]})` }"
          >
            <div class="img-count" v-if="post.images.length > 1">{{ post.images.length }}</div>
          </div>
          <div class="card-body">
            <h3 class="card-title">{{ post.title }}</h3>
            <p class="card-desc">{{ post.description }}</p>
            <div class="card-tags">
              <span v-for="tag in post.tags?.slice(0, 3)" :key="tag" class="tag">{{ tag }}</span>
            </div>
            <div class="card-footer">
              <div class="card-author">
                <img v-if="post.avatar" :src="post.avatar" class="avatar-img" />
                <span v-else class="avatar">{{ (post.username || '').charAt(0) }}</span>
                <span class="author-name">{{ post.username }}</span>
              </div>
              <span class="stat" :class="{ liked: likedPosts.includes(post.id) }"><SvgIcon :name="likedPosts.includes(post.id) ? 'heart-fill' : 'heart'" :size="14" /> {{ formatLikes(post.likes) }}</span>
            </div>
          </div>
        </article>
      </div>
    </main>

    <!-- Publish Modal -->
    <PublishModal v-if="showPublishModal" @close="showPublishModal = false" @success="handlePublishSuccess" />

    <!-- Post Detail Modal -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="selectedPost" class="modal-overlay" @click="selectedPost = null">
          <div class="modal-content" @click.stop>
            <div class="modal-top">
              <div class="modal-user">
                <img v-if="selectedPost.avatar" :src="selectedPost.avatar" class="avatar-img lg" />
                <span v-else class="avatar lg">{{ (selectedPost.username || '').charAt(0) }}</span>
                <div>
                  <span class="author-name">{{ selectedPost.username }}</span>
                  <span class="author-bio">{{ selectedPost.bio }}</span>
                </div>
              </div>
              <button class="close-btn" @click="selectedPost = null">
                <SvgIcon name="close" :size="18" />
              </button>
            </div>
            <div class="modal-scroll">
              <div class="modal-images" v-if="selectedPost.images?.length">
                <img v-for="(img, i) in selectedPost.images" :key="i" :src="img" :alt="selectedPost.title" />
              </div>
              <div class="modal-body">
                <div class="modal-title-row">
                  <h3>{{ selectedPost.title }}</h3>
                  <button class="gen-inline-btn" @click="generateSimilar(selectedPost)">
                    <SvgIcon name="sparkles" :size="14" />
                    生成同款
                  </button>
                </div>
                <p>{{ selectedPost.description }}</p>
                <div class="card-tags">
                  <span v-for="tag in selectedPost.tags" :key="tag" class="tag">{{ tag }}</span>
                </div>
              </div>
              <div v-if="showComments" class="comments-area">
                <h4>评论 ({{ selectedPost.comments }})</h4>
                <div class="comments-list">
                  <div v-for="c in comments" :key="c.id" class="comment">
                    <img v-if="c.avatar" :src="c.avatar" class="avatar-img sm" />
                    <span v-else class="avatar sm">{{ (c.username || '').charAt(0) }}</span>
                    <div class="comment-body">
                      <div class="comment-meta">
                        <span class="comment-author">{{ c.username }}</span>
                        <span class="comment-time">{{ c.createdAt }}</span>
                      </div>
                      <p class="comment-text">{{ c.content }}</p>
                    </div>
                  </div>
                  <p v-if="!comments.length" class="no-comments">暂无评论</p>
                </div>
              </div>
            </div>
            <div class="comment-input-wrapper">
              <div class="comment-input-row">
                <input v-model="newComment" placeholder="写下你的评论..." @keydown.enter="submitComment" />
                <button @click="submitComment"><SvgIcon name="send" :size="14" /></button>
              </div>
            </div>
            <div class="modal-actions">
              <button class="modal-action like" :class="{ liked: likedPosts.includes(selectedPost.id) }" @click="handleLike(selectedPost)"><SvgIcon :name="likedPosts.includes(selectedPost.id) ? 'heart-fill' : 'heart'" :size="16" /> {{ selectedPost.likes }}</button>
              <button class="modal-action" :class="{ active: showComments }" @click="toggleComments"><SvgIcon name="message" :size="16" /> {{ selectedPost.comments }}</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import SvgIcon from '../components/SvgIcon.vue'
import PublishModal from '../components/PublishModal.vue'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const { isLoggedIn } = useAuth()
const showLogin = inject('showLoginModal')

const requireAuth = (action) => {
  if (!isLoggedIn.value) { showLogin(); return }
  action()
}
const searchQuery = ref('')
const activeCategory = ref('all')
const allPosts = ref([])
const selectedPost = ref(null)
const comments = ref([])
const newComment = ref('')

const categories = [
  { id: 'all', name: '推荐' },
  { id: 'food', name: '美食' },
  { id: 'sight', name: '景点' },
  { id: 'hotel', name: '住宿' },
  { id: 'route', name: '路线' },
  { id: 'tips', name: '攻略' },
  { id: 'nature', name: '自然风光' },
  { id: 'city', name: '城市' },
  { id: 'family', name: '亲子' },
  { id: 'couple', name: '情侣' },
]

const formatLikes = (n) => {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return n
}

const loadPosts = async () => {
  try {
    const res = await fetch('/api/community/posts')
    const data = await res.json()
    if (data.code === 200) allPosts.value = data.data
  } catch { /* empty */ }
}

const handleCategoryChange = async () => {
  if (activeCategory.value === 'all') { loadPosts(); return }
  try {
    const res = await fetch(`/api/community/posts/category/${activeCategory.value}`)
    const data = await res.json()
    if (data.code === 200) allPosts.value = data.data
  } catch { /* empty */ }
}

const handleSearch = async () => {
  if (!searchQuery.value.trim()) { loadPosts(); return }
  try {
    const res = await fetch(`/api/community/posts/search?keyword=${encodeURIComponent(searchQuery.value)}`)
    const data = await res.json()
    if (data.code === 200) allPosts.value = data.data
  } catch { /* empty */ }
}

const filteredPosts = computed(() => {
  let posts = allPosts.value
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    posts = posts.filter(p =>
      p.title?.toLowerCase().includes(q) ||
      p.description?.toLowerCase().includes(q) ||
      p.tags?.some(t => t.toLowerCase().includes(q))
    )
  }
  return posts
})

const openPost = async (post) => {
  selectedPost.value = post
  comments.value = []
  newComment.value = ''
  showComments.value = false
}

const submitComment = async () => {
  if (!isLoggedIn.value) { showLogin(); return }
  if (!newComment.value.trim() || !selectedPost.value) return
  try {
    const res = await fetch(`/api/community/posts/${selectedPost.value.id}/comments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-User-Id': localStorage.getItem('userId') || '1' },
      body: JSON.stringify({ content: newComment.value })
    })
    const data = await res.json()
    if (data.code === 200) {
      comments.value.push({ id: Date.now(), content: newComment.value, avatar: '', username: localStorage.getItem('username') || '用户', createdAt: '刚刚' })
      selectedPost.value.comments++
      newComment.value = ''
    }
  } catch { /* empty */ }
}

const generateSimilar = (post) => {
  const dest = post.tags?.[0] || ''
  const query = dest ? `参考这篇攻略，帮我做一个${dest}旅行计划` : post.title
  router.push({ path: '/ai-plan', query: { q: query } })
}

const likedPosts = ref([])
const showComments = ref(false)

const toggleComments = async () => {
  showComments.value = !showComments.value
  if (showComments.value && !comments.value.length && selectedPost.value) {
    try {
      const res = await fetch(`/api/community/posts/${selectedPost.value.id}/comments`)
      const data = await res.json()
      if (data.code === 200) comments.value = data.data
    } catch { /* ignore */ }
  }
}

const handleLike = async (post) => {
  if (!isLoggedIn.value) { showLogin(); return }
  try {
    const res = await fetch(`/api/community/posts/${post.id}/like`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-User-Id': localStorage.getItem('userId') || '1' }
    })
    const data = await res.json()
    if (data.code === 200) {
      const index = likedPosts.value.indexOf(post.id)
      if (index > -1) {
        likedPosts.value.splice(index, 1)
        post.likes--
      } else {
        likedPosts.value.push(post.id)
        post.likes++
      }
    }
  } catch { /* ignore */ }
}

const showPublishModal = ref(false)

const handlePublishSuccess = () => {
  showPublishModal.value = false
  loadPosts()
}

onMounted(loadPosts)
</script>

<style scoped>
.discover-page {
  background: var(--color-bg);
  font-family: var(--font-family);
  color: var(--color-body);
  min-height: 100%;
}

.discover-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--color-surface);
  padding: 16px 28px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.publish-btn {
  display: flex; align-items: center; gap: 8px;
  padding: 14px 28px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--gradient-brand);
  color: white;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  font-family: var(--font-family);
  transition: all 0.2s;
  box-shadow: var(--shadow-button);
  flex-shrink: 0;
}
.publish-btn:hover { filter: brightness(1.1); transform: translateY(-1px); }

.search-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--color-card);
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-pill);
  padding: 8px 16px;
  width: 100%;
  max-width: 480px;
  transition: border-color 0.2s;
}

.search-box:focus-within { border-color: var(--color-red); }
.search-icon { color: var(--color-muted); flex-shrink: 0; }

.search-input {
  flex: 1;
  border: none;
  background: none;
  font-size: 14px;
  outline: none;
  color: var(--color-title);
  font-family: var(--font-family);
}
.search-input::placeholder { color: var(--color-muted); }

.category-bar {
  display: flex;
  gap: 2px;
  overflow-x: auto;
  scrollbar-width: none;
  padding: 12px 0;
  justify-content: center;
}
.category-bar::-webkit-scrollbar { display: none; }

.cat-item {
  flex-shrink: 0;
  border: none;
  background: none;
  padding: 6px 16px;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-hint);
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: all 0.2s;
  font-family: var(--font-family);
}
.cat-item:hover { color: var(--color-secondary); }
.cat-item.active { background: var(--color-red); color: white; font-weight: 600; }

/* Feed */
.feed-area { padding: 20px 28px 40px; }

.masonry { column-count: 4; column-gap: 16px; }

.post-card {
  break-inside: avoid;
  margin-bottom: 16px;
  background: var(--color-card);
  border-radius: var(--radius-card);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s ease;
  border: 1px solid var(--color-border);
}
.post-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-card-hover); border-color: #333; }

.card-img {
  width: 100%;
  background-size: cover;
  background-position: center;
  position: relative;
}
.card-img::after { content: ''; display: block; padding-bottom: 75%; }

.img-count {
  position: absolute; top: 8px; right: 8px;
  background: rgba(0,0,0,0.6); color: white;
  padding: 2px 8px; border-radius: var(--radius-pill);
  font-size: 11px; font-weight: 500;
}

.card-body { padding: 14px; }

.card-title {
  font-size: 15px; font-weight: 600; margin: 0 0 6px;
  color: var(--color-title);
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  line-height: 1.45;
}

.card-desc {
  font-size: 13px; color: var(--color-hint); margin: 0 0 10px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  line-height: 1.5;
}

.card-tags { display: flex; flex-wrap: wrap; gap: 5px; margin-bottom: 10px; }
.tag {
  font-size: 11px; padding: 3px 9px; border-radius: var(--radius-pill);
  background: rgba(230,57,70,0.12); color: var(--color-red-light);
}

.card-footer { display: flex; justify-content: space-between; align-items: center; }
.card-author { display: flex; align-items: center; gap: 5px; }

.avatar {
  width: 20px; height: 20px; border-radius: 50%;
  background: var(--gradient-brand);
  display: flex; align-items: center; justify-content: center;
  font-size: 10px; color: white; flex-shrink: 0;
}
.avatar.sm { width: 28px; height: 28px; font-size: 14px; }
.avatar.lg { width: 40px; height: 40px; font-size: 18px; }

.avatar-img {
  width: 20px; height: 20px; border-radius: 50%;
  object-fit: cover; flex-shrink: 0;
}
.avatar-img.sm { width: 28px; height: 28px; }
.avatar-img.lg { width: 40px; height: 40px; }

.author-name { font-size: 12px; font-weight: 500; color: var(--color-secondary); }
.author-bio { display: block; font-size: 11px; color: var(--color-muted); }

.stat { display: flex; align-items: center; gap: 3px; font-size: 12px; color: var(--color-muted); transition: color 0.2s; }
.stat.liked { color: #ef4444; }

/* Modal */
.modal-overlay {
  position: fixed; inset: 0;
  background: var(--color-overlay);
  display: flex; align-items: center; justify-content: center;
  z-index: 1200; padding: 20px;
}

.modal-content {
  background: var(--color-surface);
  border-radius: var(--radius-modal);
  max-width: 560px; width: 100%; max-height: 85vh;
  display: flex; flex-direction: column;
  overflow: hidden; border: 1px solid var(--color-border);
}

.modal-scroll {
  flex: 1; overflow-y: auto; scrollbar-width: none;
}
.modal-scroll::-webkit-scrollbar { display: none; }

.modal-top {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.modal-user { display: flex; align-items: center; gap: 10px; }

.close-btn {
  width: 30px; height: 30px; border: none;
  background: var(--color-card); border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; color: var(--color-hint); transition: all 0.2s;
}
.close-btn:hover { background: var(--color-border); color: var(--color-title); }

.modal-images { display: flex; overflow-x: auto; scrollbar-width: none; }
.modal-images::-webkit-scrollbar { display: none; }
.modal-images img { flex-shrink: 0; width: 100%; max-height: 360px; object-fit: cover; }

.modal-body { padding: 20px; }
.modal-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.modal-body h3 { font-size: 19px; font-weight: 700; margin: 0; color: var(--color-title); line-height: 1.4; flex: 1; }

.gen-inline-btn {
  display: flex; align-items: center; gap: 4px; flex-shrink: 0;
  padding: 6px 14px; border: 1px solid rgba(230,57,70,0.3); border-radius: var(--radius-pill);
  background: rgba(230,57,70,0.1); color: var(--color-red-light);
  font-size: 12px; font-weight: 600; cursor: pointer; font-family: var(--font-family);
  transition: all 0.2s; white-space: nowrap;
}
.gen-inline-btn:hover { background: rgba(230,57,70,0.2); border-color: var(--color-red); }

.modal-body p { font-size: 14px; line-height: 1.7; color: var(--color-secondary); margin: 0 0 14px; }
.modal-body .card-tags { margin-bottom: 0; }

.comments-area { padding: 16px 20px; border-top: 1px solid var(--color-border); }
.comments-area h4 { font-size: 14px; font-weight: 600; margin: 0 0 12px; color: var(--color-title); }

.comments-list {
  display: flex; flex-direction: column; gap: 12px;
  max-height: 260px; overflow-y: auto; scrollbar-width: none;
}
.comments-list::-webkit-scrollbar { display: none; }

.comment { display: flex; gap: 10px; }
.comment-body { flex: 1; min-width: 0; }
.comment-meta { display: flex; gap: 8px; align-items: center; margin-bottom: 3px; }
.comment-author { font-size: 13px; font-weight: 500; color: var(--color-title); }
.comment-time { font-size: 11px; color: var(--color-muted); }
.comment-text { font-size: 13px; color: var(--color-secondary); margin: 0; line-height: 1.5; }
.no-comments { text-align: center; color: var(--color-muted); font-size: 13px; padding: 20px 0; }

.comment-input-wrapper {
  padding: 12px 20px;
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}

.comment-input-row { display: flex; gap: 8px; }
.comment-input-row input {
  flex: 1; padding: 8px 14px;
  border: 1.5px solid var(--color-border); border-radius: var(--radius-pill);
  font-size: 13px; outline: none; background: var(--color-card); color: var(--color-title);
  transition: border-color 0.2s; font-family: var(--font-family);
}
.comment-input-row input:focus { border-color: var(--color-red); }
.comment-input-row button {
  width: 34px; height: 34px; border: none;
  background: var(--color-red); color: white; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; flex-shrink: 0;
}

.modal-actions { display: flex; border-top: 1px solid var(--color-border); flex-shrink: 0; }
.modal-action {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 5px;
  padding: 14px; border: none; background: none;
  font-size: 13px; color: var(--color-hint);
  cursor: pointer; font-family: var(--font-family); transition: all 0.2s;
}
.modal-action:hover { background: var(--color-card); }
.modal-action.like { color: var(--color-red-light); }
.modal-action.like.liked { color: #ef4444; font-weight: 600; }

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }

@media (max-width: 1200px) { .masonry { column-count: 3; } }
@media (max-width: 900px) { .masonry { column-count: 2; } }
@media (max-width: 560px) { .masonry { column-count: 1; } }
</style>