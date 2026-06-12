# 系统维护文档

> 版本：v1.0  
> 更新时间：2026-05-10

---

## 目录

- [系统架构](#系统架构)
- [环境配置](#环境配置)
- [部署指南](#部署指南)
- [监控与日志](#监控与日志)
- [备份与恢复](#备份与恢复)
- [故障排查](#故障排查)
- [性能优化](#性能优化)
- [安全维护](#安全维护)
- [版本更新](#版本更新)

---

## 系统架构

### 整体架构

### 技术栈

**前端：**
- Vue.js 3
- Vite
- Axios

**后端：**
- Java 17
- Spring Boot 4.x
- Spring Data JPA
- MySQL 8.x

**Agent服务：**
- Python 3.10+
- FastAPI
- Uvicorn
- LangChain

---

## 环境配置

### 后端环境配置

**配置文件：** `backend/src/main/resources/application.properties`

```properties
# 数据库配置
spring.datasource.url=jdbc:mysql://localhost:3306/travel_planning
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA配置
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# Agent服务配置
app.agent.base-url=http://localhost:8000/api/agent/chat

# 服务器配置
server.port=8080
server.servlet.context-path=/

# 文件上传配置
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=10MB

# CORS配置
spring.web.cors.allowed-origins=*
spring.web.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
spring.web.cors.allowed-headers=*

# 日志配置
logging.level.org.example.backend=INFO
logging.level.org.springframework.web=INFO
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
```

### Agent环境配置

**配置文件：** `agent/.env`

```env
# LLM配置
LLM_API_KEY=your_llm_api_key
LLM_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4

# 搜索引擎配置
SERPER_API_KEY=your_serper_api_key

# 天气API配置
WEATHER_API_KEY=your_weather_api_key

# 服务配置
HOST=0.0.0.0
PORT=8000
LOG_LEVEL=INFO
```

---

## 部署指南

### 本地开发环境部署

#### 1. 数据库初始化

```bash
# 启动MySQL服务
# Windows
net start mysql

# 创建数据库
mysql -u root -p
CREATE DATABASE travel_planning CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 执行初始化脚本
mysql -u root -p travel_planning < backend/sql/init-database.sql
```

#### 2. 后端服务部署

```bash
cd backend

# 使用Maven Wrapper构建
.\mvnw.cmd clean package

# 运行服务
.\mvnw.cmd spring-boot:run

# 或者运行打包后的jar
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

#### 3. Agent服务部署

```bash
cd agent

# 创建虚拟环境
python -m venv .venv

# 激活虚拟环境
.\.venv\Scripts\Activate.ps1

# 安装依赖
pip install -r requirements.txt

# 配置环境变量
# 复制.env.example为.env并填写配置

# 启动服务
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

#### 4. 前端服务部署

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build
```

### 生产环境部署

#### 1. 使用Docker部署

**后端Dockerfile：**
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Agent Dockerfile：**
```dockerfile
FROM python:3.10-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

**docker-compose.yml：**
```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root_password
      MYSQL_DATABASE: travel_planning
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    depends_on:
      - mysql
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/travel_planning
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: root_password

  agent:
    build: ./agent
    ports:
      - "8000:8000"
    environment:
      LLM_API_KEY: ${LLM_API_KEY}
      SERPER_API_KEY: ${SERPER_API_KEY}

  frontend:
    build: ./frontend
    ports:
      - "3000:80"
    depends_on:
      - backend

volumes:
  mysql_data:
```

**启动命令：**
```bash
docker-compose up -d
```

#### 2. 使用Nginx反向代理

**nginx.conf：**
```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    location / {
        root /var/www/frontend;
        try_files $uri $uri/ /index.html;
    }

    # 后端API代理
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # Agent服务代理
    location /agent/ {
        proxy_pass http://localhost:8000;
        proxy_set_header Host $host;
        proxy_buffering off;
        proxy_cache off;
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
    }
}
```

---

## 监控与日志

### 日志管理

#### 后端日志

**日志配置：** `backend/src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="LOG_PATH" value="logs"/>
    
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/backend.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/backend.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

#### Agent日志

**日志配置：** `agent/main.py`

```python
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('logs/agent.log'),
        logging.StreamHandler()
    ]
)
```

### 监控指标

#### 关键指标监控

**后端监控指标：**
- JVM内存使用率
- CPU使用率
- 线程池状态
- 数据库连接池状态
- API响应时间
- 错误率

**Agent监控指标：**
- 请求处理时间
- LLM调用次数
- 工具调用成功率
- SSE连接数
- Token使用量

#### 监控工具推荐

**Prometheus + Grafana：**
```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
  
  - job_name: 'agent'
    static_configs:
      - targets: ['localhost:8000']
```

---

## 备份与恢复

### 数据库备份

#### 自动备份脚本

**backup.sh：**
```bash
#!/bin/bash

BACKUP_DIR="/backups/mysql"
DATE=$(date +%Y%m%d_%H%M%S)
DB_NAME="travel_planning"
DB_USER="root"
DB_PASS="your_password"

mkdir -p $BACKUP_DIR

mysqldump -u$DB_USER -p$DB_PASS $DB_NAME | gzip > $BACKUP_DIR/backup_$DATE.sql.gz

# 删除30天前的备份
find $BACKUP_DIR -name "backup_*.sql.gz" -mtime +30 -delete

echo "Backup completed: backup_$DATE.sql.gz"
```

#### 定时任务配置

```bash
# 编辑crontab
crontab -e

# 每天凌晨2点执行备份
0 2 * * * /path/to/backup.sh
```

### 数据库恢复

```bash
gunzip < backup_20260510_020000.sql.gz | mysql -u root -p travel_planning
```

### 文件备份

**用户上传文件备份：**
```bash
# 备份上传目录
tar -czf uploads_backup_$(date +%Y%m%d).tar.gz /path/to/uploads

# 使用rsync同步到远程服务器
rsync -avz /path/to/uploads/ user@remote-server:/backups/uploads/
```

---

## 故障排查

### 常见问题

#### 1. 数据库连接失败

**症状：**

java.sql.SQLException: Access denied for user 'root'@'localhost'

**排查步骤：**
1. 检查MySQL服务是否启动
2. 验证数据库用户名和密码
3. 检查数据库是否存在
4. 验证网络连接

**解决方案：**
```bash
# 检查MySQL服务
systemctl status mysql

# 重启MySQL服务
systemctl restart mysql

# 重新创建用户
mysql -u root -p
CREATE USER 'root'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost';
FLUSH PRIVILEGES;
```

#### 2. Agent服务无响应

**症状：**
Connection refused to localhost:8000


**排查步骤：**
1. 检查Agent服务是否启动
2. 查看Agent服务日志
3. 检查端口是否被占用
4. 验证环境变量配置

**解决方案：**
```bash
# 检查服务状态
curl http://localhost:8000/health

# 查看日志
tail -f logs/agent.log

# 重启服务
pkill -f uvicorn
uvicorn main:app --host 0.0.0.0 --port 8000
```

#### 3. LLM API调用失败

**症状：**
Error: File size exceeds limit


**排查步骤：**
1. 检查文件大小限制配置
2. 验证磁盘空间
3. 检查文件格式

**解决方案：**
```properties
# application.properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=20MB
```

#### 5. SSE连接中断

**症状：**
前端无法接收流式数据

**排查步骤：**
1. 检查Nginx代理配置
2. 验证超时设置
3. 查看浏览器控制台错误

**解决方案：**
```nginx
# nginx.conf
location /agent/ {
    proxy_pass http://localhost:8000;
    proxy_buffering off;
    proxy_cache off;
    proxy_set_header Connection '';
    proxy_http_version 1.1;
    chunked_transfer_encoding off;
    proxy_read_timeout 300s;
}
```

### 性能问题排查

#### 慢查询分析

```sql
-- 启用慢查询日志
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 2;

-- 查看慢查询
SHOW VARIABLES LIKE 'slow_query%';
```

#### JVM性能分析

```bash
# 查看JVM进程
jps -l

# 生成堆转储
jmap -dump:format=b,file=heap.hprof <pid>

# 分析堆转储
jhat heap.hprof
```

---

## 性能优化

### 数据库优化

#### 索引优化

```sql
-- 为常用查询字段添加索引
CREATE INDEX idx_user_id ON travel_plans(user_id);
CREATE INDEX idx_created_at ON community_posts(created_at);
CREATE INDEX idx_post_id ON comments(post_id);
CREATE INDEX idx_user_post ON like_records(user_id, post_id);
```

#### 查询优化

```sql
-- 使用EXPLAIN分析查询
EXPLAIN SELECT * FROM travel_plans WHERE user_id = 1;

-- 优化JOIN查询
SELECT p.*, u.username, u.profile_pic_url
FROM community_posts p
JOIN users u ON p.user_id = u.id
WHERE p.created_at > DATE_SUB(NOW(), INTERVAL 7 DAY)
ORDER BY p.created_at DESC
LIMIT 20;
```

### 后端优化

#### 连接池配置

```properties
# application.properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

#### 缓存配置

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1000));
        return cacheManager;
    }
}
```

### Agent优化

#### 并发处理

```python
import asyncio
from concurrent.futures import ThreadPoolExecutor

executor = ThreadPoolExecutor(max_workers=10)

async def process_request(request):
    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(executor, heavy_computation, request)
    return result
```

#### Token优化

```python
# 使用流式输出减少等待时间
async def stream_response():
    async for chunk in llm.stream(prompt):
        yield chunk
```

---

## 安全维护

### 安全配置

#### 密码加密

```java
@Configuration
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

#### API密钥管理

```bash
# 使用环境变量存储敏感信息
export LLM_API_KEY=your_api_key
export DB_PASSWORD=your_db_password

# 或使用密钥管理服务
# AWS Secrets Manager
# HashiCorp Vault
```

### HTTPS配置

#### 生成SSL证书

```bash
# 使用Let's Encrypt
certbot certonly --standalone -d your-domain.com
```

#### Nginx SSL配置

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;
    
    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
    
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
}
```

### 防护措施

#### SQL注入防护

```java
// 使用参数化查询
@Query("SELECT u FROM User u WHERE u.username = :username")
User findByUsername(@Param("username") String username);
```

#### XSS防护

```java
// 输入验证
@NotBlank
@Size(max = 200)
@Pattern(regexp = "[a-zA-Z0-9 ]*")
private String content;
```

#### CSRF防护

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

---

## 版本更新

### 更新流程

#### 1. 代码更新

```bash
# 拉取最新代码
git pull origin main

# 检查依赖更新
cd backend
./mvnw versions:display-dependency-updates

cd ../agent
pip list --outdated
```

#### 2. 数据库迁移

```sql
-- 创建迁移脚本
-- migrations/V1.1.0__add_new_column.sql
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP;

-- 执行迁移
mysql -u root -p travel_planning < migrations/V1.1.0__add_new_column.sql
```

#### 3. 服务更新

```bash
# 停止服务
systemctl stop backend
systemctl stop agent

# 备份当前版本
cp backend.jar backend.jar.backup

# 部署新版本
cp new-backend.jar backend.jar

# 启动服务
systemctl start backend
systemctl start agent

# 验证服务
curl http://localhost:8080/health
curl http://localhost:8000/health
```

### 回滚策略

#### 数据库回滚

```sql
-- 回滚脚本
-- migrations/rollback/V1.1.0__add_new_column.sql
ALTER TABLE users DROP COLUMN last_login_at;
```

#### 服务回滚

```bash
# 停止服务
systemctl stop backend

# 恢复备份
cp backend.jar.backup backend.jar

# 启动服务
systemctl start backend
```

---

## 附录

### 常用命令

#### 后端

```bash
# 启动服务
./mvnw spring-boot:run

# 构建项目
./mvnw clean package

# 查看日志
tail -f logs/backend.log

# 健康检查
curl http://localhost:8080/actuator/health
```

#### Agent

```bash
# 启动服务
uvicorn main:app --reload

# 查看日志
tail -f logs/agent.log

# 健康检查
curl http://localhost:8000/health
```

#### 数据库

```bash
# 连接数据库
mysql -u root -p travel_planning

# 备份数据库
mysqldump -u root -p travel_planning > backup.sql

# 恢复数据库
mysql -u root -p travel_planning < backup.sql
```

### 联系方式

**技术支持：**
- 邮箱：support@example.com
- 文档：https://docs.example.com
- 问题追踪：https://github.com/example/issues

**紧急联系：** -- 迭代三完善
- 电话：
- 微信：