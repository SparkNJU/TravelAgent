<template>
  <Teleport to="body">
    <Transition name="modal">
      <div class="login-overlay" @click="$emit('close')">
        <div class="login-panel" @click.stop>
          <div class="login-header">
            <div class="logo-circle">
              <SvgIcon name="plane" :size="20" />
            </div>
            <h3>旅行计划助手</h3>
            <button class="close-btn" @click="$emit('close')">
              <SvgIcon name="close" :size="18" />
            </button>
          </div>

          <div class="tab-bar">
            <button :class="['tab', { active: activeTab === 'login' }]" @click="switchTab('login')">登录</button>
            <button :class="['tab', { active: activeTab === 'register' }]" @click="switchTab('register')">注册</button>
          </div>

          <!-- 登录表单 -->
          <form v-if="activeTab === 'login'" @submit.prevent="handleLogin" class="form-area">
            <div class="field-group">
              <input v-model="loginForm.username" type="text" placeholder="用户名" class="field" required />
            </div>
            <div class="field-group">
              <input v-model="loginForm.password" type="password" placeholder="密码" class="field" required />
            </div>
            <p v-if="errorMsg" class="msg error">{{ errorMsg }}</p>
            <button type="submit" class="submit-btn" :disabled="loading">
              <span v-if="loading" class="spinner"></span>
              {{ loading ? '登录中...' : '登 录' }}
            </button>
            <div class="tips">测试账号: <strong>admin</strong> / <strong>admin123</strong></div>
          </form>

          <!-- 注册表单 -->
          <form v-if="activeTab === 'register'" @submit.prevent="handleRegister" class="form-area">
            <div class="field-group">
              <input v-model="regForm.username" type="text" placeholder="用户名" class="field" required />
            </div>
            <div class="field-group">
              <input v-model="regForm.email" type="email" placeholder="邮箱" class="field" required />
            </div>
            <div class="field-group">
              <input v-model="regForm.password" type="password" placeholder="密码（至少6位）" class="field" required />
            </div>
            <div class="field-group">
              <input v-model="regForm.confirmPassword" type="password" placeholder="确认密码" class="field" required />
            </div>
            <p v-if="errorMsg" class="msg error">{{ errorMsg }}</p>
            <p v-if="successMsg" class="msg success">{{ successMsg }}</p>
            <button type="submit" class="submit-btn" :disabled="loading">
              <span v-if="loading" class="spinner"></span>
              {{ loading ? '注册中...' : '注 册' }}
            </button>
          </form>

          <div class="guest-hint">
            <button class="guest-btn" @click="$emit('close')">先逛逛再说</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref } from 'vue'
import SvgIcon from './SvgIcon.vue'
import { useAuth } from '../composables/useAuth'

const props = defineProps({
  defaultTab: { type: String, default: 'login' }
})
const emit = defineEmits(['close', 'success'])
const { login } = useAuth()

const activeTab = ref(props.defaultTab)
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const loginForm = ref({ username: '', password: '' })
const regForm = ref({ username: '', email: '', password: '', confirmPassword: '' })

const switchTab = (tab) => {
  activeTab.value = tab
  errorMsg.value = ''
  successMsg.value = ''
}

const handleLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: loginForm.value.username, password: loginForm.value.password })
    })
    const data = await res.json()
    if (data.success) {
      login({ token: data.token, userId: data.userId, username: loginForm.value.username, avatar: data.avatar || '' })
      emit('success')
    } else {
      errorMsg.value = data.message || '登录失败'
    }
  } catch {
    errorMsg.value = '服务器错误，请稍后重试'
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  const f = regForm.value
  if (!f.username.trim()) { errorMsg.value = '请输入用户名'; return }
  if (!f.email.includes('@')) { errorMsg.value = '请输入有效的邮箱地址'; return }
  if (f.password.length < 6) { errorMsg.value = '密码长度不能少于6位'; return }
  if (f.password !== f.confirmPassword) { errorMsg.value = '两次输入的密码不一致'; return }

  loading.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    const res = await fetch('/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: f.username, email: f.email, password: f.password })
    })
    const data = await res.json()
    if (data.success) {
      // 注册成功后自动登录
      const loginRes = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: f.username, password: f.password })
      })
      const loginData = await loginRes.json()
      if (loginData.success) {
        login({ token: loginData.token, userId: loginData.userId, username: f.username, avatar: loginData.avatar || '' })
        emit('success')
      } else {
        successMsg.value = '注册成功，请登录'
        switchTab('login')
        loginForm.value.username = f.username
      }
    } else {
      errorMsg.value = data.message || '注册失败，请稍后重试'
    }
  } catch {
    errorMsg.value = '服务器错误，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-overlay {
  position: fixed; inset: 0; background: var(--color-overlay);
  display: flex; align-items: center; justify-content: center;
  z-index: 1200; padding: 20px;
}

.login-panel {
  background: var(--color-surface); border-radius: var(--radius-modal);
  max-width: 400px; width: 100%; border: 1px solid var(--color-border);
}

.login-header {
  display: flex; align-items: center; gap: 12px;
  padding: 20px 20px 16px; position: relative;
}
.logo-circle {
  width: 36px; height: 36px; border-radius: 10px;
  background: var(--gradient-brand); display: flex;
  align-items: center; justify-content: center; color: white; flex-shrink: 0;
}
.login-header h3 {
  font-size: 16px; font-weight: 700; color: var(--color-title); margin: 0;
}
.close-btn {
  position: absolute; top: 16px; right: 16px;
  width: 30px; height: 30px; border: none; background: var(--color-card);
  border-radius: 50%; display: flex; align-items: center; justify-content: center;
  cursor: pointer; color: var(--color-hint); transition: all 0.2s;
}
.close-btn:hover { background: var(--color-border); color: var(--color-title); }

.tab-bar {
  display: flex; gap: 4px; padding: 0 20px; margin-bottom: 4px;
}
.tab {
  flex: 1; padding: 8px; border: none; background: none;
  color: var(--color-hint); font-size: 14px; font-weight: 600;
  cursor: pointer; border-radius: var(--radius-input); transition: all 0.2s;
  font-family: var(--font-family);
}
.tab:hover { color: var(--color-body); }
.tab.active { color: var(--color-red-light); background: rgba(230, 57, 70, 0.08); }

.form-area {
  padding: 12px 20px 8px; display: flex; flex-direction: column; gap: 12px;
}

.field-group { position: relative; }

.field {
  width: 100%; padding: 11px 14px; border: 1.5px solid var(--color-border);
  border-radius: var(--radius-input); font-size: 14px; font-family: var(--font-family);
  outline: none; background: var(--color-bg); color: var(--color-title);
  transition: border-color 0.2s; box-sizing: border-box;
}
.field:focus { border-color: var(--color-red); }
.field::placeholder { color: var(--color-muted); }

.msg {
  font-size: 13px; margin: 0; padding: 8px 12px; border-radius: var(--radius-input);
}
.msg.error { color: var(--color-red-light); background: rgba(230, 57, 70, 0.08); }
.msg.success { color: #4caf50; background: rgba(76, 175, 80, 0.08); }

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

.tips {
  text-align: center; font-size: 12px; color: var(--color-muted); padding: 4px 0;
}
.tips strong { color: var(--color-secondary); }

.guest-hint { padding: 8px 20px 16px; }
.guest-btn {
  width: 100%; padding: 9px; border: 1px solid var(--color-border);
  border-radius: var(--radius-pill); background: none; color: var(--color-hint);
  font-size: 13px; cursor: pointer; font-family: var(--font-family); transition: all 0.2s;
}
.guest-btn:hover { background: var(--color-card); color: var(--color-body); }

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
