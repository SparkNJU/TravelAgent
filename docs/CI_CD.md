# CI/CD 文档

## 1. 概述

本文档定义了用于 Agent、Backend、Frontend 与 Evaluator 各模块的持续集成（CI）与持续部署（CD）流程。该流程确保代码质量、自动化测试与可靠交付。

## 2. CI/CD 架构设计

### 2.1 流程总览

```
┌─────────────┐
│ 代码提交    │
│ (Git Push)  │
└──────┬──────┘
       │
       ▼
┌─────────────────────┐
│ 触发 CI Pipeline    │
│ (GitHub Actions)    │
└──────┬──────────────┘
       │
       ├─ 代码静态检查（Lint）
       ├─ 单元测试（Unit Tests）
       ├─ 集成测试（Integration Tests）
       ├─ 构建制品（Build Artifacts）
       │
       ▼
┌─────────────────────┐
│ 发布制品到仓库      │
│ (Docker Registry)   │
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│ 触发 CD Pipeline    │
│ (部署到目标环境)    │
└──────┬──────────────┘
       │
       ├─ 开发环境部署（Dev）
       ├─ 测试环境部署（Test）
       ├─ 生产环境部署（Prod）
       │
       ▼
┌─────────────────────┐
│ 部署后验证          │
│ (Smoke Tests)       │
└─────────────────────┘
```

## 3. 技术栈与工具链

| 组件 | 工具 | 版本 | 说明 |
|------|------|------|------|
| VCS | Git/GitHub | - | 版本控制 |
| CI/CD 引擎 | GitHub Actions | - | 自动化流程 |
| 构建工具 | Maven (Backend) | 3.8+ | Java 构建 |
| | npm/yarn (Frontend) | 最新 | Node.js 构建 |
| | Python pip | 最新 | Python 依赖 |
| 容器化 | Docker | 20.10+ | 容器镜像 |
| 镜像仓库 | Docker Hub / ACR | - | 镜像存储 |
| 部署工具 | Kubernetes / Docker Compose | - | 编排部署 |
| 监控 | Prometheus + Grafana | - | 性能监控 |
| 日志 | ELK Stack / Loki | - | 日志聚合 |

## 4. CI Pipeline 定义

### 4.1 触发条件

- **主分支**：`main` / `master` 任何 push 或 PR 都触发完整 CI
- **开发分支**：`develop` 分支 PR 触发完整 CI
- **功能分支**：`feature/*` 分支 PR 触发快速检查（Lint + 单元测试）
- **标签**：`v*` 标签 push 触发完整 CI + 自动发布

### 4.2 Backend (Java/Spring Boot) Pipeline

#### 4.2.1 执行步骤

```yaml
name: Backend CI

on:
  push:
    branches: [main, develop]
    paths: [backend/**, .github/workflows/backend-ci.yml]
  pull_request:
    branches: [main, develop]
    paths: [backend/**]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: maven
      
      - name: Lint & Code Quality
        run: |
          cd backend
          mvn clean verify -DskipTests
      
      - name: Run Unit Tests
        run: |
          cd backend
          mvn test
      
      - name: Run Integration Tests
        run: |
          cd backend
          mvn verify -DskipUnitTests
      
      - name: Build Package
        run: |
          cd backend
          mvn clean package -DskipTests
      
      - name: Build Docker Image
        run: |
          cd backend
          docker build -t myapp/backend:${{ github.sha }} .
          docker tag myapp/backend:${{ github.sha }} myapp/backend:latest
      
      - name: Push Docker Image
        run: |
          echo "${{ secrets.DOCKER_PASSWORD }}" | docker login -u "${{ secrets.DOCKER_USERNAME }}" --password-stdin
          docker push myapp/backend:${{ github.sha }}
          docker push myapp/backend:latest
      
      - name: Upload Test Reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: backend-test-reports
          path: backend/target/surefire-reports
```

#### 4.2.2 质量门禁

- ✓ 所有单元测试通过
- ✓ 集成测试通过（基于 TestContainers）
- ✓ 代码覆盖率 ≥ 70%（关键路径 ≥ 80%）
- ✓ Spotbugs/CheckStyle 无严重问题
- ✓ 依赖安全扫描通过（无已知 CVE）

### 4.3 Frontend (Vue/Vite) Pipeline

#### 4.3.1 执行步骤

```yaml
name: Frontend CI

on:
  push:
    branches: [main, develop]
    paths: [frontend/**, .github/workflows/frontend-ci.yml]
  pull_request:
    branches: [main, develop]
    paths: [frontend/**]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Node
        uses: actions/setup-node@v3
        with:
          node-version: '18'
          cache: npm
      
      - name: Install Dependencies
        run: |
          cd frontend
          npm ci
      
      - name: Lint
        run: |
          cd frontend
          npm run lint
      
      - name: Type Check
        run: |
          cd frontend
          npm run type-check
      
      - name: Run Unit Tests
        run: |
          cd frontend
          npm run test:unit
      
      - name: Build
        run: |
          cd frontend
          npm run build
      
      - name: Build Docker Image
        run: |
          cd frontend
          docker build -t myapp/frontend:${{ github.sha }} .
      
      - name: Push Docker Image
        run: |
          echo "${{ secrets.DOCKER_PASSWORD }}" | docker login -u "${{ secrets.DOCKER_USERNAME }}" --password-stdin
          docker push myapp/frontend:${{ github.sha }}
```

#### 4.3.2 质量门禁

- ✓ ESLint 检查无错误
- ✓ TypeScript 编译无错误
- ✓ 单元测试通过
- ✓ 构建成功产出 dist/
- ✓ Bundle 大小无异常增长（< 10% 增幅警告）

### 4.4 Agent (Python) Pipeline

#### 4.4.1 执行步骤

```yaml
name: Agent CI

on:
  push:
    branches: [main, develop]
    paths: [agent/**, .github/workflows/agent-ci.yml]
  pull_request:
    branches: [main, develop]
    paths: [agent/**]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.9'
          cache: pip
      
      - name: Install Dependencies
        run: |
          cd agent
          pip install -r requirements.txt
      
      - name: Lint & Format Check
        run: |
          cd agent
          pip install pylint black flake8
          black --check .
          flake8 .
      
      - name: Type Check
        run: |
          cd agent
          pip install mypy
          mypy .
      
      - name: Run Unit Tests
        run: |
          cd agent
          pip install pytest pytest-cov
          pytest --cov=. --cov-report=xml
      
      - name: Build Docker Image
        run: |
          cd agent
          docker build -t myapp/agent:${{ github.sha }} .
      
      - name: Push Docker Image
        run: |
          echo "${{ secrets.DOCKER_PASSWORD }}" | docker login -u "${{ secrets.DOCKER_USERNAME }}" --password-stdin
          docker push myapp/agent:${{ github.sha }}
      
      - name: Upload Coverage
        uses: codecov/codecov-action@v3
        with:
          files: ./agent/coverage.xml
```

#### 4.4.2 质量门禁

- ✓ pylint score ≥ 8.0
- ✓ black 格式检查通过
- ✓ flake8 无错误
- ✓ mypy 类型检查通过
- ✓ pytest 单元测试通过，覆盖率 ≥ 70%

### 4.5 Evaluator Pipeline

#### 4.5.1 执行步骤

```yaml
name: Evaluator CI

on:
  push:
    branches: [main, develop]
    paths: [evaluator/**, .github/workflows/evaluator-ci.yml]
  pull_request:
    branches: [main, develop]
    paths: [evaluator/**]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.9'
          cache: pip
      
      - name: Install Dependencies
        run: |
          cd evaluator
          pip install -r requirements.txt
      
      - name: Lint & Format Check
        run: |
          cd evaluator
          pip install pylint black flake8
          black --check .
          flake8 .
      
      - name: Type Check
        run: |
          cd evaluator
          pip install mypy
          mypy .
      
      - name: Run Unit Tests
        run: |
          cd evaluator
          pip install pytest pytest-cov
          pytest --cov=. --cov-report=xml
      
      - name: Build Docker Image
        run: |
          cd evaluator
          docker build -t myapp/evaluator:${{ github.sha }} .
      
      - name: Push Docker Image
        run: |
          echo "${{ secrets.DOCKER_PASSWORD }}" | docker login -u "${{ secrets.DOCKER_USERNAME }}" --password-stdin
          docker push myapp/evaluator:${{ github.sha }}
```

#### 4.5.2 质量门禁

- ✓ pylint score ≥ 8.0
- ✓ black 格式检查通过
- ✓ flake8 无错误
- ✓ mypy 类型检查通过
- ✓ pytest 单元测试通过，覆盖率 ≥ 70%
- ✓ 评估引擎核心指标验证通过

## 5. CD Pipeline 定义

### 5.1 部署环境

| 环境 | 触发条件 | 部署时机 | 回滚策略 |
|------|---------|---------|---------|
| Dev | 合并至 develop | 自动 | 10 分钟内自动回滚（若监控告警） |
| Test | 合并至 main | 手动批准后自动 | 手动 |
| Prod | Tag v* | 手动批准后自动 | 手动（灰度发布） |

### 5.2 部署流程（Kubernetes 示例）

```yaml
name: Deploy to Production

on:
  push:
    tags: ['v*']

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Get Docker Images
        run: |
          # 拉取 CI 阶段构建的镜像
          docker pull myapp/backend:${{ github.sha }}
          docker pull myapp/frontend:${{ github.sha }}
          docker pull myapp/agent:${{ github.sha }}
      
      - name: Setup Kubernetes Context
        uses: azure/setup-kubectl@v3
        with:
          version: 'latest'
      
      - name: Configure kubectl
        run: |
          mkdir -p $HOME/.kube
          echo "${{ secrets.KUBE_CONFIG }}" | base64 -d > $HOME/.kube/config
      
      - name: Deploy with Helm / Kustomize
        run: |
          # 示例使用 Helm
          helm upgrade --install myapp ./helm \
            --set backend.image=myapp/backend:${{ github.sha }} \
            --set frontend.image=myapp/frontend:${{ github.sha }} \
            --set agent.image=myapp/agent:${{ github.sha }} \
            --namespace prod
      
      - name: Wait for Deployment
        run: |
          kubectl rollout status deployment/myapp-backend -n prod --timeout=5m
          kubectl rollout status deployment/myapp-frontend -n prod --timeout=5m
      
      - name: Run Smoke Tests
        run: |
          # 调用 API 端点验证服务可用
          curl -f http://myapp.prod.example.com/api/health || exit 1
      
      - name: Notify on Success
        uses: slackapi/slack-github-action@v1
        with:
          webhook-url: ${{ secrets.SLACK_WEBHOOK }}
          payload: |
            {
              "text": "✅ Production deployment successful: ${{ github.ref }}"
            }
      
      - name: Notify on Failure
        if: failure()
        uses: slackapi/slack-github-action@v1
        with:
          webhook-url: ${{ secrets.SLACK_WEBHOOK }}
          payload: |
            {
              "text": "❌ Production deployment failed: ${{ github.ref }}"
            }
```

### 5.3 灰度发布策略

```yaml
# 使用 Istio/Flagger 进行灰度部署
apiVersion: flagger.app/v1beta1
kind: Canary
metadata:
  name: myapp-backend
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: myapp-backend
  service:
    port: 8080
  analysis:
    interval: 1m
    threshold: 5
    metrics:
      - name: error-rate
        thresholdRange:
          max: 5
      - name: latency
        thresholdRange:
          max: 500
  skipAnalysis: false
  maxWeight: 50
  stepWeight: 10
```

## 6. 监控与告警

### 6.1 部署前监控

```yaml
- Alert: HighErrorRate
  Condition: error_rate > 5% for 5 minutes
  Action: Rollback to previous version
  
- Alert: HighLatency
  Condition: p99_latency > 1000ms for 5 minutes
  Action: Scale up instances
  
- Alert: PodCrashLooping
  Condition: pod restart count > 3 in 10 minutes
  Action: Pause deployment, notify team
```

### 6.2 Prometheus 采集指标

```yaml
scrape_configs:
  - job_name: 'myapp-backend'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
  
  - job_name: 'myapp-frontend'
    static_configs:
      - targets: ['localhost:3000']
```

### 6.3 日志聚合

使用 ELK Stack 或 Loki：

```bash
# Loki 配置
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: backend
    static_configs:
      - targets:
          - localhost
        labels:
          job: backend
          __path__: /var/log/backend/*.log
```

## 7. 环境变量与配置管理

### 7.1 环境变量规范

| 环境变量类型 | 示例 | 说明 |
|-------------|------|------|
| 数据库配置 | `DB_HOST`, `DB_PORT`, `DB_NAME` | 数据库连接信息 |
| API Key | `API_KEY`, `AUTH_TOKEN` | 第三方服务凭证 |
| 应用配置 | `APP_ENV`, `LOG_LEVEL`, `PORT` | 应用运行参数 |
| 外部服务 | `REDIS_URL`, `MQ_HOST` | 中间件连接信息 |

### 7.2 配置管理策略

```yaml
# 配置文件分层结构
config/
├── .env.common          # 通用配置（所有环境共享）
├── .env.dev             # 开发环境
├── .env.test            # 测试环境
├── .env.staging         # 预发布环境
└── .env.prod            # 生产环境（不提交代码库）
```

**配置优先级**（从高到低）：
1. 命令行参数
2. 容器环境变量（K8s ConfigMap/Secret）
3. 环境特定配置文件
4. 通用配置文件

## 8. 安全性考虑

### 8.1 Secret 管理

```yaml
# GitHub Secrets（需在 Settings 中配置）
- DOCKER_USERNAME
- DOCKER_PASSWORD
- KUBE_CONFIG
- SLACK_WEBHOOK
- DATABASE_PASSWORD
- API_KEYS
```

### 8.2 代码安全扫描

```yaml
- name: SAST with SonarQube
  run: |
    sonar-scanner \
      -Dsonar.projectKey=myapp \
      -Dsonar.host.url=${{ secrets.SONAR_HOST }} \
      -Dsonar.login=${{ secrets.SONAR_TOKEN }}

- name: Dependency Check
  uses: aquasecurity/trivy-action@master
  with:
    scan-type: 'fs'
    scan-ref: '.'
```

### 8.3 镜像扫描

```yaml
- name: Scan Docker Image
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: 'myapp/backend:${{ github.sha }}'
    format: 'sarif'
    output: 'trivy-results.sarif'
```

### 8.4 安全最佳实践

1. **最小权限原则**：CI/CD 服务账号仅授予必要权限
2. **Secret 轮换**：定期轮换敏感凭证（建议每 90 天）
3. **加密传输**：所有 API 调用使用 HTTPS/TLS
4. **审计日志**：记录所有部署操作与 Secret 访问
5. **IP 白名单**：限制 Kubernetes API 访问来源

## 9. 故障恢复与回滚

### 9.1 自动回滚触发

```yaml
- Condition: Deployment health check fails
  Action: Automatically rollback to previous release
  
- Condition: Error rate > 10% for 2 minutes
  Action: Trigger canary rollback
  
- Condition: Manual rollback request
  Action: Revert to previous Helm release
```

### 9.2 手动回滚命令

```bash
# Kubernetes 回滚
kubectl rollout undo deployment/myapp-backend -n prod

# Helm 回滚
helm rollback myapp 0 --namespace prod

# Docker Compose 回滚
docker-compose down
git checkout <previous-commit>
docker-compose up -d
```

## 10. 性能优化建议

### 9.1 并行化 CI 流程

- 各模块（Backend、Frontend、Agent）独立构建，并行执行
- 单元测试与静态分析并行
- 集成测试可后置执行

### 9.2 缓存策略

```yaml
# Maven 依赖缓存
cache: maven

# npm 包缓存
cache: npm

# Docker 层缓存
docker build --cache-from myapp/backend:latest .
```

### 9.3 构建时间目标

| 模块 | 目标时间 | 实际优化后 |
|------|---------|-----------|
| Backend | < 10 分钟 | 8 分钟（使用缓存） |
| Frontend | < 5 分钟 | 3 分钟（使用缓存） |
| Agent | < 5 分钟 | 3 分钟 |

## 11. 成本控制

### 10.1 构建并发限制

- 免费版 GitHub Actions：最多 20 个并发作业
- 生产环境：限制在核心时间段（工作时间）全量构建

### 10.2 存储优化

- Docker Hub：按需清理旧镜像（保留最近 10 个发布版本）
- GitHub Artifacts：自动过期（30 天）
- 日志存储：按环境与日期归档

## 12. 文档与培训

### 11.1 团队培训内容

1. CI/CD 流程介绍与分支策略
2. 本地开发与提交规范
3. 故障排查与日志查看
4. 部署审批与灰度发布流程

### 11.2 运维手册

- [部署步骤与注意事项](./ops-guide.md)
- [故障排查流程](./troubleshooting.md)
- [监控仪表板](./monitoring.md)

## 13. 迭代计划

### v1.0（当前）
- ✓ 基础 CI Pipeline（Lint、Test、Build）
- ✓ Docker 镜像构建与推送
- ✓ 开发环境自动部署

### v1.1（计划）
- 完整的 Kubernetes 部署
- 灰度发布与自动回滚
- 生产级监控与告警

### v1.2+（远期）
- 多云部署支持（Azure、AWS、GCP）
- 自适应扩缩容
- 成本优化与资源利用分析

---

## 14. CI/CD 最佳实践

### 14.1 代码质量

1. **分支策略**：采用 Git Flow 或 Trunk Based Development
2. **代码审查**：所有 PR 必须经过至少一位开发者审查
3. **提交规范**：遵循 Conventional Commits 规范
4. **依赖更新**：定期更新依赖，使用 Dependabot 自动检测

### 14.2 Pipeline 设计

1. **原子化**：每个任务职责单一，便于复用
2. **可复用性**：使用 Actions 或模板共享通用逻辑
3. **并行化**：独立模块并行执行，缩短总耗时
4. **幂等性**：Pipeline 可重复执行，结果一致

### 14.3 部署策略

1. **蓝绿部署**：零停机切换，快速回滚
2. **灰度发布**：逐步放量，降低风险
3. **金丝雀发布**：小流量验证，自动熔断
4. **滚动更新**：渐进式部署，平滑过渡

### 14.4 监控与可观测性

1. **全链路追踪**：追踪请求从入口到出口的完整路径
2. **指标标准化**：统一指标命名规范
3. **告警分级**：区分 P0-P3 告警级别，合理响应
4. **演练计划**：定期进行故障演练（Chaos Engineering）

---

## 14. CI/CD 最佳实践

### 14.1 代码质量

1. **分支策略**：采用 Git Flow 或 Trunk Based Development
2. **代码审查**：所有 PR 必须经过至少一位开发者审查
3. **提交规范**：遵循 Conventional Commits 规范
4. **依赖更新**：定期更新依赖，使用 Dependabot 自动检测

### 14.2 Pipeline 设计

1. **原子化**：每个任务职责单一，便于复用
2. **可复用性**：使用 Actions 或模板共享通用逻辑
3. **并行化**：独立模块并行执行，缩短总耗时
4. **幂等性**：Pipeline 可重复执行，结果一致

### 14.3 部署策略

1. **蓝绿部署**：零停机切换，快速回滚
2. **灰度发布**：逐步放量，降低风险
3. **金丝雀发布**：小流量验证，自动熔断
4. **滚动更新**：渐进式部署，平滑过渡

### 14.4 监控与可观测性

1. **全链路追踪**：追踪请求从入口到出口的完整路径
2. **指标标准化**：统一指标命名规范
3. **告警分级**：区分 P0-P3 告警级别，合理响应
4. **演练计划**：定期进行故障演练（Chaos Engineering）

---

**最后更新**：2026-05-10  
**维护者**：DevOps 团队  
**反馈渠道**：通过 GitHub Issues 或团队 Slack 反馈改进建议