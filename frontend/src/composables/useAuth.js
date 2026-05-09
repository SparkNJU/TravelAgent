import { reactive, computed } from 'vue'

const state = reactive({
  token: localStorage.getItem('token') || '',
  userId: localStorage.getItem('userId') || '',
  username: localStorage.getItem('username') || '',
  avatar: localStorage.getItem('avatar') || ''
})

export function useAuth() {
  const isLoggedIn = computed(() => !!state.token)

  function login({ token, userId, username, avatar }) {
    state.token = token
    state.userId = userId
    state.username = username
    state.avatar = avatar || ''
    localStorage.setItem('token', token)
    localStorage.setItem('userId', String(userId))
    localStorage.setItem('username', username)
    localStorage.setItem('avatar', avatar || '')
  }

  function logout() {
    state.token = ''
    state.userId = ''
    state.username = ''
    state.avatar = ''
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('avatar')
  }

  function updateUsername(newName) {
    state.username = newName
    localStorage.setItem('username', newName)
  }

  function updateAvatar(url) {
    state.avatar = url || ''
    localStorage.setItem('avatar', url || '')
  }

  return {
    token: computed(() => state.token),
    userId: computed(() => state.userId),
    username: computed(() => state.username),
    avatar: computed(() => state.avatar),
    isLoggedIn,
    login,
    logout,
    updateUsername,
    updateAvatar
  }
}
