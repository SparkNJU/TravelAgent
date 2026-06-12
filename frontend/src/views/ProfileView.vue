<template>
  <div class="profile-page">
    <div class="profile-container">
      <!-- 用户信息卡片 -->
      <div class="user-card">
        <div class="avatar-wrapper" @click="triggerAvatarUpload">
          <img v-if="profile.profilePicUrl" :src="profile.profilePicUrl" class="avatar-img" />
          <div v-else class="avatar-letter">{{ (profile.username || 'U').charAt(0).toUpperCase() }}</div>
          <div class="avatar-overlay"><SvgIcon name="camera" :size="14" /></div>
          <input ref="avatarInput" type="file" accept="image/*" hidden @change="handleAvatarChange" />
        </div>
        <div class="user-info">
          <div class="name-row">
            <h1>{{ profile.username || '用户' }}</h1>
            <button class="edit-btn" @click="showEditModal = true"><SvgIcon name="edit" :size="14" /></button>
          </div>
          <p v-if="profile.email" class="user-email">{{ profile.email }}</p>
          <p v-else class="user-email hint">未设置邮箱</p>
        </div>
        <div class="user-actions">
          <button class="action-chip" @click="showPasswordModal = true">
            <SvgIcon name="lock" :size="14" /> 修改密码
          </button>
        </div>
      </div>


      <!-- 标签切换 -->
      <div class="tabs-wrapper">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="['tab-btn', { active: activeTab === tab.id }]"
          @click="activeTab = tab.id; loadData()"
        >{{ tab.name }}</button>
      </div>

      <!-- 内容区域 -->
      <div class="content-area">
        <!-- 旅行规划 -->
        <div v-if="activeTab === 'plans'" class="plans-section">
          <div v-if="loading" class="loading-box">
            <SvgIcon name="loader" :size="24" spin />
            <span>加载中...</span>
          </div>
          <div v-else-if="!plans.length" class="empty-box">
            <SvgIcon name="map-pin" :size="40" />
            <p>暂无规划记录</p>
            <span>去 AI规划 页面生成你的第一份旅行计划</span>
          </div>
          <div v-else class="plans-list">
            <div v-for="item in plans" :key="item.planId" class="plan-item">
              <div class="plan-icon">
                <SvgIcon name="map-pin" :size="20" />
              </div>
              <div class="plan-content">
                <h3>{{ item.title || '未命名行程' }}</h3>
                <div class="plan-meta">
                  <span>{{ item.destination }}</span>
                  <span>{{ item.days }}天</span>
                </div>
              </div>
              <span class="plan-date">{{ formatDate(item.createdAt) }}</span>
              <div class="plan-actions">
                <button class="action-btn" @click="viewPlanDetail(item.planId)">查看</button>
                <button class="action-btn primary" @click="$router.push(`/plan/workbench?planId=${item.planId}`)">工作台</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 我的帖子 -->
        <div v-if="activeTab === 'posts'" class="posts-section">
          <div v-if="postsLoading" class="loading-box">
            <SvgIcon name="loader" :size="24" spin />
            <span>加载中...</span>
          </div>
          <div v-else-if="!posts.length" class="empty-box">
            <SvgIcon name="message" :size="40" />
            <p>暂无帖子</p>
            <span>去 发现 页面发布你的第一篇帖子</span>
          </div>
          <div v-else class="posts-grid">
            <article v-for="post in posts" :key="post.id" class="post-card">
              <div v-if="post.images?.length" class="card-img" :style="{ backgroundImage: `url(${post.images[0]})` }" />
              <div class="card-body">
                <h3>{{ post.title }}</h3>
                <p>{{ post.description }}</p>
                <div class="card-footer">
                  <span v-for="tag in post.tags?.slice(0, 2)" :key="tag" class="tag">{{ tag }}</span>
                  <div class="card-stats">
                    <span><SvgIcon name="heart" :size="12" /> {{ post.likes }}</span>
                    <span><SvgIcon name="message" :size="12" /> {{ post.comments }}</span>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑资料弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showEditModal" class="modal-overlay" @click="showEditModal = false">
          <div class="modal-panel" @click.stop>
            <div class="modal-header">
              <h3>编辑资料</h3>
              <button class="close-btn" @click="showEditModal = false"><SvgIcon name="close" :size="18" /></button>
            </div>
            <form @submit.prevent="handleUpdateProfile" class="modal-body">
              <div class="field-group">
                <label>用户名</label>
                <input v-model="editForm.username" class="field" placeholder="用户名" required />
              </div>
              <div class="field-group">
                <label>邮箱</label>
                <input v-model="editForm.email" type="email" class="field" placeholder="邮箱" />
              </div>
              <div class="field-group">
                <label>手机号</label>
                <input v-model="editForm.phone" class="field" placeholder="手机号" />
              </div>
              <div class="field-group">
                <label>个人简介</label>
                <input v-model="editForm.bio" class="field" placeholder="介绍一下自己吧" />
              </div>
              <p v-if="editError" class="msg error">{{ editError }}</p>
              <button type="submit" class="submit-btn" :disabled="editLoading">
                <span v-if="editLoading" class="spinner"></span>
                {{ editLoading ? '保存中...' : '保存' }}
              </button>
            </form>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 修改密码弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showPasswordModal" class="modal-overlay" @click="showPasswordModal = false">
          <div class="modal-panel" @click.stop>
            <div class="modal-header">
              <h3>修改密码</h3>
              <button class="close-btn" @click="showPasswordModal = false"><SvgIcon name="close" :size="18" /></button>
            </div>
            <form @submit.prevent="handleChangePassword" class="modal-body">
              <div class="field-group">
                <label>旧密码</label>
                <input v-model="pwdForm.oldPassword" type="password" class="field" placeholder="旧密码" required />
              </div>
              <div class="field-group">
                <label>新密码</label>
                <input v-model="pwdForm.newPassword" type="password" class="field" placeholder="新密码（至少6位）" required />
              </div>
              <div class="field-group">
                <label>确认密码</label>
                <input v-model="pwdForm.confirmPassword" type="password" class="field" placeholder="再次输入新密码" required />
              </div>
              <p v-if="pwdError" class="msg error">{{ pwdError }}</p>
              <p v-if="pwdSuccess" class="msg success">{{ pwdSuccess }}</p>
              <button type="submit" class="submit-btn" :disabled="pwdLoading">
                <span v-if="pwdLoading" class="spinner"></span>
                {{ pwdLoading ? '修改中...' : '确认修改' }}
              </button>
            </form>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 规划详情弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showDetailModal" class="modal-overlay" @click="closeDetailModal">
          <div class="detail-modal" @click.stop>
            <div class="modal-header">
              <h3>{{ selectedPlan?.title || '规划详情' }}</h3>
              <button class="close-btn" @click="closeDetailModal"><SvgIcon name="close" :size="18" /></button>
            </div>
            <div class="modal-body">
              <div class="detail-row">
                <span class="detail-label">目的地</span>
                <span class="detail-value">{{ selectedPlan?.destination }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">天数</span>
                <span class="detail-value">{{ selectedPlan?.days }}天</span>
              </div>
              <div class="detail-section">
                <h4>行程安排</h4>
                <div class="itinerary-content" v-html="formatItinerary(selectedPlan?.itinerary)"></div>
              </div>
              <div v-if="selectedPlan?.highlights?.length" class="detail-section">
                <h4>行程亮点</h4>
                <ul class="highlights-list">
                  <li v-for="(h, i) in selectedPlan.highlights" :key="i">{{ h }}</li>
                </ul>
              </div>
            </div>
            <div class="modal-footer">
              <button class="footer-btn" @click="closeDetailModal">关闭</button>
              <button class="footer-btn primary" @click="$router.push(`/plan/workbench?planId=${selectedPlan?.planId}`); closeDetailModal()">
                加到工作台
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import SvgIcon from '../components/SvgIcon.vue'
import { useAuth } from '../composables/useAuth'

const { isLoggedIn, userId, updateUsername, updateAvatar } = useAuth()


const profile = ref({ username: '', email: '', phone: '', profilePicUrl: '', createdAt: '' })
const plans = ref([])
const posts = ref([])
const activeTab = ref('plans')
const loading = ref(false)
const postsLoading = ref(false)
const selectedPlan = ref(null)
const showDetailModal = ref(false)

const tabs = [
  { id: 'plans', name: '旅行规划' },
  { id: 'posts', name: '我的帖子' }
]

// 编辑资料
const showEditModal = ref(false)
const editForm = ref({ username: '', email: '', phone: '', bio: '' })
const editError = ref('')
const editLoading = ref(false)

// 修改密码
const showPasswordModal = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdError = ref('')
const pwdSuccess = ref('')
const pwdLoading = ref(false)

// 头像上传
const avatarInput = ref(null)

const formatDate = (iso) => {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const fetchProfile = async () => {
  try {
    const res = await fetch('/api/profile', {
      headers: { 'X-User-Id': localStorage.getItem('userId') || '1' }
    })
    const data = await res.json()
    if (data.code === 200) {
      profile.value = data.data
      editForm.value = {
        username: data.data.username || '',
        email: data.data.email || '',
        phone: data.data.phone || '',
        bio: data.data.bio || ''
      }
      if (data.data.profilePicUrl) {
        updateAvatar(data.data.profilePicUrl)
      }
    }
  } catch { /* ignore */ }
}

const loadData = async () => {
  if (activeTab.value === 'plans') {
    loading.value = true
    try {
      const uid = localStorage.getItem('userId') || '1'
      const res = await fetch(`/api/travel/plans/user/${uid}`)
      const data = await res.json()
      plans.value = data.code === 200 ? data.data : []
    } catch { plans.value = [] }
    finally { loading.value = false }
  } else if (activeTab.value === 'posts') {
    postsLoading.value = true
    try {
      const uid = localStorage.getItem('userId') || '1'
      const res = await fetch(`/api/community/posts/user/${uid}`)
      const data = await res.json()
      posts.value = data.code === 200 ? data.data : []
    } catch { posts.value = [] }
    finally { postsLoading.value = false }
  }
}

const viewPlanDetail = async (planId) => {
  try {
    const res = await fetch(`/api/travel/plan/${planId}`)
    const data = await res.json()
    if (data.code === 200) {
      selectedPlan.value = data.data
      showDetailModal.value = true
    }
  } catch { /* ignore */ }
}

const closeDetailModal = () => {
  showDetailModal.value = false
  selectedPlan.value = null
}

const formatItinerary = (itinerary) => {
  if (!itinerary) return '<p>暂无行程安排</p>'
  try {
    const parsed = JSON.parse(itinerary)
    if (Array.isArray(parsed)) {
      let html = ''
      parsed.forEach((day) => {
        html += `<div class="itinerary-day"><strong>第${day.day}天</strong>`
        if (day.activities && Array.isArray(day.activities)) {
          day.activities.forEach((a) => {
            html += `<div class="itinerary-item">`
            if (a.time) html += `<span class="itinerary-time">${a.time}</span>`
            html += `<span class="itinerary-location">${a.location || ''}</span>`
            if (a.description) html += `<span class="itinerary-desc">${a.description}</span>`
            html += `</div>`
          })
        }
        html += `</div>`
      })
      return html
    }
  } catch { /* not json */ }
  return itinerary.replace(/\n/g, '<br>')
}

// 编辑资料提交
const handleUpdateProfile = async () => {
  editError.value = ''
  if (!editForm.value.username.trim()) { editError.value = '用户名不能为空'; return }
  if (editForm.value.phone && !/^1[3-9]\d{9}$/.test(editForm.value.phone)) {
    editError.value = '手机号格式不正确（需为11位大陆手机号）'
    return
  }
  editLoading.value = true
  try {
    const res = await fetch('/api/profile', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'X-User-Id': localStorage.getItem('userId') || '1' },
      body: JSON.stringify(editForm.value)
    })
    const data = await res.json()
    if (data.code === 200) {
      profile.value = data.data
      updateUsername(data.data.username)
      showEditModal.value = false
    } else {
      editError.value = data.message || '更新失败'
    }
  } catch {
    editError.value = '服务器错误'
  } finally {
    editLoading.value = false
  }
}

// 修改密码提交
const handleChangePassword = async () => {
  pwdError.value = ''
  pwdSuccess.value = ''
  if (pwdForm.value.newPassword.length < 6) { pwdError.value = '新密码至少6位'; return }
  if (pwdForm.value.newPassword !== pwdForm.value.confirmPassword) { pwdError.value = '两次密码不一致'; return }
  pwdLoading.value = true
  try {
    const res = await fetch('/api/profile/password', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'X-User-Id': localStorage.getItem('userId') || '1' },
      body: JSON.stringify({ oldPassword: pwdForm.value.oldPassword, newPassword: pwdForm.value.newPassword })
    })
    const data = await res.json()
    if (data.code === 200) {
      pwdSuccess.value = '密码修改成功'
      pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
      setTimeout(() => { showPasswordModal.value = false; pwdSuccess.value = '' }, 1500)
    } else {
      pwdError.value = data.message || '修改失败'
    }
  } catch {
    pwdError.value = '服务器错误'
  } finally {
    pwdLoading.value = false
  }
}

// 头像上传
const triggerAvatarUpload = () => { avatarInput.value?.click() }

const handleAvatarChange = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await fetch('/api/profile/avatar', {
      method: 'POST',
      headers: { 'X-User-Id': localStorage.getItem('userId') || '1' },
      body: formData
    })
    const data = await res.json()
    if (data.code === 200) {
      profile.value.profilePicUrl = data.data
      updateAvatar(data.data)
    }
  } catch { /* ignore */ }
  e.target.value = ''
}

onMounted(() => {
  fetchProfile()
  loadData()
})
</script>

<style scoped>
.profile-page {
  background: var(--color-bg);
  min-height: 100%;
  padding: 24px;
  font-family: var(--font-family);
}
.profile-container { max-width: 720px; margin: 0 auto; }

/* 用户卡片 */
.user-card {
  display: flex; align-items: center; gap: 16px;
  background: var(--color-card); border-radius: var(--radius-card);
  padding: 20px 24px; border: 1px solid var(--color-border); margin-bottom: 20px;
}


.avatar-wrapper {
  position: relative; width: 64px; height: 64px; border-radius: 50%;
  background: var(--gradient-brand); display: flex; align-items: center;
  justify-content: center; color: white; flex-shrink: 0; cursor: pointer;
  overflow: hidden; font-size: 24px; font-weight: 700;
}
.avatar-img { width: 100%; height: 100%; object-fit: cover; }
.avatar-overlay {
  position: absolute; inset: 0; background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity 0.2s;
}
.avatar-wrapper:hover .avatar-overlay { opacity: 1; }

.user-info { flex: 1; min-width: 0; }
.name-row { display: flex; align-items: center; gap: 8px; }
.user-info h1 { font-size: 18px; font-weight: 700; color: var(--color-title); margin: 0; }
.edit-btn {
  width: 28px; height: 28px; border: none; background: var(--color-bg);
  border-radius: 50%; display: flex; align-items: center; justify-content: center;
  color: var(--color-hint); cursor: pointer; transition: all 0.2s;
}
.edit-btn:hover { background: var(--color-border); color: var(--color-title); }
.user-email { font-size: 13px; color: var(--color-hint); margin: 4px 0 0; }
.user-email.hint { color: var(--color-muted); }

.user-actions { flex-shrink: 0; }
.action-chip {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px; border: 1px solid var(--color-border);
  border-radius: var(--radius-pill); background: none;
  color: var(--color-hint); font-size: 12px; cursor: pointer;
  font-family: var(--font-family); transition: all 0.2s;
}
.action-chip:hover { background: var(--color-card-hover); color: var(--color-title); }

/* 标签 */
.tabs-wrapper {
  display: flex; background: var(--color-card);
  border-radius: var(--radius-pill); padding: 4px;
  border: 1px solid var(--color-border); margin-bottom: 20px;
}
.tab-btn {
  flex: 1; padding: 10px 16px; border: none; background: none;
  border-radius: calc(var(--radius-pill) - 4px); font-size: 14px;
  font-weight: 500; color: var(--color-hint); cursor: pointer;
  transition: all 0.2s; font-family: var(--font-family);
}
.tab-btn:hover { color: var(--color-title); }
.tab-btn.active { background: var(--color-red); color: white; }

/* 内容 */
.content-area {
  background: var(--color-card); border-radius: var(--radius-card);
  border: 1px solid var(--color-border); overflow: hidden;
}

.empty-box {
  display: flex; flex-direction: column; align-items: center;
  padding: 48px 24px; color: var(--color-muted);
}
.empty-box svg { margin-bottom: 12px; opacity: 0.4; }
.empty-box p { font-size: 15px; font-weight: 500; color: var(--color-hint); margin: 0 0 6px; }
.empty-box span { font-size: 13px; }

.loading-box {
  display: flex; flex-direction: column; align-items: center;
  padding: 48px 24px; color: var(--color-muted);
}
.loading-box svg { margin-bottom: 12px; }
.loading-box span { font-size: 13px; color: var(--color-hint); }

/* 规划列表 */
.plans-list { padding: 8px; }
.plan-item {
  display: flex; align-items: center; gap: 12px; padding: 14px;
  border-radius: var(--radius-input); background: var(--color-bg);
  margin-bottom: 8px; transition: background 0.2s;
}
.plan-item:hover { background: var(--color-border); }
.plan-icon {
  width: 40px; height: 40px; border-radius: 10px;
  background: rgba(230,57,70,0.1); display: flex;
  align-items: center; justify-content: center;
  color: var(--color-red-light); flex-shrink: 0;
}
.plan-content { flex: 1; min-width: 0; }
.plan-content h3 { font-size: 14px; font-weight: 600; color: var(--color-title); margin: 0 0 4px; }
.plan-meta { display: flex; gap: 12px; font-size: 12px; color: var(--color-hint); }
.plan-date { font-size: 11px; color: var(--color-muted); flex-shrink: 0; }
.plan-actions { display: flex; gap: 8px; flex-shrink: 0; }
.plan-actions .action-btn {
  padding: 6px 14px; border: 1px solid var(--color-border);
  border-radius: var(--radius-pill); font-size: 12px; font-weight: 500;
  color: var(--color-hint); background: var(--color-bg); cursor: pointer;
  transition: all 0.2s; font-family: var(--font-family);
}
.plan-actions .action-btn:hover { border-color: var(--color-red); color: var(--color-red); }
.plan-actions .action-btn.primary {
  background: var(--gradient-brand); border-color: transparent; color: white;
}
.plan-actions .action-btn.primary:hover { filter: brightness(1.1); }

/* 帖子网格 */
.posts-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px; padding: 12px;
}
.post-card {
  background: var(--color-bg); border-radius: var(--radius-input);
  overflow: hidden; border: 1px solid var(--color-border); transition: all 0.2s;
}
.post-card:hover { border-color: var(--color-red); }
.card-img {
  width: 100%; padding-bottom: 70%; background-size: cover;
  background-position: center; position: relative;
}
.card-body { padding: 12px; }
.card-body h3 {
  font-size: 14px; font-weight: 600; color: var(--color-title); margin: 0 0 6px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.card-body p {
  font-size: 12px; color: var(--color-secondary); margin: 0 0 10px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.card-footer { display: flex; align-items: center; justify-content: space-between; }
.card-footer .tag {
  font-size: 11px; padding: 3px 8px; border-radius: var(--radius-pill);
  background: rgba(230,57,70,0.12); color: var(--color-red-light);
}
.card-stats { display: flex; gap: 12px; font-size: 12px; color: var(--color-muted); }
.card-stats span { display: flex; align-items: center; gap: 3px; }

/* 弹窗通用 */
.modal-overlay {
  position: fixed; inset: 0; background: var(--color-overlay);
  display: flex; align-items: center; justify-content: center;
  z-index: 1200; padding: 20px;
}
.modal-panel {
  background: var(--color-surface); border-radius: var(--radius-modal);
  max-width: 400px; width: 100%; border: 1px solid var(--color-border);
}
.detail-modal {
  background: var(--color-surface); border-radius: var(--radius-modal);
  max-width: 560px; width: 100%; border: 1px solid var(--color-border);
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; border-bottom: 1px solid var(--color-border);
}
.modal-header h3 { font-size: 16px; font-weight: 700; color: var(--color-title); margin: 0; }
.close-btn {
  width: 30px; height: 30px; border: none; background: var(--color-card);
  border-radius: 50%; display: flex; align-items: center; justify-content: center;
  cursor: pointer; color: var(--color-hint); transition: all 0.2s;
}
.close-btn:hover { background: var(--color-border); color: var(--color-title); }
.modal-body {
  padding: 20px; display: flex; flex-direction: column; gap: 14px;
  max-height: 50vh; overflow-y: auto; scrollbar-width: none;
}
.modal-body::-webkit-scrollbar { display: none; }
.modal-footer {
  display: flex; justify-content: flex-end; gap: 10px;
  padding: 14px 20px; border-top: 1px solid var(--color-border);
}

.field-group {}
.field-group label {
  display: block; font-size: 12px; font-weight: 600;
  color: var(--color-secondary); margin-bottom: 6px;
}
.field {
  width: 100%; padding: 10px 12px; border: 1.5px solid var(--color-border);
  border-radius: var(--radius-input); font-size: 14px; font-family: var(--font-family);
  outline: none; background: var(--color-bg); color: var(--color-title);
  transition: border-color 0.2s; box-sizing: border-box;
}
.field:focus { border-color: var(--color-red); }
.field::placeholder { color: var(--color-muted); }

.msg { font-size: 13px; margin: 0; padding: 8px 12px; border-radius: var(--radius-input); }
.msg.error { color: var(--color-red-light); background: rgba(230,57,70,0.08); }
.msg.success { color: #4caf50; background: rgba(76,175,80,0.08); }

.submit-btn {
  width: 100%; padding: 11px; border: none; border-radius: var(--radius-pill);
  background: var(--gradient-brand); color: white; font-size: 14px;
  font-weight: 600; cursor: pointer; font-family: var(--font-family);
  transition: all 0.2s; display: flex; align-items: center; justify-content: center; gap: 8px;
}
.submit-btn:hover:not(:disabled) { filter: brightness(1.1); }
.submit-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.spinner {
  width: 16px; height: 16px; border: 2px solid rgba(255,255,255,0.3);
  border-top-color: white; border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.footer-btn {
  padding: 8px 20px; border: 1px solid var(--color-border);
  border-radius: var(--radius-pill); background: none;
  color: var(--color-hint); font-size: 13px; cursor: pointer;
  font-family: var(--font-family); transition: all 0.2s;
}
.footer-btn:hover { background: var(--color-card); }
.footer-btn.primary {
  background: var(--gradient-brand); border-color: transparent; color: white; font-weight: 600;
}
.footer-btn.primary:hover { filter: brightness(1.1); }

/* 详情 */
.detail-row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid var(--color-border); }
.detail-label { font-size: 13px; color: var(--color-hint); }
.detail-value { font-size: 13px; font-weight: 600; color: var(--color-title); }
.detail-section { margin-top: 16px; }
.detail-section h4 { font-size: 14px; font-weight: 600; color: var(--color-title); margin: 0 0 10px; }
.itinerary-content { font-size: 13px; line-height: 1.8; color: var(--color-body); }
.highlights-list { margin: 0; padding: 0; list-style: none; }
.highlights-list li { padding: 8px 0; font-size: 13px; color: var(--color-body); border-bottom: 1px dashed var(--color-border); }
.highlights-list li:last-child { border-bottom: none; }

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
