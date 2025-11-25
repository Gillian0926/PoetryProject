<script setup>
import SideBar from './components/SideBar.vue';
</script>

<template>
  <div class="app-layout">
    <SideBar />
    
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
  /* 占满剩余空间：Sidebar占固定宽 */
  background-color: #f4f6f8;
  overflow-y: auto; 
  position: relative;
}



.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>