import { reactive, computed } from 'vue'

const state = reactive({
  token: localStorage.getItem('token') || '',
  userId: localStorage.getItem('userId') || '',
  username: localStorage.getItem('username') || ''
})

export function useAuth() {
  const isLoggedIn = computed(() => !!state.token)

  function login({ token, userId, username }) {
    state.token = token
    state.userId = userId
    state.username = username
    localStorage.setItem('token', token)
    localStorage.setItem('userId', String(userId))
    localStorage.setItem('username', username)
  }

  function logout() {
    state.token = ''
    state.userId = ''
    state.username = ''
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
  }

  function updateUsername(newName) {
    state.username = newName
    localStorage.setItem('username', newName)
  }

  return {
    token: computed(() => state.token),
    userId: computed(() => state.userId),
    username: computed(() => state.username),
    isLoggedIn,
    login,
    logout,
    updateUsername
  }
}
