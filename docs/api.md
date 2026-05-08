# API 接口文档

> 最近更新：2026-05-08

---

## 一、用户资料 `/api/profile`

### 1.1 获取当前用户资料

```
GET /api/profile
```

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户 ID |

**响应：**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": 1,
    "username": "admin",
    "email": "admin@example.com",
    "phone": "13800138000",
    "profilePicUrl": "/api/profile/avatars/xxx.jpg",
    "bio": "走过30+国家的旅行博主",
    "createdAt": "2026-05-01T10:00:00"
  }
}
```

---

### 1.2 编辑资料

```
PUT /api/profile
```

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户 ID |

**请求体：**
```json
{
  "username": "新用户名",
  "email": "new@email.com",
  "phone": "13800138000",
  "bio": "个人简介"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 否 | 必须唯一，与其他用户不可重复 |
| email | String | 否 | 必须唯一 |
| phone | String | 否 | |
| bio | String | 否 | 个人简介，最长 200 字 |

**响应：** 同 1.1 的 data 结构，成功时 `message` 为 `"资料更新成功"`

**错误情况：**
- `"用户名已存在"` — username 被占用
- `"邮箱已被使用"` — email 被占用
- `"用户不存在"` — userId 无效

---

### 1.3 修改密码

```
PUT /api/profile/password
```

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户 ID |

**请求体：**
```json
{
  "oldPassword": "原密码",
  "newPassword": "新密码"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| oldPassword | String | 是 | 当前密码 |
| newPassword | String | 是 | 至少 6 位 |

**成功响应：**
```json
{ "code": 200, "message": "密码修改成功", "data": "密码修改成功" }
```

**错误情况：**
- `"旧密码不正确"`
- `"新密码长度不能少于6位"`

---

### 1.4 上传头像

```
POST /api/profile/avatar
Content-Type: multipart/form-data
```

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户 ID |

**请求体（FormData）：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 图片文件，仅支持 image/* 格式，最大 5MB |

**成功响应：**
```json
{ "code": 200, "message": "头像上传成功", "data": "/api/profile/avatars/uuid.jpg" }
```

---

### 1.5 获取头像文件

```
GET /api/profile/avatars/{filename}
```

直接返回图片二进制流，可用于 `<img src="/api/profile/avatars/xxx.jpg">`。

---

## 二、社区帖子 `/api/community/posts`（已修改）

### 2.1 发布帖子

```
POST /api/community/posts
```

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户 ID |

**请求体：**
```json
{
  "title": "帖子标题",
  "description": "帖子内容",
  "images": ["data:image/png;base64,..."],
  "tags": ["东京", "攻略"]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | 是 | |
| description | String | 是 | |
| images | String[] | 否 | 图片数组（base64 或 URL） |
| tags | String[] | 是 | 标签列表 |

**注意：** `avatar`、`nickname`、`bio` 字段已移除，后端自动从 users 表获取。

---

### 2.2 获取帖子列表

```
GET /api/community/posts
GET /api/community/posts/hot
GET /api/community/posts/category/{category}
GET /api/community/posts/search?keyword=xxx
GET /api/community/posts/tag/{tag}
GET /api/community/posts/user/{userId}
```

**响应中的帖子对象（已修改）：**
```json
{
  "id": 1,
  "title": "东京5日游",
  "description": "...",
  "images": ["url1", "url2"],
  "avatar": "/api/profile/avatars/xxx.jpg",
  "username": "admin",
  "bio": "个人简介",
  "likes": 234,
  "comments": 15,
  "shares": 8,
  "tags": ["东京", "攻略"],
  "originalPostId": null,
  "userId": 1,
  "createdAt": "...",
  "updatedAt": "..."
}
```

**变更说明：** `nickname` 已改为 `username`，`avatar` 和 `bio` 现在从 users 表关联获取。

---

### 2.3 获取帖子评论

```
GET /api/community/posts/{postId}/comments
```

**响应中的评论对象（已修改）：**
```json
{
  "id": 1,
  "content": "太棒了！",
  "avatar": "/api/profile/avatars/xxx.jpg",
  "username": "admin",
  "createdAt": "2026-05-08T10:00:00"
}
```

**变更说明：** `nickname` 已改为 `username`，`avatar` 从 users 表获取。

---

### 2.4 发表评论

```
POST /api/community/posts/{postId}/comments
```

**请求体：**
```json
{
  "content": "评论内容"
}
```

**注意：** `avatar`、`nickname` 字段已移除，后端自动从 users 表获取。

---

### 2.5 点赞

```
POST /api/community/posts/{postId}/like
Header: X-User-Id
```

无请求体。再次调用取消点赞。

---

### 2.6 转发

```
POST /api/community/posts/{postId}/share
Header: X-User-Id
```

无请求体。

---

### 2.7 旅行规划转帖子

```
POST /api/community/posts/from-plan
Header: X-User-Id
```

**请求体：**
```json
{
  "planId": 1,
  "title": "自定义标题（可选）"
}
```

**注意：** `avatar`、`nickname`、`bio` 字段已移除。

---

## 三、认证 `/api/auth`（未修改，供参考）

### 3.1 登录

```
POST /api/auth/login
```

```json
{ "username": "admin", "password": "admin123" }
```

**响应：**
```json
{ "success": true, "message": "Authentication successful", "token": "token_xxx_1", "userId": 1 }
```

### 3.2 注册

```
POST /api/auth/register
```

```json
{ "username": "newuser", "password": "123456", "email": "user@email.com", "phone": "13800138000" }
```

**响应：**
```json
{ "success": true, "message": "Registration successful", "userId": 5 }
```

---

## 四、通用说明

### 认证方式
所有需要用户身份的接口通过 `X-User-Id` 请求头传递用户 ID。

### 响应格式
- 社区/资料接口：`{ "code": 200, "message": "...", "data": {...} }`
- 认证接口：`{ "success": true/false, "message": "...", ... }`
- 错误时 `code` 为 `400`，`message` 包含错误描述

### 用户信息关联（重构后）
帖子/评论的 `avatar`、`username`、`bio` 不再存储在帖子/评论表中，而是通过 `user_id` 关联 `users` 表实时获取：
- `avatar` ← `users.profile_pic_url`
- `username` ← `users.username`
- `bio` ← `users.bio`
