<template>
  <div class="container">
    <div class="header">
      <h2 class="title">诗人星空图谱 (3D)</h2>
      <p class="subtitle">🔴唐代 🔵宋代 |  点击星球查看生平</p>
    </div>

    <div class="search-panel">
      <input 
        v-model="searchQuery" 
        @keyup.enter="searchPoet" 
        placeholder="搜诗人 (如: 李清照)" 
        class="search-input"
      />
      <button @click="searchPoet" class="search-btn">全库搜索</button>
    </div>

    <div id="3d-graph" ref="graphContainer"></div>

    <transition name="slide-up">
      <div v-if="panelContent" class="ai-float-panel">
        <div class="panel-header">
          <h4>{{ panelTitle }}</h4>
          <span class="close-icon" @click="closePanel">×</span>
        </div>
        <div class="panel-content">
           <div v-if="isLoading" class="loading">
             <div class="spinner"></div>
             {{ loadingText }}
           </div>
           <div v-else v-html="formatText(panelContent)"></div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import ForceGraph3D from '3d-force-graph';
import * as THREE from 'three';
import SpriteText from 'three-spritetext';
import axios from 'axios';
import { API_BASE } from '../../config.js';

const graphContainer = ref(null);

// 面板状态
const panelContent = ref('');
const panelTitle = ref('');
const isLoading = ref(false);
const loadingText = ref('');

const searchQuery = ref('');
let Graph = null;
let gData = { nodes: [], links: [] };

let isAutoRotating = true;
let animationFrameId = null;

const highlightNodes = new Set();
const highlightLinks = new Set();
let hoverNode = null;

onMounted(async () => {
  try {
    const res = await axios.get(API_BASE + '/api/graph/data');
    const rawNodes = res.data.nodes;
    const rawLinks = res.data.links;

    gData = {
      nodes: rawNodes.map(n => ({
        id: n.name,
        group: n.category, 
        val: n.value,
        ...n
      })),
      links: rawLinks.map(l => ({
        source: l.source,
        target: l.target,
        value: l.distance
      }))
    };

    // 把所有节点固定在斐波那契球面上：无论窗口多大、放大缩小，都是一个完整的大球，不会乱飘
    const N = gData.nodes.length;
    const R = 260 + Math.sqrt(N) * 26;
    const golden = Math.PI * (3 - Math.sqrt(5));
    gData.nodes.forEach((n, i) => {
      const y = 1 - (i / Math.max(1, N - 1)) * 2;
      const r = Math.sqrt(Math.max(0, 1 - y * y));
      const theta = golden * i;
      n.fx = Math.cos(theta) * r * R;
      n.fy = y * R;
      n.fz = Math.sin(theta) * r * R;
    });
  } catch (e) {
    console.error("数据加载失败", e);
    return;
  }

  if (graphContainer.value) {
    Graph = ForceGraph3D()(graphContainer.value)
      .graphData(gData)
      .backgroundColor('#000011')
      .nodeLabel('id')
      .showNavInfo(false)
      
      .nodeThreeObject(node => {
        const group = new THREE.Group();
        let color = '#999';
        if (node.group === 0) color = '#d93a49'; 
        if (node.group === 1) color = '#0098d9'; 
        if (hoverNode && !highlightNodes.has(node)) color = '#333333'; 

        const size = Math.max(1.2, Math.log(node.val || 1) * 1.2); 
        const geometry = new THREE.SphereGeometry(size, 16, 16);
        const material = new THREE.MeshLambertMaterial({ color: color, transparent: true, opacity: 0.9 });
        const sphere = new THREE.Mesh(geometry, material);
        group.add(sphere);

        // 智能标签：重要诗人或高亮时显示
        const isImportant = node.val > 300; 
        const isHovered = (hoverNode === node) || highlightNodes.has(node);
        if (isImportant || isHovered) {
            const sprite = new SpriteText(node.id);
            sprite.color = isHovered ? '#fff' : 'rgba(255,255,255,0.8)';
            sprite.textHeight = isHovered ? 5 : (isImportant ? 3 : 0);
            sprite.position.y = size + 2;
            group.add(sprite);
        }
        return group;
      })
      
      .linkWidth(link => highlightLinks.has(link) ? 1.5 : 0.2)
      .linkColor(link => {
        if (hoverNode && !highlightLinks.has(link)) return 'rgba(0,0,0,0)';
        return 'rgba(100, 200, 255, 0.15)';
      })
      
      .onNodeHover(node => {
        isAutoRotating = !node;
        if ((!node && !hoverNode) || (node && hoverNode === node)) return;
        highlightNodes.clear();
        highlightLinks.clear();
        if (node) {
          highlightNodes.add(node);
          gData.links.forEach(link => {
            if (link.source.id === node.id || link.target.id === node.id) {
              highlightLinks.add(link);
              highlightNodes.add(link.source);
              highlightNodes.add(link.target);
            }
          });
        }
        hoverNode = node || null;
        Graph.refresh();
      })
      
      .onNodeClick(node => {
        flyToNode(node);
        askAIPoet(node.id); // 点击3D球体依然触发AI分析
      })
      .onLinkClick(link => {
        isAutoRotating = false;
        askAIRelation(link.source.id, link.target.id);
      })
      .onBackgroundClick(() => { isAutoRotating = true; });

    Graph.d3Force('charge').strength(-80);
    // 位置已固定，不需要再跑力学：避免节点漂移成异形
    if (Graph.cooldownTicks) Graph.cooldownTicks(0);
    setTimeout(() => { try { Graph.zoomToFit(600, 60); } catch (e) {} }, 800);
    
    const controls = Graph.controls();
    if (controls) {
      controls.enableDamping = true;
      controls.dampingFactor = 0.1;
      controls.minDistance = 50;
      controls.maxDistance = 2000;
      controls.autoRotate = false;
    }
    
    animate();
  }
});

const animate = () => {
  if (Graph && isAutoRotating) {
    const camera = Graph.camera();
    const angle = 0.001;
    const x = camera.position.x;
    const z = camera.position.z;
    camera.position.x = x * Math.cos(angle) - z * Math.sin(angle);
    camera.position.z = x * Math.sin(angle) + z * Math.cos(angle);
    camera.lookAt(0, 0, 0); 
  }
  animationFrameId = requestAnimationFrame(animate);
};

const closePanel = () => {
  panelContent.value = '';
  isAutoRotating = true;
};

// 【核心升级】全库搜索逻辑
const searchPoet = async () => {
  const name = searchQuery.value.trim();
  if (!name) return;
  
  // 1. 先尝试在 3D 图里找
  const node = gData.nodes.find(n => n.id === name);
  
  if (node) {
    // A. 找到了：飞过去 + AI 分析
    flyToNode(node);
    askAIPoet(name);
    hoverNode = node;
    highlightNodes.clear();
    highlightNodes.add(node);
    Graph.refresh();
  } else {
    // B. 没找到：去后端全库搜
    searchDatabase(name);
  }
};

// 查询数据库逻辑
const searchDatabase = async (name) => {
  panelTitle.value = `搜索：${name}`;
  panelContent.value = '';
  loadingText.value = `正在全库检索 ${name}...`;
  isLoading.value = true;
  
  try {
    const res = await axios.get(`${API_BASE}/api/poems/author?name=${name}`);
    
    if (res.data.found) {
      // 构建展示 HTML
      const { dynasty, total_count, poems } = res.data;
      let html = `
        <div style="margin-bottom:15px; border-bottom:1px solid rgba(255,255,255,0.2); padding-bottom:10px;">
          <span style="background:#d93a49; color:white; padding:2px 6px; border-radius:4px; font-size:12px;">${dynasty}</span>
          <span style="color:#ccc; margin-left:10px;">收录诗作: <b style="color:white;">${total_count}</b> 首</span>
          <p style="font-size:12px; color:#888; margin-top:5px;">(此诗人未收录进3D星空图谱，但为您查到了作品)</p>
        </div>
        <div class="poem-list">
      `;
      
      poems.forEach(p => {
        html += `
          <div style="margin-bottom:15px;">
            <div style="color:#0098d9; font-weight:bold;">${p.title}</div>
            <div style="color:#bbb; font-size:13px; margin-top:4px;">${p.content.replace(/。/g, '。<br>')}</div>
          </div>
        `;
      });
      html += `</div>`;
      
      panelContent.value = html;
    } else {
      panelContent.value = `<p style="text-align:center; color:#888;">全库检索完毕<br>未找到诗人 "<b>${name}</b>"</p>`;
    }
  } catch (e) {
    panelContent.value = "搜索出错，请检查后端连接。";
  } finally {
    isLoading.value = false;
  }
};

const flyToNode = (node) => {
  isAutoRotating = false;
  const distance = 60;
  const distRatio = 1 + distance/Math.hypot(node.x, node.y, node.z);
  Graph.cameraPosition({ x: node.x * distRatio, y: node.y * distRatio, z: node.z * distRatio }, node, 2000);
};

const askAIPoet = async (name) => {
  panelTitle.value = ` ${name} · 人物志`;
  panelContent.value = '';
  loadingText.value = 'AI 正在查阅史书...';
  isLoading.value = true;
  try {
    const res = await axios.post(API_BASE + '/api/ai/analyze', { era: '唐宋', type: 'poet_profile', target: name });
    panelContent.value = formatText(res.data.result);
  } catch (e) { panelContent.value = "AI 分析失败"; } finally { isLoading.value = false; }
};

const askAIRelation = async (poetA, poetB) => {
  panelTitle.value = ' 关系深度解读';
  panelContent.value = '';
  loadingText.value = 'AI 正在分析关系...';
  isLoading.value = true;
  try {
    const res = await axios.post(API_BASE + '/api/ai/analyze', { era: '唐宋', type: 'relationship', target: `${poetA}与${poetB}` });
    panelContent.value = formatText(res.data.result);
  } catch (e) { panelContent.value = "AI 分析失败"; } finally { isLoading.value = false; }
};

const formatText = (text) => {
  if (!text) return '';
  return text.replace(/^(#+)\s+(.*)$/gm, '<h5>$2</h5>').replace(/\*\*(.*?)\*\*/g, '<b style="color:#d93a49">$1</b>').replace(/\n/g, '<br>');
};

onUnmounted(() => {
  if (animationFrameId) cancelAnimationFrame(animationFrameId);
  if (graphContainer.value) graphContainer.value.innerHTML = '';
});
</script>

<style scoped>
/* 样式同前，增加 loading-text 样式 */
.container { width: 100%; height: 100vh; background: #000011; position: relative; overflow: hidden; }
.header { position: absolute; top: 20px; left: 0; right: 0; text-align: center; z-index: 10; pointer-events: none; }
.title { margin: 0; color: #fff; text-shadow: 0 0 10px rgba(0,255,255,0.5); font-size: 24px; }
.subtitle { margin: 5px 0; color: #aaa; font-size: 12px; }
#3d-graph { width: 100%; height: 100%; display: block; }

.search-panel { position: absolute; top: 20px; right: 20px; z-index: 100; display: flex; gap: 8px; }
.search-input { background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.3); color: white; padding: 8px 12px; border-radius: 20px; outline: none; backdrop-filter: blur(5px); }
.search-btn { background: #0098d9; color: white; border: none; padding: 8px 16px; border-radius: 20px; cursor: pointer; font-weight: bold; }
.search-btn:hover { background: #00baef; }

.ai-float-panel { position: absolute; right: 30px; bottom: 30px; width: 320px; max-height: 60vh; background: rgba(20, 20, 40, 0.9); backdrop-filter: blur(10px); border-radius: 12px; box-shadow: 0 0 20px rgba(0,0,0,0.5); border: 1px solid rgba(100, 100, 255, 0.3); display: flex; flex-direction: column; z-index: 100; color: #eee; }
.panel-header { padding: 15px; border-bottom: 1px solid rgba(255,255,255,0.1); display: flex; justify-content: space-between; align-items: center; }
.panel-header h4 { margin: 0; color: #fff; }
.close-icon { cursor: pointer; font-size: 20px; color: #aaa; transition: color 0.2s; }
.close-icon:hover { color: #fff; transform: scale(1.2); }
.panel-content { padding: 15px; overflow-y: auto; font-size: 14px; line-height: 1.6; color: #ccc; }

.loading { display: flex; flex-direction: column; align-items: center; color: #888; }
.spinner { width: 30px; height: 30px; border: 3px solid #333; border-top: 3px solid #0098d9; border-radius: 50%; animation: spin 1s linear infinite; margin-bottom: 10px; }
@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
</style>