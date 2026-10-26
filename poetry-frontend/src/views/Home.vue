<template>
  <div class="home">
    <section class="hero">
      <div class="hero-inner">
        <p class="eyebrow">古典诗词 · 语义计算 · 可视化</p>
        <h1 class="hero-title">唐宋·大观</h1>
        <p class="hero-sub">意象与情感，在三百年的诗句里如何流变</p>
        <p class="hero-desc">
          以 33 万首唐宋诗词为语料，用大语言模型抽取意象与情感，用文本嵌入把“意境”变成可检索的向量，
          再以河流图与星图呈现它们的历时演化。
        </p>
        <div class="hero-actions">
          <router-link to="/river" class="btn primary">开始探索 →</router-link>
          <router-link to="/graph" class="btn ghost">看看诗人星图</router-link>
        </div>
      </div>
    </section>

    <section class="stats">
      <div class="stat">
        <div class="stat-num">331,205</div>
        <div class="stat-label">首诗词</div>
      </div>
      <div class="stat">
        <div class="stat-num">13,485</div>
        <div class="stat-label">位诗人</div>
      </div>
      <div class="stat">
        <div class="stat-num">1,190</div>
        <div class="stat-label">条风格相似边</div>
      </div>
      <div class="stat">
        <div class="stat-num">7 · 10</div>
        <div class="stat-label">类意象 · 类情感</div>
      </div>
    </section>

    <section class="modules">
      <h2 class="section-title">四个入口</h2>
      <div class="cards">
        <router-link to="/river" class="card">
          <h3>花卉河流</h3>
          <p>梅花、菊花、桃花……看各类意象在不同时代的此消彼长，点选流带可查看代表诗作。</p>
          <span class="go">进入 →</span>
        </router-link>

        <router-link to="/emotion" class="card">
          <h3>情感河流</h3>
          <p>闲适、忧伤、豪迈、思念……十类情感的时间分布，与安史之乱、靖康之变遥相呼应。</p>
          <span class="go">进入 →</span>
        </router-link>

        <router-link to="/search" class="card">
          <h3>语义相似度检索</h3>
          <p>输入“写尽漂泊与孤独的秋夜”，返回意境最契合的诗词——按语义找，而不是按字面找。</p>
          <span class="go">进入 →</span>
        </router-link>

        <router-link to="/graph" class="card">
          <h3>灿若星辰</h3>
          <p>每位诗人是一颗星，诗风相近者彼此相连。点击星球读生平，点击连线看两人的风格关联。</p>
          <span class="go">进入 →</span>
        </router-link>
      </div>
    </section>

    <section class="howto">
      <h2 class="section-title">建议的浏览顺序</h2>
      <ol class="steps">
        <li><b>先看河流</b>：了解意象与情感的整体走势，注意 755 年与 1127 年前后的起伏。</li>
        <li><b>再点细节</b>：在河流图上点选某条流带，查看该意象在这一时期的代表诗作。</li>
        <li><b>换个问法</b>：去语义检索，用一句白话描述你心里的画面，看系统能不能找到对应的诗。</li>
        <li><b>最后看关系</b>：在星图里找李白、杜甫、苏轼，看算法认为谁与谁“文风相近”。</li>
      </ol>
    </section>

    <footer class="foot">
      <p>数据来源：公开唐宋诗词语料｜意象与情感标签由大语言模型抽取并经人工抽样校验</p>
      <p>本科毕业设计 · 2027</p>
    </footer>
  </div>
</template>

<script setup>
// 首页为纯展示页，数据为静态统计值（来自数据库实际统计）
</script>

<style scoped>
.home {
  min-height: 100%;
  background: transparent; /* 底色由 App 的 paper-surface 提供 */
  padding-bottom: 40px;
}

/* 顶部主视觉 */
.hero {
  color: #fdf1d9;
  padding: 72px 60px 64px;
  /* 赭石 → 土黄（敦煌壁画暖色），叠一层纸纹 */
  background-color: #7a3a2b;
  background-image:
    var(--paper-noise-dark),
    linear-gradient(135deg, #7a3a2b 0%, #93502f 55%, #b3763a 100%);
  background-size: 150px 150px, cover;
  background-blend-mode: overlay, normal;
}
.hero-inner { max-width: 880px; }
.eyebrow {
  font-size: 13px;
  letter-spacing: 2px;
  color: #f0c986;
  margin: 0 0 14px;
}
.hero-title {
  font-size: 52px;
  margin: 0 0 12px;
  letter-spacing: 6px;
  font-weight: 700;
}
.hero-sub {
  font-size: 20px;
  color: #f7e4c8;
  margin: 0 0 20px;
}
.hero-desc {
  font-size: 15px;
  line-height: 1.9;
  color: #ecd6b4;
  margin: 0 0 28px;
  max-width: 680px;
}
.hero-actions { display: flex; gap: 14px; }
.btn {
  padding: 11px 22px;
  border-radius: 8px;
  font-size: 15px;
  text-decoration: none;
  transition: all .2s;
}
.btn.primary { background: var(--accent); color: #3d1f16; font-weight: 600; }
.btn.primary:hover { background: var(--accent-dark); transform: translateY(-1px); }
.btn.ghost { border: 1px solid rgba(253, 241, 217, 0.42); color: #fdf1d9; }
.btn.ghost:hover { background: rgba(253, 241, 217, 0.12); }

/* 统计条 */
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
  max-width: 1100px;
  margin: -34px auto 0;
  padding: 0 40px;
}
.stat {
  background-color: var(--card);
  background-image: var(--paper-noise);
  background-size: 150px 150px;
  background-blend-mode: multiply;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 20px 16px;
  text-align: center;
  box-shadow: 0 6px 20px rgba(60, 45, 25, 0.10);
}
.stat-num {
  font-size: 26px;
  font-weight: 700;
  color: var(--ink-800);
  letter-spacing: 1px;
}
.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-3);
}

/* 模块卡片 */
.modules, .howto {
  max-width: 1100px;
  margin: 56px auto 0;
  padding: 0 40px;
}
.section-title {
  font-size: 20px;
  color: var(--ink-800);
  margin: 0 0 18px;
  font-weight: 700;
}
.cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18px;
}
.card {
  display: block;
  background-color: var(--card);
  background-image: var(--paper-noise);
  background-size: 150px 150px;
  background-blend-mode: multiply;
  border-radius: 14px;
  padding: 24px 24px 20px;
  text-decoration: none;
  color: inherit;
  box-shadow: 0 4px 16px rgba(60, 45, 25, 0.07);
  border: 1px solid var(--line);
  transition: all .22s;
  position: relative;
}
.card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 28px rgba(60, 45, 25, 0.14);
  border-color: #e3d6bf;
}
.card-icon { display: none; }
.card h3 {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--ink-800);
}
.card p {
  margin: 0 0 14px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-2);
}
.go {
  font-size: 13px;
  color: var(--accent);
  font-weight: 600;
}

/* 使用顺序 */
.steps {
  background-color: var(--card);
  background-image: var(--paper-noise);
  background-size: 150px 150px;
  background-blend-mode: multiply;
  border-radius: 14px;
  padding: 24px 28px 24px 48px;
  margin: 0;
  box-shadow: 0 4px 16px rgba(60, 45, 25, 0.06);
  border: 1px solid var(--line);
}
.steps li {
  font-size: 14px;
  line-height: 2;
  color: var(--text-2);
}
.steps b { color: var(--ink-800); }

/* 页脚 */
.foot {
  max-width: 1100px;
  margin: 46px auto 0;
  padding: 0 40px;
  color: var(--text-3);
  font-size: 12.5px;
  line-height: 1.9;
}
.foot p { margin: 2px 0; }

@media (max-width: 900px) {
  .hero { padding: 48px 24px; }
  .hero-title { font-size: 38px; }
  .stats { grid-template-columns: repeat(2, 1fr); padding: 0 20px; }
  .modules, .howto, .foot { padding: 0 20px; }
  .cards { grid-template-columns: 1fr; }
}
</style>
