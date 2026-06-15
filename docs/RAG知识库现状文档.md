# RAG 知识库系统现状文档

> 生成日期: 2026-06-15

## 1. 系统概览

项目包含 **两套并行的知识系统**，功能定位不同：

| 系统 | 存储 | 技术栈 | 状态 | 用途 |
|------|------|--------|------|------|
| **向量 RAG 知识库** | Milvus | pymilvus + DashScope Embedding | ✅ 已实现 | Agent 对话时语义检索，注入上下文 |
| **关系型公共知识** | MySQL `agent_public_knowledge` | Spring Data JPA | ✅ 已实现 | Agent Memory 自动沉淀，结构化存储 |

两者 **互不连通**。向量 RAG 是 Agent 检索增强生成的核心；关系型知识是 Memory 系统的副产品。

---

## 2. 向量 RAG 知识库（主系统）

### 2.1 架构

```
前端 AIPlanView.vue
  ├─ "同步到知识中心" 按钮 ──→ POST /api/knowledge/sync-turn (Backend)
  └─ "知识检索" 开关 ──────→ formData.append('knowledgeSearchEnabled', ...)
                                    │
Backend KnowledgeController          │
  └─ KnowledgeService.syncTurn()     │
     构建 Markdown ─────────────────→ POST http://localhost:8000/api/knowledge/documents (Agent)
                                          │
Agent knowledge/router.py                 │
  ├─ POST /api/knowledge/documents ←──────┘  (写入)
  ├─ POST /api/knowledge/search                 (检索)
  └─ GET  /api/knowledge/health                 (健康检查)
        │
        ├── knowledge/service.py       编排层：分块 → Embedding → 存储 / 检索
        ├── knowledge/splitter.py      文本分块器
        ├── knowledge/embedding_client.py  DashScope Embedding API 客户端
        ├── knowledge/vector_store.py  Milvus 向量数据库操作
        ├── knowledge/provider.py      单例工厂
        ├── knowledge/client.py        进程内/HTTP 两种客户端
        └── knowledge/schemas.py       Pydantic 请求/响应模型
```

### 2.2 核心组件详解

#### 2.2.1 文本分块器 (`agent/knowledge/splitter.py`)

- **算法**: 递归段落分割，先按 `\n\n` 分段，超长段落再按字符窗口滑动切分
- **默认参数**: `chunk_size=800`, `chunk_overlap=200`
- **chunk_id 格式**: `{doc_id}_{序号}_{uuid前8位}`

#### 2.2.2 Embedding 客户端 (`agent/knowledge/embedding_client.py`)

- **API**: DashScope OpenAI 兼容模式 (`https://dashscope.aliyuncs.com/compatible-mode/v1`)
- **模型**: `text-embedding-v4`，维度 2048
- **缓存**: 本地 JSON 文件 `.knowledge_embedding_cache.json`，SHA256 哈希键 → 向量映射
- **Rerank**: 配置了 `qwen3-rerank` 模型但 **当前禁用** (`rerank_enabled: false`)，实现为占位符

#### 2.2.3 向量存储 (`agent/knowledge/vector_store.py`)

- **数据库**: Milvus（通过 `pymilvus` 客户端）
- **连接地址**: `http://localhost:19530`（可通过 `MILVUS_URI` 环境变量覆盖）
- **Collection**: `travel_knowledge_chunks`（首次访问自动创建）

**Schema 字段**:

| 字段 | 类型 | 说明 |
|------|------|------|
| `chunk_id` | VARCHAR(128) PK | 分块唯一 ID |
| `doc_id` | VARCHAR(128) | 文档 ID |
| `doc_title` | VARCHAR(512) | 文档标题 |
| `namespace` | VARCHAR(64) | 命名空间（多租户隔离） |
| `source_type` | VARCHAR(64) | 来源类型：`manual_text` / `conversation_turn` |
| `source_ref` | VARCHAR(512) | 来源引用 |
| `content` | VARCHAR(65535) | 文本内容（启用中文分词器 + match） |
| `metadata` | JSON | 元数据 |
| `created_at` | VARCHAR(64) | 创建时间 |
| `embedding` | FLOAT_VECTOR(2048) | 稠密向量 |
| `sparse_bm25` | SPARSE_FLOAT_VECTOR | 稀疏 BM25 向量（自动生成） |

**索引**:
- 稠密: HNSW, COSINE, M=16, efConstruction=64
- 稀疏: SPARSE_WAND, BM25

**混合检索**:
- `WeightedRanker(dense_weight=0.75, sparse_weight=0.25)`
- 稠密语义检索 (75%) + BM25 词法检索 (25%)
- `recall_limit=30`，最终返回 `top_k=6`

#### 2.2.4 知识服务编排 (`agent/knowledge/service.py`)

**写入流程** (`ingest_text`):
1. `TextSplitter.split_text(content)` → 分块列表
2. `KnowledgeEmbeddingClient.embed(chunks)` → 每块的 2048 维向量
3. 构建行数据（chunk_id, doc_id, content, embedding 等）
4. `MilvusKnowledgeStore.insert(rows)` → 写入 Milvus（BM25 稀疏向量自动生成）

**检索流程** (`search`):
1. `embed([query])` → 查询向量
2. `hybrid_search(query_vector, query_text, namespace, limit=30)` → Milvus 混合检索
3. `rerank(query, docs, top_k=6)` → 重排序（当前为占位实现）
4. 返回 `KnowledgeSearchResponse` 包含 chunk_id, title, content, score 等

### 2.3 数据写入路径

#### 路径 A：对话同步（主要路径）

```
用户在 AI 对话页面点击 "同步到知识中心" 按钮
  ↓
frontend/src/utils/knowledgeSync.js
  buildKnowledgeSyncPayload(conversation, assistantIndex)
  提取: title, userMessage, assistantAnswer, planContent, webSearchResults
  ↓
POST /api/knowledge/sync-turn (Backend)
  ↓
backend/.../KnowledgeService.java
  buildKnowledgeMarkdown() 构建结构化 Markdown:
    # 标题
    ## 用户问题
    ## Agent 执行计划
    ## 联网搜索结果（含链接和摘要）
    ## 最终回答
    ## 元信息（conversation_id, turn_index）
  ↓
POST http://localhost:8000/api/knowledge/documents (Agent)
  ↓
Agent knowledge/router.py → KnowledgeService.ingest_text()
  分块 → Embedding → 存入 Milvus
```

#### 路径 B：直接 API 写入

```bash
curl -X POST http://localhost:8000/api/knowledge/documents \
  -H "Content-Type: application/json" \
  -d '{
    "title": "北京三日游攻略",
    "content": "第一天：天安门广场...",
    "source_type": "manual_text",
    "source_ref": "https://example.com/beijing-guide"
  }'
```

**没有前端管理页面**。只能通过 API 或对话同步按钮添加知识。

### 2.4 检索路径

```
用户发送消息 → Agent ReAct 循环
  ↓
LLM 判断需要知识检索 → 调用 knowledge_search tool
  ↓
KnowledgeSearchTool.execute(query, top_k=6)
  ↓
InProcessKnowledgeClient.search() → KnowledgeService.search()
  ↓
Milvus 混合检索 → 返回 top 6 知识片段
  ↓
结果注入 ReAct Agent 上下文 → LLM 结合知识生成回答
```

**前端控制**: "知识检索" 开关（`useAgentTools.js`）控制 `knowledgeSearchEnabled` 布尔值，通过 `formData` 传给后端，后端转发给 Agent，Agent 根据该值决定是否注册 `KnowledgeSearchTool`。

### 2.5 配置

**Agent 配置文件** (`agent/config/default.yaml`):

```yaml
knowledge:
  enabled: true                    # 总开关
  mode: "inprocess"                # "inprocess" (进程内) 或 "http" (独立服务)
  base_url: "http://localhost:8000/api/knowledge"
  milvus_uri: "http://localhost:19530"
  collection: "travel_knowledge_chunks"
  namespace: "default"
  chunk_size: 800
  chunk_overlap: 200
  embedding_base_url: "https://dashscope.aliyuncs.com/compatible-mode/v1"
  embedding_api_key_env: "DASHSCOPE_API_KEY"
  embedding_model: "text-embedding-v4"
  embedding_dim: 2048
  recall_limit: 30
  top_k: 6
  dense_weight: 0.75
  sparse_weight: 0.25
  rerank_enabled: false
  rerank_model: "qwen3-rerank"
```

**环境变量覆盖** (`agent/config/__init__.py`):

| 环境变量 | 覆盖字段 | 默认值 |
|----------|----------|--------|
| `KNOWLEDGE_ENABLED` | `knowledge.enabled` | `true` |
| `KNOWLEDGE_MODE` | `knowledge.mode` | `"inprocess"` |
| `KNOWLEDGE_BASE_URL` | `knowledge.base_url` | `"http://localhost:8000/api/knowledge"` |
| `MILVUS_URI` | `knowledge.milvus_uri` | `"http://localhost:19530"` |
| `KNOWLEDGE_COLLECTION` | `knowledge.collection` | `"travel_knowledge_chunks"` |
| `KNOWLEDGE_NAMESPACE` | `knowledge.namespace` | `"default"` |
| `KNOWLEDGE_RERANK_ENABLED` | `knowledge.rerank_enabled` | `false` |

**后端配置** (`application.properties`):

```properties
app.agent.knowledge-url=http://localhost:8000/api/knowledge/documents
```

---

## 3. 关系型公共知识（MySQL 副系统）

### 3.1 表结构

`agent_public_knowledge` 表（MySQL）:

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 自增主键 |
| `knowledge_key` | VARCHAR(128) UNIQUE | 知识唯一标识 |
| `knowledge_title` | VARCHAR(255) | 标题 |
| `knowledge_content` | LONGTEXT | 内容 |
| `knowledge_json` | JSON | 原始 JSON |
| `knowledge_scope` | VARCHAR(50) | 范围，默认 `global` |
| `contributor_user_id` | BIGINT | 贡献者用户 ID |
| `source_conversation_id` | BIGINT | 来源会话 ID |
| `confidence_score` | DECIMAL(3,2) | 置信度，默认 0.80 |
| `usage_count` | INT | 使用次数 |

### 3.2 写入路径

```
Agent 对话结束 → AgentMemorySyncRequest (包含 publicKnowledgeJson)
  ↓
AgentMemoryService.syncMemory()
  ↓
upsertPublicKnowledge() → 解析 JSON 数组 → 按 knowledgeKey upsert
```

### 3.3 现状

- ✅ 表已创建，JPA Entity + Repository 已实现
- ✅ Agent Memory 系统会自动沉淀公共知识
- ❌ **没有 API 暴露给前端管理**
- ❌ **没有接入向量检索**，与 Milvus RAG 系统完全独立
- ❌ **没有被 Agent 检索使用**

---

## 4. 前端功能现状

### 4.1 AI 对话页面 (`AIPlanView.vue`)

| 功能 | UI 位置 | 说明 |
|------|---------|------|
| 同步到知识中心 | 每条助手消息下方按钮 | 点击后将该轮对话同步到 Milvus 知识库 |
| 知识检索开关 | 设置区域 "知识检索" 按钮 | 控制 Agent 是否使用 knowledge_search 工具 |

### 4.2 工具状态管理 (`useAgentTools.js`)

- `knowledgeSearchEnabled` 持久化到 `localStorage` (`travel_agent_knowledge_search_enabled`)
- 默认值: `true`（开启）

### 4.3 知识同步工具 (`knowledgeSync.js`)

- `extractWebSearchResults(events)`: 从 Agent 事件流中提取联网搜索结果
- `buildKnowledgeSyncPayload(conversation, assistantIndex)`: 构建同步请求体

### 4.4 缺失功能

- ❌ **没有独立的知识管理页面**（查看、编辑、删除知识条目）
- ❌ **没有批量导入功能**
- ❌ **没有知识库搜索/浏览 UI**
- ❌ **没有知识条目列表展示**

---

## 5. 本地启用指南

### 5.1 前置依赖

| 依赖 | 是否必须 | 安装方式 |
|------|----------|----------|
| **Milvus** | ✅ 必须 | Docker / Milvus Standalone |
| **DashScope API Key** | ✅ 必须 | `.env` 已配置 `DASHSCOPE_API_KEY` |
| **pymilvus** | ✅ 必须 | `requirements.txt` 已包含 |
| **openai** | ✅ 必须 | `requirements.txt` 已包含 |
| MySQL | 后端需要 | 已有 |
| Spring Boot Backend | 对话同步需要 | 已有 |

### 5.2 安装 Milvus

项目中 **没有** docker-compose 包含 Milvus。需要手动安装：

#### 方式 A：Docker Standalone（推荐）

```bash
# 下载安装脚本
curl -sfL https://raw.githubusercontent.com/milvus-io/milvus/master/scripts/standalone_embed.sh -o standalone_embed.sh

# 启动 Milvus Standalone（使用 embedded etcd，无需额外依赖）
bash standalone_embed.sh start

# 或者直接用 Docker
docker run -d \
  --name milvus \
  -p 19530:19530 \
  -p 9091:9091 \
  -v milvus-data:/var/lib/milvus \
  milvusdb/milvus:latest \
  milvus run standalone
```

#### 方式 B：Milvus Lite（轻量，无需 Docker）

```bash
pip install milvus[model]
```

然后修改 `agent/config/default.yaml`：
```yaml
knowledge:
  milvus_uri: "./milvus_data.db"  # 使用本地文件而非服务器
```

> ⚠️ Milvus Lite 功能有限，不支持 BM25 Function 和 hybrid_search。需要改用纯稠密检索。

#### 方式 C：Docker Compose（完整版）

创建 `agent/docker-compose.yml`：

```yaml
version: '3.9'
services:
  milvus:
    image: milvusdb/milvus:latest
    ports:
      - "19530:19530"
      - "9091:9091"
    volumes:
      - milvus-data:/var/lib/milvus
    command: milvus run standalone
    environment:
      - ETCD_USE_EMBED=true
      - COMMON_STORAGETYPE=local

volumes:
  milvus-data:
```

### 5.3 启动步骤

```bash
# 1. 启动 Milvus
docker start milvus  # 或用上面的 docker run

# 2. 确认 Milvus 运行
curl http://localhost:9091/healthz

# 3. 确认 .env 已配置
cat agent/.env
# 应包含: DASHSCOPE_API_KEY=sk-xxx

# 4. 启动 Agent
cd agent
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000

# 5. 验证知识服务健康
curl http://localhost:8000/api/knowledge/health
# 返回: {"ok": true, "mode": "embedded"}

# 6. 测试写入
curl -X POST http://localhost:8000/api/knowledge/documents \
  -H "Content-Type: application/json" \
  -d '{"title":"测试","content":"这是一条测试知识"}'

# 7. 测试检索
curl -X POST http://localhost:8000/api/knowledge/search \
  -H "Content-Type: application/json" \
  -d '{"query":"测试","top_k":3}'
```

### 5.4 Collection 自动创建

首次调用 `/api/knowledge/documents` 或 `/api/knowledge/search` 时，`MilvusKnowledgeStore._ensure_collection()` 会自动创建 collection、schema 和索引。**无需手动建表**。

---

## 6. API 接口汇总

### Agent 端 (FastAPI, :8000)

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| GET | `/api/knowledge/health` | 健康检查 | 无 |
| POST | `/api/knowledge/documents` | 写入知识文档 | `KnowledgeDocumentCreate` |
| POST | `/api/knowledge/search` | 检索知识 | `KnowledgeSearchRequest` |

**KnowledgeDocumentCreate**:
```json
{
  "title": "string (required)",
  "content": "string (required)",
  "source_type": "manual_text (default) | conversation_turn",
  "source_ref": "string | null",
  "metadata": {}
}
```

**KnowledgeSearchRequest**:
```json
{
  "query": "string (required)",
  "top_k": 6  // 1-20, default 6
}
```

### Backend 端 (Spring Boot, :8080)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/sync-turn` | 同步对话轮次到知识库 |

**KnowledgeSyncTurnRequest**:
```json
{
  "title": "string",
  "conversationId": "string",
  "turnIndex": 0,
  "userMessage": "string",
  "assistantAnswer": "string",
  "planContent": "string",
  "webSearchResults": [
    {
      "query": "搜索关键词",
      "results": [
        {"title": "", "link": "", "snippet": ""}
      ]
    }
  ],
  "metadata": {}
}
```

---

## 7. 关键源文件索引

### Agent (Python)

| 文件 | 行数 | 职责 |
|------|------|------|
| `agent/knowledge/__init__.py` | 1 | 包声明 |
| `agent/knowledge/router.py` | 30 | FastAPI 路由（3 个端点） |
| `agent/knowledge/service.py` | 93 | 编排层：分块 → Embedding → 存储 → 检索 |
| `agent/knowledge/splitter.py` | 56 | 文本分块器 |
| `agent/knowledge/embedding_client.py` | 91 | DashScope Embedding + 缓存 + Rerank 占位 |
| `agent/knowledge/vector_store.py` | 132 | Milvus 连接、Schema、索引、混合检索 |
| `agent/knowledge/provider.py` | 41 | 单例工厂，组装所有组件 |
| `agent/knowledge/client.py` | 30 | 进程内/HTTP 两种检索客户端 |
| `agent/knowledge/schemas.py` | 46 | Pydantic 模型 |
| `agent/services/tool_registry.py:91-124` | 34 | KnowledgeSearchTool 定义 |
| `agent/main.py:56-64` | 9 | 知识客户端初始化 + 工具注册 |
| `agent/config/default.yaml:22-39` | 18 | 知识系统配置 |
| `agent/config/__init__.py:46-64` | 19 | KnowledgeConfig 数据类 |

### Backend (Java)

| 文件 | 行数 | 职责 |
|------|------|------|
| `backend/.../controller/KnowledgeController.java` | 30 | `/api/knowledge/sync-turn` 端点 |
| `backend/.../service/KnowledgeService.java` | 131 | Markdown 构建 + 转发到 Agent |
| `backend/.../dto/KnowledgeSyncTurnRequest.java` | 71 | 同步请求 DTO |
| `backend/.../entity/AgentPublicKnowledge.java` | 163 | MySQL 公共知识实体（独立系统） |
| `backend/.../service/AgentMemoryService.java:251-286` | 36 | 公共知识 upsert 逻辑 |

### Frontend (Vue)

| 文件 | 行数 | 职责 |
|------|------|------|
| `frontend/src/utils/knowledgeSync.js` | 68 | 同步 payload 构建 |
| `frontend/src/composables/useAgentTools.js` | 80 | 知识检索开关状态管理 |
| `frontend/src/views/AIPlanView.vue:117-123` | 7 | 同步按钮 UI |
| `frontend/src/views/AIPlanView.vue:271-286` | 16 | 知识检索开关 UI |
| `frontend/src/views/AIPlanView.vue:1366-1406` | 41 | syncTurnToKnowledge() 实现 |

---

## 8. 已知限制与待改进

### 8.1 功能缺失

1. **无知识管理 UI**: 不能在前端查看/编辑/删除已入库的知识条目
2. **无批量导入**: 只能逐条同步或通过 API 写入
3. **Rerank 未实现**: `embedding_client.py` 的 `rerank()` 方法是占位符，返回合成分数
4. **两套知识系统未打通**: MySQL `agent_public_knowledge` 与 Milvus 完全独立
5. **无知识过期/清理机制**: 没有 TTL 或定期清理策略

### 8.2 技术债务

1. `provider.py` 使用 `lru_cache(1)` 做单例，Milvus 连接不会被释放
2. `vector_store.py` 的 `try/except` 导入 pymilvus（line 5-8）在缺少依赖时不会报错，只在实例化时报 RuntimeError
3. BM25 中文分词依赖 Milvus 内置的 `{"type": "chinese"}` 分词器，需要 Milvus 版本支持
4. Embedding 缓存文件 `.knowledge_embedding_cache.json` 没有大小限制，长期使用会持续增长
