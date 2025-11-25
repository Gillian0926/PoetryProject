<template>
  <div class="container">
    <div class="header">
      <h2 class="title">灿若星辰</h2>
      <p class="subtitle">🔴唐 🔵宋 | 点击星球查看生平</p>
    </div>

    <div class="search-panel">
      <input 
        v-model="searchQuery" 
        @keyup.enter="searchPoet" 
        placeholder="搜诗人 (如: 李白)" 
        class="search-input"
      />
      <button @click="searchPoet" class="search-btn">搜索</button>
    </div>

    <div id="3d-graph" ref="graphContainer"></div>

    <transition name="slide-up">
      <div v-if="panelContent && !isEasterEgg" class="ai-float-panel">
        <div class="panel-header">
          <h4> {{ panelTitle }}</h4>
          <span class="close-icon" @click="closePanel">×</span>
        </div>
        <div class="panel-content">
           <div v-if="isLoading" class="loading">
             <div class="spinner"></div>
             {{ loadingText }}
           </div>
           <div v-else>
             <div v-html="formatText(panelContent)"></div>
             <div v-if="currentPoetName" class="action-area">
               <button class="view-works-btn" @click="openPoemList(currentPoetName)">
                 阅读 {{ currentPoetName }} 的20首作品
               </button>
             </div>
           </div>
        </div>
      </div>

      <div v-else-if="panelContent && isEasterEgg" class="easter-egg-card">
        <div class="egg-content" v-html="panelContent"></div>
        <span class="close-egg" @click="closePanel">×</span>
      </div>
    </transition>

    <div v-if="showPoemListModal" class="fullscreen-modal">
      <div class="modal-container">
        <div class="modal-top">
          <h2>{{ currentPoetName }} · 作品集</h2>
          <span class="close-btn-large" @click="showPoemListModal = false">×</span>
        </div>
        <div class="modal-scroll-area">
          <div v-if="isListLoading" class="loading-large">正在从数据库提取...</div>
          <div v-else class="poem-grid">
            <div v-for="(p, i) in poetWorks" :key="i" class="poem-card-mini">
              <div class="mini-title">{{ p.title }}</div>
              <div class="mini-content" v-html="formatPoem(p.content)"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import ForceGraph3D from '3d-force-graph';
import * as THREE from 'three';
import SpriteText from 'three-spritetext';
import axios from 'axios';

const graphContainer = ref(null);

// 面板状态
const panelContent = ref('');
const panelTitle = ref('');
const isLoading = ref(false);
const loadingText = ref('');
const currentPoetName = ref('');
const isEasterEgg = ref(false); 

const showPoemListModal = ref(false);
const isListLoading = ref(false);
const poetWorks = ref([]);
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
    const res = await axios.get('http://localhost:8080/api/graph/data');
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

    
    const teacherName = "白玉琪"; 
    gData.nodes.push({
      id: teacherName,
      group: 99, 
      val: 800, 
      isTeacher: true 
    });

  } catch (e) { console.error("数据加载失败", e); return; }

  if (graphContainer.value) {
    Graph = ForceGraph3D()(graphContainer.value)
      .graphData(gData)
      .backgroundColor('#000011')
      .nodeLabel('id')
      .showNavInfo(false)
      
      .nodeThreeObject(node => {
        const group = new THREE.Group();
        let color = '#999';
        if (node.group === 0) color = '#ff4d4f'; 
        if (node.group === 1) color = '#1890ff'; 
        if (node.isTeacher) color = '#1890ff'; 

        if (hoverNode && !highlightNodes.has(node)) color = '#333'; 

        const size = Math.max(2, Math.log(node.val || 1) * 2); 
        const geometry = new THREE.SphereGeometry(size, 32, 32);
        const material = new THREE.MeshPhongMaterial({ 
          color: color, transparent: true, opacity: 0.95,
          
          emissiveIntensity: 1, shininess: 100
        });
        const sphere = new THREE.Mesh(geometry, material);
        group.add(sphere);

        if (hoverNode === node) {
            const glowGeo = new THREE.SphereGeometry(size * 1.6, 32, 32);
            const glowMat = new THREE.MeshBasicMaterial({ color: color, transparent: true, opacity: 0.2, side: THREE.BackSide });
            const glow = new THREE.Mesh(glowGeo, glowMat);
            group.add(glow);
        }

        const isImportant = node.val > 50; 
        const isHovered = (hoverNode === node) || highlightNodes.has(node);
        if (isImportant || isHovered || node.isTeacher) {
            const sprite = new SpriteText(node.id);
            sprite.color = isHovered ? '#fff' : 'rgba(255,255,255,0.9)';
            sprite.textHeight = isHovered ? 6 : (isImportant ? 4 : 4);
            sprite.position.y = size + 4;
            group.add(sprite);
        }
        return group;
      })
      
      .linkWidth(link => highlightLinks.has(link) ? 2 : 0.5)
      .linkColor(link => {
        if (hoverNode && !highlightLinks.has(link)) return 'rgba(0,0,0,0)';
        return highlightLinks.has(link) ? 'rgba(255,255,255,0.4)' : 'rgba(100, 200, 255, 0.15)';
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
        if (node.isTeacher) showTeacherInfo(node.id);
        else askAIPoet(node.id);
      })
      .onLinkClick(link => {
        isAutoRotating = false;
        askAIRelation(link.source.id, link.target.id);
      })
      .onBackgroundClick(() => { isAutoRotating = true; });

    const ambientLight = new THREE.AmbientLight(0xbbbbbb);
    Graph.scene().add(ambientLight);
    const pointLight = new THREE.PointLight(0xffffff, 1);
    pointLight.position.set(100, 100, 100);
    Graph.scene().add(pointLight);

    Graph.d3Force('charge').strength(-120);
    
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
  currentPoetName.value = '';
  isEasterEgg.value = false; 
  isAutoRotating = true;
};

const searchPoet = async () => {
  const name = searchQuery.value.trim();
  if (!name) return;
  const node = gData.nodes.find(n => n.id === name);
  if (node) {
    flyToNode(node);
    if (node.isTeacher) showTeacherInfo(name);
    else askAIPoet(name);
    hoverNode = node;
    highlightNodes.clear();
    highlightNodes.add(node);
    Graph.refresh();
  } else {
    searchDatabase(name);
  }
};

const flyToNode = (node) => {
  isAutoRotating = false;
  const distance = 60;
  const distRatio = 1 + distance/Math.hypot(node.x, node.y, node.z);
  Graph.cameraPosition({ x: node.x * distRatio, y: node.y * distRatio, z: node.z * distRatio }, node, 2000);
};


const showTeacherInfo = (name) => {
  isEasterEgg.value = true;
  isLoading.value = false;

  panelContent.value = `
    <div style="text-align: center; padding: 25px 15px;">
      <h3 style="color: #D4C4A8; font-size: 16px; font-weight: normal; margin: 0 0 15px 0; letter-spacing: 1.5px; line-height: 1.6;">
        感谢 <b>白老师</b>的<br>悉心指导与专业建议
      </h3>
      <div style="width: 50px; height: 1px; background: #D4C4A8; margin: 0 auto; opacity: 0.4;"></div>
    </div>
  `;
};

const askAIPoet = async (name) => {
  isEasterEgg.value = false;
  panelTitle.value = `${name} · 人物志`;
  panelContent.value = '';
  currentPoetName.value = name;
  loadingText.value = 'AI 正在查阅...';
  isLoading.value = true;
  try {
    const res = await axios.post('http://localhost:8080/api/ai/analyze', { era: '唐宋', type: 'poet_profile', target: name });
    panelContent.value = formatText(res.data.result);
  } catch (e) { panelContent.value = "AI 分析失败"; } finally { isLoading.value = false; }
};

const askAIRelation = async (poetA, poetB) => {
  isEasterEgg.value = false;
  panelTitle.value = '关系深度解读';
  panelContent.value = '';
  currentPoetName.value = '';
  loadingText.value = 'AI 正在分析关系...';
  isLoading.value = true;
  try {
    const res = await axios.post('http://localhost:8080/api/ai/analyze', { era: '唐宋', type: 'relationship', target: `${poetA}与${poetB}` });
    panelContent.value = formatText(res.data.result);
  } catch (e) { panelContent.value = "AI 分析失败"; } finally { isLoading.value = false; }
};

const openPoemList = async (name) => {

  showPoemListModal.value = true;

  isListLoading.value = true;

  poetWorks.value = [];

 

  try {

    const res = await axios.get(`http://localhost:8080/api/poems/author?name=${name}`);

    if (res.data.found) {

      poetWorks.value = res.data.poems; // 默认返回前 20 首

    }

  } catch (e) {

    console.error(e);

  } finally {

    isListLoading.value = false;

  }

};
const searchDatabase = async (name) => {
  isEasterEgg.value = false;
  panelTitle.value = `搜索：${name}`;
  panelContent.value = '';
  currentPoetName.value = name;
  loadingText.value = `正在全库检索 ${name}...`;
  isLoading.value = true;
  try {
    const res = await axios.get(`http://localhost:8080/api/poems/author?name=${name}`);
    if (res.data.found) {
      const { dynasty, total_count } = res.data;
      panelContent.value = `
        <div style="margin-bottom:10px;">
          <span style="background:#d93a49; color:white; padding:2px 6px; border-radius:4px;">${dynasty}</span>
          <span style="color:#ccc; margin-left:10px;">总收录: <b>${total_count}</b> 首</span>
        </div>
        <p style="font-size:13px; color:#888;">(此诗人未在星空图中显示)</p>
      `;
    } else {
      panelContent.value = `<p style="text-align:center; color:#888;">未找到诗人 "<b>${name}</b>"</p>`;
      currentPoetName.value = '';
    }
  } catch (e) { panelContent.value = "搜索出错"; } finally { isLoading.value = false; }
};

const formatText = (text) => text.replace(/^(#+)\s+(.*)$/gm, '<h5>$2</h5>').replace(/\*\*(.*?)\*\*/g, '<b style="color:#d93a49">$1</b>').replace(/\n/g, '<br>');
const formatPoem = (text) => text.replace(/([。！？])/g, '$1<br>');

onUnmounted(() => {
  if (animationFrameId) cancelAnimationFrame(animationFrameId);
  if (graphContainer.value) graphContainer.value.innerHTML = '';
});
</script>

<style scoped>

.container { width: 100%; height: 100vh; background: #000011; position: relative; overflow: hidden; }
.header { position: absolute; top: 20px; left: 0; right: 0; text-align: center; z-index: 10; pointer-events: none; }
.title { margin: 0; color: #fff; text-shadow: 0 0 10px rgba(0,255,255,0.5); font-size: 24px; }
.subtitle { margin: 5px 0; color: #aaa; font-size: 12px; }
#3d-graph { width: 100%; height: 100%; display: block; }
.search-panel { position: absolute; top: 20px; right: 20px; z-index: 100; display: flex; gap: 8px; }
.search-input { background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.3); color: white; padding: 8px 12px; border-radius: 20px; outline: none; backdrop-filter: blur(5px); }
.search-btn { background: #0098d9; color: white; border: none; padding: 8px 16px; border-radius: 20px; cursor: pointer; font-weight: bold; }
.search-btn:hover { background: #00baef; }
.loading { display: flex; flex-direction: column; align-items: center; color: #888; }
.spinner { width: 30px; height: 30px; border: 3px solid #333; border-top: 3px solid #0098d9; border-radius: 50%; animation: spin 1s linear infinite; margin-bottom: 10px; }

/* 正常 AI 面板 */
.ai-float-panel { position: absolute; right: 30px; bottom: 30px; width: 320px; max-height: 60vh; background: rgba(20, 20, 40, 0.9); backdrop-filter: blur(10px); border-radius: 12px; box-shadow: 0 0 20px rgba(0,0,0,0.5); border: 1px solid rgba(100, 100, 255, 0.3); display: flex; flex-direction: column; z-index: 100; color: #eee; }
.panel-header { padding: 15px; border-bottom: 1px solid rgba(255,255,255,0.1); display: flex; justify-content: space-between; align-items: center; }
.panel-header h4 { margin: 0; color: #fff; }
.close-icon { cursor: pointer; font-size: 20px; color: #aaa; transition: color 0.2s; }
.close-icon:hover { color: #fff; transform: scale(1.2); }
.panel-content { padding: 15px; overflow-y: auto; font-size: 14px; line-height: 1.6; color: #ccc; }


.easter-egg-card {
  position: absolute;
  right: 30px; bottom: 30px;
  width: 280px; 
  background: rgba(35, 40, 50, 0.95); 
  border: 1px solid rgba(212, 196, 168, 0.5); 
  border-radius: 8px;
  
  box-shadow: 0 5px 20px rgba(0, 0, 0, 0.3); 
  z-index: 100;
  padding: 5px;
  animation: eggPopFade 0.5s ease-out;
}
.close-egg {
  position: absolute; top: 8px; right: 10px; 
  color: #888; cursor: pointer; font-size: 18px;
}
.close-egg:hover { color: #D4C4A8; }



@keyframes eggPop { from { transform: scale(0.8); opacity: 0; } to { transform: scale(1); opacity: 1; } }

/* 作品列表样式 (保持不变) */
.action-area { margin-top: 15px; padding-top: 15px; border-top: 1px solid rgba(255,255,255,0.1); text-align: center; }
.view-works-btn { background: linear-gradient(90deg, #0098d9, #005eaa); color: white; border: none; padding: 8px 20px; border-radius: 20px; cursor: pointer; font-size: 13px; transition: transform 0.2s; }
.view-works-btn:hover { transform: scale(1.05); }
.fullscreen-modal { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(0,0,0,0.85); z-index: 999; display: flex; justify-content: center; align-items: center; backdrop-filter: blur(5px); }
.modal-container { width: 800px; max-width: 90%; height: 80vh; background: #fff; border-radius: 12px; display: flex; flex-direction: column; overflow: hidden; box-shadow: 0 20px 60px rgba(0,0,0,0.5); }
.modal-top { padding: 20px; background: #f8f9fa; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; align-items: center; }
.modal-top h2 { margin: 0; color: #333; }
.close-btn-large { font-size: 30px; cursor: pointer; color: #666; line-height: 1; }
.close-btn-large:hover { color: #000; }
.modal-scroll-area { flex: 1; padding: 30px; overflow-y: auto; background: #fff; }
.poem-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; }
.poem-card-mini { background: #fbfbfb; border: 1px solid #eee; padding: 15px; border-radius: 8px; transition: all 0.2s; }
.poem-card-mini:hover { border-color: #0098d9; box-shadow: 0 5px 15px rgba(0,0,0,0.05); transform: translateY(-2px); }
.mini-title { font-weight: bold; color: #333; margin-bottom: 8px; font-size: 16px; }
.mini-content { font-size: 14px; color: #666; line-height: 1.6; font-family: "Kaiti", serif; }
.loading-large { text-align: center; padding: 50px; color: #999; font-size: 18px; }
</style>