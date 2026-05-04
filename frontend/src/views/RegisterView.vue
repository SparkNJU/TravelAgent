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
  background-color: #f5f7fa;
  padding: 20px;
}

.register-box {
  background: white;
  padding: 40px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 400px;
  text-align: center;
}

h2 {
  margin-top: 0;
  color: #333;
}

.subtitle {
  color: #666;
  margin-bottom: 30px;
  font-size: 14px;
}

.register-form {
  text-align: left;
}

.form-group {
  margin-bottom: 18px;
}

label {
  display: block;
  margin-bottom: 8px;
  color: #333;
  font-weight: 600;
  font-size: 14px;
}

input[type="text"],
input[type="email"],
input[type="tel"],
input[type="password"] {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 16px;
  box-sizing: border-box;
}

input[type="text"]:focus,
input[type="email"]:focus,
input[type="tel"]:focus,
input[type="password"]:focus {
  border-color: #4CAF50;
  outline: none;
}

.terms {
  margin-bottom: 20px;
  font-size: 13px;
}

.terms label {
  display: flex;
  align-items: center;
  margin-bottom: 0;
  font-weight: normal;
  cursor: pointer;
}

.terms input[type="checkbox"] {
  margin-right: 8px;
  cursor: pointer;
  width: auto;
}

.register-btn {
  width: 100%;
  padding: 12px;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.3s;
  margin-bottom: 20px;
}

.register-btn:hover:not(:disabled) {
  background-color: #45a049;
}

.register-btn:disabled {
  background-color: #9acb9c;
  cursor: not-allowed;
}
.error-text {
  color: #f44336;
  font-size: 14px;
  margin-bottom: 15px;
  text-align: center;
}

.success-text {
  color: #4caf50;
  font-size: 14px;
  margin-bottom: 15px;
  text-align: center;
}

.login-link {
  text-align: center;
  margin-top: 20px;
  color: #666;
  font-size: 14px;
}

.login-link a {
  color: #4CAF50;
  text-decoration: none;
  font-weight: bold;
  margin-left: 8px;
  transition: all 0.2s;
}

.login-link a:hover {
  color: #45a049;
  text-decoration: underline;
}
</style>