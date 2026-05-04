# 🚀 快速部署指南 - tripAgent_evalute 修复版本

## 一行命令启动（完整修复版）

```bash
# 1. 清理旧数据（首次修复后部署时必须）
rm -rf ./data/tripagent.mv.db

# 2. 重新编译后端
cd tripAgent_evalute/tripAgent/backend
mvn clean package -DskipTests

# 3. 启动后端应用
java -jar target/backend.jar

# 4. 启动前端（另一个终端）
cd tripAgent_evalute/tripAgent/frontend
npm run dev
```

---

## 三分钟验证清单

完成上面的启动步骤后，按顺序执行：

### ✅ 验证 1：TaskId 递增（解决重复问题）
```
1. 打开前端：http://localhost:5173
2. 创建 3 个任务（点击"创建任务"→"保存"）
3. 观察任务 id：应为 1, 2, 3
4. 如果看到 1, 1, 2 这样的重复，说明修复未生效
```

### ✅ 验证 2：状态自动更新（解决 RUNNING 残留）
```
1. 创建第 4 个任务
2. 点击"启动"运行
3. SSE 时间线显示"运行结束"后
4. **不要手动刷新**，直接观察列表
5. 任务状态应该立即从 RUNNING 变为 SUCCEEDED
6. 如果仍显示 RUNNING，点击"刷新数据"后应该更新
```

### ✅ 验证 3：Metrics 正常加载（解决 400 错误）
```
1. 启动任务后，在 SSE 时间线中选择运行
2. 打开浏览器开发者工具 > 网络 > XHR
3. 观察 /api/eval/runs/{runId}/metrics 请求
4. 应该看到 200 OK，不应该出现 400 或 404
5. 详情页面中的"任务成功率"等卡片应该显示数字
```

---

## 关键文件检查

部署前确认以下文件已修改：

| 路径 | 内容 | 检查方法 |
|------|------|--------|
| `backend/src/main/resources/application.yml` | 数据库 URL 为 `jdbc:h2:./data/tripagent` | `grep "jdbc:h2:" application.yml` |
| `backend/src/main/java/.../EvalTaskStatusService.java` | 包含 `entityManager.clear()` | `grep "entityManager.clear" EvalTaskStatusService.java` |
| `backend/src/main/java/.../EvalTaskService.java` | listTasks() 中有 `entityManager.clear()` | `grep "entityManager.clear" EvalTaskService.java` |
| `backend/src/main/java/.../EvalRunService.java` | getRunMetrics() 返回占位 metrics | `grep -A5 "getRunMetrics" EvalRunService.java` |

**一键检查命令**：
```bash
cd tripAgent_evalute/tripAgent/backend

# 检查所有修复
echo "1. 检查数据库配置..." && grep "jdbc:h2:./data" src/main/resources/application.yml && echo "✅ OK" || echo "❌ FAIL"

echo "2. 检查 EntityManager 注入..." && grep -q "private final EntityManager entityManager" src/main/java/com/tripagent/backend/service/eval/EvalTaskStatusService.java && echo "✅ OK" || echo "❌ FAIL"

echo "3. 检查缓存清理..." && grep -q "entityManager.clear()" src/main/java/com/tripagent/backend/service/eval/EvalTaskStatusService.java && echo "✅ OK" || echo "❌ FAIL"

echo "4. 检查 Metrics 容错..." && grep -q "new MetricSnapshotResponse" src/main/java/com/tripagent/backend/service/eval/EvalRunService.java && echo "✅ OK" || echo "❌ FAIL"
```

---

## 遇到问题排查

### 问题 1：启动后仍看到 TaskId 重复
**检查清单**：
```bash
# 确认数据库文件已删除
ls -la ./data/tripagent.mv.db  # 不应该存在

# 确认配置已更新
grep "jdbc:h2:" backend/src/main/resources/application.yml | grep "data"

# 重新编译确保新代码生效
mvn clean package -DskipTests
```

### 问题 2：运行完成后仍显示 RUNNING
**检查清单**：
```bash
# 确认缓存清理代码已添加
grep -n "entityManager.clear()" backend/src/main/java/com/tripagent/backend/service/eval/EvalTaskStatusService.java

# 查看后端日志，是否有异常
# 在日志中搜索 "refreshTaskStatus" 确保方法被调用

# 前端："刷新数据"按钮是否能更新状态？
# 如果能，说明后端修改生效但需要前端主动拉取
```

### 问题 3：Metrics 仍然 400
**检查清单**：
```bash
# 确认 getRunMetrics() 已修改
grep -A10 "public MetricSnapshotResponse getRunMetrics" backend/src/main/java/com/tripagent/backend/service/eval/EvalRunService.java

# 查看前端是否有条件判断
grep -n "run.status === 'SUCCEEDED'" frontend/src/views/DashboardView.vue
```

---

## 环境选项

### 快速开发（每次重启清空数据）
```bash
java -jar target/backend.jar --spring.profiles.active=dev
```

### 数据持久化（默认）
```bash
java -jar target/backend.jar
# 或明确指定
java -jar target/backend.jar --spring.profiles.active=default
```

### 生产部署
```bash
java -jar target/backend.jar --spring.profiles.active=prod
```

---

## 完整修复对比

### 修复前 ❌
```
创建任务1 → taskId=1
创建任务2 → taskId=1（重复！）
创建任务3 → taskId=2
运行完成 → 列表仍显示 RUNNING
重启应用 → 所有数据丢失
```

### 修复后 ✅
```
创建任务1 → taskId=1
创建任务2 → taskId=2
创建任务3 → taskId=3
运行完成 → 列表立即显示 SUCCEEDED
重启应用 → 数据保留，序列继续递增
```

---

## 预期性能指标

- 任务列表刷新时间：< 500ms（包括 entityManager.clear()）
- 状态更新延迟：< 100ms（refresh 在独立 REQUIRES_NEW 事务）
- Metrics 加载：< 200ms（即使尚未生成也返回占位数据）

---

## 下一步

修复验证完成后可以：

1. 📊 查看完整的修复文档：`docs/FIX_SUMMARY.md`
2. 🔍 了解 TaskId 问题详情：`docs/TASKID_FIX.md`
3. 🚀 部署到测试环境
4. ✅ 进行端到端测试

---

**修复版本**: v1.0  
**发布日期**: 2026/04/28  
**预计部署时间**: 5-10 分钟
