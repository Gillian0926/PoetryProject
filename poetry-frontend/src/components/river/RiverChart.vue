<template>
  <div class="chart-container">
    <div ref="chartRef" class="chart-box"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onUnmounted, nextTick } from 'vue';
import * as echarts from 'echarts';
import { createRiverSparkle } from '@/utils/riverSparkle';

const props = defineProps({
  rawData: Object,
  groupType: String,
  selectedEra: String,
  loading: Boolean
});

const emit = defineEmits(['chart-click']);

const chartRef = ref(null);
let myChart = null;
let fx = null; // 细闪 + 流光的叠加层
let currentRenderedLabels = [];

// 【核心修改】补全所有历史时期的年份定义
// 这里的定义必须和后端 PoetryUtils 里的逻辑保持一致
const ERA_RANGES = {
  // 大朝代
  tang: { start: 618, end: 907 }, // 注意：含晚唐结束
  song: { start: 960, end: 1279 },
  
  // 唐代细分
  early_tang: { start: 618, end: 712 }, // 初唐
  high_tang:  { start: 713, end: 765 }, // 盛唐
  mid_tang:   { start: 766, end: 835 }, // 中唐
  late_tang:  { start: 836, end: 907 }, // 晚唐
  
  // 宋代细分
  north_song: { start: 960, end: 1127 }, // 北宋
  south_song: { start: 1127, end: 1279 } // 南宋
};

onMounted(() => {
  initChart();
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  if (fx) fx.destroy();
  if (myChart) myChart.dispose();
});

const initChart = () => {
  if (chartRef.value) {
    // SVG 渲染：细闪效果需要从真实的 <path> 上采样色带几何（canvas 渲染拿不到）
    myChart = echarts.init(chartRef.value, null, { renderer: 'svg' });
    myChart.getZr().on('click', handleCanvasClick);
    // 生长动画结束后自动采样并淡入光点；悬停切换色带不会重建（几何命中缓存）
    fx = createRiverSparkle(myChart, chartRef.value, { mode: 'spark' });
  }
};

const handleResize = () => myChart && myChart.resize();

// 监听属性变化重绘
watch([() => props.rawData, () => props.selectedEra, () => props.groupType], () => {
  nextTick(() => renderChart());
}, { deep: true });

watch(() => props.loading, (val) => {
  if (myChart) val ? myChart.showLoading() : myChart.hideLoading();
});

// 物理点击反算
const handleCanvasClick = (params) => {
  if (!myChart || currentRenderedLabels.length === 0) return;
  const pointInPixel = [params.offsetX, params.offsetY];
  let pointInGrid = 0;
  try {
    if (myChart.containPixel({singleAxisIndex: 0}, pointInPixel)) {
      pointInGrid = myChart.convertFromPixel({singleAxisIndex: 0}, pointInPixel);
    } else { return; }
  } catch(e) { return; }

  const timeIndex = Math.round(pointInGrid);
  // 保留此函数用于可能的扩展，目前点击主要靠标准click结合物理反算
};

// 绑定标准点击 + 物理修正
const bindStandardClick = () => {
  if (!myChart) return;
  myChart.off('click');
  myChart.on('click', (params) => {
    const flowerName = params.data[2] || params.name;
    const pointInPixel = [params.event.offsetX, params.event.offsetY];
    let pointInGrid = 0;
    try {
       pointInGrid = myChart.convertFromPixel({singleAxisIndex: 0}, pointInPixel);
    } catch(e) { return; }
    
    const timeIndex = Math.round(pointInGrid);
    if (timeIndex >= 0 && timeIndex < currentRenderedLabels.length) {
       const timeLabel = currentRenderedLabels[timeIndex];
       if (timeLabel && flowerName) {
         emit('chart-click', { flower: flowerName, time: timeLabel });
       }
    }
  });
};

// 过滤逻辑
const filterLabels = (allLabels, era, type) => {
  if (!allLabels || allLabels.length === 0) return [];
  if (era === 'all') return allLabels;

  return allLabels.filter(label => {
    // 1. 朝代模式 (标签是中文)
    if (type === 'dynasty') {
        // 这里为了简单，如果选了细分朝代，在朝代模式下只显示匹配的那个词
        // 比如 selectedEra='high_tang', 这里很难直接匹配中文 '盛唐'，
        // 建议用户在点击细分按钮时，自动切到了年份模式(groupType=5)。
        // 如果用户非要切回朝代模式，我们这里做一个简单的包含匹配：
        if (era === 'tang') return label.includes('唐') || label.includes('五代');
        if (era === 'song') return label.includes('宋');
        
        // 映射细分 (Optional, 如果后端返回的是 "盛唐")
        if (era === 'high_tang') return label === '盛唐';
        if (era === 'late_tang') return label === '晚唐';
        // ... 其他映射，或者直接返回 false (因为朝代模式下标签很少)
        return true; 
    } 
    // 2. 年份模式 (标签是 "500-504")
    else {
      const startYear = parseInt(label.split('-')[0]);
      if (isNaN(startYear)) return false;
      
      const range = ERA_RANGES[era];
      // 核心：判断年份是否在定义的范围内
      return range && startYear >= range.start && startYear <= range.end;
    }
  });
};

const renderChart = () => {
  if (!myChart) initChart();
  if (!props.rawData || !props.rawData.dynasties) return;

  // 1. 过滤
  currentRenderedLabels = filterLabels(props.rawData.dynasties, props.selectedEra, props.groupType);

  if (currentRenderedLabels.length === 0) {
    myChart.clear();
    myChart.setOption({ title: { text: '该时期无数据', left: 'center', top: 'center', textStyle: { color: '#999' } }});
    return;
  }

  // 2. 构建数据
  const riverData = [];
  currentRenderedLabels.forEach((label, index) => {
      props.rawData.flowers.forEach(flower => {
          const count = (props.rawData.dataMap[label] && props.rawData.dataMap[label][flower]) 
                        ? props.rawData.dataMap[label][flower] 
                        : 0;
          riverData.push([index, count, flower, label]); 
      });
  });

  // 3. 绘图
  const option = {
    title: { text: '' },
    color: [
      '#8c3a2b', '#b5622f', '#d9a441', '#6b8f5e',
      '#4f7d8c', '#8f6b8a', '#c8874a', '#7a3a2b',
      '#a3705f', '#5c7a8a', '#9a7b3f', '#6d3327'
    ],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: 'rgba(0,0,0,0.3)' } },
      formatter: function (params) {
          const labelName = params[0].data[3];
          let html = `<div style="font-weight:bold; border-bottom:1px solid #ddd; margin-bottom:5px">${labelName}</div>`;
          const sortedParams = params.sort((a, b) => b.data[1] - a.data[1]);
          sortedParams.slice(0, 15).forEach(item => {
               if(item.data[1] > 0) {
                  html += `<div style="display:flex; justify-content:space-between; width:160px;">
                          <span>${item.marker} ${item.data[2]}</span>
                          <b>${item.data[1]}</b></div>`;
               }
          });
          return html;
      }
    },
    legend: { data: props.rawData.flowers, type: 'scroll', top: 5 },
    singleAxis: {
      top: '10%', bottom: '15%', type: 'value', boundaryGap: false,
      data: currentRenderedLabels.map((_, i) => i),
      axisLabel: {
          formatter: (value) => {
              const text = currentRenderedLabels[value] || '';
              if (text.includes('-')) return text.split('-')[0];
              return text;
          },
          interval: 'auto'
      },
      axisTick: { show: true },
      axisLine: { show: true, lineStyle: { opacity: 0.3 } },
      splitLine: { show: false }
    },
    series: [{
        type: 'themeRiver', singleAxisIndex: 0, label: { show: false },
        emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.5)' } },
        data: riverData
    }],
    dataZoom: [{ type: 'slider', show: true, singleAxisIndex: 0, start: 0, end: 100, bottom: 20, height: 18 }]
  };

  try {
    myChart.setOption(option, { notMerge: true });
  } catch (err) {
    // 保底：任何标注/散点异常都不能让图表白屏，回退到纯河流图
    console.warn('[RiverChart] 完整配置渲染失败，已回退到基础视图：', err);
    myChart.setOption({
      color: option.color,
      tooltip: option.tooltip,
      legend: option.legend,
      singleAxis: option.singleAxis,
      series: [{ type: 'themeRiver', singleAxisIndex: 0, label: { show: false }, data: riverData }]
    }, { notMerge: true });
  }
  bindStandardClick();
};
</script>

<style scoped>
.chart-container { width: 100%; height: 100%; }
.chart-box { width: 100%; min-height: 500px; height: 65vh; background-color: var(--card, #fff); background-image: var(--paper-noise); background-size: 150px 150px; background-blend-mode: multiply; border: 1px solid var(--line, #eee); border-radius: 12px; padding: 15px; box-shadow: 0 5px 15px rgba(60,45,25,0.06); position: relative; overflow: hidden; }
</style>