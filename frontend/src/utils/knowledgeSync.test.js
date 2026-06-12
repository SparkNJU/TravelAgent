import test from 'node:test'
import assert from 'node:assert/strict'
import { buildKnowledgeSyncPayload } from './knowledgeSync.js'

test('buildKnowledgeSyncPayload extracts previous user message, answer, plan and web search results only', () => {
  const conversation = {
    id: 'local-1',
    messages: [
      { role: 'user', content: '帮我做东京5天计划' },
      {
        role: 'assistant',
        answer: '# 东京5日游',
        planContent: '1. 搜索东京景点',
        events: [
          {
            type: 'action',
            content: 'Calling tool: web_search({"query":"东京 五天 美食"})',
            metadata: { tool: 'web_search' },
          },
          {
            type: 'observation',
            content: '[{"title":"东京美食","link":"https://example.com","snippet":"拉面"}]',
            metadata: { tool: 'web_search' },
          },
          {
            type: 'observation',
            content: '[{"title":"知识库结果"}]',
            metadata: { tool: 'knowledge_search' },
          },
        ],
      },
    ],
  }

  const payload = buildKnowledgeSyncPayload(conversation, 1, { model: 'qwen3.6-plus', mode: 'agent' })

  assert.equal(payload.title, '帮我做东京5天计划')
  assert.equal(payload.conversationId, 'local-1')
  assert.equal(payload.turnIndex, 1)
  assert.equal(payload.userMessage, '帮我做东京5天计划')
  assert.equal(payload.assistantAnswer, '# 东京5日游')
  assert.equal(payload.planContent, '1. 搜索东京景点')
  assert.deepEqual(payload.webSearchResults, [
    {
      query: '东京 五天 美食',
      results: [{ title: '东京美食', link: 'https://example.com', snippet: '拉面' }],
    },
  ])
  assert.deepEqual(payload.metadata, { model: 'qwen3.6-plus', mode: 'agent' })
})
