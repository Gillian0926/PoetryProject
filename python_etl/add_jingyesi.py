import psycopg2
import os
from dashscope import TextEmbedding
import dashscope

# === 配置 ===
dashscope.api_key = os.getenv('DASHSCOPE_API_KEY')
DB_CONFIG = {
    "dbname": "vectordb",
    "user": "pguser",
    "password": "pgpass",
    "host": "localhost",
    "port": "5432"
}

TARGET_POEM = {
    "title": "静夜思",
    "author": "李白",
    "dynasty": "726",  
    "content": "床前明月光，疑是地上霜。举头望明月，低头思故乡。"
}

TARGET_TAGS = [
    {"flower": "无", "emotion": "思念", "tag": "manual_add"}
]

def get_embedding(text):
    print(f"正在生成向量: {text[:10]}...")
    try:
        resp = TextEmbedding.call(
            model=TextEmbedding.Models.text_embedding_v1,
            input=text
        )
        if resp.status_code == 200:
            return resp.output['embeddings'][0]['embedding']
    except Exception as e:
        print(f"API Error: {e}")
    return None

# 手动获取下一个可用ID
def get_next_id(cursor, table):
    cursor.execute(f"SELECT MAX(id) FROM {table}")
    row = cursor.fetchone()
    if row and row[0] is not None:
        return row[0] + 1
    return 1

def add_poem():
    conn = psycopg2.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        # 1. 检查是否已存在
        cursor.execute("SELECT id FROM poems WHERE title = %s AND author = %s", 
                       (TARGET_POEM['title'], TARGET_POEM['author']))
        exists = cursor.fetchone()
        
        if exists:
            poem_id = exists[0]
            print(f"这首诗已经存在，ID: {poem_id}")
        else:
            # 【修改】手动计算 ID 并插入
            new_poem_id = get_next_id(cursor, "poems")
            print(f"正在插入原文: {TARGET_POEM['title']} (新ID: {new_poem_id})")
            
            cursor.execute("""
                INSERT INTO poems (id, title, author, dynasty, content, created_time)
                VALUES (%s, %s, %s, %s, %s, NOW())
            """, (new_poem_id, TARGET_POEM['title'], TARGET_POEM['author'], TARGET_POEM['dynasty'], TARGET_POEM['content']))
            
            poem_id = new_poem_id
            print(f"原文插入成功")

        # 2. 生成向量并插入 Tags 表
        text_to_embed = f"{TARGET_POEM['title']} {TARGET_POEM['content']}"
        vector = get_embedding(text_to_embed)

        if vector:
            for tag_data in TARGET_TAGS:
                cursor.execute("""
                    SELECT id FROM tags WHERE poem_id = %s AND emotion = %s
                """, (poem_id, tag_data['emotion']))
                
                if not cursor.fetchone():
                    # 【修改】Tags 表手动计算 ID
                    new_tag_id = get_next_id(cursor, "tags")
                    
                    cursor.execute("""
                        INSERT INTO tags (id, poem_id, flower, emotion, tag, embedding)
                        VALUES (%s, %s, %s, %s, %s, %s::vector)
                    """, (
                        new_tag_id,
                        poem_id, 
                        tag_data['flower'], 
                        tag_data['emotion'], 
                        tag_data['tag'], 
                        str(vector)
                    ))
                    print(f"向量标签已插入 (ID: {new_tag_id})")
                else:
                    print("标签已存在，跳过")

        conn.commit()
        print("\n补录完成")

    except Exception as e:
        conn.rollback()
        print(f"错误: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    add_poem()