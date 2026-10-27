# 数据库说明（PoetryProject）

本项目用 **PostgreSQL 16 + pgvector** 存诗库、标签、诗人风格向量与术语嵌入。
数据库**本体（物理数据目录 / 全量数据）不入仓库**，仓库里只保留「能重建数据库」的东西。

## 文件

| 文件 | 作用 | 大小 |
| --- | --- | --- |
| `01-schema.sql` | 建表 DDL（7 张表 + 索引 + 扩展），由 `pg_dump --schema-only` 导出 | ~5 KB |
| `02-seed.sql` | 最小可跑通样本：30 首诗 + 30 条标签 + 22 位诗人 + 41 条术语嵌入 + 31 条历史事件（含真实 1536 维向量） | ~1.5 MB |

## 从零重建

```bash
# 1. 起库（docker-compose 里已是 pgvector/pgvector:pg16）
docker volume create poetry_pgdata          # 首次才需要（compose 里声明的是 external volume）
docker compose up -d

# 2. 建表
docker exec -i pgvector-db psql -U pguser -d vectordb < sql/01-schema.sql

# 3. 灌最小样本（能跑通检索/图谱/时间轴，不含全量诗库）
docker exec -i pgvector-db psql -U pguser -d vectordb < sql/02-seed.sql

# 4. 跑后端
cd poetry-backend && mvn spring-boot:run      # 需先设 DASHSCOPE_API_KEY
```

> Windows PowerShell 下 `<` 重定向不可用，改用管道：
> `Get-Content sql\01-schema.sql | docker exec -i pgvector-db psql -U pguser -d vectordb`

## 全量数据

33.1 万首诗 + 33.1 万条标签向量，`tags` 表就 2.65 GB，仓库和 GitHub Release 都放不下（单文件 100 MB 硬限制）。

全量备份走 `pg_dump -Fc`，存本地/网盘：

```bash
docker exec pgvector-db pg_dump -U pguser -d vectordb -Fc --no-owner --no-privileges > poetrydb.dump
# 恢复
docker exec -i pgvector-db pg_restore -U pguser -d vectordb --no-owner --no-privileges < poetrydb.dump
```

或者重新跑 `python_etl/` 的采集脚本重建（需要 DashScope key，耗时长）。

## 表结构速览

- `poems` — 诗库主体（title/author/content/dynasty/original_id），33.1 万行
- `tags` — 每首诗的花/情绪/标签 + `vector(1536)` 嵌入，与 poems 1:1；IVFFlat 索引（`lists=100`）
- `poet_profiles` — 诗人风格向量 `vector(1536)` + 作品数
- `term_embeddings` — 检索术语嵌入（41 条固定词表）
- `history_events` — 历史时间轴事件
- `ai_analysis_cache` / `poem_details_cache` — AI 结果缓存，可清空重建，不进 seed

## 注意

- `application.properties` 里密码是开发用的 `pgpass`，线上/答辩演示别用同一套；`DASHSCOPE_API_KEY` 只走环境变量，勿写死。
- `spring.jpa.hibernate.ddl-auto=none`，表结构**只以 `01-schema.sql` 为准**，别靠 Hibernate 自动建表。
- `tags.embedding` 是 1536 维 float4（每行约 6 KB）。若存储吃紧，可考虑 pgvector ≥ 0.7 的 `halfvec(1536)` 省一半空间。
