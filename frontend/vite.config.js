import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

/** 远程原版 TestBrain；可用 TESTBRAIN_URL 覆盖（本机定制版为 http://127.0.0.1:8001） */
const TESTBRAIN_TARGET = process.env.TESTBRAIN_URL || 'http://10.0.98.20:8000'
const TB_PREFIX = '/testbrain'

const FETCH_PATCH = `<script>
(function (p) {
  if (window.__ATP_TB_PREFIX__) return;
  window.__ATP_TB_PREFIX__ = p;
  function fix(u) {
    if (typeof u !== 'string') return u;
    if (!u || u.charAt(0) !== '/') return u;
    if (u === p || u.indexOf(p + '/') === 0) return u;
    return p + u;
  }
  var ofetch = window.fetch;
  window.fetch = function (input, init) {
    if (typeof input === 'string') input = fix(input);
    else if (typeof Request !== 'undefined' && input instanceof Request) {
      var nu = fix(input.url.replace(/^https?:\\/\\/[^/]+/, '') || input.url);
      if (nu !== input.url && nu.charAt(0) === '/') input = new Request(nu, input);
    }
    return ofetch.call(this, input, init);
  };
  var oopen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function () {
    if (arguments.length > 1) arguments[1] = fix(arguments[1]);
    return oopen.apply(this, arguments);
  };
})("${TB_PREFIX}");
</script>`

function rewriteHtml(text) {
  let out = text.replace(
    /\b(href|src|action)=(['"])\/(?!testbrain\/)/gi,
    `$1=$2${TB_PREFIX}/`
  )
  if (!out.includes('__ATP_TB_PREFIX__')) {
    out = /<\/head>/i.test(out)
      ? out.replace(/<\/head>/i, `${FETCH_PATCH}\n</head>`)
      : `${FETCH_PATCH}\n${out}`
  }
  return out
}

function rewriteJs(text) {
  return text.replace(
    /\bfetch\(\s*(['"])\/(?!testbrain\/)/g,
    `fetch($1${TB_PREFIX}/`
  )
}

function createTestBrainProxy() {
  return {
    target: TESTBRAIN_TARGET,
    changeOrigin: true,
    timeout: 180000,
    proxyTimeout: 180000,
    selfHandleResponse: true,
    bypass(req) {
      const path = (req.url || '').split('?')[0]
      // 仅精确 /testbrain（无尾斜杠）交给 Vue 壳页；/testbrain/ 必须反代到远程
      if (path === '/testbrain') return '/index.html'
    },
    rewrite: (path) => path.replace(/^\/testbrain/, '') || '/',
    configure(proxy) {
      proxy.on('proxyRes', (proxyRes, req, res) => {
        // 允许被 ATP 同域 iframe 嵌入
        delete proxyRes.headers['x-frame-options']
        delete proxyRes.headers['X-Frame-Options']
        if (proxyRes.headers['content-security-policy']) {
          proxyRes.headers['content-security-policy'] = String(
            proxyRes.headers['content-security-policy']
          ).replace(/frame-ancestors[^;]*;?/gi, '')
        }
        // 重定向 Location 加前缀
        const loc = proxyRes.headers['location']
        if (
          loc &&
          typeof loc === 'string' &&
          loc.startsWith('/') &&
          !loc.startsWith(`${TB_PREFIX}/`)
        ) {
          proxyRes.headers['location'] =
            loc === '/' ? `${TB_PREFIX}/` : `${TB_PREFIX}${loc}`
        }

        const ct = String(proxyRes.headers['content-type'] || '')
        const needRewrite =
          ct.includes('text/html') ||
          ct.includes('javascript') ||
          ct.includes('ecmascript')

        const chunks = []
        proxyRes.on('data', (chunk) => chunks.push(Buffer.from(chunk)))
        proxyRes.on('end', () => {
          let buf = Buffer.concat(chunks)
          if (needRewrite) {
            let body = buf.toString('utf8')
            body = ct.includes('text/html') ? rewriteHtml(body) : rewriteJs(body)
            buf = Buffer.from(body, 'utf8')
          }
          const headers = { ...proxyRes.headers }
          delete headers['content-length']
          headers['content-length'] = String(buf.length)
          headers['x-frame-options'] = 'SAMEORIGIN'
          if (!res.headersSent) {
            res.writeHead(proxyRes.statusCode || 200, headers)
          }
          res.end(buf)
        })
        proxyRes.on('error', (err) => {
          if (!res.headersSent) {
            res.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' })
          }
          res.end('TestBrain proxy error: ' + (err && err.message ? err.message : String(err)))
        })
      })
    }
  }
}

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    host: '0.0.0.0',
    port: 3000,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        timeout: 180000,
        proxyTimeout: 180000
      },
      // TestBrain：反代到远程实例，并做嵌入路径改写
      '/testbrain': createTestBrainProxy(),
      '/ws/executor': {
        target: 'http://localhost:9002',
        ws: true,
        changeOrigin: true,
        timeout: 30000,
        proxyTimeout: 30000,
        rewrite: (path) => path.replace(/^\/ws\/executor/, '')
      }
    }
  }
})
