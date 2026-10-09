# Paperize-PikPak 使用说明（开发中）

目前这是对上游 Paperize 的开发分支，不是可以登录 PikPak 的成品。原版的本地相册和桌面/锁屏轮播功能仍应正常使用；我们没有更改原版行为。

## 预计使用流程（尚未可用）
1. 进入云端图库，使用 PikPak 支持的安全授权方式连接账号。
2. 在文件夹树中勾选一个或多个目录，设置是否递归及排除目录。
3. 点击同步：只拉取文件列表和图片元数据，**不下载整个图库**。
4. 在壁纸设置中选择云端相册，并选择桌面锁屏同图同步、独立轮播或仅锁屏。
5. 每次换图仅下载当前图片；命中私有缓存时无需联网。默认缓存 500MiB，独立于相册下载目录。

## 隐私与免费原则
- 严禁把 PikPak 密码、Cookie、Token 贴进 GitHub 仓库、PR、Issue 或聊天。
- 我们不会要求 Root、收费 WebDAV、常开电脑或服务器。
- PikPak 官方 Connected Apps 对免费账户存在流量配额，具体见 [官方 FAQ](https://mypikpak.com/en-US/faq)，不能保证无限速或无限量。
- 如锁屏在 ColorOS16 上未更新，请先核实系统是否允许本应用更改锁屏；不要尝试绕过系统权限。

## 开发状态
官方认证与真实图片下载尚未接通，不存在可验证的 PikPak 登录按钮或成品 APK。关注本仓库 PR 与 Actions；安装任何测试 APK 前确认其构建来源。

## 已构建的技术预览 APK（不含 PikPak 登录）

GitHub Actions [Run #37944089312](https://github.com/3ll3-3ll3/Paperize/actions/runs/37944089312) 上传了名称为 `paperize-pikpak-debug` 的 Artifact（对应 Commit `4dafff4`，不是最新代码）。在该运行页面 Artifacts 区下载 ZIP、解压并获取其中的 Debug APK。此 APK **不能**连接 PikPak 云端图库；它用于验证基础代码可编译，未经一加 Ace 2 实机验证。
