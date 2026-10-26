<template>
  <div class="container">
    <div class="header">
      <div>
        <h2 class="title">唐宋词情感演变图</h2>
        <p class="subtitle">悲欢离合的历史流变 | 情感分析</p>
      </div>
      
      <button class="ai-btn" @click="analyzeCurrentView" :disabled="isAnalyzing">
        <span v-if="!isAnalyzing">AI 解读当前时期</span>
        <span v-else>正在查阅...</span>
      </button>
      
      <ControlPanel 
        v-model:groupType="groupType" 
        v-model:selectedEra="selectedEra"
      />
    </div>

    <RiverChart 
      :rawData="rawData"
      :groupType="groupType"
      :selectedEra="selectedEra"
      :loading="isChartLoading"
      @chart-click="handleChartClick"
    />

    <div v-if="aiAnalysisResult" class="ai-result-panel">
      <div class="ai-avatar">J</div>
      <div class="ai-content">
        <h4>AI 历史洞察报告</h4>
        <div class="ai-text" v-html="formatText(aiAnalysisResult)"></div>
      </div>
    </div>
    
    <PoemModal 
      :visible="showModal"
      :title="modalTitle"
      :poems="poemList"
      :loading="isLoadingPoems"
      :flowerName="currentClickedEmotion" 
      @close="showModal = false"
      type="emotion"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import axios from 'axios';

import ControlPanel from './river/ControlPanel.vue';
import RiverChart from './river/RiverChart.vue';
import PoemModal from './river/PoemModal.vue';
import { API_BASE } from '../config.js';

const groupType = ref('dynasty'); 
const selectedEra = ref('all');   

const isChartLoading = ref(false);
const rawData = ref({}); 

const showModal = ref(false);
const modalTitle = ref('');
const currentClickedEmotion = ref('');
const poemList = ref([]);
const isLoadingPoems = ref(false);

const isAnalyzing = ref(false);
const aiAnalysisResult = ref('');

// 1. 获取情感河流数据
const fetchRiverData = async () => {
  isChartLoading.value = true;
  aiAnalysisResult.value = ''; 
  try {
    // 【修复点 1】URL 变更为 /api/chart/emotion
    const res = await axios.get(`${API_BASE}/api/chart/emotion?groupType=${groupType.value}`);
    
    rawData.value = {
      dynasties: res.data.dynasties,
      flowers: Array.from(res.data.flowers), 
      dataMap: res.data.data
    };
  } catch (e) {
    console.error("获取河流图失败:", e);
  } finally {
    isChartLoading.value = false;
  }
};

// 2. 处理点击
const handleChartClick = async ({ flower, time }) => {
  const emotion = flower; 
  currentClickedEmotion.value = emotion;
  modalTitle.value = `${time} · ${emotion}`;
  showModal.value = true;
  isLoadingPoems.value = true;
  poemList.value = [];

  try {
    // 【修复点 2】URL 变更为 /api/poems/by-emotion
    const res = await axios.get(API_BASE + '/api/poems/by-emotion', {
      params: {
        emotion: emotion,
        timeLabel: time,
        groupType: groupType.value
      }
    });
    poemList.value = res.data;
  } catch (e) {
    console.error("获取详情失败", e);
  } finally {
    isLoadingPoems.value = false;
  }
};

// 3. AI 分析
const analyzeCurrentView = async () => {
  if (!rawData.value.dynasties || rawData.value.dynasties.length === 0) {
    alert("请等待图表加载完成");
    return;
  }

  isAnalyzing.value = true;
  aiAnalysisResult.value = ''; 

  try {
    let mockPeakYear = "755"; 
    let mockEmotion = "悲愁";
    let eraName = "唐宋";

    if (selectedEra.value === 'tang') {
        eraName = "唐代";
        mockPeakYear = "755"; 
    } else if (selectedEra.value === 'song') {
        eraName = "宋代";
        mockPeakYear = "1127"; 
    }

    // 【修复点 3】URL 保持 /api/ai/analyze (AiController)
    const res = await axios.post(
      API_BASE + '/api/ai/analyze', 
      {
        era: eraName,
        emotion: mockEmotion,
        peakYear: mockPeakYear
      },
      { timeout: 120000 }
    );

    aiAnalysisResult.value = res.data.result;

  } catch (e) {
    console.error(e);
    if (e.code === 'ECONNABORTED') {
        aiAnalysisResult.value = "**AI 思考超时**：分析内容较多，请重试。";
    } else {
        aiAnalysisResult.value = "**AI 服务异常**，请检查后端连接。";
    }
  } finally {
    isAnalyzing.value = false;
  }
};

const formatText = (text) => {
  if (!text) return '';
  return text
    .replace(/#{2,}\s*(.*?)\s*$/gm, '<h4 style="margin: 15px 0 8px 0; color: #2c3e50; font-weight:600;">$1</h4>')
    .replace(/\*\*(.*?)\*\*/g, '<b style="color: #0098d9;">$1</b>')
    .replace(/^\s*-\s/gm, '• ')
    .replace(/\n/g, '<br>');
};

watch(groupType, () => fetchRiverData());
watch(selectedEra, () => aiAnalysisResult.value = '');

onMounted(() => {
  fetchRiverData();
});
</script>

<style scoped>

.container { width: 100%; min-height: 100vh; padding: 20px; box-sizing: border-box; background-color: #f4f6f8; display: flex; flex-direction: column; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; background: white; padding: 15px 20px; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.03); }
.title { margin: 0; color: #333; font-size: 22px; }
.subtitle { margin: 5px 0 0 0; color: #777; font-size: 13px; }

.ai-btn {
  background: linear-gradient(135deg, #c9a0f5 0%, #edeff2 100%);
  color: rgb(115, 110, 110);
  border: none;
  padding: 10px 24px;
  border-radius: 30px;
  cursor: pointer;
  font-weight: bold;
  font-size: 14px;
  box-shadow: 0 4px 15px rgba(37, 117, 252, 0.3);
  transition: all 0.3s ease;
  display: flex; align-items: center; gap: 5px;
}
.ai-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(37, 117, 252, 0.4); }
.ai-btn:disabled { background: #b0b0b0; cursor: not-allowed; transform: none; box-shadow: none; }

.ai-result-panel {
  margin-top: 20px;
  background: white;
  border-radius: 12px;
  padding: 25px;
  display: flex; gap: 20px;
  box-shadow: 0 8px 20px rgba(0,0,0,0.05);
  border-left: 5px solid #6a11cb;
  animation: slideDown 0.5s ease;
}
.ai-avatar { font-size: 35px; background: #f0f4ff; width: 60px; height: 60px; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.ai-content { flex: 1; }
.ai-content h4 { margin: 0 0 10px 0; color: #2c3e50; font-size: 18px; font-weight: 700; }
.ai-text { margin: 0; color: #444; line-height: 1.8; font-size: 15px; text-align: justify; font-family: "Microsoft YaHei", sans-serif; }

@keyframes slideDown { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }
</style>