<template>
  <div class="community-page">
    <!-- 搜索和分类区域 -->
    <header class="community-header">
      <!-- 发布按钮 -->
      <button class="upload-btn" @click="showUploadModal = true">
        <span>+</span> 发布
      </button>
      
      <!-- 搜索框 -->
      <div class="search-bar">
        <input 
          type="text" 
          v-model="searchQuery" 
          placeholder="搜索旅行攻略、目的地..."
          class="search-input"
        />
        <button class="search-btn" @click="handleSearch">搜索</button>
      </div>

      <!-- 分类标签 -->
      <div class="category-tabs">
        <button 
          v-for="cat in categories" 
          :key="cat.id"
          :class="['tab-item', { active: activeCategory === cat.id }]"
          @click="activeCategory = cat.id; handleCategoryChange()"
        >
          {{ cat.name }}
        </button>
      </div>
    </header>

    <!-- 内容区域 -->
    <main class="content-area">
      <!-- 精选推荐 -->
      <section class="featured-section" v-if="activeCategory === 'all'">
        <div class="section-header">
          <h3>热门推荐</h3>
          <span class="more-link">查看更多</span>
        </div>
        <div class="featured-grid">
          <div 
            v-for="post in featuredPosts" 
            :key="post.id" 
            class="featured-card"
            @click="openPost(post)"
          >
            <div class="featured-image" :style="{ backgroundImage: `url(${post.images[0]})` }">
              <div class="featured-overlay">
                <span class="featured-title">{{ post.title }}</span>
                <span class="featured-likes">{{ post.likes }} 喜欢</span>
              </div>
            </div>
            <div class="featured-content">
              <div class="user-info">
                <span class="user-avatar">{{ post.avatar }}</span>
                <span class="user-name">{{ post.nickname }}</span>
              </div>
              <p class="featured-desc">{{ post.description }}</p>
              <div class="post-tags">
                <span v-for="tag in post.tags" :key="tag" class="tag">#{{ tag }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 瀑布流内容 -->
      <div class="waterfall-container">
        <div class="waterfall-column" v-for="(column, index) in 3" :key="index">
          <article 
            v-for="post in getPostsForColumn(index)" 
            :key="post.id" 
            class="post-card"
            @click="openPost(post)"
          >
            <div class="post-images">
              <div 
                v-for="(img, i) in post.images" 
                :key="i"
                class="post-image"
                :class="{ 'single': post.images.length === 1 }"
                :style="{ backgroundImage: `url(${img})` }"
              ></div>
              <div v-if="post.images.length > 3" class="image-count">
                +{{ post.images.length - 3 }}
              </div>
            </div>
            <div class="post-content">
              <h4 class="post-title">{{ post.title }}</h4>
              <p class="post-desc">{{ post.description }}</p>
              <div class="post-tags">
                <span v-for="tag in post.tags.slice(0, 3)" :key="tag" class="tag">#{{ tag }}</span>
              </div>
              <div class="post-footer">
                <div class="user-info">
                  <span class="user-avatar">{{ post.avatar }}</span>
                  <span class="user-name">{{ post.nickname }}</span>
                </div>
                <div class="post-stats">
                  <span class="stat-item">{{ post.likes }} 喜欢</span>
                  <span class="stat-item">{{ post.comments }} 评论</span>
                  <span class="stat-item">{{ post.shares }} 分享</span>
                </div>
              </div>
            </div>
          </article>
        </div>
      </div>
    </main>



    <!-- 发布弹窗 -->
    <div v-if="showUploadModal" class="post-modal" @click="showUploadModal = false">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>发布帖子</h3>
          <button class="close-btn" @click="showUploadModal = false">关闭</button>
        </div>
        <div class="modal-body">
          <input type="text" v-model="newPost.title" placeholder="标题" class="post-input" />
          
          <!-- 图片上传 -->
          <div class="images-section">
            <label class="images-label">上传图片（最多9张）</label>
            <div class="images-preview">
              <div 
                v-for="(img, index) in newPost.images" 
                :key="index"
                class="image-preview-item"
              >
                <img :src="img" :alt="`图片${index + 1}`" />
                <button class="remove-image-btn" @click="removeImage(index)">删除</button>
              </div>
              <label 
                v-if="newPost.images.length < 9" 
                class="add-image-btn"
              >
                <input 
                  type="file" 
                  accept="image/*" 
                  multiple
                  @change="handleImageUpload" 
                  style="display: none;"
                />
                <span class="add-icon">+</span>
                <span class="add-text">添加图片</span>
              </label>
            </div>
          </div>
          
          <textarea v-model="newPost.description" placeholder="分享你的旅行故事..." class="post-textarea"></textarea>
          
          <!-- 标签选择 -->
          <div class="tags-section">
            <label class="tags-label">选择标签（可多选）</label>
            <div class="tags-grid">
              <button 
                v-for="tag in availableTags" 
                :key="tag.value"
                :class="['tag-btn', { selected: newPost.selectedTags.includes(tag.value) }]"
                @click="toggleTag(tag.value)"
              >
                {{ tag.label }}
              </button>
            </div>
            <input 
              type="text" 
              v-model="newPost.customTag" 
              placeholder="或输入自定义标签" 
              class="post-input custom-tag-input"
              @keydown.enter="addCustomTag"
            />
          </div>
        </div>
        <div class="modal-footer">
          <button class="action-btn cancel-btn" @click="showUploadModal = false">取消</button>
          <button class="action-btn submit-btn" @click="submitPost">发布</button>
        </div>
      </div>
    </div>

    <!-- 帖子详情弹窗 -->
    <div v-if="selectedPost" class="post-modal" @click="selectedPost = null">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <div class="user-info">
            <span class="user-avatar large">{{ selectedPost.avatar }}</span>
            <div>
              <span class="user-name">{{ selectedPost.nickname }}</span>
              <span class="user-bio">{{ selectedPost.bio }}</span>
            </div>
          </div>
          <button class="close-btn" @click="selectedPost = null">关闭</button>
        </div>
        <div class="modal-images">
          <img 
            v-for="(img, i) in selectedPost.images" 
            :key="i" 
            :src="img" 
            :alt="selectedPost.title"
            class="modal-image"
          />
        </div>
        <div class="modal-body">
          <h3>{{ selectedPost.title }}</h3>
          <p>{{ selectedPost.description }}</p>
          <div class="post-tags">
            <span v-for="tag in selectedPost.tags" :key="tag" class="tag">#{{ tag }}</span>
          </div>
        </div>
        
        <!-- 评论区域 -->
        <div class="comments-section">
          <div class="comments-header">
            <h4>评论 ({{ selectedPost.comments }})</h4>
          </div>
          <div class="comments-list">
            <div v-for="comment in comments" :key="comment.id" class="comment-item">
              <span class="comment-avatar">{{ comment.avatar }}</span>
              <div class="comment-content">
                <div class="comment-header">
                  <span class="comment-author">{{ comment.nickname }}</span>
                  <span class="comment-time">{{ comment.createdAt }}</span>
                </div>
                <p class="comment-text">{{ comment.content }}</p>
              </div>
            </div>
            <div v-if="comments.length === 0" class="no-comments">
              暂无评论，快来发表第一条评论吧！
            </div>
          </div>
          <div class="comment-input-section">
            <input 
              type="text" 
              v-model="newComment" 
              placeholder="写下你的评论..."
              class="comment-input"
              @keydown="handleCommentKeydown"
            />
            <button class="send-comment-btn" @click="submitComment">发送</button>
          </div>
        </div>
        
        <div class="modal-footer">
          <button class="action-btn like-btn">
            <span>点赞</span> {{ selectedPost.likes }}
          </button>
          <button class="action-btn comment-btn">
            <span>评论</span> {{ selectedPost.comments }}
          </button>
          <button class="action-btn share-btn">
            <span>分享</span> {{ selectedPost.shares }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const searchQuery = ref('')
const activeCategory = ref('all')

const categories = ref([
  { id: 'all', name: '全部' },
  { id: 'food', name: '美食' },
  { id: 'sight', name: '景点' },
  { id: 'hotel', name: '住宿' },
  { id: 'route', name: '路线' },
  { id: 'tips', name: '攻略' },
  { id: 'nature', name: '自然风光' },
  { id: 'city', name: '城市旅行' },
  { id: 'family', name: '亲子游' },
  { id: 'couple', name: '情侣游' },
  { id: 'overseas', name: '出境游' },
  { id: 'selfdrive', name: '自驾游' },
  { id: 'free', name: '自由行' }
])

// 分类变化时加载对应分类的帖子
const handleCategoryChange = async () => {
  try {
    const response = await fetch(`http://localhost:8080/api/community/posts/category/${activeCategory.value}`)
    const result = await response.json()
    if (result.code === 200) {
      allPosts.value = result.data
    }
  } catch (error) {
    console.error('加载分类帖子失败:', error)
  }
}

const featuredPosts = ref([
  {
    id: 1,
    title: '东京5日游｜人均8000玩遍热门景点',
    description: '分享我去年东京自由行的详细攻略，包括交通、住宿、美食推荐，性价比超高！',
    images: ['https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?w=600&h=400&fit=crop'],
    avatar: '',
    nickname: '旅行达人小美',
    bio: '走过30+国家的旅行博主',
    likes: 2341,
    comments: 156,
    shares: 89,
    tags: ['东京', '自由行', '日本', '攻略']
  },
  {
    id: 2,
    title: '云南大理｜洱海边上的慢生活',
    description: '在大理住了一个月，发现了很多本地人都不知道的小众景点',
    images: ['https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=600&h=400&fit=crop'],
    avatar: '',
    nickname: '背包客阿杰',
    bio: '记录在路上的每一天',
    likes: 1892,
    comments: 123,
    shares: 67,
    tags: ['大理', '洱海', '云南', '慢生活']
  },
  {
    id: 3,
    title: '泰国清迈｜人均500玩转古城',
    description: '清迈真的太适合度假了！物价便宜，美食超多，泰式按摩一定要体验',
    images: ['https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=600&h=400&fit=crop'],
    avatar: '',
    nickname: '小清新旅行',
    bio: '专注东南亚旅行攻略',
    likes: 1567,
    comments: 89,
    shares: 54,
    tags: ['清迈', '泰国', '美食', '度假']
  }
])

const allPosts = ref([
  {
    id: 4,
    title: '新疆伊犁｜夏天必去的草原天堂',
    description: '那拉提草原真的太美了！随手一拍都是壁纸级别的风景',
    images: [
      'https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=400&h=500&fit=crop',
      'https://images.unsplash.com/photo-1448375240586-882707db888b?w=400&h=300&fit=crop',
      'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400&h=300&fit=crop'
    ],
    avatar: '',
    nickname: '摄影师老李',
    bio: '专注自然风光摄影',
    likes: 3456,
    comments: 234,
    shares: 156,
    tags: ['新疆', '伊犁', '草原', '自然风光']
  },
  {
    id: 5,
    title: '成都美食攻略｜本地人私藏的10家小店',
    description: '作为一个成都人，分享我平时最爱去的苍蝇馆子，味道绝了！',
    images: ['https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=400&h=400&fit=crop'],
    avatar: '',
    nickname: '吃货小雯',
    bio: '美食探店博主',
    likes: 2890,
    comments: 345,
    shares: 178,
    tags: ['成都', '美食', '川菜', '探店']
  },
  {
    id: 6,
    title: '厦门鼓浪屿｜文艺青年必打卡',
    description: '鼓浪屿的小巷子里藏着很多宝藏小店，适合慢悠悠地逛上一天',
    images: [
      'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400&h=400&fit=crop',
      'https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?w=400&h=400&fit=crop'
    ],
    avatar: '',
    nickname: '文艺小青年',
    bio: '喜欢记录生活中的小美好',
    likes: 1234,
    comments: 89,
    shares: 45,
    tags: ['厦门', '鼓浪屿', '文艺', '旅行']
  },
  {
    id: 7,
    title: '三亚亲子游｜带娃必看攻略',
    description: '带两岁宝宝去三亚的经验分享，哪些景点适合带娃，哪些酒店有亲子设施',
    images: [
      'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400&h=500&fit=crop',
      'https://images.unsplash.com/photo-1448375240586-882707db888b?w=400&h=300&fit=crop'
    ],
    avatar: '',
    nickname: '宝妈旅行记',
    bio: '带着宝宝看世界',
    likes: 2156,
    comments: 167,
    shares: 98,
    tags: ['三亚', '亲子游', '海南', '带娃旅行']
  },
  {
    id: 8,
    title: '西藏拉萨｜心灵之旅',
    description: '终于实现了去西藏的梦想，布达拉宫真的太震撼了！',
    images: ['https://images.unsplash.com/photo-1530521954074-e64f6810b32d?w=400&h=600&fit=crop'],
    avatar: '',
    nickname: '行者无疆',
    bio: '一生必去一次西藏',
    likes: 4567,
    comments: 321,
    shares: 234,
    tags: ['西藏', '拉萨', '布达拉宫', '心灵之旅']
  },
  {
    id: 9,
    title: '杭州西湖｜最美不过西湖的秋天',
    description: '秋天的西湖真的太美了，桂花飘香，枫叶红了，一定要去一次',
    images: [
      'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400&h=400&fit=crop',
      'https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=400&h=400&fit=crop',
      'https://images.unsplash.com/photo-1448375240586-882707db888b?w=400&h=400&fit=crop',
      'https://images.unsplash.com/photo-154095973332-eab4deabeeaf?w=400&h=400&fit=crop'
    ],
    avatar: '',
    nickname: '江南烟雨',
    bio: '江南女子爱江南',
    likes: 1890,
    comments: 145,
    shares: 78,
    tags: ['杭州', '西湖', '秋天', '江南']
  },
  {
    id: 10,
    title: '重庆洪崖洞｜现实版千与千寻',
    description: '晚上的洪崖洞真的太梦幻了，仿佛走进了宫崎骏的动画世界',
    images: ['https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?w=400&h=500&fit=crop'],
    avatar: '',
    nickname: '山城漫步',
    bio: '重庆土著带你逛山城',
    likes: 3210,
    comments: 267,
    shares: 189,
    tags: ['重庆', '洪崖洞', '夜景', '千与千寻']
  },
  {
    id: 11,
    title: '西安兵马俑｜世界奇迹震撼人心',
    description: '亲眼看到兵马俑的那一刻真的被震撼到了，古人的智慧太伟大了',
    images: ['https://images.unsplash.com/photo-1552410262-d7663397e04f?w=400&h=400&fit=crop'],
    avatar: '',
    nickname: '历史爱好者',
    bio: '探寻历史的痕迹',
    likes: 2678,
    comments: 198,
    shares: 134,
    tags: ['西安', '兵马俑', '历史', '世界奇迹']
  },
  {
    id: 12,
    title: '青岛啤酒节｜夏日狂欢盛宴',
    description: '每年夏天都要来青岛啤酒节，喝啤酒吃海鲜，太爽了！',
    images: [
      'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400&h=300&fit=crop',
      'https://images.unsplash.com/photo-1448375240586-882707db888b?w=400&h=400&fit=crop'
    ],
    avatar: '',
    nickname: '啤酒小王子',
    bio: '喜欢喝啤酒的旅行博主',
    likes: 1543,
    comments: 89,
    shares: 56,
    tags: ['青岛', '啤酒节', '夏天', '狂欢']
  }
])

const selectedPost = ref(null)
const showUploadModal = ref(false)
const comments = ref([])
const newComment = ref('')

// 从后端加载帖子
const loadPosts = async () => {
  try {
    const response = await fetch('http://localhost:8080/api/community/posts')
    const result = await response.json()
    if (result.code === 200) {
      allPosts.value = result.data
    }
  } catch (error) {
    console.error('加载帖子失败:', error)
  }
}

// 页面加载时获取帖子
loadPosts()

// 可选标签列表
const availableTags = ref([
  { label: '🏮 美食', value: '美食' },
  { label: '景点', value: '景点' },
  { label: '🏨 住宿', value: '住宿' },
  { label: '路线', value: '路线' },
  { label: '📝 攻略', value: '攻略' },
  { label: '🌄 自然风光', value: '自然风光' },
  { label: '城市旅行', value: '城市旅行' },
  { label: '自由行', value: '自由行' },
  { label: '👨‍👩‍👧‍👦 亲子游', value: '亲子游' },
  { label: '💑 情侣游', value: '情侣游' },
  { label: '🌍 出境游', value: '出境游' },
  { label: '🚗 自驾游', value: '自驾游' }
])

const newPost = ref({
  title: '',
  description: '',
  selectedTags: [],
  customTag: '',
  images: []
})

// 切换标签选择
const toggleTag = (tag) => {
  const index = newPost.value.selectedTags.indexOf(tag)
  if (index > -1) {
    newPost.value.selectedTags.splice(index, 1)
  } else {
    if (newPost.value.selectedTags.length < 5) {
      newPost.value.selectedTags.push(tag)
    } else {
      alert('最多选择5个标签')
    }
  }
}

// 添加自定义标签
const addCustomTag = () => {
  const customTag = newPost.value.customTag.trim()
  if (customTag && !newPost.value.selectedTags.includes(customTag)) {
    if (newPost.value.selectedTags.length < 5) {
      newPost.value.selectedTags.push(customTag)
      newPost.value.customTag = ''
    } else {
      alert('最多选择5个标签')
    }
  }
}

// 处理图片上传
const handleImageUpload = (event) => {
  const files = event.target.files
  if (files) {
    const remainingSlots = 9 - newPost.value.images.length
    const filesToProcess = Array.from(files).slice(0, remainingSlots)
    
    filesToProcess.forEach(file => {
      const reader = new FileReader()
      reader.onload = (e) => {
        newPost.value.images.push(e.target.result)
      }
      reader.readAsDataURL(file)
    })
  }
}

// 删除图片
const removeImage = (index) => {
  newPost.value.images.splice(index, 1)
}

const submitPost = async () => {
  if (!newPost.value.title.trim()) {
    alert('请输入标题')
    return
  }
  if (!newPost.value.description.trim()) {
    alert('请输入内容')
    return
  }
  if (newPost.value.selectedTags.length === 0) {
    alert('请至少选择一个标签')
    return
  }
  
  try {
    // 调用后端API发布帖子
    const response = await fetch('http://localhost:8080/api/community/posts', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': '1' // 实际应用中应该从登录状态获取
      },
      body: JSON.stringify({
        title: newPost.value.title,
        description: newPost.value.description,
        images: newPost.value.images,
        avatar: '👤',
        nickname: '用户',
        bio: '',
        tags: newPost.value.selectedTags
      })
    })
    
    const result = await response.json()
    if (result.code === 200) {
      alert('发布成功！')
      // 刷新帖子列表
      loadPosts()
    } else {
      alert('发布失败：' + result.message)
    }
  } catch (error) {
    console.error('发布失败:', error)
    alert('发布失败，请稍后重试')
  }
  
  // 重置表单
  newPost.value = {
    title: '',
    description: '',
    selectedTags: [],
    customTag: '',
    images: []
  }
  showUploadModal.value = false
}

const filteredPosts = computed(() => {
  let posts = allPosts.value
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    posts = posts.filter(post => 
      post.title.toLowerCase().includes(query) ||
      post.description.toLowerCase().includes(query) ||
      post.tags.some(tag => tag.toLowerCase().includes(query))
    )
  }
  return posts
})

const getPostsForColumn = (columnIndex) => {
  return filteredPosts.value.filter((_, index) => index % 3 === columnIndex)
}

const handleSearch = async () => {
  if (!searchQuery.value.trim()) {
    // 如果搜索词为空，加载所有帖子
    loadPosts()
    return
  }
  
  try {
    const response = await fetch(`http://localhost:8080/api/community/posts/search?keyword=${encodeURIComponent(searchQuery.value)}`)
    const result = await response.json()
    if (result.code === 200) {
      allPosts.value = result.data
    } else {
      alert('搜索失败：' + result.message)
    }
  } catch (error) {
    console.error('搜索失败:', error)
    alert('搜索失败，请稍后重试')
  }
}

// 按回车键搜索
const handleKeydown = (e) => {
  if (e.key === 'Enter') {
    handleSearch()
  }
}

const openPost = async (post) => {
  selectedPost.value = post
  comments.value = []
  newComment.value = ''
  // 加载评论
  await loadComments(post.id)
}

// 加载评论
const loadComments = async (postId) => {
  try {
    const response = await fetch(`http://localhost:8080/api/community/posts/${postId}/comments`)
    const result = await response.json()
    if (result.code === 200) {
      comments.value = result.data
    }
  } catch (error) {
    console.error('加载评论失败:', error)
  }
}

// 发表评论
const submitComment = async () => {
  if (!newComment.value.trim()) {
    alert('请输入评论内容')
    return
  }
  if (!selectedPost.value) return
  
  try {
    const response = await fetch(`http://localhost:8080/api/community/posts/${selectedPost.value.id}/comments`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': '1'
      },
      body: JSON.stringify({
        content: newComment.value,
        avatar: '👤',
        nickname: '用户'
      })
    })
    const result = await response.json()
    if (result.code === 200) {
      comments.value.push({
        id: Date.now(),
        content: newComment.value,
        avatar: '👤',
        nickname: '用户',
        createdAt: '刚刚'
      })
      selectedPost.value.comments++
      newComment.value = ''
    } else {
      alert('发表评论失败：' + result.message)
    }
  } catch (error) {
    console.error('发表评论失败:', error)
    alert('发表评论失败，请稍后重试')
  }
}

const handleCommentKeydown = (e) => {
  if (e.key === 'Enter') {
    submitComment()
  }
}
</script>

<style scoped>
.community-page {
  min-height: 100vh;
  background: #f8fafc;
  padding-top: 60px;
}

/* 搜索和分类区域 */
.community-header {
  background: white;
  padding: 16px 20px;
  position: sticky;
  top: 60px;
  z-index: 100;
  border-bottom: 1px solid rgba(148, 163, 184, 0.1);
}

/* 发布按钮 */
.upload-btn {
  background: linear-gradient(135deg, #ff6b6b, #ee5a9b);
  color: white;
  border: none;
  border-radius: 999px;
  padding: 10px 20px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  box-shadow: 0 4px 16px rgba(238, 90, 155, 0.3);
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 900;
}

.upload-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(238, 90, 155, 0.4);
}

/* 搜索框 */
.search-bar {
  display: flex;
  align-items: center;
  background: white;
  border-radius: 4px;
  padding: 8px 14px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.search-icon {
  font-size: 16px;
  color: #999;
  margin-right: 10px;
}

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 14px;
  outline: none;
}

.search-btn {
  background: #ff6b35;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 6px 14px;
  font-weight: 600;
  cursor: pointer;
}

/* 分类标签 */
.category-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
  padding-bottom: 4px;
}

.category-tabs::-webkit-scrollbar {
  display: none;
}

.tab-item {
  flex-shrink: 0;
  background: rgba(255,255,255,0.9);
  border: 1px solid rgba(255,255,255,0.8);
  border-radius: 4px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  color: white;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-item.active {
  background: white;
  color: #ff6b35;
  border-color: white;
}

/* 精选推荐 */
.featured-section {
  padding: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h3 {
  font-size: 18px;
  font-weight: 700;
  margin: 0;
}

.more-link {
  color: #64748b;
  font-size: 14px;
}

.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.featured-card {
  background: white;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
}

.featured-image {
  height: 200px;
  background-size: cover;
  background-position: center;
  position: relative;
}

.featured-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  padding: 30px 16px 16px;
  color: white;
}

.featured-title {
  display: block;
  font-weight: 600;
  margin-bottom: 8px;
}

.featured-likes {
  font-size: 14px;
}

.featured-content {
  padding: 16px;
}

.featured-content .user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.user-avatar {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #ff6b6b, #ee5a9b);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.user-avatar.large {
  width: 56px;
  height: 56px;
  font-size: 28px;
}

.user-name {
  font-weight: 600;
  color: #1e293b;
}

.user-bio {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.featured-desc {
  font-size: 14px;
  color: #475569;
  line-height: 1.5;
  margin-bottom: 12px;
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  background: rgba(238, 90, 155, 0.1);
  color: #ee5a9b;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
}

/* 瀑布流 */
.waterfall-container {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding: 0 20px;
}

.waterfall-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.post-card {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.04);
  transition: all 0.3s ease;
}

.post-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}

.post-images {
  position: relative;
}

.post-image {
  width: 100%;
  aspect-ratio: 1;
  background-size: cover;
  background-position: center;
}

.post-image.single {
  aspect-ratio: 4/3;
}

.images-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px;
}

.images-3 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px;
}

.images-3 .post-image:nth-child(1) {
  grid-row: span 2;
}

.image-count {
  position: absolute;
  bottom: 4px;
  right: 4px;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.post-content {
  padding: 14px;
}

.post-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 8px;
  color: #1e293b;
}

.post-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  margin: 0 0 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.1);
}

.post-footer .user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.post-footer .user-avatar {
  width: 28px;
  height: 28px;
  font-size: 14px;
}

.post-footer .user-name {
  font-size: 13px;
}

.post-stats {
  display: flex;
  gap: 16px;
}

.stat-item {
  font-size: 12px;
  color: #64748b;
}



/* 弹窗 */
.post-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.modal-content {
  background: white;
  border-radius: 24px;
  max-width: 600px;
  width: 100%;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.1);
}

.modal-header .user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.close-btn {
  background: #f1f5f9;
  border: none;
  border-radius: 50%;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.modal-images {
  display: flex;
  overflow-x: auto;
}

.modal-image {
  flex-shrink: 0;
  width: 100%;
  max-width: 600px;
  height: 400px;
  object-fit: cover;
}

.modal-body {
  padding: 20px;
}

.modal-body h3 {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 12px;
}

.modal-body p {
  font-size: 15px;
  line-height: 1.6;
  color: #475569;
  margin: 0 0 16px;
}

.modal-footer {
  display: flex;
  justify-content: space-around;
  padding: 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.1);
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  font-size: 16px;
  cursor: pointer;
  padding: 12px 24px;
  border-radius: 999px;
  transition: all 0.3s ease;
}

.action-btn:hover {
  background: #f1f5f9;
}

.like-btn {
  color: #ef4444;
}

.comment-btn {
  color: #3b82f6;
}

.share-btn {
  color: #10b981;
}

.cancel-btn {
  background: #f1f5f9;
}

.submit-btn {
  background: linear-gradient(135deg, #ff6b6b, #ee5a9b);
  color: white;
}

.post-input {
  width: 100%;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  margin-bottom: 12px;
  font-size: 14px;
  outline: none;
}

.post-textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  margin-bottom: 12px;
  font-size: 14px;
  outline: none;
  height: 100px;
  resize: none;
}

/* 标签选择区域 */
.tags-section {
  margin-bottom: 12px;
}

.tags-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 10px;
}

.tags-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.tag-btn {
  padding: 8px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background: white;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.tag-btn:hover {
  border-color: #ee5a9b;
  color: #ee5a9b;
}

.tag-btn.selected {
  background: linear-gradient(135deg, #ff6b6b, #ee5a9b);
  border-color: #ee5a9b;
  color: white;
}

.custom-tag-input {
  margin-bottom: 0;
}

/* 图片上传区域 */
.images-section {
  margin-bottom: 12px;
}

.images-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 10px;
}

.images-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.image-preview-item {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
}

.image-preview-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remove-image-btn {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 20px;
  height: 20px;
  background: #ef4444;
  color: white;
  border: none;
  border-radius: 50%;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-image-btn {
  width: 80px;
  height: 80px;
  border: 2px dashed #cbd5e1;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.add-image-btn:hover {
  border-color: #ee5a9b;
  background: rgba(238, 90, 155, 0.05);
}

.add-icon {
  font-size: 28px;
  color: #94a3b8;
  line-height: 1;
}

.add-text {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

.comment-input-section {
  background: #f1f5f9;
}

.submit-btn {
  background: linear-gradient(135deg, #ff6b6b, #ee5a9b);
  color: white;
}

.post-input {
  width: 100%;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  margin-bottom: 12px;
  font-size: 14px;
  outline: none;
}

.post-textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  margin-bottom: 12px;
  font-size: 14px;
  outline: none;
  height: 100px;
  resize: none;
}

/* 评论区域样式 */
.comments-section {
  padding: 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.1);
}

.comments-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.comments-header h4 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: #1e293b;
}

.comments-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 300px;
  overflow-y: auto;
}

.comments-list::-webkit-scrollbar {
  width: 4px;
}

.comments-list::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 2px;
}

.comments-list::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 2px;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 16px;
  transition: all 0.3s ease;
}

.comment-item:hover {
  background: #f1f5f9;
}

.comment-avatar {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #ff6b6b, #ee5a9b);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.comment-content {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.comment-author {
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
}

.comment-time {
  font-size: 12px;
  color: #94a3b8;
}

.comment-text {
  font-size: 14px;
  color: #475569;
  line-height: 1.5;
  margin: 0;
  word-break: break-word;
}

.no-comments {
  text-align: center;
  padding: 32px;
  color: #94a3b8;
  font-size: 14px;
}

.comment-input-section {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.1);
}

.comment-input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  font-size: 14px;
  outline: none;
  transition: all 0.2s ease;
  background: white;
}

.comment-input:focus {
  border-color: #ff6b35;
  box-shadow: 0 0 0 2px rgba(255, 107, 53, 0.1);
}

.send-comment-btn {
  background: linear-gradient(135deg, #ff6b35, #f7931e);
  color: white;
  border: none;
  border-radius: 4px;
  padding: 10px 20px;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.send-comment-btn:hover {
  opacity: 0.9;
}

/* 响应式 */
@media (max-width: 768px) {
  .waterfall-container {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .featured-grid {
    grid-template-columns: 1fr;
  }
  
  .modal-image {
    height: 300px;
  }
}

@media (max-width: 480px) {
  .waterfall-container {
    grid-template-columns: 1fr;
  }
  
  .logo-text {
    font-size: 20px;
  }
  
  .section-header h3 {
    font-size: 16px;
  }
}
</style>