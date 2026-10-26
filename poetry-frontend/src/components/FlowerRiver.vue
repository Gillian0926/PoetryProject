<template>
  <div class="container">
    <div class="header">
      <div>
        <h2 class="title">唐宋词花卉意象演变图</h2>
        <p class="subtitle">点击河流查看诗词详情 | AI 语义分析</p>
      </div>
      
      <button class="ai-btn" @click="analyzeCurrentView" :disabled="isAnalyzing">
        <span v-if="!isAnalyzing">解读审美变迁</span>
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
        <h4>AI 审美变迁报告</h4>
        <div class="ai-text" v-html="formatText(aiAnalysisResult)"></div>
      </div>
    </div>
    
    <PoemModal 
      :visible="showModal"
      :title="modalTitle"
      :poems="poemList"
      :loading="isLoadingPoems"
      :flowerName="currentClickedFlower" 
      @close="showModal = false"
      type="flower"
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
const currentClickedFlower = ref('');
const poemList = ref([]);
const isLoadingPoems = ref(false);

// AI 状态
const isAnalyzing = ref(false);
const aiAnalysisResult = ref('');

const fetchRiverData = async () => {
  isChartLoading.value = true;
  aiAnalysisResult.value = ''; 
  try {
    const res = await axios.get(`${API_BASE}/api/chart/flower?groupType=${groupType.value}`);
    rawData.value = {
      dynasties: res.data.dynasties,
      flowers: Array.from(res.data.flowers), 
      dataMap: res.data.data
    };
  } catch (e) {
    console.error(e);
  } finally {
    isChartLoading.value = false;
  }
};

const handleChartClick = async ({ flower, time }) => {
  currentClickedFlower.value = flower;
  modalTitle.value = `${time} · ${flower}`;
  showModal.value = true;
  isLoadingPoems.value = true;
  poemList.value = [];

  try {
    const res = await axios.get(API_BASE + '/api/poems/by-flower', {
      params: {
        flower: flower,
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

// 【AI 分析逻辑】
const analyzeCurrentView = async () => {
  if (!rawData.value.dynasties || rawData.value.dynasties.length === 0) {
    alert("请等待图表加载完成");
    return;
  }

  isAnalyzing.value = true;
  aiAnalysisResult.value = ''; 

  try {
    // 发送请求，带上 type='flower'
    const res = await axios.post(
      API_BASE + '/api/ai/analyze', 
      {
        era: selectedEra.value === 'all' ? '唐宋' : selectedEra.value,
        type: 'flower' // 告诉后端要分析花卉
      },
      { timeout: 120000 } // 2分钟超时
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

/* 粉色系按钮 (区别于情感页的紫色) */
.ai-btn {
  margin-left: 20px;
  background: linear-gradient(135deg, #ff9a9e 0%, #fecfef 99%, #fecfef 100%);
  color: #d93a49;
  border: none;
  padding: 10px 24px;
  border-radius: 30px;
  cursor: pointer;
  font-weight: bold;
  font-size: 14px;
  box-shadow: 0 4px 15px rgba(255, 154, 158, 0.3);
  transition: all 0.3s ease;
  display: flex; align-items: center; gap: 5px;
}
.ai-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(255, 154, 158, 0.5); }
.ai-btn:disabled { background: #e0e0e0; color: #999; cursor: not-allowed; box-shadow: none; transform: none; }

.ai-result-panel {
  margin-top: 20px;
  background: white;
  border-radius: 12px;
  padding: 25px;
  display: flex; gap: 20px;
  box-shadow: 0 8px 20px rgba(0,0,0,0.05);
  border-left: 5px solid #ff9a9e; /* 粉色边框 */
  animation: slideDown 0.5s ease;
}
.ai-avatar { font-size: 35px; background: #fff0f0; width: 60px; height: 60px; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.ai-content { flex: 1; }
.ai-content h4 { margin: 0 0 10px 0; color: #2c3e50; font-size: 18px; font-weight: 700; }
.ai-text { margin: 0; color: #444; line-height: 1.8; font-size: 15px; text-align: justify; font-family: "Microsoft YaHei", sans-serif; }

@keyframes slideDown { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }
</style>