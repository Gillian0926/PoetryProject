<template>
  <div class="sidebar">
    <div class="logo-area">
      <div class="logo-icon">词</div>
      <h1 class="app-name">唐宋·大观</h1>
    </div>

    <nav class="nav-menu">
      <router-link to="/river" class="nav-item" active-class="active">
        <span class="icon">🌸</span>
        <span class="text">花卉河流</span>
      </router-link>

      <router-link to="/emotion" class="nav-item" active-class="active">
        <span class="icon">❤️</span>
        <span class="text">情感河流</span>
      </router-link>

      <router-link to="/search" class="nav-item" active-class="active">
        <span class="icon">🔍</span>
        <span class="text">语义检索</span>
      </router-link>

      <router-link to="/graph" class="nav-item" active-class="active">
        <span class="icon">⭐</span>
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
  background: linear-gradient(180deg, #2c3e50 0%, #34495e 100%);
  color: white;
  display: flex;
  flex-direction: column;
  box-shadow: 4px 0 15px rgba(0,0,0,0.1);
  z-index: 100;
}

.logo-area {
  padding: 30px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}

.logo-icon {
  width: 36px;
  height: 36px;
  background: #d93a49;
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
  padding: 12px 15px;
  margin-bottom: 8px;
  text-decoration: none;
  color: #b0bec5;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.nav-item:hover {
  background: rgba(255,255,255,0.1);
  color: white;
}

.nav-item.active {
  background: #0098d9;
  color: white;
  box-shadow: 0 4px 10px rgba(0,152,217,0.3);
}

.icon {
  margin-right: 12px;
  font-size: 18px;
}

.text {
  font-size: 15px;
  letter-spacing: 1px;
}

.footer {
  padding: 20px;
  font-size: 12px;
  color: rgba(255,255,255,0.3);
  text-align: center;
}
</style>