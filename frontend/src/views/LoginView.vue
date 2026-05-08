<template>
  <div class="login-container">
    <!-- 装饰背景 -->
    <div class="decor-bg">
      <div class="floating-circle circle-1"></div>
      <div class="floating-circle circle-2"></div>
      <div class="floating-circle circle-3"></div>
    </div>

    <div class="login-box">
      <div class="login-header">
        <div class="logo-wrapper">
          <span class="logo-icon">✈️</span>
        </div>
        <h2>旅行计划助手</h2>
        <p class="subtitle">登录你的专属行程助手</p>
      </div>
      
      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label for="username">用户名</label>
          <div class="input-wrapper">
            <span class="input-icon">👤</span>
            <input type="text" id="username" v-model="username" placeholder="请输入用户名" required />
          </div>
        </div>
        
        <div class="form-group">
          <label for="password">密码</label>
          <div class="input-wrapper">
            <span class="input-icon">🔒</span>
            <input type="password" id="password" v-model="password" placeholder="请输入密码" required />
          </div>
        </div>
        
        <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
        
        <button type="submit" class="login-btn" :disabled="loading">
          <span v-if="loading" class="loading-spinner"></span>
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </form>
      <div class="tips">
        <small>测试账号: <strong>admin</strong> / 测试密码: <strong>admin123</strong></small>
      </div>

      <div class="register-link">
        <span>还没有账户？</span>
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const username = ref('')
const password = ref('')
const errorMessage = ref('')
const loading = ref(false)
const router = useRouter()

const handleLogin = async () => {
  if (!username.value || !password.value) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        username: username.value,
        password: password.value
      })
    })

    const data = await response.json()

    if (data.success) {
      localStorage.setItem('token', data.token)
      localStorage.setItem('userId', data.userId)
      localStorage.setItem('username', username.value)
      router.push('/')
    } else {
      errorMessage.value = data.message || '登录失败'
    }
  } catch (error) {
    errorMessage.value = '服务器错误，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #f0f4ff 0%, #f8fafc 50%, #eef2ff 100%);
  position: relative;
  overflow: hidden;
}

/* 装饰背景 */
.decor-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
  pointer-events: none;
}

.floating-circle {
  position: absolute;
  border-radius: 50%;
  animation: float 8s ease-in-out infinite;
}

.circle-1 {
  width: 400px;
  height: 400px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.15), rgba(124, 58, 237, 0.1));
  top: -100px;
  left: -100px;
  animation-delay: 0s;
}

.circle-2 {
  width: 300px;
  height: 300px;
  background: linear-gradient(135deg, rgba(124, 58, 237, 0.12), rgba(37, 99, 235, 0.08));
  bottom: -50px;
  right: -50px;
  animation-delay: 2s;
}

.circle-3 {
  width: 200px;
  height: 200px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.1), rgba(139, 92, 246, 0.08));
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation-delay: 4s;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(30px, -20px) scale(1.05); }
  50% { transform: translate(-20px, 30px) scale(0.95); }
  75% { transform: translate(20px, 20px) scale(1.02); }
}

.login-box {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  padding: 48px;
  border-radius: 28px;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.1);
  width: 100%;
  max-width: 420px;
  text-align: center;
  position: relative;
  z-index: 1;
  border: 1px solid rgba(148, 163, 184, 0.1);
}

.login-header {
  margin-bottom: 32px;
}

.logo-wrapper {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  box-shadow: 0 8px 32px rgba(37, 99, 235, 0.3);
}

.logo-icon {
  font-size: 40px;
}

h2 {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.subtitle {
  color: #64748b;
  margin: 0;
  font-size: 14px;
}

.form-group {
  margin-bottom: 24px;
  text-align: left;
}

.form-group {
  margin-bottom: var(--space-5);
}

label {
  display: block;
  margin-bottom: 10px;
  color: #334155;
  font-weight: 600;
  font-size: 14px;
}

.input-wrapper {
  position: relative;
  background: #f8fafc;
  border-radius: 14px;
  border: 2px solid #e2e8f0;
  transition: all 0.3s ease;
}

.input-wrapper:focus-within {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.input-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 18px;
}

input {
  width: 100%;
  padding: 14px 14px 14px 48px;
  border: none;
  border-radius: 14px;
  font-size: 15px;
  background: transparent;
  color: #1e293b;
}

input:focus {
  outline: none;
}

input::placeholder {
  color: #94a3b8;
}

.login-btn {
  width: 100%;
  padding: 16px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: white;
  border: none;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(37, 99, 235, 0.4);
}

.login-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.error-text {
  color: #dc2626;
  font-size: 14px;
  margin: 0 0 16px;
  padding: 12px;
  background: rgba(220, 38, 38, 0.05);
  border-radius: 10px;
}

.tips {
  margin-top: 24px;
  color: #64748b;
  padding: 14px;
  background: #f8fafc;
  border-radius: 12px;
}

.tips strong {
  color: #2563eb;
}

.register-link {
  margin-top: var(--space-5);
  text-align: center;
  color: #64748b;
  font-size: 14px;
}

.register-link a {
  color: #2563eb;
  text-decoration: none;
  font-weight: 600;
  margin-left: 6px;
  transition: all 0.2s;
}

.register-link a:hover {
  color: #1d4ed8;
  text-decoration: underline;
}
</style>