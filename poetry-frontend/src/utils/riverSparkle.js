/**
 * riverSparkle.js —— 给 ECharts themeRiver 叠加「呼吸光点」效果（无描边流光）
 *
 * 设计要点（踩过的坑都在这）：
 * 1. chart 必须用 SVG 渲染：只有 SVG 才能从真实 DOM path 上取到准确几何。
 * 2. 几何缓存在 path 的 d 字符串上：鼠标悬停时 ECharts 会替换 path 节点但 d 不变，
 *    命中缓存 → 零采样、零重算，因此悬停切换色带不会卡顿、不会重置光点。
 * 3. 光点画在自建 <canvas> overlay 里，ECharts 怎么重绘都碰不到。
 * 4. 生长动画期间（以及 dataZoom 缩放动画期间）不画光点：此时色带形状正在变化，
 *    画了会飘到带外；动画结束（finished）再采样并淡入。
 * 5. 尊重 prefers-reduced-motion。
 *
 * 用法：
 *   const fx = createRiverSparkle(chart, chartEl, { mode: 'spark' });
 *   fx.setMode('spark' | 'none');
 *   fx.destroy();
 */

function hexToRgb(hex) {
  const s = String(hex).trim();
  let m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(s);
  if (m) return [parseInt(m[1], 16), parseInt(m[2], 16), parseInt(m[3], 16)];
  m = /^#?([a-f\d])([a-f\d])([a-f\d])$/i.exec(s);
  if (m) return [parseInt(m[1] + m[1], 16), parseInt(m[2] + m[2], 16), parseInt(m[3] + m[3], 16)];
  m = /^rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)/i.exec(s);
  if (m) return [+m[1], +m[2], +m[3]];
  return null;
}

const lighten = (rgb, k) => rgb.map((c) => Math.round(c + (255 - c) * k));

function yAt(arr, x) {
  if (!arr || !arr.length) return 0;
  if (x <= arr[0][0]) return arr[0][1];
  const last = arr[arr.length - 1];
  if (x >= last[0]) return last[1];
  let lo = 0;
  let hi = arr.length - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (arr[mid][0] <= x) lo = mid;
    else hi = mid;
  }
  const [x0, y0] = arr[lo];
  const [x1, y1] = arr[hi];
  const t = x1 === x0 ? 0 : (x - x0) / (x1 - x0);
  return y0 + (y1 - y0) * t;
}

export function createRiverSparkle(chart, host, options = {}) {
  const reduceMotion =
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const opts = {
    mode: reduceMotion ? 'none' : (options.mode || 'spark'),
    density: options.density || 1800,     // 越小越密
    maxPerBand: options.maxPerBand || 60,
    minPerBand: options.minPerBand || 10,
    speed: options.speed || [0.018, 0.063], // 沿岸方向漂移速度（占图宽比例/秒）
    size: options.size || [1.6, 4.0],
    breathe: options.breathe || [1.1, 2.1], // 呼吸周期（秒）：越大越慢越柔
    maxCache: options.maxCache || 240,
  };

  /* ---------- overlay ---------- */
  const canvas = document.createElement('canvas');
  canvas.setAttribute('class', 'river-fx-canvas');
  Object.assign(canvas.style, {
    position: 'absolute', left: '0', top: '0', pointerEvents: 'none', zIndex: '4',
    width: '100%', height: '100%',
  });

  if (getComputedStyle(host).position === 'static') host.style.position = 'relative';
  host.appendChild(canvas);
  const ctx = canvas.getContext('2d');

  const DPR = Math.min(window.devicePixelRatio || 1, 2);
  let W = 0;
  let H = 0;

  function resize() {
    W = host.clientWidth || 0;
    H = host.clientHeight || 0;
    canvas.width = Math.max(1, Math.round(W * DPR));
    canvas.height = Math.max(1, Math.round(H * DPR));
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
    sampleAll(true);
  }

  /* ---------- 状态 ---------- */
  const geoCache = new Map(); // d -> geometry
  let bands = [];             // { fill, rgb, el, d, geo, particles: [] }
  let animating = true;
  let reveal = 0;
  let rafId = null;
  let lastTs = 0;
  let connTick = 0;
  let destroyed = false;
  const sprites = new Map();

  /* ---------- 采样 ---------- */
  function sampleBand(pathEl, n) {
    const L = pathEl.getTotalLength();
    if (!L) return null;
    const ctm = pathEl.getScreenCTM ? pathEl.getScreenCTM() : null;
    const ovRect = canvas.getBoundingClientRect();
    const mapPt = (pt) => (ctm
      ? [ctm.a * pt.x + ctm.c * pt.y + ctm.e - ovRect.left, ctm.b * pt.x + ctm.d * pt.y + ctm.f - ovRect.top]
      : [pt.x, pt.y]);

    const pts = [];
    for (let i = 0; i <= n; i++) pts.push(mapPt(pathEl.getPointAtLength((L * i) / n)));

    const half = Math.floor(pts.length / 2);
    let top = pts.slice(0, half).sort((a, b) => a[0] - b[0]);
    let bottom = pts.slice(half).sort((a, b) => a[0] - b[0]);
    if (top.length < 3 || bottom.length < 3) return null;

    const mean = (arr) => arr.reduce((s, p) => s + p[1], 0) / arr.length;
    if (mean(bottom) < mean(top)) { const t = top; top = bottom; bottom = t; }

    return { top, bottom, x0: top[0][0], x1: top[top.length - 1][0] };
  }

  function collectBandEls() {
    const svgEl = host.querySelector('svg');
    if (!svgEl) return [];
    const chartW = W || 1;
    const out = [];
    svgEl.querySelectorAll('path').forEach((p) => {
      const fill = (p.getAttribute('fill') || '').trim();
      if (!fill || fill === 'none' || fill === 'transparent') return;
      const d = p.getAttribute('d') || '';
      if (d.length < 100) return;
      const rgb = hexToRgb(fill);
      if (!rgb) return;
      const mx = Math.max(...rgb);
      const mn = Math.min(...rgb);
      if (mx === 0 || (mx - mn) / mx < 0.15) return; // 排除黑/白/灰底（坐标轴、dataZoom 底板等）
      let box = null;
      try { box = p.getBBox(); } catch { return; }
      if (!box || box.width < chartW * 0.45) return; // 排除图例图标这类窄图形
      out.push({ el: p, fill, rgb, d });
    });
    return out;
  }

  function sampleAll(force = false) {
    const found = collectBandEls();
    found.forEach(({ el: pathEl, fill, rgb, d }) => {
      let st = bands.find((b) => b.fill === fill);
      if (!st) {
        st = { fill, rgb, particles: [] };
        bands.push(st);
      }
      st.el = pathEl;
      st.rgb = rgb;
      if (!force && st.geo && st.d === d) return; // 节点换了但几何没变 → 直接复用
      if (geoCache.has(d)) {
        st.geo = geoCache.get(d);
        st.d = d;
        return;
      }
      st.geo = sampleBand(pathEl, force ? 240 : 200);
      st.d = d;
      if (st.geo) {
        if (geoCache.size >= opts.maxCache) geoCache.delete(geoCache.keys().next().value);
        geoCache.set(d, st.geo);
      }
    });
    bands = bands.filter((b) => b.geo && found.some((f) => f.fill === b.fill));
    syncParticles();
  }

  /* ---------- 光点 ---------- */
  function spriteFor(rgb) {
    const key = rgb.join(',');
    if (sprites.has(key)) return sprites.get(key);
    const S = 28;
    const c = document.createElement('canvas');
    c.width = c.height = S;
    const g = c.getContext('2d');
    const core = lighten(rgb, 0.82);
    const mid = lighten(rgb, 0.45);
    const grad = g.createRadialGradient(S / 2, S / 2, 0, S / 2, S / 2, S / 2);
    grad.addColorStop(0, 'rgba(255,255,255,0.98)');
    grad.addColorStop(0.28, `rgba(${core[0]},${core[1]},${core[2]},0.85)`);
    grad.addColorStop(0.6, `rgba(${mid[0]},${mid[1]},${mid[2]},0.28)`);
    grad.addColorStop(1, `rgba(${mid[0]},${mid[1]},${mid[2]},0)`);
    g.fillStyle = grad;
    g.fillRect(0, 0, S, S);
    sprites.set(key, c);
    return c;
  }

  function newParticle() {
    const period = opts.breathe[0] + Math.random() * (opts.breathe[1] - opts.breathe[0]);
    return {
      u: Math.random(),
      v: 0.12 + Math.random() * 0.76,
      speed: opts.speed[0] + Math.random() * (opts.speed[1] - opts.speed[0]),
      size: opts.size[0] + Math.random() * (opts.size[1] - opts.size[0]),
      phase: Math.random() * Math.PI * 2,
      omega: (Math.PI * 2) / period, // 呼吸角速度：慢、柔
    };
  }

  function syncParticles() {
    bands.forEach((st) => {
      const { geo } = st;
      if (!geo) return;
      let thickness = 0;
      let steps = 0;
      for (let x = geo.x0; x <= geo.x1; x += 16) {
        thickness += Math.max(0, yAt(geo.bottom, x) - yAt(geo.top, x));
        steps++;
      }
      thickness /= Math.max(1, steps);
      const want = Math.max(
        opts.minPerBand,
        Math.min(opts.maxPerBand, Math.round(((geo.x1 - geo.x0) * thickness) / opts.density)),
      );
      if (st.particles.length === 0) {
        for (let i = 0; i < want; i++) st.particles.push(newParticle());
      } else if (st.particles.length < want) {
        while (st.particles.length < want) st.particles.push(newParticle());
      } else if (st.particles.length > want) {
        st.particles.length = want;
      }
      st.particles.forEach((p) => { p.sprite = spriteFor(st.rgb); });
    });
  }

  /* ---------- 主循环 ---------- */
  const wantSpark = () => opts.mode === 'spark' || opts.mode === 'both';

  function frame(ts) {
    if (destroyed) return;
    const dt = lastTs ? Math.min((ts - lastTs) / 1000, 0.05) : 0.016;
    lastTs = ts;

    // 悬停换色带时 ECharts 会替换 path 节点：每 2 帧查一次，失联立刻补引用（几何走缓存）
    if (!animating && ++connTick >= 2) {
      connTick = 0;
      if (bands.some((b) => !b.el || !b.el.isConnected)) sampleAll(false);
    }

    ctx.clearRect(0, 0, W, H);

    if (!animating) {
      reveal = Math.min(1, reveal + dt / 0.5);

      if (wantSpark()) {
        ctx.globalCompositeOperation = 'lighter';
        for (const st of bands) {
          if (!st.geo) continue;
          for (const p of st.particles) {
            p.u += p.speed * dt;
            if (p.u > 1) p.u -= 1;

            const x = st.geo.x0 + (st.geo.x1 - st.geo.x0) * p.u;
            const yTop = yAt(st.geo.top, x);
            const yBot = yAt(st.geo.bottom, x);
            if (yBot - yTop < 1.5) continue;

            const y = yTop + (yBot - yTop) * p.v;
            p.phase += p.omega * dt;

            // 呼吸：亮度大幅起伏 + 半径轻微起伏（sin 的 0~1，平方让“暗”的时间更长）
            const breath = (Math.sin(p.phase) + 1) / 2;
            const alpha = (0.12 + 0.78 * Math.pow(breath, 2.0)) * reveal;
            const s = p.size * (0.78 + 0.44 * breath);

            ctx.globalAlpha = alpha;
            ctx.drawImage(p.sprite, x - s, y - s, s * 2, s * 2);
          }
        }
        ctx.globalAlpha = 1;
        ctx.globalCompositeOperation = 'source-over';
      }
    }

    rafId = requestAnimationFrame(frame);
  }

  /* ---------- 事件 ---------- */
  const onFinished = () => {
    if (animating) {
      animating = false;
      reveal = 0;
      sampleAll(true);
    } else {
      sampleAll(false);
    }
  };
  const onZoom = () => { animating = true; };

  chart.on('finished', onFinished);
  chart.on('datazoom', onZoom);
  chart.on('legendselectchanged', () => { animating = true; });
  chart.on('restore', () => { animating = true; });

  let ro = null;
  if (typeof ResizeObserver !== 'undefined') {
    ro = new ResizeObserver(() => { if (!destroyed) resize(); });
    ro.observe(host);
  } else {
    window.addEventListener('resize', resize);
  }

  resize();
  rafId = requestAnimationFrame(frame);

  // 兜底：万一张图没有 finished 事件（数据为空等），2.6s 后也进入正常态
  const fallbackTimer = setTimeout(() => {
    if (!destroyed && animating) {
      animating = false;
      sampleAll(true);
    }
  }, 2600);

  /* ---------- 对外 API ---------- */
  return {
    setMode(m) {
      opts.mode = m;
      if (!wantSpark()) ctx.clearRect(0, 0, W, H);
    },
    getMode() { return opts.mode; },
    refresh() { sampleAll(true); },
    stats() { return { bands: bands.length, particles: bands.reduce((a, b) => a + b.particles.length, 0) }; },
    destroy() {
      destroyed = true;
      if (rafId) cancelAnimationFrame(rafId);
      clearTimeout(fallbackTimer);
      chart.off('finished', onFinished);
      chart.off('datazoom', onZoom);
      if (ro) ro.disconnect();
      else window.removeEventListener('resize', resize);
      canvas.remove();
    },
  };
}
