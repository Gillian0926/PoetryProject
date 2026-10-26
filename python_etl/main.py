import sqlite3
import os
import json
import time
from dashscope import TextEmbedding, Generation
import dashscope

dashscope.api_key = os.getenv('DASHSCOPE_API_KEY')
DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "poetry_embedding.db")

def get_connection():
    return sqlite3.connect(DB_PATH)

def init_db():
    """确保Tags表存在"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS Tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT, 
            poem_id INTEGER, 
            flower TEXT, 
            emotion TEXT, 
            tag TEXT, 
            embedding TEXT
        )
    """)
    conn.commit()
    conn.close()

def analyze_poem(content):
    """千问"""
    # 1. 提取花卉与情感
    prompt = f"分析这首词：'{content}'。请提取其中提到的花卉（如果没有填None），并判断情感（悲伤/喜悦/思念/恬淡/豪放）。返回严格的JSON格式：{{\"flowers\": [\"梅花\"], \"emotion\": \"思念\"}}"
    
    try:
        # 生成分析
        gen_resp = Generation.call(model='qwen-turbo', prompt=prompt, result_format='message')
        if gen_resp.status_code != 200:
            print(f"Gen Error: {gen_resp.message}")
            return None, None
            
        content_str = gen_resp.output.choices[0].message.content
        # JSON 清洗
        start = content_str.find('{')
        end = content_str.rfind('}') + 1
        analysis = json.loads(content_str[start:end])

        # 2. 生成 Embedding
        embed_resp = TextEmbedding.call(
            model=TextEmbedding.Models.text_embedding_v1,
            input=content
        )
        embedding = []
        if embed_resp.status_code == 200:
            embedding = embed_resp.output.embeddings[0].embedding
        
        return analysis, embedding

    except Exception as e:
        print(f"API Error: {e}")
        return None, None

def run_etl():
    init_db()
    conn = get_connection()
    cursor = conn.cursor()


    cursor.execute("""
        SELECT p.id, p.content 
        FROM Poems p 
        WHERE p.id NOT IN (SELECT DISTINCT poem_id FROM Tags)
        LIMIT 20 
    """)
    poems = cursor.fetchall()
    
    print(f"待处理诗词数量: {len(poems)}")

    for poem_id, content in poems:
        print(f"正在处理 ID: {poem_id}...")
        analysis, embedding = analyze_poem(content)
        
        if analysis:
            flowers = analysis.get('flowers', [])
            emotion = analysis.get('emotion', 'unknown')
            embed_str = json.dumps(embedding) 
            
            
            if flowers and flowers[0] != "None":
                for flower in flowers:
                    cursor.execute("INSERT INTO Tags (poem_id, flower, emotion, tag, embedding) VALUES (?, ?, ?, ?, ?)", 
                                   (poem_id, flower, emotion, 'flower_tag', embed_str))
            else:
                # 防重复
                cursor.execute("INSERT INTO Tags (poem_id, flower, emotion, tag, embedding) VALUES (?, ?, ?, ?, ?)", 
                                   (poem_id, None, emotion, 'processed', embed_str))
            
            conn.commit()
            time.sleep(0.5) # 防限流

    conn.close()
    print("处理完成！")

if __name__ == "__main__":
    run_etl()