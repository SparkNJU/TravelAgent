function parseWebSearchAction(content) {
  const match = String(content || '').match(/web_search\((.*)\)/)
  if (!match) return ''
  try {
    const args = JSON.parse(match[1])
    return args.query || ''
  } catch {
    return ''
  }
}

function parseObservationResults(content) {
  try {
    const parsed = JSON.parse(content || '[]')
    return Array.isArray(parsed)
      ? parsed.map((item) => ({
          title: item.title || '',
          link: item.link || '',
          snippet: item.snippet || '',
        }))
      : []
  } catch {
    return []
  }
}

export function extractWebSearchResults(events = []) {
  const groups = []
  let pendingQuery = ''
  events.forEach((event) => {
    const tool = event.metadata?.tool || event.metadata?.tool_name
    if (tool !== 'web_search') return
    if (event.type === 'action') {
      pendingQuery = parseWebSearchAction(event.content)
      return
    }
    if (event.type === 'observation') {
      const results = parseObservationResults(event.content)
      if (!results.length) return
      groups.push({ query: pendingQuery, results })
      pendingQuery = ''
    }
  })
  return groups
}

export function buildKnowledgeSyncPayload(conversation, assistantIndex, metadata = {}) {
  const assistantMsg = conversation?.messages?.[assistantIndex]
  if (!assistantMsg || assistantMsg.role === 'user') {
    throw new Error('请选择一条助手回复进行同步')
  }
  const previousUser = [...conversation.messages.slice(0, assistantIndex)]
    .reverse()
    .find((msg) => msg.role === 'user')
  const userMessage = previousUser?.content || ''
  const title = userMessage.slice(0, 40) || conversation.title || 'TravelAgent 对话沉淀'

  return {
    title,
    conversationId: conversation.id || conversation.backendId || '',
    turnIndex: assistantIndex,
    userMessage,
    assistantAnswer: assistantMsg.answer || assistantMsg.content || '',
    planContent: assistantMsg.planContent || '',
    webSearchResults: extractWebSearchResults(assistantMsg.events || []),
    metadata,
  }
}
