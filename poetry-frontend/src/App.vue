<script setup>
import { ref, watch, onMounted } from 'vue';
import SideBar from './components/SideBar.vue';

const showSidebar = ref(true);

onMounted(() => {
  const saved = localStorage.getItem('sidebar-visible');
  if (saved !== null) showSidebar.value = saved === '1';
});

watch(showSidebar, (v) => {
  localStorage.setItem('sidebar-visible', v ? '1' : '0');
});
</script>

<template>
  <div class="app-layout" :class="{ 'sidebar-hidden': !showSidebar }">
    <transition name="slide">
      <SideBar v-show="showSidebar" />
    </transition>

    <button
      class="sidebar-toggle"
      :class="{ collapsed: !showSidebar }"
      :title="showSidebar ? '收起侧边栏' : '展开侧边栏'"
      @click="showSidebar = !showSidebar"
    >
      <span class="bars">{{ showSidebar ? '«' : '»' }}</span>
    </button>

    <div class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.main-content {
  flex: 1;
  background-color: #f4f6f8;
  overflow-y: auto;
  position: relative;
}

/* 收起/展开按钮：贴在侧边栏右缘，收起后挪到左上角 */
.sidebar-toggle {
  position: fixed;
  top: 14px;
  left: 240px;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 0 8px 8px 0;
  background: rgba(44, 62, 80, 0.85);
  color: #fff;
  cursor: pointer;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
  padding: 0;
  transition: left 0.25s ease, background 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
}
.sidebar-toggle:hover { background: #d93a49; }
.sidebar-toggle.collapsed {
  left: 0;
  border-radius: 0 8px 8px 0;
}

.slide-enter-active, .slide-leave-active { transition: width 0.25s ease, opacity 0.25s ease; }
.slide-enter-from, .slide-leave-to { width: 0 !important; opacity: 0; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
