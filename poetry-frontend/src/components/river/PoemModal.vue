<template>
  <div v-if="visible" class="modal-overlay" @click.self="close">
    <div class="modal-content">
      <div class="modal-header">
        <h3>{{ title }}</h3>
        <button class="close-btn" @click="close">×</button>
      </div>
      
      <div class="modal-body">
        <div class="ai-analysis-section">
          <button class="analyze-btn" @click="analyzeTarget" :disabled="isAnalyzing">
            {{ isAnalyzing ? 'AI 正在思考...' : 'AI 深度解读：' + flowerName }}
          </button>
          <div v-if="analysisResult" class="analysis-result" v-html="formatAiText(analysisResult)"></div>
        </div>

        <div v-if="loading" class="loading">正在加载诗词...</div>
        <div v-else-if="!poems || poems.length === 0" class="no-data">
          暂无数据
        </div>
        <div v-else class="poem-list">
          <div v-for="(poem, index) in poems" :key="index" class="poem-card">
            <div class="poem-title">{{ poem.title }}</div>
            <div class="poem-author">
              <span class="tag-year">{{ poem.year_str }}</span> {{ poem.author }}
            </div>
            
            <div class="poem-text" v-html="formatPoemContent(poem.content)"></div>
            
            <div v-if="poem.match_score" class="sim-score">
              契合度: {{ (poem.match_score * 100).toFixed(1) }}%
            </div>
          </div>
          <div v-if="poems.length >= 50" class="tip">注：仅显示前50首</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import axios from 'axios';

// 定义接收的参数
const props = defineProps({
  visible: Boolean,
  title: String,
  poems: Array,
  loading: Boolean,
  flowerName: String, // 当前点击的花名或情感名
  type: String        // 'flower' 或 'emotion' (用于AI分析区分)
});

const emit = defineEmits(['close']);
const close = () => emit('close');

const isAnalyzing = ref(false);
const analysisResult = ref('');

// 监听弹窗打开，清空旧的分析结果
watch(() => props.visible, (val) => {
  if (val) {
    analysisResult.value = '';
    isAnalyzing.value = false;
  }
});

// --- 诗词内容格式化 (换行 + 高亮) ---
const formatPoemContent = (content) => {
  if (!content) return '';
  
  let formatted = content;

  // 1. 自动换行：遇到 句号、问号、感叹号 后加一个 <br>
  // 这里的 $1 代表正则匹配到的标点符号本身
  formatted = formatted.replace(/([。！？])/g, '$1<br>');

  // 2. 关键词高亮
  if (props.flowerName) {
    // 根据类型选择颜色：花卉用红色，情感用紫色
    const color = props.type === 'emotion' ? '#6a11cb' : '#d93a49';
    
    // 动态正则替换
    formatted = formatted.replace(new RegExp(props.flowerName, 'g'), 
      `<span style="color:${color}; font-weight:bold;">${props.flowerName}</span>`);
  }

  return formatted;
};

// --- AI 文本格式化 (###处理) ---
const formatAiText = (text) => {
  if (!text) return '';
  return text
    .replace(/^(#+)\s+(.*)$/gm, '<h4 style="margin: 12px 0 5px 0; color: #333;">$2</h4>')
    .replace(/\*\*(.*?)\*\*/g, '<b>$1</b>')
    .replace(/\n/g, '<br>');
};

// AI 分析逻辑
const analyzeTarget = async () => {
  if (!props.flowerName) return;
  isAnalyzing.value = true;
  try {
    const res = await axios.post('http://localhost:8080/api/ai/analyze', {
      era: 'all', // 针对该意象的全景分析
      type: props.type || 'emotion', // 默认为 emotion，如果是花卉页面要传 flower
      target: props.flowerName
    }, { timeout: 120000 });
    
    analysisResult.value = res.data.result;
  } catch (e) {
    analysisResult.value = "AI 分析失败，请稍后重试。";
  } finally {
    isAnalyzing.value = false;
  }
};
</script>

<style scoped>
.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; justify-content: center; align-items: center; z-index: 1000; backdrop-filter: blur(3px); }
.modal-content { background: white; width: 600px; max-width: 90%; height: 80vh; border-radius: 12px; display: flex; flex-direction: column; box-shadow: 0 10px 30px rgba(0,0,0,0.2); animation: slideUp 0.3s ease-out; }
.modal-header { padding: 15px 20px; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; align-items: center; }
.modal-header h3 { margin: 0; color: #333; }
.close-btn { background: none; border: none; font-size: 24px; cursor: pointer; color: #999; }
.close-btn:hover { color: #333; }
.modal-body { flex: 1; padding: 20px; overflow-y: auto; background: #f9f9f9; }

.poem-card { background: white; padding: 15px; margin-bottom: 15px; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.03); border-left: 4px solid #0098d9; }
.poem-title { font-size: 18px; font-weight: bold; color: #333; margin-bottom: 5px; }
.poem-author { font-size: 12px; color: #888; margin-bottom: 10px; }
.tag-year { background: #eee; padding: 2px 6px; border-radius: 4px; margin-right: 5px; color: #555; }

/* 诗词正文样式 */
.poem-text { 
  font-size: 16px; 
  line-height: 1.8; 
  color: #444; 
  margin-top: 10px;
  font-family: "Kaiti", "STKaiti", "KaiTi", serif; /* 楷体 */
}

.sim-score { font-size: 12px; color: #0098d9; text-align: right; margin-top: 5px; }
.tip { text-align: center; color: #999; font-size: 12px; margin-top: 10px; }
.loading, .no-data { text-align: center; margin-top: 50px; color: #888; }

/* AI 区域 */
.ai-analysis-section { margin-bottom: 20px; padding: 15px; background: #f0f8ff; border-radius: 8px; border: 1px solid #bae7ff; }
.analyze-btn { width: 100%; padding: 10px; background: #1890ff; color: white; border: none; border-radius: 6px; cursor: pointer; font-weight: bold; transition: background 0.3s; }
.analyze-btn:hover { background: #40a9ff; }
.analyze-btn:disabled { background: #ccc; }
.analysis-result { margin-top: 15px; font-size: 14px; line-height: 1.6; color: #333; background: white; padding: 10px; border-radius: 4px; }

@keyframes slideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
</style>