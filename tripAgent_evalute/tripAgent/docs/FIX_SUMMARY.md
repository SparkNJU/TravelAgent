# tripAgent_evalute 完整修复清单 - 2026/04/28

## 问题总结
1. ❌ **任务状态混乱**：运行完成后任务仍显示 RUNNING
2. ❌ **TaskId 重复**：多个任务拥有相同 taskId（尤其是 taskId=1）
3. ❌ **Metrics 接口 400 错误**：运行中请求 metrics 导致异常

---

## 修复 1：数据库持久化（TaskId 重复问题根治）

**问题根因**
- 使用 H2 内存数据库，每次应用重启数据清空
- IDENTITY 序列重置为 1，新任务与旧任务 id 冲突
- 状态更新基于 taskId 时可能操作错误的任务

**修复文件**
1. [application.yml](backend/src/main/resources/application.yml) ✅
   - 改为文件数据库：`jdbc:h2:./data/tripagent`
   - 保留重启后的数据和序列

2. [application-dev.yml](backend/src/main/resources/application-dev.yml) ✅（新建）
   - 开发环境：保留内存 DB 选项
   - 快速重置用途

3. [application-prod.yml](backend/src/main/resources/application-prod.yml) ✅（新建）
   - 生产环境：禁用自动 schema 初始化
   - 防止表被意外重建

**验证**
```bash
# 删除旧数据库
rm ./data/tripagent.mv.db

# 启动应用
java -jar backend.jar

# 检查任务 id 是否递增：1, 2, 3, ...
```

---

## 修复 2：任务状态同步（RUNNING 残留问题）

**问题根因**
- `@Async` 方法中 run 状态写入数据库后，但任务状态刷新未正确提交
- Hibernate 一级缓存中旧的 RUNNING 状态未被清除
- 前端列表查询读取到缓存中的陈旧值

**修复文件**

1. [EvalTaskStatusService.java](backend/src/main/java/com/tripagent/backend/service/eval/EvalTaskStatusService.java) ✅
   - 注入 `EntityManager`
   - 在 `refreshTaskStatus()` 开始时调用 `entityManager.clear()`
   - 强制从数据库查询最新数据

2. [EvalTaskService.java](backend/src/main/java/com/tripagent/backend/service/eval/EvalTaskService.java) ✅
   - 在 `listTasks()` 循环中每次刷新后调用 `entityManager.clear()`
   - 确保前端读取最新任务状态

3. [EvalRunService.java](backend/src/main/java/com/tripagent/backend/service/eval/EvalRunService.java) ✅
   - run 完成时调用 `refreshTaskStatus()`（REQUIRES_NEW）
   - 移除了 `refreshTaskStatusInTransaction()` 调用
   - 确保独立事务正确提交

**验证**
```
1. 启动后端应用
2. 创建并启动任务
3. SSE 流显示 "运行结束" 后
4. 观察任务列表是否立即从 RUNNING 更新为 SUCCEEDED
5. 点击"刷新数据"按钮，确认状态保持正确
```

---

## 修复 3：Metrics 接口容错（400 错误处理）

**问题根因**
- 运行中或失败时请求 `/runs/{runId}/metrics` 导致 404/400
- Metric Snapshot 尚未生成时直接抛异常
- 前端无条件请求 metrics，即使运行未完成

**修复文件**

1. [EvalRunService.java](backend/src/main/java/com/tripagent/backend/service/eval/EvalRunService.java) ✅
   - `getRunMetrics()` 改为返回占位 metrics
   - 包含当前 run 状态说明，不抛异常

2. [DashboardView.vue](frontend/src/views/DashboardView.vue) ✅
   - `refreshCurrentRun()` 仅在 run.status === 'SUCCEEDED' 时请求 metrics
   - 运行中状态下 metrics 显示为 null，避免无意义请求

**验证**
```
1. 启动任务
2. 打开浏览器开发者工具 > 网络
3. 观察 /api/eval/runs/{runId}/metrics 请求
4. 应不出现 400/404 错误
5. 任务完成后 metrics 正常返回
```

---

## 总体修复架构

```
前端                          后端
─────────────────────────────────────
1. 刷新任务列表
   GET /api/eval/tasks
                              listTasks()
                              ├─ 遍历任务
                              ├─ refreshTaskStatus(taskId)
                              │  └─ entityManager.clear()
                              │     └─ 查询最新 run 状态
                              ├─ entityManager.clear()
                              └─ 组装响应
                              
2. SSE 流关闭（运行完成）
   run.status = SUCCEEDED
   
   └─ refreshTaskStatus(taskId)
      ├─ REQUIRES_NEW 事务
      ├─ entityManager.clear()
      ├─ 计算 task 最新状态
      └─ saveAndFlush()

3. 查询 Metrics
   GET /api/eval/runs/{runId}/metrics
   
   ├─ if (run.status === SUCCEEDED)
   │  └─ 返回真实 metrics
   └─ else
      └─ 返回占位 metrics（含 run.status）
```

---

## 部署检查清单

- [ ] 删除旧的 `./data/tripagent.mv.db` 文件
- [ ] 确认后端已编译无错误
- [ ] 启动后端应用，观察日志无异常
- [ ] 前端已刷新，缓存已清
- [ ] 创建新任务，验证 taskId 为 1（如果这是第一个任务）
- [ ] 启动任务，SSE 流正常推送事件
- [ ] 运行完成后刷新列表，状态为 SUCCEEDED/FAILED
- [ ] 查看运行详情，metrics 正常显示
- [ ] 重启后端应用，验证数据仍然存在
- [ ] 再创建任务，验证 taskId 为 2（序列递增）

---

## 文件变更总览

### 后端 (Java)
| 文件 | 修改内容 | 状态 |
|------|--------|------|
| EvalTaskStatusService.java | 注入 EntityManager，clear() 清缓存 | ✅ |
| EvalTaskService.java | listTasks() 中 clear() 清缓存 | ✅ |
| EvalRunService.java | refreshTaskStatus() REQUIRES_NEW，metrics 容错 | ✅ |

### 配置
| 文件 | 修改内容 | 状态 |
|------|--------|------|
| application.yml | 改用文件 DB + always mode | ✅ |
| application-dev.yml | 开发环境内存 DB 配置 | ✅ |
| application-prod.yml | 生产环境文件 DB + never mode | ✅ |

### 前端 (Vue)
| 文件 | 修改内容 | 状态 |
|------|--------|------|
| DashboardView.vue | refreshCurrentRun() 条件检查 | ✅ |

### 文档
| 文件 | 内容 | 状态 |
|------|------|------|
| TASKID_FIX.md | TaskId 问题与修复说明 | ✅ |
| FIX_SUMMARY.md | 本文档 - 完整修复清单 | ✅ |

---

## 预期效果

🎯 **修复后的行为**
1. ✅ TaskId 唯一递增，不再重复
2. ✅ 任务完成后列表状态立即更新为 SUCCEEDED
3. ✅ 应用重启后所有数据保留
4. ✅ Metrics 接口无 400 错误
5. ✅ 前端界面与后端状态同步无延迟

---

## 回滚方案（如需快速重置）

```bash
# 1. 删除数据库文件
rm ./data/tripagent.mv.db

# 2. 临时切换到 dev 配置
java -jar app.jar --spring.profiles.active=dev

# 3. 应用重启后恢复正常
```

---

**修复完成时间**: 2026/04/28  
**验证状态**: ✅ 代码编译无错误  
**建议**: 立即重启后端应用并验证部署检查清单
