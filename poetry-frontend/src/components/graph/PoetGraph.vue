<template>
  <div class="container">
    <div class="header">
      <h2 class="title">诗人风格关系图谱</h2>
      <p class="subtitle">基于 AI 向量计算文风相似度 | <i class="dot tang"></i>唐代 <i class="dot song"></i>宋代</p>
    </div>

    <div class="graph-container" ref="chartRef"></div>

    <transition name="slide-up">
      <div v-if="aiResult" class="ai-float-panel">
        <div class="panel-header">
          <h4> AI 关系解读</h4>
          <span class="close-icon" @click="aiResult = ''">×</span>
        </div>
        <div class="panel-content" v-html="formatText(aiResult)"></div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import * as echarts from 'echarts';
import axios from 'axios';
import { API_BASE } from '../../config.js';

const chartRef = ref(null);
const aiResult = ref('');
let myChart = null;

onMounted(async () => {
  if (chartRef.value) {
    myChart = echarts.init(chartRef.value);
    myChart.showLoading();
    window.addEventListener('resize', () => myChart && myChart.resize());

    try {
      const res = await axios.get(API_BASE + '/api/graph/data');
      const { nodes, links, categories } = res.data;
      myChart.hideLoading();

      const option = {
        // 全局颜色盘 (红、蓝、灰)
        color: ['#d93a49', '#0098d9', '#999999'],
        
        tooltip: {
          formatter: '{b}: 收录 {c} 首'
        },
        // 图例：显示唐代、宋代
        legend: [{
          data: categories.map(a => a.name),
          bottom: 20,
          textStyle: { color: '#666' }
        }],
        series: [
          {
            type: 'graph',
            layout: 'force',
            // 必须把分类传给 series
            categories: categories,
            data: nodes,
            links: links,
            roam: true, // 允许缩放和平移
            label: {
              show: true,
              position: 'right',
              formatter: '{b}',
              color: '#333'
            },
            lineStyle: {
              color: 'source', 
              curveness: 0.2,  // 弯
              opacity: 0.6
            },
            // 力引导布局配置
            force: {
              repulsion: 200, // 斥力
              gravity: 0.1,   // 引力
              edgeLength: [50, 150] // 连线长度范围
            },
            // 鼠标悬停效果
            emphasis: {
              focus: 'adjacency', // 只高亮当前节点和相连的节点
              lineStyle: { width: 3 }
            }
          }
        ]
      };

      myChart.setOption(option);
      
      // 点击事件
      myChart.on('click', (params) => {
        // 如果点击的是连线 (edge)
        if (params.dataType === 'edge') {
          askAI(params.data.source, params.data.target);
        } 
        // 如果点击的是节点 (node)，也可以触发分析
        else if (params.dataType === 'node') {
           // 可选：点击诗人头像分析该诗人
           // askAI(params.name, null); 
        }
      });

    } catch (e) {
      console.error(e);
      myChart.hideLoading();
    }
  }
});

onUnmounted(() => {
  if (myChart) myChart.dispose();
});

const askAI = async (poetA, poetB) => {
  aiResult.value = `<p>正在分析 <b>${poetA}</b> 与 <b>${poetB}</b> 的文学关系...</p>`;
  try {
    const res = await axios.post(API_BASE + '/api/ai/analyze', {
      era: '唐宋',
      type: 'relationship', // 告诉后端这是relationship
      target: `${poetA}与${poetB}`
    });
    aiResult.value = res.data.result;
  } catch (e) {
    aiResult.value = "AI 分析失败，请检查后端连接。";
  }
};

const formatText = (text) => {
  if (!text) return '';
  return text
    .replace(/^(#+)\s+(.*)$/gm, '<h5>$2</h5>')
    .replace(/\*\*(.*?)\*\*/g, '<b style="color:#d93a49">$1</b>')
    .replace(/\n/g, '<br>');
};
</script>

<style scoped>
.container { 
  width: 100%; 
  height: 100vh; 
  padding: 20px; 
  box-sizing: border-box; 
  background-color: #f4f6f8; 
  display: flex; 
  flex-direction: column; 
  position: relative; /* 为了悬浮窗定位 */
}
.header { text-align: center; margin-bottom: 10px; flex-shrink: 0; }
.title { margin: 0; color: #2c3e50; letter-spacing: 1px; }
.subtitle { margin: 5px 0; color: #777; font-size: 13px; }
.subtitle .dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin: 0 4px 0 8px; vertical-align: middle; }
.subtitle .dot.tang { background: #d94f45; }
.subtitle .dot.song { background: #4f7d8c; }

.graph-container { 
  flex: 1; 
  background: white; 
  border-radius: 12px; 
  box-shadow: 0 5px 15px rgba(0,0,0,0.05); 
  overflow: hidden;
}

/* AI 悬浮窗样式 */
.ai-float-panel {
  position: absolute; 
  right: 30px; 
  bottom: 30px; 
  width: 320px;
  max-height: 60vh;
  background: rgba(255, 255, 255, 0.95); 
  backdrop-filter: blur(10px);
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.15);
  border-left: 5px solid #6a11cb;
  display: flex;
  flex-direction: column;
  z-index: 1000;
}

.panel-header {
  padding: 15px;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.panel-header h4 { margin: 0; color: #333; }
.close-icon { cursor: pointer; font-size: 20px; color: #999; }
.close-icon:hover { color: #333; }

.panel-content {
  padding: 15px;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.6;
  color: #444;
}

/* 动画 */
.slide-up-enter-active, .slide-up-leave-active { transition: all 0.3s ease; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateY(20px); opacity: 0; }
</style>