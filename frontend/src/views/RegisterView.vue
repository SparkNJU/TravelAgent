<template>
  <div class="register-container">
    <div class="register-box">
      <h2>创建账户</h2>
      <p class="subtitle">成为 AgentLLM 旅游平台的一员</p>
      
      <form @submit.prevent="handleRegister" class="register-form">
        <div class="form-group">
          <label for="username">用户名</label>
          <input 
            type="text" 
            id="username" 
            v-model="formData.username" 
            placeholder="请输入用户名" 
            required 
          />
        </div>
        
        <div class="form-group">
          <label for="email">邮箱</label>
          <input 
            type="email" 
            id="email" 
            v-model="formData.email" 
            placeholder="请输入邮箱地址" 
            required 
          />
        </div>
        
        <div class="form-group">
          <label for="phone">手机号（可选）</label>
          <input 
            type="tel" 
            id="phone" 
            v-model="formData.phone" 
            placeholder="请输入手机号" 
          />
        </div>
        
        <div class="form-group">
          <label for="password">密码</label>
          <input 
            type="password" 
            id="password" 
            v-model="formData.password" 
            placeholder="请输入密码（至少6位）" 
            required 
          />
        </div>
        
        <div class="form-group">
          <label for="confirmPassword">确认密码</label>
          <input 
            type="password" 
            id="confirmPassword" 
            v-model="formData.confirmPassword" 
            placeholder="请再次输入密码" 
            required 
          />
        </div>

        <div class="terms">
          <label>
            <input type="checkbox" v-model="agreeTerms" required />
            我同意服务条款和隐私政策
          </label>
        </div>
        
        <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
        <p v-if="successMessage" class="success-text">{{ successMessage }}</p>
        
        <button type="submit" class="register-btn" :disabled="loading">
          {{ loading ? '注册中...' : '注 册' }}
        </button>
      </form>

      <div class="login-link">
        <span>已有账户？</span>
        <router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const formData = ref({
  username: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: ''
})
const agreeTerms = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const loading = ref(false)

const validateForm = () => {
  if (!formData.value.username.trim()) {
    errorMessage.value = '请输入用户名'
    return false
  }
  
  if (!formData.value.email.includes('@')) {
    errorMessage.value = '请输入有效的邮箱地址'
    return false
  }
  
  if (formData.value.password.length < 6) {
    errorMessage.value = '密码长度不能少于6位'
    return false
  }
  
  if (formData.value.password !== formData.value.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return false
  }
  
  if (!agreeTerms.value) {
    errorMessage.value = '请同意服务条款和隐私政策'
    return false
  }
  
  return true
}

const handleRegister = async () => {
  errorMessage.value = ''
  successMessage.value = ''
  
  if (!validateForm()) {
    return
  }

  loading.value = true

  try {
    const response = await fetch('/api/auth/register', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        username: formData.value.username,
        email: formData.value.email,
        phone: formData.value.phone,
        password: formData.value.password
      })
    })

    const data = await response.json()

    if (data.success) {
      successMessage.value = '注册成功！即将跳转到登录页面...'
      setTimeout(() => {
        router.push('/login')
      }, 2000)
    } else {
      errorMessage.value = data.message || '注册失败，请稍后重试'
    }
  } catch (error) {
    errorMessage.value = '服务器错误，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, var(--color-gray-50) 0%, var(--color-white) 100%);
  padding: var(--space-5);
}

.register-box {
  background: var(--color-white);
  padding: var(--space-10) var(--space-8);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg);
  width: 100%;
  max-width: 400px;
  text-align: center;
}

.register-box h2 {
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

.register-form {
  text-align: left;
}

.form-group {
  margin-bottom: var(--space-4);
}

label {
  display: block;
  margin-bottom: var(--space-2);
  color: var(--color-gray-700);
  font-weight: 600;
  font-size: 14px;
}

input[type="text"],
input[type="email"],
input[type="tel"],
input[type="password"] {
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

input[type="text"]:focus,
input[type="email"]:focus,
input[type="tel"]:focus,
input[type="password"]:focus {
  border-color: var(--color-primary);
  outline: none;
  box-shadow: 0 0 0 3px var(--color-primary-lighter);
}

.terms {
  margin-bottom: var(--space-5);
  font-size: 13px;
}

.terms label {
  display: flex;
  align-items: center;
  margin-bottom: 0;
  font-weight: 500;
  cursor: pointer;
  color: var(--color-gray-600);
}

.terms input[type="checkbox"] {
  margin-right: var(--space-2);
  cursor: pointer;
  width: auto;
  accent-color: var(--color-primary);
}

.register-btn {
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

.register-btn:hover:not(:disabled) {
  background-color: var(--color-primary-dark);
  transform: translateY(-1px);
}

.register-btn:active:not(:disabled) {
  transform: translateY(0);
}

.register-btn:disabled {
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

.success-text {
  color: var(--color-success);
  font-size: 14px;
  margin-bottom: var(--space-4);
  text-align: center;
}

.login-link {
  text-align: center;
  margin-top: var(--space-5);
  color: var(--color-gray-500);
  font-size: 14px;
}

.login-link a {
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 600;
  margin-left: var(--space-2);
  transition: color var(--transition-fast);
}

.login-link a:hover {
  color: var(--color-primary-dark);
}
</style>