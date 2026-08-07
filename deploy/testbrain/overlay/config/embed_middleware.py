"""使 TestBrain HTML/接口可在 ATP 同域 /testbrain 反代下嵌入。"""
import re

from django.conf import settings


_FETCH_PATCH = """
<script>
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
    if (typeof input === 'string') {
      input = fix(input);
    } else if (typeof Request !== 'undefined' && input instanceof Request) {
      var nu = fix(input.url.replace(/^https?:\\/\\/[^/]+/, '') || input.url);
      if (nu !== input.url && nu.charAt(0) === '/') {
        input = new Request(nu, input);
      }
    }
    return ofetch.call(this, input, init);
  };
  var oopen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function () {
    if (arguments.length > 1) arguments[1] = fix(arguments[1]);
    return oopen.apply(this, arguments);
  };
})("%s");
</script>
""".strip()


class TestBrainEmbedMiddleware:
    """
    1) 允许同域 iframe（覆盖 Django 默认 X-Frame-Options: DENY）
    2) HTML 绝对路径加 /testbrain 前缀
    3) 注入 fetch/XHR 补丁，避免 JS 里 fetch('/xxx') 打到 Vite 根路径
    4) 静态 JS 中的 fetch('/...') 一并改写
    """

    _ATTR = re.compile(
        r"""\b(href|src|action)=(['"])/(?!testbrain/)""",
        re.IGNORECASE,
    )
    _FETCH = re.compile(
        r"""\bfetch\(\s*(['"])/(?!testbrain/)""",
    )

    def __init__(self, get_response):
        self.get_response = get_response
        self.prefix = (getattr(settings, "EMBED_URL_PREFIX", None) or "/testbrain").rstrip(
            "/"
        )

    def __call__(self, request):
        response = self.get_response(request)
        response["X-Frame-Options"] = "SAMEORIGIN"

        loc = response.get("Location")
        if loc and loc.startswith("/") and not loc.startswith(self.prefix + "/"):
            if loc == "/":
                response["Location"] = self.prefix + "/"
            else:
                response["Location"] = self.prefix + loc

        ct = response.get("Content-Type", "")
        if not hasattr(response, "content"):
            return response

        try:
            text = response.content.decode(response.charset or "utf-8")
        except (UnicodeDecodeError, AttributeError):
            return response

        new_text = text
        if "text/html" in ct:
            new_text = self._ATTR.sub(rf"\1=\2{self.prefix}/", new_text)
            patch = _FETCH_PATCH % self.prefix
            if "__ATP_TB_PREFIX__" not in new_text:
                if "</head>" in new_text.lower():
                    # 大小写不敏感替换首个 </head>
                    new_text = re.sub(
                        r"</head>",
                        patch + "\n</head>",
                        new_text,
                        count=1,
                        flags=re.IGNORECASE,
                    )
                else:
                    new_text = patch + "\n" + new_text
        elif "javascript" in ct or "ecmascript" in ct:
            new_text = self._FETCH.sub(rf"fetch(\1{self.prefix}/", new_text)
            response["Cache-Control"] = "no-store, no-cache, must-revalidate"
            response["Pragma"] = "no-cache"

        if new_text != text:
            response.content = new_text.encode(response.charset or "utf-8")
            if "Content-Length" in response:
                response["Content-Length"] = str(len(response.content))
        return response
