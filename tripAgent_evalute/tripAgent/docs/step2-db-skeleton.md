# Step 2 交付物：数据库骨架与持久化层（2026-04-24）

## 1. 目标

完成评测模块第二步：数据库骨架、JPA 实体与仓库层，作为第三步 API 开发的基础。

## 2. 已完成内容

1. 持久化依赖与运行配置
- 引入 JPA 与 H2 运行时依赖
- 启用 SQL 初始化
- 关闭 Hibernate 自动建表，统一由 schema.sql 管理

2. 建表脚本
- 新增 `backend/src/main/resources/db/schema.sql`
- 包含以下表：
  - `eval_task`
  - `eval_run`
  - `qa_record`
  - `metric_snapshot`
  - `eval_strategy`
  - `eval_strategy_version`
  - `custom_metric`

3. 领域实体
- `EvalTask`
- `EvalRun`
- `QaRecord`
- `MetricSnapshot`
- `EvalStrategy`
- `EvalStrategyVersion`
- `CustomMetric`

4. 业务枚举
- `TaskStatus`
- `RunStatus`
- `EvaluationMode`
- `EvaluationMethod`
- `CustomMetricType`

5. Repository 层
- `EvalTaskRepository`
- `EvalRunRepository`
- `QaRecordRepository`
- `MetricSnapshotRepository`
- `EvalStrategyRepository`
- `EvalStrategyVersionRepository`
- `CustomMetricRepository`

## 3. 结构落点

后端新增目录：

- `backend/src/main/java/com/tripagent/backend/entity`
- `backend/src/main/java/com/tripagent/backend/entity/enums`
- `backend/src/main/java/com/tripagent/backend/repository`
- `backend/src/main/resources/db`

## 4. 与 Step 1 冻结项对齐情况

1. 数据对象覆盖：已覆盖 Step 1 冻结的全部核心对象。
2. 字段层面：按 MVP 字段落库，复杂结构使用 CLOB 存储 JSON 文本。
3. 可扩展性：策略和自定义指标已具备版本化与规则扩展落点。

## 5. 下一步（Step 3）直接可做

1. 任务 API（创建、列表、详情、更新）
2. 启动执行 API（生成 run）
3. run/records/metrics 查询 API
4. 策略与自定义指标 API

## 6. 验收标准（Step 2）

1. 应用启动时可执行 `schema.sql`。
2. JPA 实体映射无编译错误。
3. Repository 接口可被 Spring 容器扫描。
4. 可进入 Step 3 直接编写 Service/Controller。
