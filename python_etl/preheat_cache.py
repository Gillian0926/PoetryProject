import psycopg2
import os
import json
from dashscope import TextEmbedding
import dashscope

# 负责把常用词的向量先跑出来，存进 term_embeddings 表
dashscope.api_key = os.getenv('DASHSCOPE_API_KEY')


DB_CONFIG = {
    "dbname": "vectordb",
    "user": "pguser",
    "password": "pgpass",
    "host": "localhost",
    "port": "5432"
}


COMMON_FLOWERS = [
    "梅花", "荷花", "菊花", "牡丹", "海棠", "梨花", "杏花", "桃花", "桂花", "杨花",
    "柳絮", "芭蕉", "梧桐", "红豆", "杜鹃", "兰花", "水仙", "丁香", "芍药", "蔷薇"
]

def get_embedding(text):
    print(f"API Calling: {text}...")
    try:
        resp = TextEmbedding.call(
            model=TextEmbedding.Models.text_embedding_v1,
            input=text
        )
        
        if resp.status_code == 200:

            embeddings_list = resp.output['embeddings']
            return embeddings_list[0]['embedding']
        else:
            print(f"API Error: {resp.code} - {resp.message}")
            return None
    except Exception as e:
        print(f"Exception: {e}")
        return None

def preheat():
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
 
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS term_embeddings (
                term VARCHAR(255) PRIMARY KEY,
                embedding vector(1536)
            );
        """)
        conn.commit()

        for flower in COMMON_FLOWERS:
            term = f"描写{flower}的古诗"
            
            cursor.execute("SELECT 1 FROM term_embeddings WHERE term = %s", (term,))
            if cursor.fetchone():
                print(f"已存在: {term}")
                continue

            vec = get_embedding(term)
            if vec:
                vec_str = str(vec)
                cursor.execute(
                    "INSERT INTO term_embeddings (term, embedding) VALUES (%s, %s::vector)",
                    (term, vec_str)
                )
                conn.commit()
                print(f"已存入: {term}")
            else:
                print(f"生成失败: {term}")

        conn.close()
        print("完成！")
        
    except Exception as e:
        print(f"db连接失败: {e}")

if __name__ == "__main__":
    preheat()