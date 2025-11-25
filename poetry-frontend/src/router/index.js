import { createRouter, createWebHistory } from 'vue-router'


import FlowerRiver from '../components/FlowerRiver.vue'
import EmotionRiver from '../components/EmotionRiver.vue'
import SemanticSearch from '../components/search/SemanticSearch.vue' //路径修正
import PoetGraph3D2th from '../components/graph/PoetGraph3D2th.vue'           

const routes = [
  {
    path: '/',
    redirect: '/river' // 默认首页
  },
  {
    path: '/river',
    name: 'River',
    component: FlowerRiver,
    meta: { title: '花卉河流' }
  },
  {
    path: '/emotion',
    name: 'Emotion',
    component: EmotionRiver,
    meta: { title: '情感河流' }
  },
  {
    path: '/search',
    name: 'Search',
    component: SemanticSearch,
    meta: { title: '语义检索' }
  },
  {
    path: '/graph',
    name: 'Graph',
    component: PoetGraph3D2th,
    meta: { title: '灿若星辰' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router