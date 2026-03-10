# 🚀 Lilishop (Boot 3) Release & Canary SOP

本标准作业程序 (SOP) 用于规范如何在线上 (Azure) 环境中进行蓝绿/灰度发布 (Canary Release) 的切流、回滚以及闭环验收。
**警告**：切勿手动盲目修改 18888 端口，所有操作请遵循以下步骤！

## 🎯 环境前置背景
- **主流量组 (Stable)**: 接收 100% 用户流量，运行端口范围 `888X`
- **金丝雀组 (Pilot)**: 接收少量比例流量 (例如 10% 或仅供指定 IP)，运行端口范围 `1888X`
- **Nginx 配置文件路径**: `/etc/nginx/sites-available/maollar` / `/etc/nginx/sites-enabled/maollar`

---

## 🚦 1. 灰度切流 (开始放量)

当新版本 (例如 18888 端口组) 在后台已经被拉起并通过最初始的验收后，可通过修改 `split_clients` 权重开始引流。

1. **登录系统**:
   ```bash
   ssh -i ~/Downloads/maomall_key.pem azureuser@4.242.76.35
   ```

2. **调整权重 (如放入 10% 的流量)**:
   ```bash
   sudo sed -i 's/100%    "stable";/90%    "stable";\n    10%     "pilot";/' /etc/nginx/sites-available/maollar
   ```
   > 备注：当全部流量均要切往新版时，将 `100%` 指向新的一边。

3. **语法热重载**:
   ```bash
   sudo nginx -t && sudo systemctl reload nginx
   ```

---

## ⏪ 2. 紧急回滚 (熔断旧版本)

如果灰度期间或全量后发现 P0 级别问题 (如 502/500 大量爆发)，立即将所有流量切回 `888X` 组 (Stable)。

1. **强制复原配置**:
   ```bash
   sudo tee /etc/nginx/sites-available/maollar << 'EOF' > /dev/null
   split_clients "${remote_addr}${http_user_agent}" $is_pilot {
       100%    "stable";
       *       "stable";
   }

   map $host $target_upstream {
       api.maollar.com          127.0.0.1:8888;
       store-api.maollar.com    127.0.0.1:8889;
       admin-api.maollar.com    127.0.0.1:8890;
       common-api.maollar.com   127.0.0.1:8891;
       default                  127.0.0.1:8888;
   }

   server {
       listen 80;
       server_name api.maollar.com admin-api.maollar.com store-api.maollar.com common-api.maollar.com;

       location /actuator/health {
           proxy_pass http://$target_upstream$request_uri;
           proxy_set_header Host $host;
           # ... standard headers
           add_header X-Release-Status "GA-Boot3" always;
       }
       location /actuator/info {
           proxy_pass http://$target_upstream$request_uri;
           proxy_set_header Host $host;
           add_header X-Release-Status "GA-Boot3" always;
       }
       location /actuator { return 404; }

       location / {
           proxy_pass http://$target_upstream$request_uri;
           proxy_set_header Host $host;
           proxy_hide_header Access-Control-Allow-Origin;
           add_header Access-Control-Allow-Origin "*" always;
           add_header X-Release-Status "GA-Boot3" always;
           add_header X-Release-Type $is_pilot always;
       }
   }
   EOF
   ```

2. **一键恢复服务**:
   ```bash
   sudo nginx -t && sudo systemctl reload nginx
   ```

---

## ✅ 3. 闭环验收 (自动探测护栏)

每次发生发布/回滚切流后，必须强执行外部探测。该命令用于判断外部 H5 环境是否被断流。

在任何本地或跳板机环境执行：

```bash
for d in api.maollar.com store-api.maollar.com admin-api.maollar.com common-api.maollar.com; do
  echo "== $d ==";
  # 验证服务健康端点，必须为 200
  curl -sS -o /dev/null -w "%{http_code}\n" -I "https://$d/actuator/health";
  # 验证 header 放量标识
  curl -sS -I "https://$d/actuator/health" | grep -i x-release-type || true
done
```

**✅ 预期结果示例**:
```
== api.maollar.com ==
200
x-release-type: stable
```

如果出现 `502` 或 `x-release-type: pilot` 且并非您的意图，马上执行第二步的回滚熔断！
