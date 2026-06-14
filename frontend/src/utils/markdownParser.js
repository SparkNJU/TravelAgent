/**
 * AGENT.md 解析与构建工具
 *
 * 解析/构建格式：
 *   ## 个人信息
 *   - key: value（证据: evidence）
 *   - key: value
 *
 *   ## 旅游偏好
 *   - key: value
 */

const SECTION_HEADER_RE = /^## (.+)$/m
const ITEM_RE = /^-\s*(.+?)\s*:\s*(.+?)(?:\s*（证据:\s*(.+?)）)?$/
const VALUE_ONLY_RE = /^-\s*(.+)$/

/**
 * 解析 "key: value" 格式的内容字符串，返回结构化对象
 * @param {string} content - 原始内容，如 "user_name: 小米"
 * @returns {{ key: string, value: string }}
 */
export function parseMemoryContent(content) {
  if (!content) return { key: '', value: '' }
  const colonIdx = content.indexOf(':')
  if (colonIdx > 0) {
    return {
      key: content.substring(0, colonIdx).trim(),
      value: content.substring(colonIdx + 1).trim()
    }
  }
  return { key: '', value: content.trim() }
}

/**
 * 将 AGENT.md 字符串解析为结构化段落
 * @param {string} markdown - AGENT.md 原始文本
 * @returns {Array<{title: string, items: Array<{key: string, value: string, evidence: string}>}>}
 */
export function parseMemoryMarkdown(markdown) {
  if (!markdown || !markdown.trim()) return []

  const sections = []
  const lines = markdown.split('\n')
  let currentSection = null

  for (const line of lines) {
    const headerMatch = line.match(SECTION_HEADER_RE)
    if (headerMatch) {
      const title = headerMatch[1].trim()

      if (title === '用户' || title === '对话摘要' || title === '可复用公共知识') {
        currentSection = null
        continue
      }
      currentSection = { title, items: [] }
      sections.push(currentSection)
      continue
    }

    if (!currentSection) continue

    const itemMatch = line.match(ITEM_RE)
    if (itemMatch) {
      currentSection.items.push({
        key: itemMatch[1].trim(),
        value: itemMatch[2].trim(),
        evidence: (itemMatch[3] || '').trim()
      })
      continue
    }

    const valueMatch = line.match(VALUE_ONLY_RE)
    if (valueMatch && valueMatch[1].trim() !== '暂无') {
      currentSection.items.push({
        key: '',
        value: valueMatch[1].trim(),
        evidence: ''
      })
    }
  }

  return sections
}

/**
 * 从结构化段落重建 AGENT.md
 * @param {string} username - 用户名
 * @param {Array<{title: string, items: Array<{key: string, value: string, evidence: string}>}>} sections
 * @param {string} conversationSummary - 对话摘要
 * @returns {string} AGENT.md 文本
 */
export function buildMemoryMarkdown(username, sections, conversationSummary) {
  const lines = []
  lines.push('# AGENT.md')
  lines.push('')
  lines.push('## 用户')
  lines.push('- username: ' + (username || '未知'))
  lines.push('')

  let hasAnyFact = false
  for (const section of sections) {
    if (!section.items || section.items.length === 0) continue
    hasAnyFact = true
    lines.push('## ' + section.title)
    for (const item of section.items) {
      const evidencePart = item.evidence ? `（证据: ${item.evidence}）` : ''
      if (item.key) {
        lines.push(`- ${item.key}: ${item.value}${evidencePart}`)
      } else {
        lines.push(`- ${item.value}`)
      }
    }
    lines.push('')
  }

  if (!hasAnyFact) {
    lines.push('## 个人信息')
    lines.push('- 暂无')
    lines.push('')
  }

  lines.push('## 对话摘要')
  lines.push(conversationSummary || '暂无')

  return lines.join('\n')
}

/**
 * 将 sections 转为卡片数组（前端展示）
 * @param {Array<{title: string, items: Array}>} sections
 * @param {Set<string>} disabledKeys
 * @returns {Array<{id: number, key: string, content: string, category: string, isEnabled: boolean}>}
 */
export function sectionsToCards(sections, disabledKeys = new Set()) {
  let id = 1
  const cards = []
  for (const section of sections) {
    for (const item of section.items) {
      cards.push({
        id: id++,
        key: item.key,
        content: item.key ? `${item.key}: ${item.value}` : item.value,
        category: section.title,
        isEnabled: !disabledKeys.has(item.key)
      })
    }
  }
  return cards
}

/**
 * 从卡片数组重建 sections（将卡片按 category 分组）
 * @param {Array<{key: string, content: string, category: string, isEnabled: boolean}>} cards
 * @param {Array<string>} categoryOrder - 分类顺序
 * @returns {Array<{title: string, items: Array<{key: string, value: string, evidence: string}>}>}
 */
export function cardsToSections(cards, categoryOrder = ['个人信息', '旅游偏好', '口味偏好', '其他']) {
  const groups = {}
  for (const cat of categoryOrder) {
    groups[cat] = []
  }

  for (const card of cards) {
    if (!card.isEnabled) continue
    const cat = card.category || '其他'
    if (!groups[cat]) groups[cat] = []
    const content = card.content || ''
    const colonIdx = content.indexOf(':')
    const key = colonIdx > 0 ? content.substring(0, colonIdx).trim() : ''
    const value = colonIdx > 0 ? content.substring(colonIdx + 1).trim() : content.trim()
    groups[cat].push({ key, value, evidence: '' })
  }

  const sections = []
  for (const cat of categoryOrder) {
    if (groups[cat] && groups[cat].length > 0) {
      sections.push({ title: cat, items: groups[cat] })
    }
  }
  return sections
}
