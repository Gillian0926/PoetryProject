# PoetryProject · 唐宋诗词意象与情感演化分析系统

基于大语言模型与文本嵌入相似度检索的古诗词意象 / 情感演化分析与可视化系统。
本科毕业设计项目（软件工程技术）。

- 语料规模：**331,205 首**唐宋诗词（含意象标签 331,205 条）、**13,485 位**诗人
- 核心能力：意象与情感的历时演化（河流图）、诗人风格相似度星图、自然语言语义检索

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3 + Vite + ECharts + 3d-force-graph |
| 后端 | Spring Boot 3.2 + Java 17 + Spring JDBC + dashscope-sdk-java |
| 模型 | 阿里云百炼 DashScope（qwen-plus 生成 / text-embedding-v* 向量） |
| 存储 | PostgreSQL 16 + pgvector（Docker 容器 `pgvector-db`） |
| 数据处理 | Python（dashscope / psycopg2 / numpy） |
| 部署 | 本机服务 + Cloudflare Tunnel（`gillianjin0926.online`） |

## 目录结构

```
poetry-backend/     Spring Boot 后端（8080）
  src/main/java/    Controller / Service / Repository
  src/main/resources/application.properties
poetry-frontend/    Vue 3 前端（vite dev 5173 / preview 4173）
  src/views/        首页、河流图、情感图、语义检索、星图
  src/components/   RiverChart / PoetGraph3D / SemanticSearch 等
python_etl/         数据清洗、向量化、诗人图构建脚本
docker-compose.yml  pgvector 数据库定义
start-all.ps1       Windows 一键启动（数据库 → 后端 → 前端 → 隧道）
```

## 快速开始

### 1. 数据库

```bash
docker compose up -d           # 启动 pgvector-db（localhost:5432 / vectordb）
```

> `data/` 是本地 Postgres 数据目录（约 6GB），**不入库**；首次使用需自行导入语料。

### 2. 后端

```bash
export DASHSCOPE_API_KEY=sk-xxx        # Windows: setx DASHSCOPE_API_KEY sk-xxx
cd poetry-backend
mvn spring-boot:run                    # 或 mvn package && java -jar target/*.jar
```

服务地址 http://localhost:8080

### 3. 前端

```bash
cd poetry-frontend
npm install
npm run dev        # 开发模式 http://localhost:5173
npm run build && npm run preview   # 生产预览 http://localhost:4173
```

### 4. 一键启动（Windows）

```powershell
powershell -ExecutionPolicy Bypass -File .\start-all.ps1
# 停止
powershell -ExecutionPolicy Bypass -File .\start-all.ps1 -Stop
```

## 配置说明

- 数据库连接与模型参数在 `poetry-backend/src/main/resources/application.properties`
- **API Key 一律通过环境变量 `DASHSCOPE_API_KEY` 注入**，不要写进配置文件
- 前端后端地址在 `poetry-frontend/src/config.js`（本地走 8080，线上走 `api.gillianjin0926.online`）

## 主要功能入口

| 页面 | 说明 |
| --- | --- |
| `/` | 首页：数据概览与四个入口 |
| `/river` | 花卉河流：七类意象的历时此消彼长 |
| `/emotion` | 情感河流：十类情感的时间分布 |
| `/search` | 语义相似度检索：用白话描述意境找诗 |
| `/graph` | 灿若星辰：诗人风格相似度星图 |
