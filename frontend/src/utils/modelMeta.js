/**
 * 共享模型元数据 & 厂商 Logo 映射
 *
 * 所有需要模型信息、厂商 Logo 的组件统一从这里引用，
 * 避免 ModelLeaderboardPanel / ModelLeaderboardView 等组件中重复定义。
 */

import alibabaLogo from '@/assets/logo/alibaba.svg'
import deepseekLogo from '@/assets/logo/deepseek.svg'
import glmLogo from '@/assets/logo/glm.svg'
import kimiLogo from '@/assets/logo/kimi.svg'
import miniMaxLogo from '@/assets/logo/MiniMax.svg'
import moonshotLogo from '@/assets/logo/moonshot.svg'
import qwenLogo from '@/assets/logo/qwen.svg'
import zhipuLogo from '@/assets/logo/zhipu.svg'

// ── 模型元数据 ──────────────────────────────────
// key 为模型 ID（与后端 / arena 接口返回的 model 字段一致）

export const MODEL_META = {
  'deepseek-v4-flash': {
    vendor: 'DeepSeek',
    family: 'DeepSeek',
    accent: '#ff2442',
    price: '$0.20 / $0.80',
    context: '128K',
    priceValue: 0.8,
    contextScore: 128,
  },
  'kimi-k2.6': {
    vendor: '月之暗面',
    family: 'Kimi',
    accent: '#111827',
    price: '$0.60 / $2.00',
    context: '128K',
    priceValue: 2,
    contextScore: 128,
  },
  'MiniMax-M2.5': {
    vendor: 'MiniMax',
    family: 'MiniMax',
    accent: '#d97706',
    price: '$0.30 / $1.20',
    context: '1M',
    priceValue: 1.2,
    contextScore: 1000,
  },
  'qwen3.6-plus': {
    vendor: '阿里巴巴',
    family: 'Qwen',
    accent: '#ff2442',
    price: '$0.40 / $1.20',
    context: '1M',
    priceValue: 1.2,
    contextScore: 1000,
  },
  'glm-5.1': {
    vendor: '智谱',
    family: 'GLM',
    accent: '#b91c1c',
    price: '$0.50 / $1.50',
    context: '128K',
    priceValue: 1.5,
    contextScore: 128,
  },
}

const FALLBACK_META = {
  vendor: 'Unknown',
  family: 'Arena Model',
  accent: '#6b7280',
  price: 'N/A',
  context: 'N/A',
  priceValue: 5,
  contextScore: 0,
}

/**
 * 根据模型 ID 获取元数据，支持模糊匹配。
 * @param {string} model 模型 ID
 * @returns {object} 元数据对象
 */
export function getModelMeta(model) {
  if (MODEL_META[model]) return MODEL_META[model]
  if (/qwen/i.test(model)) return MODEL_META['qwen3.6-plus']
  if (/deepseek/i.test(model)) return MODEL_META['deepseek-v4-flash']
  if (/kimi/i.test(model)) return MODEL_META['kimi-k2.6']
  if (/minimax/i.test(model)) return MODEL_META['MiniMax-M2.5']
  if (/glm/i.test(model)) return MODEL_META['glm-5.1']
  return FALLBACK_META
}

// ── 厂商 Logo ────────────────────────────────────
// 所有 SVG Logo 统一在这里注册，新增厂商只需改这一处。

const VENDOR_LOGO_MAP = [
  { test: (v) => v.includes('阿里巴巴') || v.includes('alibaba'), logo: alibabaLogo },
  { test: (v) => v.includes('deepseek'), logo: deepseekLogo },
  { test: (v) => v.includes('月之暗面') || v.includes('kimi') || v.includes('moonshot'), logo: kimiLogo },
  { test: (v) => v.includes('minimax'), logo: miniMaxLogo },
  { test: (v) => v.includes('智谱') || v.includes('glm') || v.includes('zhipu'), logo: zhipuLogo },
]

/**
 * 根据厂商名称字符串获取 Logo SVG。
 * @param {string} vendorLabel 厂商名称
 * @returns {string|null} SVG 文件路径，未匹配到返回 null
 */
export function getVendorLogo(vendorLabel) {
  const v = String(vendorLabel || '').toLowerCase()
  for (const entry of VENDOR_LOGO_MAP) {
    if (entry.test(v)) return entry.logo
  }
  return null
}

/**
 * 根据模型条目获取 Logo SVG。
 * 从 `item.meta.vendor` 或 `item.model` 中推断厂商。
 * @param {object} item 模型条目（需含 meta.vendor 或 model 字段）
 * @returns {string|null} SVG 文件路径，未匹配到返回 null
 */
export function getModelLogo(item) {
  const vendor = String(item?.meta?.vendor || item?.model || '').toLowerCase()
  return getVendorLogo(vendor)
}

// ── 星级点调色板 ──────────────────────────────────

export const POINT_PALETTE = ['#ff2442', '#2563eb', '#f59e0b', '#14b8a6', '#8b5cf6', '#64748b']

// ── 排行榜指标选项（共享配置）─────────────────────

export const METRIC_OPTIONS = [
  { id: 'overall', icon: '🏆', label: '综合 Arena 分数', sortKey: 'score' },
  { id: 'winRate', icon: '🎯', label: '胜率优先', sortKey: 'winRate' },
  { id: 'votes', icon: '🗳️', label: '投票样本量', sortKey: 'matches' },
  { id: 'confidence', icon: '✅', label: '稳定可信度', sortKey: 'confidenceScore' },
  { id: 'cost', icon: '💰', label: '单位成本表现', sortKey: 'costEfficiency' },
  { id: 'context', icon: '🧠', label: '上下文能力', sortKey: 'contextScore' },
]
