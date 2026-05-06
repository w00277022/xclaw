# XClaw 管理平台

创建、管理和对话 OpenClaw / Hermes 实例的分布式管理平台。

## 项目结构

```
xclaw-platform/
├── frontend/                # Vue 3 前端
│   ├── src/
│   │   ├── api/             # API 封装 (axios)
│   │   ├── router/          # Vue Router 路由
│   │   ├── views/           # 页面组件
│   │   ├── App.vue          # 根组件
│   │   └── main.js          # 入口
│   ├── .env.production      # 生产环境变量
│   ├── Dockerfile
│   ├── nginx.conf           # Nginx 配置模板
│   ├── vite.config.js
│   └── package.json
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/com/xclaw/
│   │   ├── controller/      # REST Controller
│   │   ├── service/         # 业务逻辑
│   │   ├── mapper/          # MyBatis-Plus Mapper
│   │   ├── entity/          # 数据实体
│   │   ├── dto/             # 请求 DTO
│   │   ├── config/          # 配置类
│   │   └── websocket/       # WebSocket 聊天代理
│   ├── src/main/resources/
│   │   ├── application.yml  # 配置文件（支持环境变量）
│   │   └── schema.sql       # H2 数据库 schema
│   ├── Dockerfile
│   └── pom.xml
├── sql/
│   └── init.sql             # MySQL 初始化脚本
├── docker-compose.yml       # Docker Compose 一键部署
└── README.md
```

## 功能

| 模块 | 说明 |
|------|------|
| 创建实例 | 支持 OpenClaw / Hermes 类型，选择部署节点 |
| 实例管理 | 列表查看、启停、删除、状态同步 |
| 实例对话 | WebSocket 实时聊天，消息持久化 |
| 用户管理 | SSO 登录、角色权限、审批流程 |
| 节点管理 | 分布式部署，SSH 远程管理 Agent 节点 |

## 部署方式

### 方式一：本地开发

#### 前置条件
- JDK 17+, Node.js 20+, MySQL 8（可选，默认 H2 内嵌）

#### 1. 初始化数据库（MySQL 模式）
```bash
mysql -u root -p < sql/init.sql
```

#### 2. 启动后端
```bash
cd backend
# H2 模式（无需 MySQL）
mvn spring-boot:run

# MySQL 模式
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/xclaw_platform?useSSL=false\&allowPublicKeyRetrieval=true\&serverTimezone=Asia/Shanghai \
SPRING_DATASOURCE_USERNAME=root \
SPRING_DATASOURCE_PASSWORD=root \
SPRING_DATASOURCE_DRIVER=com.mysql.cj.jdbc.Driver \
mvn spring-boot:run
```

#### 3. 启动前端
```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173

### 方式二：Docker Compose 部署

```bash
# 一键启动（MySQL + Backend + Frontend）
docker-compose up -d

# 自定义配置
XCLAW_HOST=your-server-ip \
XCLAW_REMOTE_ACCESS=true \
OPENCLAW_LLM_API_KEY=your-api-key \
docker-compose up -d
```

访问 http://localhost

**环境变量：**

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_ROOT_PASSWORD` | root | MySQL root 密码 |
| `XCLAW_HOST` | localhost | 平台对外访问地址 |
| `XCLAW_REMOTE_ACCESS` | false | 是否允许远程访问实例 |
| `OPENCLAW_LLM_API_KEY` | - | LLM API Key |

### 方式三：生产部署

#### 1. 数据库
在独立服务器部署 MySQL 8，执行 `sql/init.sql` 初始化。

#### 2. 后端（可执行 JAR）
```bash
cd backend
mvn clean package -DskipTests
java -jar target/xclaw-platform-1.0.0.jar \
  --spring.datasource.url=jdbc:mysql://db-server:3306/xclaw_platform?useSSL=false\&allowPublicKeyRetrieval=true\&serverTimezone=Asia/Shanghai \
  --spring.datasource.username=xclaw \
  --spring.datasource.password=your-password \
  --spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver \
  --xclaw.host=your-server-ip \
  --xclaw.remote-access=true
```

#### 3. 前端（Nginx）
```bash
cd frontend
# 构建生产版本
VITE_API_BASE_URL="" npm run build
# 或指定后端地址（前后端分离部署）
# VITE_API_BASE_URL="https://api.your-domain.com/api" npm run build

# 将 dist/ 目录部署到 Nginx
# 使用 frontend/nginx.conf 作为配置模板
```

**Nginx 配置要点：**
- `/api/` 反向代理到后端地址
- `/ws/` WebSocket 代理到后端
- 移除 `X-Frame-Options` 以支持 iframe 嵌入
- 参考 `frontend/nginx.conf`

## 配置说明

### 后端环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SERVER_PORT` | 8081 | 后端服务端口 |
| `SPRING_DATASOURCE_URL` | H2 内嵌 | 数据库连接 URL |
| `SPRING_DATASOURCE_USERNAME` | sa | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | 空 | 数据库密码 |
| `SPRING_DATASOURCE_DRIVER` | org.h2.Driver | 数据库驱动 |
| `JWT_SECRET` | (内置) | JWT 签名密钥 |
| `XCLAW_HOST` | localhost | 实例访问地址 |
| `XCLAW_REMOTE_ACCESS` | false | 远程访问开关 |
| `OPENCLAW_BACKEND_URL` | - | LLM API 地址 |
| `OPENCLAW_LLM_API_KEY` | - | LLM API Key |
| `OPENCLAW_BACKEND_MODEL` | deepseek-v4-flash | 默认模型 |
| `OPENCLAW_RUNTIME` | /usr/lib/node_modules/openclaw/dist/index.js | OpenClaw 运行时路径 |
| `OPENCLAW_INSTANCE_DIR` | /root/.openclaw-instances | 实例数据目录 |

### 前端环境变量

| 变量 | 说明 |
|------|------|
| `VITE_API_BASE_URL` | 生产环境后端 API 地址，留空则使用 Nginx 代理 |

## 分布式节点

XClaw 支持在多台服务器上部署 OpenClaw 实例：

1. 系统自动创建"本机节点"（host=localhost）
2. 通过管理 API 添加远程节点（需配置 SSH 访问）
3. 创建实例时选择目标节点
4. 后端通过 SSH 在远程节点上创建/启停实例

**节点 API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/nodes | 节点列表 |
| POST | /api/nodes | 添加节点 |
| PUT | /api/nodes/{id} | 更新节点 |
| DELETE | /api/nodes/{id} | 删除节点 |
| POST | /api/nodes/{id}/test | 测试节点连接 |

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/xclaw | 创建实例 |
| GET | /api/xclaw | 实例列表 |
| GET | /api/xclaw/{id} | 实例详情 |
| POST | /api/xclaw/{id}/start | 启动 |
| POST | /api/xclaw/{id}/stop | 停止 |
| DELETE | /api/xclaw/{id} | 删除 |
| POST | /api/xclaw/{id}/sync | 同步状态 |
| GET | /api/xclaw/nodes | 可用节点 |
| GET | /api/chat/{instanceId}/history | 聊天历史 |
| WS | /ws/chat/{instanceId} | 聊天 WebSocket |
