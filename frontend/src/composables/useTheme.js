import { ref } from 'vue'

const theme = ref(localStorage.getItem('theme') || 'light')

function applyTheme(t) {
  document.documentElement.setAttribute('data-theme', t)
  localStorage.setItem('theme', t)
}

export function useTheme() {
  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    applyTheme(theme.value)
  }

  // Apply on first use
  applyTheme(theme.value)

  return { theme, toggleTheme }
}
