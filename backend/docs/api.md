# 后端API接口文档

> 版本：v1.0  
> 更新时间：2026-05-10  
> 基础URL：`http://localhost:8080`

---

## 目录

- [认证模块](#认证模块)
- [用户资料模块](#用户资料模块)
- [旅行规划模块](#旅行规划模块)
- [地图服务模块](#地图服务模块)
- [社区模块](#社区模块)
- [对话历史模块](#对话历史模块)
- [AI助手模块](#ai助手模块)

---

## 认证模块

### 1.1 用户注册

**接口地址：** `POST /api/auth/register`

**请求体：**
```json
{
  "username": "newuser",
  "password": "123456",
  "email": "user@email.com",
  "phone": "13800138000"
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名，必须唯一 |
| password | String | 是 | 密码，至少6位 |
| email | String | 是 | 邮箱，必须唯一 |
| phone | String | 否 | 手机号 |

**响应示例：**
```json
{
  "success": true,
  "message": "Registration successful",
  "userId": 5
}
```

**错误响应：**
```json
{
  "success": false,
  "message": "用户名已存在"
}
```

---

### 1.2 用户登录

**接口地址：** `POST /api/auth/login`

**请求体：**
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |

**响应示例：**
```json
{
  "success": true,
  "message": "Authentication successful",
  "token": "token_xxx_1",
  "userId": 1
}
```

---

## 用户资料模块

### 2.1 获取当前用户资料

**接口地址：** `GET /api/profile`

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户ID |

**响应示例：**
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

### 2.2 编辑用户资料

**接口地址：** `PUT /api/profile`

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户ID |

**请求体：**
```json
{
  "username": "新用户名",
  "email": "new@email.com",
  "phone": "13800138000",
  "bio": "个人简介"
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 否 | 必须唯一 |
| email | String | 否 | 必须唯一 |
| phone | String | 否 | 手机号 |
| bio | String | 否 | 个人简介，最长200字 |

**响应示例：**
```json
{
  "code": 200,
  "message": "资料更新成功",
  "data": {
    "id": 1,
    "username": "新用户名",
    "email": "new@email.com",
    "phone": "13800138000",
    "profilePicUrl": "/api/profile/avatars/xxx.jpg",
    "bio": "个人简介",
    "createdAt": "2026-05-01T10:00:00"
  }
}
```

---

### 2.3 修改密码

**接口地址：** `PUT /api/profile/password`

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户ID |

**请求体：**
```json
{
  "oldPassword": "原密码",
  "newPassword": "新密码"
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| oldPassword | String | 是 | 当前密码 |
| newPassword | String | 是 | 至少6位 |

**响应示例：**
```json
{
  "code": 200,
  "message": "密码修改成功",
  "data": "密码修改成功"
}
```

---

### 2.4 上传头像

**接口地址：** `POST /api/profile/avatar`

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户ID |

**请求体（FormData）：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 图片文件，仅支持image/*格式，最大5MB |

**响应示例：**
```json
{
  "code": 200,
  "message": "头像上传成功",
  "data": "/api/profile/avatars/uuid.jpg"
}
```

---

### 2.5 获取头像文件

**接口地址：** `GET /api/profile/avatars/{filename}`

**说明：** 直接返回图片二进制流，可用于`<img src="/api/profile/avatars/xxx.jpg">`

---

## 旅行规划模块

### 3.1 获取热门目的地

**接口地址：** `GET /api/travel/destinations/popular`

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": [
    {
      "id": 1,
      "name": "巴黎",
      "country": "法国",
      "description": "浪漫之都",
      "imageUrl": "https://example.com/paris.jpg",
      "rating": 4.8,
      "popularScore": 95
    }
  ]
}
```

---

### 3.2 搜索目的地

**接口地址：** `GET /api/travel/destinations/search`

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | String | 是 | 搜索关键词 |

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": [
    {
      "id": 1,
      "name": "东京",
      "country": "日本",
      "description": "现代与传统融合的城市",
      "imageUrl": "https://example.com/tokyo.jpg",
      "rating": 4.7,
      "popularScore": 88
    }
  ]
}
```

---

### 3.3 生成旅行计划

**接口地址：** `POST /api/travel/plan/generate`

**请求体：**
```json
{
  "destination": "东京",
  "days": 5,
  "budget": 10000,
  "preferences": ["美食", "购物"],
  "userId": 1
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| destination | String | 是 | 目的地 |
| days | Integer | 是 | 天数 |
| budget | Integer | 否 | 预算 |
| preferences | String[] | 否 | 偏好标签 |
| userId | Long | 是 | 用户ID |

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": 1,
    "title": "东京5日游",
    "destination": "东京",
    "days": 5,
    "itinerary": "Day 1: 抵达东京\nDay 2: 浅草寺\nDay 3: 涩谷\nDay 4: 新宿\nDay 5: 返程",
    "estimatedCost": 10000,
    "matchScore": 0.92,
    "highlights": ["浅草寺", "涩谷十字路口", "新宿御苑"]
  }
}
```

---

### 3.4 获取示例计划

**接口地址：** `GET /api/travel/plans/samples`

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": [
    {
      "id": 1,
      "title": "梦幻欧洲10日游",
      "destination": "法国",
      "days": 10,
      "itinerary": "Day 1-3: 巴黎\nDay 4-7: 瑞士\nDay 8-10: 意大利",
      "estimatedCost": 24999,
      "matchScore": 0.95,
      "highlights": ["埃菲尔铁塔", "卢浮宫", "阿尔卑斯山", "威尼斯"]
    }
  ]
}
```

---

### 3.5 保存旅行计划

**接口地址：** `POST /api/travel/plan/save`

**请求体：**
```json
{
  "title": "我的东京之旅",
  "destination": "东京",
  "days": 5,
  "itinerary": "详细行程...",
  "estimatedCost": 10000,
  "userId": 1
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | 是 | 计划标题 |
| destination | String | 是 | 目的地 |
| days | Integer | 是 | 天数 |
| itinerary | String | 否 | 详细行程 |
| estimatedCost | Integer | 否 | 预估费用 |
| userId | Long | 是 | 用户ID |

**响应示例：**
```json
{
  "code": 200,
  "message": "计划保存成功",
  "data": {
    "id": 1,
    "title": "我的东京之旅",
    "destination": "东京",
    "days": 5,
    "itinerary": "详细行程...",
    "estimatedCost": 10000,
    "matchScore": 0.0,
    "highlights": []
  }
}
```

---

### 3.6 获取用户保存的计划

**接口地址：** `GET /api/travel/plans/user/{userId}`

**路径参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户ID |

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": [
    {
      "id": 1,
      "title": "我的东京之旅",
      "destination": "东京",
      "days": 5,
      "itinerary": "详细行程...",
      "estimatedCost": 10000,
      "matchScore": 0.0,
      "highlights": []
    }
  ]
}
```

---

### 3.7 获取指定计划

**接口地址：** `GET /api/travel/plan/{planId}`

**路径参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| planId | Long | 是 | 计划ID |

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": 1,
    "title": "我的东京之旅",
    "destination": "东京",
    "days": 5,
    "itinerary": "详细行程...",
    "estimatedCost": 10000,
    "matchScore": 0.0,
    "highlights": []
  }
}
```

---

### 3.8 更新旅行计划

**接口地址：** `PUT /api/travel/plan/{planId}`

**路径参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| planId | Long | 是 | 计划ID |

**请求体：**
```json
{
  "title": "更新后的标题",
  "itinerary": "更新后的行程",
  "estimatedCost": 12000
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": 1,
    "title": "更新后的标题",
    "destination": "东京",
    "days": 5,
    "itinerary": "更新后的行程",
    "estimatedCost": 12000,
    "matchScore": 0.0,
    "highlights": []
  }
}
```

---

### 3.9 删除旅行计划

**接口地址：** `DELETE /api/travel/plan/{planId}`

**路径参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| planId | Long | 是 | 计划ID |

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户ID |

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": true
}
```

---

## 地图服务模块

### 4.1 搜索地点

**接口地址：** `GET /api/map/search`

**查询参数：**
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| keyword | String | 是 | - | 搜索关键词 |
| city | String | 否 | "全国" | 搜索城市 |

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "status": "1",
    "info": "OK",
    "pois": [
      {
        "name": "故宫博物院",
        "address": "北京市东城区景山前街4号",
        "location": "116.397128,39.917544",
        "tel": "010-85007421"
      }
    ]
  }
}
```

---

### 4.2 地理编码

**接口地址：** `GET /api/map/geocode`

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| address | String | 是 | 地址 |

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "status": "1",
    "info": "OK",
    "geocodes": [
      {
        "formatted_address": "北京市东城区景山前街4号",
        "location": "116.397128,39.917544"
      }
    ]
  }
}
```

---

## 社区模块

### 5.1 发布帖子

**接口地址：** `POST /api/community/posts`

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户ID |

**请求体：**
```json
{
  "title": "帖子标题",
  "description": "帖子内容",
  "images": ["data:image/png;base64,..."],
  "tags": ["东京", "攻略"]
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | 是 | 帖子标题 |
| description | String | 是 | 帖子内容 |
| images | String[] | 否 | 图片数组（base64或URL） |
| tags | String[] | 是 | 标签列表 |

**响应示例：**
```json
{
  "code": 200,
  "message": "帖子发布成功",
  "data": {
    "id": 1,
    "title": "帖子标题",
    "description": "帖子内容",
    "images": ["url1", "url2"],
    "avatar": "/api/profile/avatars/xxx.jpg",
    "username": "admin",
    "bio": "个人简介",
    "likes": 0,
    "comments": 0,
    "shares": 0,
    "tags": ["东京", "攻略"],
    "originalPostId": null,
    "userId": 1,
    "createdAt": "2026-05-10T10:00:00",
    "updatedAt": "2026-05-10T10:00:00"
  }
}
```

---

### 5.2 获取帖子列表

**接口地址：** 
- `GET /api/community/posts` - 获取所有帖子
- `GET /api/community/posts/hot` - 获取热门帖子
- `GET /api/community/posts/category/{category}` - 按分类获取
- `GET /api/community/posts/search?keyword=xxx` - 搜索帖子
- `GET /api/community/posts/tag/{tag}` - 按标签获取
- `GET /api/community/posts/user/{userId}` - 获取用户帖子

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": [
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
      "createdAt": "2026-05-08T10:00:00",
      "updatedAt": "2026-05-08T10:00:00"
    }
  ]
}
```

---

### 5.3 获取帖子评论

**接口地址：** `GET /api/community/posts/{postId}/comments`

**路径参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| postId | Long | 是 | 帖子ID |

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": [
    {
      "id": 1,
      "content": "太棒了！",
      "avatar": "/api/profile/avatars/xxx.jpg",
      "username": "admin",
      "createdAt": "2026-05-08T10:00:00"
    }
  ]
}
```

---

### 5.4 发表评论

**接口地址：** `POST /api/community/posts/{postId}/comments`

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户ID |

**请求体：**
```json
{
  "content": "评论内容"
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | String | 是 | 评论内容 |

**响应示例：**
```json
{
  "code": 200,
  "message": "评论成功",
  "data": {
    "id": 1,
    "content": "评论内容",
    "avatar": "/api/profile/avatars/xxx.jpg",
    "username": "admin",
    "createdAt": "2026-05-10T10:00:00"
  }
}
```

---

### 5.5 点赞帖子

**接口地址：** `POST /api/community/posts/{postId}/like`

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户ID |

**说明：** 再次调用取消点赞

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": true
}
```

---

### 5.6 转发帖子

**接口地址：** `POST /api/community/posts/{postId}/share`

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户ID |

**响应示例：**
```json
{
  "code": 200,
  "message": "转发成功",
  "data": {
    "id": 2,
    "title": "转发：原帖子标题",
    "description": "原帖子内容",
    "images": [],
    "avatar": "/api/profile/avatars/xxx.jpg",
    "username": "admin",
    "bio": "个人简介",
    "likes": 0,
    "comments": 0,
    "shares": 0,
    "tags": [],
    "originalPostId": 1,
    "userId": 1,
    "createdAt": "2026-05-10T10:00:00",
    "updatedAt": "2026-05-10T10:00:00"
  }
}
```

---

### 5.7 旅行规划转帖子

**接口地址：** `POST /api/community/posts/from-plan`

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户ID |

**请求体：**
```json
{
  "planId": 1,
  "title": "自定义标题（可选）"
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| planId | Long | 是 | 计划ID |
| title | String | 否 | 自定义标题 |

**响应示例：**
```json
{
  "code": 200,
  "message": "发布成功",
  "data": {
    "id": 1,
    "title": "我的东京旅行计划",
    "description": "详细行程...",
    "images": [],
    "avatar": "/api/profile/avatars/xxx.jpg",
    "username": "admin",
    "bio": "个人简介",
    "likes": 0,
    "comments": 0,
    "shares": 0,
    "tags": ["东京", "旅行计划"],
    "originalPostId": null,
    "userId": 1,
    "createdAt": "2026-05-10T10:00:00",
    "updatedAt": "2026-05-10T10:00:00"
  }
}
```

---

## 对话历史模块

### 6.1 获取对话列表

**接口地址：** `GET /api/conversations`

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户ID |

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": [
    {
      "id": 1,
      "title": "东京旅行规划",
      "createdAt": "2026-05-10T10:00:00",
      "updatedAt": "2026-05-10T11:00:00"
    }
  ]
}
```

---

### 6.2 获取对话详情

**接口地址：** `GET /api/conversations/{id}`

**路径参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 对话ID |

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户ID |

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": 1,
    "title": "东京旅行规划",
    "createdAt": "2026-05-10T10:00:00",
    "updatedAt": "2026-05-10T11:00:00",
    "messagesJson": "[{\"role\":\"user\",\"content\":\"帮我规划东京旅行\"}]",
    "resultJson": "{\"plan\":\"...\"}"
  }
}
```

---

### 6.3 保存对话

**接口地址：** `POST /api/conversations`

**请求体：**
```json
{
  "userId": 1,
  "title": "新对话",
  "messagesJson": "[{\"role\":\"user\",\"content\":\"...\"}]",
  "resultJson": "{\"result\":\"...\"}",
  "id": 1
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户ID |
| title | String | 否 | 对话标题，默认"新对话" |
| messagesJson | String | 否 | 消息JSON字符串 |
| resultJson | String | 否 | 结果JSON字符串 |
| id | Long | 否 | 对话ID，提供则更新 |

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": 1,
    "title": "新对话",
    "createdAt": "2026-05-10T10:00:00",
    "updatedAt": "2026-05-10T10:00:00",
    "messagesJson": "[{\"role\":\"user\",\"content\":\"...\"}]",
    "resultJson": "{\"result\":\"...\"}"
  }
}
```

---

### 6.4 更新对话

**接口地址：** `PUT /api/conversations/{id}`

**路径参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 对话ID |

**请求体：**
```json
{
  "userId": 1,
  "title": "更新后的标题",
  "messagesJson": "[{\"role\":\"user\",\"content\":\"...\"}]",
  "resultJson": "{\"result\":\"...\"}"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": 1,
    "title": "更新后的标题",
    "createdAt": "2026-05-10T10:00:00",
    "updatedAt": "2026-05-10T11:00:00",
    "messagesJson": "[{\"role\":\"user\",\"content\":\"...\"}]",
    "resultJson": "{\"result\":\"...\"}"
  }
}
```

---

### 6.5 删除对话

**接口地址：** `DELETE /api/conversations/{id}`

**路径参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 对话ID |

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户ID |

**响应示例：**
```json
{
  "code": 200,
  "message": "Success",
  "data": null
}
```

---

## AI助手模块

### 7.1 AI对话

**接口地址：** `POST /api/assistant/chat`

**请求头：**
| Header | 必填 | 说明 |
|--------|------|------|
| X-User-Id | 是 | 当前用户ID |

**请求体（FormData）：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | String | 是 | 用户问题 |
| file | File | 否 | 上传的文件 |
| mode | String | 否 | 运行模式，默认"agent" |
| generate_plan_first | Boolean | 否 | 是否先生成计划，默认true |

**响应：** 返回SSE流式数据

**SSE事件类型：**
| 事件类型 | 说明 |
|----------|------|
| plan | 计划生成过程 |
| thought | Agent思考过程 |
| tool_call | 工具调用 |
| tool_result | 工具返回结果 |
| message | 最终回复内容 |
| error | 错误信息 |
| [DONE] | 数据流结束 |

---

## 通用说明

### 认证方式

所有需要用户身份的接口通过`X-User-Id`请求头传递用户ID。

### 响应格式

**成功响应：**
```json
{
  "code": 200,
  "message": "Success",
  "data": {...}
}
```

**错误响应：**
```json
{
  "code": 400,
  "message": "错误描述",
  "data": null
}
```

### 错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 附录

### 数据库表结构

**users表：**
- id (PK)
- username (UNIQUE)
- password
- email (UNIQUE)
- phone
- profile_pic_url
- bio
- created_at
- updated_at

**travel_plans表：**
- id (PK)
- user_id (FK)
- title
- destination
- days
- itinerary
- estimated_cost
- match_score
- created_at
- updated_at

**community_posts表：**
- id (PK)
- user_id (FK)
- title
- description
- images (JSON)
- tags (JSON)
- original_post_id (FK)
- created_at
- updated_at

**comments表：**
- id (PK)
- post_id (FK)
- user_id (FK)
- content
- created_at

**like_records表：**
- id (PK)
- user_id (FK)
- post_id (FK)
- created_at

**chat_conversations表：**
- id (PK)
- user_id (FK)
- title
- messages_json (JSON)
- result_json (JSON)
- created_at
- updated_at