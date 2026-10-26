<template>
  <div class="sidebar">
    <router-link to="/" class="logo-area" title="返回首页">
      <div class="logo-icon">词</div>
      <h1 class="app-name">唐宋·大观</h1>
    </router-link>

    <nav class="nav-menu">
      <router-link to="/" class="nav-item" exact-active-class="active">
        <span class="text">首页</span>
      </router-link>

      <router-link to="/river" class="nav-item" active-class="active">
        <span class="text">花卉河流</span>
      </router-link>

      <router-link to="/emotion" class="nav-item" active-class="active">
        <span class="text">情感河流</span>
      </router-link>

      <router-link to="/search" class="nav-item" active-class="active">
        <span class="text">语义相似度检索</span>
      </router-link>

      <router-link to="/graph" class="nav-item" active-class="active">
        <span class="text">灿若星辰</span>
      </router-link>
    </nav>

    <div class="footer">
      <p>2025 PoetryJSQ</p>
    </div>
  </div>
</template>

<style scoped>
.sidebar {
  width: 240px;
  height: 100vh;
  color: #f7e9cd;
  display: flex;
  flex-direction: column;
  box-shadow: 4px 0 15px rgba(77, 36, 28, 0.22);
  z-index: 100;
  /* 赭石底 + 壁画纸纹 */
  background-color: #6d3327;
  background-image:
    var(--paper-noise-dark),
    linear-gradient(180deg, #6d3327 0%, #4d241c 100%);
  background-size: 150px 150px, cover;
  background-blend-mode: overlay, normal;
}

.logo-area {
  padding: 30px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: background .2s;
}
.logo-area:hover { background: rgba(255,255,255,0.06); }

.logo-icon {
  width: 36px;
  height: 36px;
  background: var(--accent);
  color: #3d1f16;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 20px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  letter-spacing: 2px;
  font-family: "Noto Serif SC", serif; 
}

.nav-menu {
  flex: 1;
  padding: 20px 10px;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  margin-bottom: 8px;
  text-decoration: none;
  color: rgba(250, 235, 209, 0.72);
  border-radius: 8px;
  transition: all 0.3s ease;
}

.nav-item:hover {
  background: rgba(253, 236, 205, 0.09);
  color: #fdf1d9;
}

.nav-item.active {
  background: var(--accent);
  color: #3d1f16;
  font-weight: 600;
  box-shadow: 0 4px 10px rgba(217, 164, 65, 0.28);
}

.text {
  font-size: 15px;
  letter-spacing: 1px;
}

.footer {
  padding: 20px;
  font-size: 12px;
  color: rgba(250, 235, 209, 0.32);
  text-align: center;
}
</style>