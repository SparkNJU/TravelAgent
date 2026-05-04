# TaskId 重复与数据库持久化问题修复

## 问题症状
- 多个任务拥有相同的 taskId（特别是 taskId=1）
- 任务状态更新混乱，不同任务的状态互相影响
- 应用重启后之前的数据丢失

## 根本原因
之前的配置使用 **H2 内存数据库**（`jdbc:h2:mem:tripagent`）：
1. 每次应用启动时都会清空所有数据
2. IDENTITY 序列被重置为 1
3. 新创建的任务重新获得 taskId=1，导致 id 冲突
4. 基于 taskId 的状态更新可能更新到错误的任务

## 解决方案

### 1. 主配置（application.yml）- 默认使用文件数据库
改为 H2 文件数据库持久化方式：
```yaml
datasource:
  url: jdbc:h2:./data/tripagent;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false
```
- 数据存储在 `./data/tripagent.mv.db` 文件中
- 应用重启后数据仍然保留
- IDENTITY 序列连续递增，不会重复
- 确保 taskId 的唯一性

### 2. 开发环境配置（application-dev.yml）
如需在开发阶段快速重置数据库，使用：
```bash
java -jar app.jar --spring.profiles.active=dev
```
- 恢复为内存数据库模式
- 每次重启清空数据，快速迭代

### 3. 生产环境配置（application-prod.yml）
```bash
java -jar app.jar --spring.profiles.active=prod
```
- 使用文件数据库保留数据
- 关闭自动 schema 初始化（防止表被意外重建）

## 修复效果
✅ TaskId 自增且唯一  
✅ 应用重启后数据保留  
✅ 状态更新准确定位到正确的任务  
✅ 解决 RUNNING 状态更新混乱问题

## 迁移步骤
1. 删除旧的 `./data/tripagent.mv.db` 文件（如果存在）
2. 重启后端应用
3. 观察任务 id 是否正常递增（1, 2, 3, ...）
4. 验证任务状态更新是否正确

## 常见问题

### Q: 如何完全清空数据库？
A: 停止应用，删除 `./data/tripagent.mv.db` 文件，重启应用。

### Q: 想在测试时每次快速重置？
A: 临时切换到 dev 环境：
```bash
java -jar app.jar --spring.profiles.active=dev
```
或在 IDE 中设置 `SPRING_PROFILES_ACTIVE=dev`。

### Q: 为什么仍然保持内存 H2 作为选项？
A: 单元测试和快速开发迭代时有用，不需要磁盘 I/O，但不适合长期运行的测试或演示。
