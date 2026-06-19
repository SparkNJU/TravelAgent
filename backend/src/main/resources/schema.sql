-- Travel Planning System Database Schema
-- Created for AgentLLM Travel Platform

-- Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    profile_pic_url VARCHAR(255),
    bio VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Chat Conversations Table (conversation archive)
CREATE TABLE IF NOT EXISTS chat_conversations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),
    messages_json LONGTEXT,
    result_json LONGTEXT,
    workbench_plan_id BIGINT,
    workbench_status VARCHAR(20) DEFAULT 'none',
    workbench_error VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_chat_conversations_user_id (user_id),
    INDEX idx_chat_conversations_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- User Agent Memory Table (long-term per-user memory, like AGENT.md)
CREATE TABLE IF NOT EXISTS user_agent_memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    memory_markdown LONGTEXT NOT NULL,
    memory_json JSON,
    memory_version VARCHAR(50) DEFAULT 'v1',
    source_conversation_id BIGINT,
    summary_source VARCHAR(50) DEFAULT 'conversation',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (source_conversation_id) REFERENCES chat_conversations(id) ON DELETE SET NULL,
    UNIQUE KEY uk_user_agent_memory_user_id (user_id),
    INDEX idx_user_agent_memory_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Public Knowledge Table (shareable knowledge extracted from conversations)
CREATE TABLE IF NOT EXISTS agent_public_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_key VARCHAR(128) NOT NULL,
    knowledge_title VARCHAR(255) NOT NULL,
    knowledge_content LONGTEXT NOT NULL,
    knowledge_json JSON,
    knowledge_scope VARCHAR(50) DEFAULT 'global',
    contributor_user_id BIGINT,
    source_conversation_id BIGINT,
    confidence_score DECIMAL(3, 2) DEFAULT 0.80,
    usage_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (contributor_user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (source_conversation_id) REFERENCES chat_conversations(id) ON DELETE SET NULL,
    UNIQUE KEY uk_agent_public_knowledge_key (knowledge_key),
    INDEX idx_agent_public_knowledge_scope (knowledge_scope),
    INDEX idx_agent_public_knowledge_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Memory Change Log Table (audit trail for memory updates)
CREATE TABLE IF NOT EXISTS agent_memory_change_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    memory_scope VARCHAR(30) NOT NULL,
    change_type VARCHAR(30) NOT NULL,
    target_key VARCHAR(128),
    source_conversation_id BIGINT,
    trigger_query TEXT,
    before_snapshot LONGTEXT,
    after_snapshot LONGTEXT,
    token_input INT,
    token_output INT,
    model_version VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (source_conversation_id) REFERENCES chat_conversations(id) ON DELETE SET NULL,
    INDEX idx_agent_memory_change_logs_user_id (user_id),
    INDEX idx_agent_memory_change_logs_scope (memory_scope),
    INDEX idx_agent_memory_change_logs_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Destinations Table
CREATE TABLE IF NOT EXISTS destinations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    rating DECIMAL(3, 2) DEFAULT 0,
    review_count INT DEFAULT 0,
    image_url VARCHAR(255),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    country VARCHAR(50),
    region VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_country (country)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Travel Plans Table
CREATE TABLE IF NOT EXISTS travel_plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    destination_id BIGINT,
    destination_name VARCHAR(100),
    days INT NOT NULL,
    estimated_budget DECIMAL(10, 2),
    ai_confidence_score DECIMAL(3, 2),
    interests VARCHAR(255),
    travel_style VARCHAR(50),
    status VARCHAR(20) DEFAULT 'draft',
    start_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_destination_id (destination_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Plan Activities Table
CREATE TABLE IF NOT EXISTS plan_activities (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT,
    day_number INT NOT NULL,
    activity_time VARCHAR(50),
    location_name VARCHAR(255) NOT NULL,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    description TEXT,
    tips TEXT,
    cost DECIMAL(10, 2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (plan_id) REFERENCES travel_plans(id) ON DELETE CASCADE,
    INDEX idx_plan_id (plan_id),
    INDEX idx_day_number (day_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Travel Plan Highlights Table
CREATE TABLE IF NOT EXISTS plan_highlights (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    highlight_text VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (plan_id) REFERENCES travel_plans(id) ON DELETE CASCADE,
    INDEX idx_plan_id (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- AI Planning History Table (for tracking AI requests)
CREATE TABLE IF NOT EXISTS ai_planning_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    request_params JSON,
    response_plan_id BIGINT,
    ai_model_version VARCHAR(50),
    execution_time_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Model Arena Votes Table (pairwise comparisons)
CREATE TABLE IF NOT EXISTS model_arena_votes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_a VARCHAR(60) NOT NULL,
    model_b VARCHAR(60) NOT NULL,
    result VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_model_a (model_a),
    INDEX idx_model_b (model_b)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert Sample Data
INSERT IGNORE INTO users (username, password, email) VALUES 
('admin', '$2a$10$NV2o.nQkK8p/swp2g1S6s.zlAduL5vLRn1T06cKJLzVk8u8cH3DJO', 'admin@example.com'),
('user1', 'password123', 'user1@example.com');

INSERT IGNORE INTO destinations (name, description, rating, review_count, image_url, country, region) VALUES
('巴黎', '浪漫之都，艺术与文化的结晶。参观埃菲尔铁塔，卢浮宫博物馆，欣赏塞纳河美景。', 4.8, 2350, 'https://picsum.photos/400/200?random=1', '法国', '欧洲'),
('东京', '现代与传统的完美融合。探索繁华街道、古寺庙宇和美食天堂。', 4.7, 1820, 'https://picsum.photos/400/200?random=2', '日本', '亚洲'),
('杭州', '人间天堂。西湖秀色、茶文化、丝绸之路的起点。', 4.6, 1550, 'https://picsum.photos/400/200?random=3', '中国', '亚洲'),
('巴厘岛', '热带天堂。美丽的海滩、古老的神庙和忠诚的文化体验。', 4.7, 2100, 'https://picsum.photos/400/200?random=4', '印度尼西亚', '亚洲'),
('纽约', '不夜城。摩天大楼、百老汇、美食街、人文艺术。', 4.6, 1890, 'https://picsum.photos/400/200?random=5', '美国', '北美'),
('新加坡', '狮城明珠。现代化城市，美食天堂，购物天地。', 4.8, 2050, 'https://picsum.photos/400/200?random=6', '新加坡', '亚洲');

-- Community Posts Table
CREATE TABLE IF NOT EXISTS community_posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    images TEXT,
    likes INT NOT NULL DEFAULT 0,
    comments INT NOT NULL DEFAULT 0,
    shares INT NOT NULL DEFAULT 0,
    tags TEXT,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO community_posts (title, description, images, likes, comments, shares, tags, user_id, created_at, updated_at) VALUES
('东京5日游｜人均8000玩遍热门景点', '分享我去年东京自由行的详细攻略，包括交通、住宿、美食推荐，性价比超高！', '["https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?w=600&h=400&fit=crop"]', 2341, 156, 89, '东京,自由行,日本,攻略', 1, NOW(), NOW()),
('云南大理｜洱海边上的慢生活', '在大理住了一个月，发现了很多本地人都不知道的小众景点', '["https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=600&h=400&fit=crop"]', 1892, 123, 67, '大理,洱海,云南,慢生活', 1, NOW(), NOW()),
('新疆伊犁｜夏天必去的草原天堂', '那拉提草原真的太美了！随手一拍都是壁纸级别的风景', '["https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=400&h=500&fit=crop","https://images.unsplash.com/photo-1448375240586-882707db888b?w=400&h=300&fit=crop"]', 3456, 234, 156, '新疆,伊犁,草原,自然风光', 2, NOW(), NOW()),
('成都美食攻略｜本地人私藏的10家小店', '作为一个成都人，分享我平时最爱去的苍蝇馆子，味道绝了！', '["https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=400&h=400&fit=crop"]', 2890, 345, 178, '成都,美食,川菜,探店', 2, NOW(), NOW()),
('西藏拉萨｜心灵之旅', '终于实现了去西藏的梦想，布达拉宫真的太震撼了！', '["https://images.unsplash.com/photo-1530521954074-e64f6810b32d?w=400&h=600&fit=crop"]', 4567, 321, 234, '西藏,拉萨,布达拉宫,心灵之旅', 1, NOW(), NOW()),
('重庆洪崖洞｜现实版千与千寻', '晚上的洪崖洞真的太梦幻了，仿佛走进了宫崎骏的动画世界', '["https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?w=400&h=500&fit=crop"]', 3210, 267, 189, '重庆,洪崖洞,夜景,千与千寻', 3, NOW(), NOW()),
('泰国清迈｜人均500玩转古城', '清迈真的太适合度假了！物价便宜，美食超多，泰式按摩一定要体验', '["https://images.unsplash.com/photo-1528181304800-259b08848526?w=400&h=400&fit=crop"]', 1567, 89, 54, '清迈,泰国,美食,度假', 2, NOW(), NOW()),
('三亚亲子游｜带娃必看攻略', '带两岁宝宝去三亚的经验分享，哪些景点适合带娃，哪些酒店有亲子设施', '["https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=400&h=300&fit=crop","https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?w=400&h=300&fit=crop"]', 2156, 167, 98, '三亚,亲子游,海南,带娃旅行', 1, NOW(), NOW()),
('西安兵马俑｜世界奇迹震撼人心', '亲眼看到兵马俑的那一刻真的被震撼到了，古人的智慧太伟大了', '["https://images.unsplash.com/photo-1552410262-d7663397e04f?w=400&h=400&fit=crop"]', 2678, 198, 134, '西安,兵马俑,历史,世界奇迹', 2, NOW(), NOW()),
('青岛啤酒节｜夏日狂欢盛宴', '每年夏天都要来青岛啤酒节，喝啤酒吃海鲜，太爽了！', '["https://images.unsplash.com/photo-1504681869696-d977211a5f4c?w=400&h=300&fit=crop"]', 1543, 89, 56, '青岛,啤酒节,夏天,狂欢', 1, NOW(), NOW()),
('京都秋日｜红叶季必去寺庙巡礼', '京都的红叶真的绝了，清水寺、岚山竹林、南禅寺每一个都美到窒息', '["https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=400&h=500&fit=crop","https://images.unsplash.com/photo-1545569341-9eb8b30979d9?w=400&h=400&fit=crop"]', 3789, 245, 167, '京都,红叶,日本,寺庙', 2, NOW(), NOW()),
('瑞士因特拉肯｜少女峰下的童话小镇', '阿尔卑斯山的壮丽景色配上瑞士的小镇风情，每一帧都是明信片', '["https://images.unsplash.com/photo-1531366936337-7c912a4589a7?w=400&h=300&fit=crop"]', 4123, 289, 201, '瑞士,因特拉肯,少女峰,欧洲', 1, NOW(), NOW()),
('厦门鼓浪屿｜文艺青年必打卡', '鼓浪屿的小巷子里藏着很多宝藏小店，适合慢悠悠地逛上一天', '["https://images.unsplash.com/photo-1504274066651-8d31a536b11a?w=400&h=400&fit=crop","https://images.unsplash.com/photo-1566438480900-0609be27a4be?w=400&h=300&fit=crop"]', 1234, 89, 45, '厦门,鼓浪屿,文艺,旅行', 2, NOW(), NOW()),
('日本大阪｜道顿堀美食地图', '大阪真的是美食天堂！章鱼烧、拉面、大阪烧，每一样都好吃到哭', '["https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=400&h=400&fit=crop"]', 2987, 201, 143, '大阪,美食,日本,道顿堀', 1, NOW(), NOW()),
('冰岛环岛｜追极光自驾攻略', '自驾环岛14天，看到了极光、冰川、黑沙滩，人生必去一次', '["https://images.unsplash.com/photo-1483347756197-71ef80e95f73?w=400&h=500&fit=crop","https://images.unsplash.com/photo-1476610182048-b716b8515aaa?w=400&h=300&fit=crop"]', 5678, 423, 312, '冰岛,极光,自驾,冰川', 2, NOW(), NOW()),
('杭州西湖｜最美不过西湖的秋天', '秋天的西湖真的太美了，桂花飘香，枫叶红了，一定要去一次', '["https://images.unsplash.com/photo-1599707367812-042b7e3a6345?w=400&h=300&fit=crop"]', 1890, 145, 78, '杭州,西湖,秋天,江南', 1, NOW(), NOW()),
('马尔代夫｜水屋攻略避坑指南', '去之前做了大量功课，选岛、选房型、预算，分享给大家避坑', '["https://images.unsplash.com/photo-1514282401047-d79a71a590e8?w=400&h=300&fit=crop"]', 3890, 278, 198, '马尔代夫,水屋,海岛,避坑', 2, NOW(), NOW()),
('长沙夜生活｜凌晨2点的解放西', '长沙的夜生活真的太丰富了，从太平街吃到解放西，凌晨都不想回家', '["https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400&h=400&fit=crop"]', 2345, 187, 121, '长沙,夜生活,美食,解放西', 1, NOW(), NOW()),
('新西兰南岛｜自驾环线全攻略', '14天自驾南岛，皇后镇、米尔福德峡湾、蒂卡普湖，美到不真实', '["https://images.unsplash.com/photo-1469521669194-babb45599def?w=400&h=300&fit=crop","https://images.unsplash.com/photo-1507699622108-4be3abd695ad?w=400&h=400&fit=crop"]', 4567, 334, 245, '新西兰,南岛,自驾,峡湾', 2, NOW(), NOW()),
('云南丽江｜古城之外的小众秘境', '大家都去古城，但其实丽江周边有很多更美的地方，比如白沙古镇、玉湖村', '["https://images.unsplash.com/photo-1528164344705-47542687000d?w=400&h=400&fit=crop"]', 1678, 112, 67, '丽江,小众,云南,秘境', 1, NOW(), NOW());

-- Comments Table
CREATE TABLE IF NOT EXISTS comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (post_id) REFERENCES community_posts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_post_id (post_id),
    INDEX idx_user_id (user_id),
    INDEX idx_comments_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert Sample Comments
INSERT IGNORE INTO comments (post_id, user_id, content, created_at, updated_at) VALUES
(1, 2, '太棒了！收藏了，下次去东京就靠这份攻略了', NOW(), NOW()),
(1, 3, '请问交通卡是在哪里买的？', NOW(), NOW()),
(2, 1, '好想去大理住一段时间', NOW(), NOW()),
(3, 2, '草原真的好美！', NOW(), NOW()),
(4, 1, '求店名！', NOW(), NOW()),
(5, 3, '布达拉宫一定要去！', NOW(), NOW()),
(6, 1, '夜景确实很美', NOW(), NOW());

-- Create index for better query performance
-- Note: These indexes may already exist from table creation
-- Skipping duplicate index creation to avoid errors