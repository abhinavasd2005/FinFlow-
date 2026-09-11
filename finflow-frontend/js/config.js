const localHosts = new Set(['localhost', '127.0.0.1']);
const apiHost = window.location.hostname || 'localhost';

window.FINFLOW_API_BASE = window.location.protocol === 'file:' || localHosts.has(window.location.hostname)
    ? `http://${apiHost}:10000/api`
    : 'https://finflow-backend-bvg9.onrender.com/api';
