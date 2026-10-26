<template>
  <div class="container">
    <div class="header">
      <h2 class="title">基于文本嵌入的语义相似度检索</h2>
      <p class="subtitle">支持中英文跨语言检索 · 输入任意描述，寻找意境最契合的诗词</p>
    </div>

    <div class="search-box-wrapper">
      <div class="input-group">
        <input 
          v-model="searchQuery" 
          @keyup.enter="handleSearch" 
          placeholder="例如：细雨中的江南春色" 
          class="search-input"
        />
        <button @click="handleSearch" class="search-btn" :disabled="loading">
          <span v-if="loading" class="spinner-icon">↻</span>
          {{ loading ? 'AI 搜索中...' : '搜 索' }}
        </button>
      </div>
      </div>

    <div class="results-area">
      <div v-if="loading" class="status-state">
        <div class="loading-spinner"></div>
        <p>正在进行向量匹配与语义分析...</p>
      </div>

      <div v-else-if="hasSearched && results.length === 0" class="status-state">
        <div class="empty-icon">空</div>
        <p>未找到相关诗词</p>
        <span class="sub-tip">尝试更换关键词</span>
      </div>

      <div v-else class="poem-grid">
        <div v-for="(poem, index) in results" :key="index" class="poem-card">
          <div class="card-header">
            <span class="poem-title">{{ poem.title }}</span>
            <span class="similarity">
              契合度: {{ calculateScore(poem) }}%
            </span>
          </div>
          
          <div class="poem-meta">
            <span class="dynasty-tag">{{ poem.formatted_year || poem.year_str }}</span>
            <span class="author-name">{{ poem.author }}</span>
          </div>
          
          <div class="poem-content" v-html="formatContent(poem.content)"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import axios from 'axios';
import { API_BASE } from '../../config.js';

const searchQuery = ref('');
const results = ref([]);
const loading = ref(false);
const hasSearched = ref(false);

const handleSearch = async () => {
  if (!searchQuery.value.trim()) return;
  
  loading.value = true;
  hasSearched.value = true;
  results.value = [];

  try {
    const res = await axios.get(API_BASE + '/api/poems/search', {
      params: {
        query: searchQuery.value,
        timeLabel: 'all', 
        groupType: 'dynasty'
      }
    });
    results.value = res.data;
  } catch (e) {
    console.error(e);
    alert("搜索服务连接失败");
  } finally {
    loading.value = false;
  }
};

// 分数计算函数
const calculateScore = (poem) => {
  // 1. 如果后端已经算好了 match_score (0~1)，直接用
  if (poem.match_score !== undefined && poem.match_score !== null) {
    return (poem.match_score * 100).toFixed(1);
  }
  
  // 2. 如果只有 raw distance (可能很大)，前端自己算一个合理的百分比
  // 公式: 1 / (1 + distance)
  // 距离=0 -> 100%
  // 距离=1 -> 50%
  // 距离=45 -> 2% (不会是负数)
  if (poem.distance !== undefined) {
    const dist = parseFloat(poem.distance);
    const score = 1.0 / (1.0 + dist);
    return (score * 100).toFixed(1);
  }
  
  return "0.0"; // 兜底
};

const formatContent = (content) => {
  if (!content) return '';
  let formatted = content.replace(/([。！？])/g, '$1<br>');
  const keywords = searchQuery.value.split(/[\s,，]+/);
  keywords.forEach(kw => {
    if (kw.length >= 1) {
      formatted = formatted.replace(new RegExp(kw, 'g'), `<span style="color:#d93a49;font-weight:bold;">${kw}</span>`);
    }
  });
  return formatted;
};
</script>

<style scoped>

.container { width: 100%; min-height: 100vh; padding: 40px 8%; box-sizing: border-box; background-color: #f4f6f8; display: flex; flex-direction: column; }
.header { text-align: center; margin-bottom: 30px; }
.title { font-size: 28px; color: #2c3e50; margin-bottom: 8px; letter-spacing: 1px; }
.subtitle { color: #7f8c8d; font-size: 14px; }
.search-box-wrapper { background: white; padding: 35px; border-radius: 16px; box-shadow: 0 8px 30px rgba(0,0,0,0.05); margin-bottom: 30px; }
.input-group { display: flex; gap: 12px; }
.search-input { flex: 1; padding: 14px 20px; font-size: 16px; border: 2px solid #eee; border-radius: 10px; outline: none; transition: border-color 0.3s; }
.search-input:focus { border-color: #0098d9; }
.search-btn { padding: 0 35px; font-size: 16px; font-weight: bold; background: linear-gradient(135deg, #0098d9 0%, #007bb5 100%); color: white; border: none; border-radius: 10px; cursor: pointer; transition: transform 0.2s, box-shadow 0.2s; display: flex; align-items: center; gap: 5px; }
.search-btn:hover { transform: translateY(-2px); box-shadow: 0 5px 15px rgba(0,152,217,0.3); }
.search-btn:disabled { background: #ccc; transform: none; box-shadow: none; cursor: wait; }
.results-area { flex: 1; }
.poem-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 20px; }
.poem-card { background: white; padding: 20px; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.03); transition: all 0.3s ease; border: 1px solid transparent; display: flex; flex-direction: column; }
.poem-card:hover { transform: translateY(-5px); box-shadow: 0 10px 25px rgba(0,0,0,0.08); border-color: #0098d9; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.poem-title { font-size: 18px; font-weight: bold; color: #333; }
.similarity { font-size: 12px; color: #0098d9; background: #e6f7ff; padding: 3px 8px; border-radius: 12px; font-weight: 600; }
.poem-meta { margin-bottom: 15px; font-size: 13px; color: #888; }
.dynasty-tag { background: #f0f2f5; padding: 2px 6px; border-radius: 4px; margin-right: 8px; color: #555; font-weight: bold; }
.poem-content { font-size: 16px; color: #444; line-height: 1.8; font-family: "Kaiti", "STKaiti", serif; flex: 1; }
.status-state { text-align: center; padding: 80px 0; color: #999; }
.empty-icon { font-size: 48px; margin-bottom: 15px; opacity: 0.5; }
.sub-tip { font-size: 13px; color: #bbb; margin-top: 5px; display: block; }
.loading-spinner { width: 40px; height: 40px; border: 3px solid #f3f3f3; border-top: 3px solid #0098d9; border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 15px; }
.spinner-icon { animation: spin 1s linear infinite; display: inline-block; }
@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
</style>