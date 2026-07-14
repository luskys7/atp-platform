package com.atp.platform.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String rootHint() {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>TestFlow 后端 API</title>
                  <style>
                    body { font-family: system-ui, sans-serif; max-width: 640px; margin: 48px auto; padding: 0 20px; color: #1e293b; line-height: 1.6; }
                    h1 { font-size: 22px; }
                    code { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; }
                    a { color: #2563eb; }
                    .box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 16px; margin-top: 16px; }
                  </style>
                </head>
                <body>
                  <h1>TestFlow 后端 API 已运行</h1>
                  <p>当前地址 <code>http://localhost:8080</code> 是<strong>后端接口服务</strong>，不提供 Web 管理界面。</p>
                  <div class="box">
                    <p><strong>请打开前端平台：</strong><br/>
                    <a href="http://localhost:3000">http://localhost:3000</a></p>
                    <p>默认账号：<code>admin</code> / <code>admin123</code></p>
                    <p>健康检查：<a href="/api/health">/api/health</a></p>
                  </div>
                </body>
                </html>
                """;
    }

    @GetMapping({"/health", "/api/health", "/api/v1/health"})
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "atp-platform-java");
    }
}
