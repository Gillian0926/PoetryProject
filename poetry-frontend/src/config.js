// 统一的后端地址配置：
// 本地开发（localhost / 127.0.0.1）走 8080，线上走 Cloudflare Tunnel 暴露的 api 子域。
// 以后换域名只改这一处。
const host = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
const isLocal = host === 'localhost' || host === '127.0.0.1' || host === '' || host === '0.0.0.0';

const LOCAL_API = 'http://127.0.0.1:8080';
const REMOTE_API = 'https://api.gillianjin0926.online';

export const API_BASE = isLocal ? LOCAL_API : REMOTE_API;

export default API_BASE;
