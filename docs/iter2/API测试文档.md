# API 接口测试记录

> 基于文档：`docs/api.md`
> 测试时间：2026-05-09
> 测试用户：testuser2 (userId=3, password=654321)

## 状态说明

- ✅ 通过
- ❌ 失败
- ⏳ 待测试
- ⚠️ 文档与实际不符

---

## 一、用户资料 `/api/profile`

| # | 接口 | 状态 | 备注 |
|---|------|------|------|
| 1.1 | GET /api/profile | ✅ | 返回正确，字段与文档一致 |
| 1.2 | PUT /api/profile | ✅ | 修改 username/email/phone/bio 均正常 |
| 1.3 | PUT /api/profile/password | ✅ | 旧密码错误/新密码过短/正常修改 三种场景均正确 |
| 1.4 | POST /api/profile/avatar | ❌ | 500 错误，`file.transferTo()` 失败 |
| 1.5 | GET /api/profile/avatars/{filename} | ⏳ | 依赖 1.4，无法独立验证 |

## 二、社区帖子 `/api/community/posts`

| # | 接口 | 状态 | 备注 |
|---|------|------|------|
| 2.1 | POST /api/community/posts | ✅ | 创建成功，返回完整帖子对象 |
| 2.2 | GET /api/community/posts | ✅ | 返回全部帖子列表 |
| 2.2 | GET /api/community/posts/hot | ✅ | 按热度排序，正常返回 |
| 2.2 | GET /api/community/posts/category/{category} | ✅ | 按分类过滤，正常返回 |
| 2.2 | GET /api/community/posts/search?keyword=xxx | ✅ | 按关键词搜索，正常返回 |
| 2.2 | GET /api/community/posts/tag/{tag} | ✅ | 按标签过滤，正常返回 |
| 2.2 | GET /api/community/posts/user/{userId} | ✅ | 按用户过滤，正常返回 |
| 2.3 | GET /api/community/posts/{postId}/comments | ✅ | 返回评论列表，字段与文档一致 |
| 2.4 | POST /api/community/posts/{postId}/comments | ✅ | 评论成功，avatar/username 自动从 users 表获取 |
| 2.5 | POST /api/community/posts/{postId}/like | ✅ | 点赞/取消点赞切换正常，计数正确 |
| 2.6 | POST /api/community/posts/{postId}/share | ✅ | 转发创建新帖子，originalPostId 正确关联 |
| 2.7 | POST /api/community/posts/from-plan | ⚠️ | planId 不存在时返回 404 + "旅行规划不存在"，功能正确但缺少测试数据 |

## 三、认证 `/api/auth`

| # | 接口 | 状态 | 备注 |
|---|------|------|------|
| 3.1 | POST /api/auth/login | ✅ | 认证成功返回 token + userId |
| 3.2 | POST /api/auth/register | ✅ | 注册成功返回 userId |

---

## 详细测试记录

### 3.1 登录

- ✅ 正确账密登录成功：`{"success":true,"token":"token_xxx","userId":3}`
- ✅ 错误密码返回：`{"success":false,"message":"Invalid username or password"}`

### 3.2 注册

- ✅ 注册新用户：`{"success":true,"userId":3}`

### 1.1 获取用户资料

```
GET /api/profile  Header: X-User-Id: 3
→ {"code":200,"data":{"id":3,"username":"testuser2","email":"test2@test.com","phone":"13800000002","profilePicUrl":null,"bio":"hello","createdAt":"..."}}
```

### 1.2 编辑资料

```
PUT /api/profile  Body: {username, email, phone, bio}
→ {"code":200,"message":"资料更新成功","data":{...}}
```

注意：请求体中的中文在 Windows bash 环境下可能导致 400 错误（编码问题），非接口本身 Bug。

### 1.3 修改密码

- ✅ 错误旧密码 → `{"code":400,"message":"旧密码不正确"}`
- ✅ 正确修改 → `{"code":200,"message":"Success","data":"密码修改成功"}`
- ✅ 新密码太短 → `{"code":400,"message":"新密码长度不能少于6位"}`

### 1.4 上传头像

```
POST /api/profile/avatar  FormData: file=test_avatar.png
→ {"code":400,"message":"头像上传失败"} HTTP 500
```

**Bug：** `file.transferTo(new File(avatarUploadDir, newFilename))` 在当前环境下抛异常。`avatarUploadDir` 默认值 `./uploads/avatars`，目录已成功创建但文件写入失败。可能是 Windows 路径或文件锁问题。

### 2.1 发布帖子

```
POST /api/community/posts  Body: {title, description, tags}
→ {"code":200,"message":"创建成功","data":{id:20,...}}
```

### 2.2 获取帖子列表

所有 6 种查询方式均通过：全部列表、热门、分类、搜索、标签、用户。

### 2.3 获取评论

```
GET /api/community/posts/20/comments
→ {"code":200,"data":[]}
```

### 2.4 发表评论

```
POST /api/community/posts/20/comments  Body: {content:"Great post!"}
→ {"code":200,"message":"评论成功","data":{"id":6,"content":"Great post!","avatar":null,"username":"testuser2",...}}
```

### 2.5 点赞

- ✅ 首次点赞 → likes: 0→1，message: "点赞成功"
- ✅ 再次点赞 → likes: 1→0，message: "取消点赞成功"

### 2.6 转发

- ✅ 转发成功，创建新帖子 id=21，`originalPostId=20`，原帖 shares+1

### 2.7 旅行规划转帖子

- ⚠️ 因测试数据库无 travel_plan 记录，返回 404 "旅行规划不存在"，符合预期

---

## 待修复问题

1. **头像上传 500 错误** — `POST /api/profile/avatar` 的 `file.transferTo()` 失败，需排查 Windows 环境下的文件写入路径问题
2. **1.4 HTTP 状态码不一致** — 接口返回 HTTP 500 但 body 中 `code:400`，controller catch 块应返回 `HttpStatus.INTERNAL_SERVER_ERROR` 而非 `badRequest()`
