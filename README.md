# StudentClockin

一个面向家庭场景的学生任务打卡系统，支持：

- 家长为多个孩子分别创建任务
- 孩子按任务打卡、计时、上传照片 / 视频 / 音频
- 完成任务自动发积分
- 孩子发起积分兑换，家长审核
- 家长查看每日完成情况、积分明细、兑换统计
- 可选接入钉钉机器人 / Webhook 通知
- 后端支持 Docker 自部署

## 仓库结构

本仓库当前把实际项目代码放在 `student-checkin/` 目录下：

- `student-checkin/android-app/`
  - Android 客户端，Kotlin + Jetpack Compose
- `student-checkin/backend/`
  - Node.js API 服务
- `student-checkin/supabase/migrations/`
  - PostgreSQL 数据库迁移脚本
- `student-checkin/deploy/`
  - 部署辅助文件
- `student-checkin/docs/`
  - 开发、部署、说明文档

## 适合什么场景

- 自己给孩子使用
- 一个家庭多个孩子分别管理任务
- 后续二开、开源、部署到自己的服务器

## 快速开始

### 1. 拉取仓库

```bash
git clone https://github.com/runi898/StudentClockin.git
cd StudentClockin/student-checkin
```

### 2. 配置后端

```bash
cp .env.example .env
```

至少修改这些值：

- `POSTGRES_PASSWORD`
- `JWT_SECRET`
- `API_PORT`

启动：

```bash
docker compose up -d --build
```

### 3. 配置 Android 客户端连接你的服务器

复制本地配置示例：

```bash
cp android-app/gradle-local.example.properties android-app/gradle-local.properties
```

然后把里面的服务端地址改成你自己的，例如：

```properties
studentclockinSupabaseUrl=http://your-server:28547
studentclockinSupabasePublicKey=student-checkin-public
```

说明：

- 这里沿用了旧字段名，目的是减少 Android 构建层改动
- `studentclockinSupabasePublicKey` 目前只需要非空占位值即可

### 4. 编译 APK

Windows PowerShell：

```powershell
cd student-checkin
.\scripts\build-debug-apk.ps1
```

或直接进入 Android 工程目录：

```powershell
cd student-checkin\android-app
.\gradlew.bat assembleDebug
```

## Docker 部署说明

当前后端是精简的 2 容器方案：

1. PostgreSQL
2. API

不再依赖一整套 Supabase 容器，部署和维护都更简单。

详细文档：

- [开发环境说明](./student-checkin/docs/developer-setup.md)
- [家用服务器 Docker 部署](./student-checkin/docs/home-server-docker.md)

## 隐私与公开仓库说明

这份源码已经按公开仓库方式整理：

- 不包含你的内网 IP
- 不包含你的个人测试域名
- 不包含你的 SSH 密码
- 不包含固定的数据库密码或 JWT 密钥

别人拉取仓库后，只需要填写自己的：

- 后端地址
- 数据库密码
- JWT 密钥
- 反向代理 / 域名配置

## 推荐下一步

如果你准备长期维护这个项目，建议继续做这几件事：

1. 把 `feat/mvp-foundation` 合并或切换为 `main`
2. 在 GitHub 仓库页补充截图
3. 增加 Release 打包说明
4. 增加生产环境备份与恢复文档

