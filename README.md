# XClaw 管理平台

创建、管理和对话 OpenClaw Docker 实例的管理平台。

## 项目结构

```
xclaw-platform/
├── frontend/                # Vue 3 前端
│   ├── src/
│   │   ├── api/             # API 封装 (axios)
│   │   ├── router/          # Vue Router 路由
│   │   ├── views/           # 页面组件
│   │   │   ├── CreateView.vue   # 创建 XClaw
│   │   │   ├── ManageView.vue   # XClaw 管理
│   │   │   └── ChatView.vue     # XClaw 对话
│   │   ├── App.vue          # 根组件（左侧导航）
│   │   └── main.js          # 入口
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── vite.config.js
│   └── package.json
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/com/xclaw/
│   │   ├── controller/      # REST Controller
│   │   ├── service/         # 业务逻辑
│   │   ├── mapper/          # MyBatis-Plus Mapper
│   │   ├── entity/          # 数据实体
│   │   ├── dto/             # 请求 DTO
│   │   ├── config/          # 配置类 (WebSocket, CORS)
│   │   └── websocket/       # WebSocket 聊天代理
│   ├── src/main/resources/
│   │   └── application.yml  # 配置文件
│   ├── Dockerfile
│   └── pom.xml
├── sql/
│   └── init.sql             # 数据库初始化脚本
└── docker-compose.yml       # Docker Compose 部署
```

## 功能

| 模块 | 说明 |
|------|------|
| 创建 XClaw | 填写名称/描述，后台启动 Docker 容器 |
| XClaw 管理 | 列表查看、启停、删除、状态同步 |
| XClaw 对话 | WebSocket 实时聊天，消息持久化 |

## 本地开发

### 前置条件
- JDK 17+, Node.js 20+, MySQL 8, Docker

### 1. 初始化数据库
```bash
mysql -u root -p < sql/init.sql
```

### 2. 启动后端
```bash
cd backend
# 修改 src/main/resources/application.yml 中的数据库连接信息
mvn spring-boot:run
```

### 3. 启动前端
```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173

### Docker Compose 一键部署
```bash
docker-compose up -d
```

访问 http://localhost

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
| GET | /api/chat/{instanceId}/history | 聊天历史 |
| WS | /ws/chat/{instanceId} | 聊天 WebSocket |

## 配置说明

`application.yml` 关键配置：
- `spring.datasource.*` — MySQL 连接
- `docker.openclaw.image` — OpenClaw Docker 镜像
- `docker.openclaw.network` — Docker 网络模式
- `docker.openclaw.env.OPENCLAW_MODEL` — 默认模型

后端需要挂载 Docker socket (`/var/run/docker.sock`) 才能管理容器。
