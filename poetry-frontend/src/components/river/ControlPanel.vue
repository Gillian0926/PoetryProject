<template>
  <div class="controls-wrapper">
    <div class="controls">
      <div class="control-group">
        <span class="label">统计粒度：</span>
        <div class="btn-group">
          <button 
            v-for="type in ['dynasty', '50', '20', '5']" 
            :key="type"
            :class="{ active: groupType === type }"
            @click="$emit('update:groupType', type)"
          >
            {{ type === 'dynasty' ? '朝代' : (type === '5' ? '5年' : type + '年') }}
          </button>
        </div>
      </div>

      <div class="control-group">
        <span class="label">聚焦：</span>
        <select 
          :value="selectedEra" 
          @change="handleMainEraChange($event.target.value)"
          class="era-select"
        >
          <option value="all">全景 (All)</option>
          <option value="tang">唐代 (618-907)</option>
          <option value="song">宋代 (960-1279)</option>
        </select>
      </div>
    </div>

    <transition name="slide-fade">
      <div v-if="subOptions.length > 0" class="sub-controls">
        <span class="sub-label">↳ 细化：</span>
        <div class="sub-btn-group">
          <button 
            v-for="item in subOptions" 
            :key="item.value"
            :class="{ active: selectedEra === item.value }"
            @click="handleSubEraClick(item.value)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  groupType: String,
  selectedEra: String
});

const emit = defineEmits(['update:groupType', 'update:selectedEra']);

// 定义子时期映射
const SUB_ERAS = {
  tang: [
    { label: '初唐', value: 'early_tang' },
    { label: '盛唐', value: 'high_tang' },
    { label: '中唐', value: 'mid_tang' },
    { label: '晚唐', value: 'late_tang' }
  ],
  song: [
    { label: '北宋', value: 'north_song' },
    { label: '南宋', value: 'south_song' }
  ]
};

// 计算当前应该显示哪些子按钮
const subOptions = computed(() => {
  // 如果当前选的是 tang 或者 tang 的子集
  if (props.selectedEra === 'tang' || SUB_ERAS.tang.some(e => e.value === props.selectedEra)) {
    return SUB_ERAS.tang;
  }
  // 如果当前选的是 song 或者 song 的子集
  if (props.selectedEra === 'song' || SUB_ERAS.song.some(e => e.value === props.selectedEra)) {
    return SUB_ERAS.song;
  }
  return [];
});

// 处理大朝代切换
const handleMainEraChange = (val) => {
  emit('update:selectedEra', val);
  // 切换回大朝代时，如果当前是5年粒度，切回 'dynasty' 或 '20' 看概览，或者保持不变
  // 这里保持不变，由用户自己决定
};

// 处理子时期点击
const handleSubEraClick = (val) => {
  // 1. 切换聚焦区域
  emit('update:selectedEra', val);
  // 2. 【自动】切换粒度为 5年 (为了看细节)
  emit('update:groupType', '5');
};
</script>

<style scoped>
.controls-wrapper {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.controls { display: flex; gap: 20px; align-items: center; }
.control-group { display: flex; align-items: center; }
.label { font-weight: bold; font-size: 14px; margin-right: 8px; color: var(--text-2, #555); }

.btn-group { display: flex; border: 1px solid var(--line, #ddd); border-radius: 6px; overflow: hidden; background: var(--card, #fff); }
.btn-group button { border: none; background: var(--card, #fff); padding: 6px 12px; cursor: pointer; font-size: 13px; border-right: 1px solid var(--line, #ddd); transition: all 0.2s; color: var(--text-1, #333); }
.btn-group button:last-child { border-right: none; }
.btn-group button:hover { background: rgba(179, 56, 44, 0.06); }
.btn-group button.active { background: var(--accent, #0098d9); color: white; }

.era-select { padding: 6px; border-radius: 4px; border: 1px solid #ddd; }

/* 子菜单样式 */
.sub-controls {
  display: flex;
  align-items: center;
  margin-left: 90px; /* 对齐 */
  padding-top: 5px;
}
.sub-label { font-size: 12px; color: #999; margin-right: 8px; }

.sub-btn-group { display: flex; gap: 8px; }
.sub-btn-group button {
  border: 1px solid var(--line, #eee);
  background: var(--card, #fff);
  padding: 4px 10px;
  border-radius: 15px;
  font-size: 12px;
  color: var(--text-2, #666);
  cursor: pointer;
  transition: all 0.2s;
}
.sub-btn-group button:hover { border-color: var(--accent, #0098d9); color: var(--accent, #0098d9); }
.sub-btn-group button.active {
  background: var(--accent, #0098d9);
  border-color: var(--accent, #0098d9);
  color: white;
  box-shadow: 0 2px 5px rgba(179, 56, 44, 0.2);
}

/* 动画 */
.slide-fade-enter-active, .slide-fade-leave-active { transition: all 0.3s ease; }
.slide-fade-enter-from, .slide-fade-leave-to { transform: translateY(-5px); opacity: 0; }
</style>