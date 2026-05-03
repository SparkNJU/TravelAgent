<template>
  <div class="login-container">
    <div class="login-box">
      <h2>AgentLLM 旅游规划平台</h2>
      <p class="subtitle">登录你的专属行程助手</p>
      
      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label for="username">用户名</label>
          <input type="text" id="username" v-model="username" placeholder="请输入用户名" required />
        </div>
        
        <div class="form-group">
          <label for="password">密码</label>
          <input type="password" id="password" v-model="password" placeholder="请输入密码" required />
        </div>
        
        <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
        
        <button type="submit" class="login-btn" :disabled="loading">
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </form>
      <div class="tips">
        <small>测试账号: admin / 测试密码: admin123</small>
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
  background: linear-gradient(135deg, var(--color-gray-50) 0%, var(--color-white) 100%);
  padding: var(--space-5);
}

.login-box {
  background: var(--color-white);
  padding: var(--space-10) var(--space-8);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg);
  width: 100%;
  max-width: 400px;
  text-align: center;
}

.login-box h2 {
  margin-top: 0;
  margin-bottom: var(--space-2);
  font-size: 24px;
  font-weight: 700;
  color: var(--color-gray-900);
  letter-spacing: -0.02em;
}

.subtitle {
  color: var(--color-gray-500);
  margin-bottom: var(--space-8);
  font-size: 14px;
}

.login-form {
  text-align: left;
}

.form-group {
  margin-bottom: var(--space-5);
}

label {
  display: block;
  margin-bottom: var(--space-2);
  color: var(--color-gray-700);
  font-weight: 600;
  font-size: 14px;
}

input {
  width: 100%;
  padding: 12px var(--space-4);
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-md);
  font-size: 15px;
  font-family: var(--font-sans);
  box-sizing: border-box;
  background: var(--color-white);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

input::placeholder {
  color: var(--color-gray-400);
}

input:focus {
  border-color: var(--color-primary);
  outline: none;
  box-shadow: 0 0 0 3px var(--color-primary-lighter);
}

.login-btn {
  width: 100%;
  padding: 14px;
  background-color: var(--color-primary);
  color: var(--color-white);
  border: none;
  border-radius: var(--radius-full);
  font-size: 16px;
  font-weight: 600;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: all var(--transition-fast);
  box-shadow: var(--shadow-primary);
}

.login-btn:hover:not(:disabled) {
  background-color: var(--color-primary-dark);
  transform: translateY(-1px);
}

.login-btn:active:not(:disabled) {
  transform: translateY(0);
}

.login-btn:disabled {
  background-color: var(--color-gray-300);
  cursor: not-allowed;
  box-shadow: none;
}

.error-text {
  color: var(--color-error);
  font-size: 14px;
  margin-bottom: var(--space-4);
  text-align: center;
}

.tips {
  margin-top: var(--space-5);
  color: var(--color-gray-400);
  font-size: 13px;
}

.register-link {
  margin-top: var(--space-5);
  text-align: center;
  color: var(--color-gray-500);
  font-size: 14px;
}

.register-link a {
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 600;
  margin-left: var(--space-2);
  transition: color var(--transition-fast);
}

.register-link a:hover {
  color: var(--color-primary-dark);
}
</style>
