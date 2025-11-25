import psycopg2
import os
import numpy as np

# 数据库配置
DB_CONFIG = {
    "dbname": "vectordb",
    "user": "pguser",
    "password": "pgpass",
    "host": "localhost",
    "port": "5432"
}

def build_poet_profiles():
    conn = psycopg2.connect(**DB_CONFIG)
    cursor = conn.cursor()

    # 1. 重建表
    cursor.execute("DROP TABLE IF EXISTS poet_profiles")
    cursor.execute("""
        CREATE TABLE poet_profiles (
            author VARCHAR(255) PRIMARY KEY,
            dynasty VARCHAR(50),
            poem_count INT,
            style_vector vector(1536)
        );
    """)
    conn.commit()

    print("正在平衡提取唐宋诗人数据 (各取 Top 60)...")

    # 2. 核心 SQL：唐宋分榜提取，然后合并
    # 逻辑：
    # - 唐：年份 618-907 或 包含'唐'
    # - 宋：年份 960-1279 或 包含'宋'
    sql = """
        WITH tang_poets AS (
            SELECT p.author, '唐' as dynasty, COUNT(*) as cnt, AVG(t.embedding) as avg_vec
            FROM tags t JOIN poems p ON t.poem_id = p.id
            WHERE t.embedding IS NOT NULL 
              AND (p.dynasty LIKE '%唐%' OR (p.dynasty ~ '^\\d+$' AND CAST(p.dynasty AS INT) BETWEEN 618 AND 907))
            GROUP BY p.author
            HAVING COUNT(*) > 5
            ORDER BY cnt DESC
            LIMIT 60
        ),
        song_poets AS (
            SELECT p.author, '宋' as dynasty, COUNT(*) as cnt, AVG(t.embedding) as avg_vec
            FROM tags t JOIN poems p ON t.poem_id = p.id
            WHERE t.embedding IS NOT NULL 
              AND (p.dynasty LIKE '%宋%' OR (p.dynasty ~ '^\\d+$' AND CAST(p.dynasty AS INT) BETWEEN 960 AND 1279))
            GROUP BY p.author
            HAVING COUNT(*) > 5
            ORDER BY cnt DESC
            LIMIT 60
        )
        SELECT * FROM tang_poets
        UNION ALL
        SELECT * FROM song_poets;
    """
    
    cursor.execute(sql)
    poets = cursor.fetchall()

    print(f"计算完成，共提取 {len(poets)} 位诗人（唐宋平衡），正在存入数据库...")

    for row in poets:
        author, dynasty, count, vector = row
        
        # 处理向量格式
        if hasattr(vector, 'tolist'): vec_str = str(vector.tolist())
        else: vec_str = str(vector)

        cursor.execute("""
            INSERT INTO poet_profiles (author, dynasty, poem_count, style_vector)
            VALUES (%s, %s, %s, %s)
            ON CONFLICT (author) DO NOTHING
        """, (author, dynasty, count, vec_str))

    conn.commit()
    conn.close()
    print("🎉 诗人图谱数据构建完成！唐宋各半！")

if __name__ == "__main__":
    build_poet_profiles()