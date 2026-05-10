server {
listen 80;
server_name _; # 接收所有 IP 访问

    # 1. 前端 Vue 的配置
    location / {
        root /home/admin/myproject/frontend; # 告诉 Nginx 前端文件在这里
        index index.html;
        try_files $uri $uri/ /index.html;    # 解决 Vue 刷新页面 404 的问题
    }

    # 2. 后端 Spring Boot 的配置 (极其重要的跨域解决方案)
    # 假设你前端请求后端的路径都有 /api 前缀，比如 http://ip/api/login
    location /api/ {
        # 将请求悄悄转发给本地的 Java 8080 端口
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /agent-api/ {
        proxy_pass http://127.0.0.1:8000;  # 注意最后不要带斜杠
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

}
