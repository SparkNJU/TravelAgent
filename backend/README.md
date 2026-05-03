# Backend - Travel Planning API

Spring Boot 后端服务，负责用户认证、目的地数据、行程生成接口，以及到 Python Agent 的桥接调用。

## 1. 技术栈

- Java 17
- Spring Boot 4.x
- Spring Web + Spring Data JPA
- MySQL 8.x
- Maven Wrapper (`mvnw.cmd`)

## 2. 目录与架构

```text
backend/
  src/main/java/org/example/backend/
    controller/    # API 控制器层
    service/       # 业务层（含 Agent 桥接）
    repository/    # 数据访问层
    entity/        # JPA 实体
    dto/           # 接口入参/出参对象
  src/main/resources/
    application.properties
    schema.sql
    data.sql
  sql/
    init-database.sql
```

分层关系：
- `controller` 接收 HTTP 请求并做参数校验
- `service` 组织业务逻辑
- `repository` 读写数据库
- `TripAssistantService` 通过 HTTP 调用 Agent：`app.agent.base-url`

## 3. 环境准备

1. 安装 JDK 17，并确保 `java -version` 可用
2. 准备 MySQL 8.x（本地可访问）
3. 在 MySQL 中执行建库脚本

```sql
SOURCE sql/init-database.sql;
```

## 4. 配置说明

配置文件：`src/main/resources/application.properties`

关键项：
- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `app.agent.base-url`（默认 `http://localhost:8000/api/trip/plan`）

当前数据库策略：
- `spring.jpa.hibernate.ddl-auto=create-drop`
- 含义：应用启动自动建表、停止时可能删除表结构
- 开发影响：重启后数据可能被清理，不适合持久化数据验证

`schema.sql` 与 `data.sql`：
- 用于初始化结构/演示数据
- 当 `create-drop` 生效时，请以当前实际启动行为为准

## 5. 启动方式

在 `backend` 目录执行：

```powershell
.\mvnw.cmd spring-boot:run
```

默认端口：`8080`

## 6. 主要接口

认证：
- `POST /api/auth/register`
- `POST /api/auth/login`

旅行相关：
- `GET /api/travel/destinations/popular`
- `GET /api/travel/destinations/search?keyword=...`
- `POST /api/travel/plan/generate`

Agent 对话：
- `POST /api/assistant/chat`（`multipart/form-data`，支持文件上传）

## 7. 与 Agent 集成说明

`TripAssistantController` 调用 `TripAssistantService`，后者将请求转换为 JSON 后发给 Agent：
- 目标接口：`/api/trip/plan`
- 包含字段：`query`、`user_id`、可选文件 base64

若 Agent 不可用：
- 后端返回本地 fallback 旅行草案，便于前端继续调试

## 8. 快速自检

1. 访问登录接口并验证可返回 token
2. 调用 `GET /api/travel/destinations/popular` 可返回目的地列表
3. 在 Agent 已启动情况下，调用 `/api/assistant/chat` 可返回 `markdown/images/sources`

## 9. 常见问题

- 启动报数据库连接错误：先检查 MySQL 是否启动、用户名密码是否匹配
- `/api/assistant/chat` 慢或失败：检查 Agent 进程、`app.agent.base-url`、网络连通
- 每次重启数据丢失：与 `ddl-auto=create-drop` 配置有关
