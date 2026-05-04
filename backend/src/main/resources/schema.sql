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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
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
    itinerary LONGTEXT,
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

-- Travel Plan Highlights Table
CREATE TABLE IF NOT EXISTS plan_highlights (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    highlight_text VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (plan_id) REFERENCES travel_plans(id) ON DELETE CASCADE,
    INDEX idx_plan_id (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Orders Table
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    plan_id BIGINT,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    total_amount DECIMAL(10, 2),
    status VARCHAR(20) DEFAULT 'pending',
    payment_method VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (plan_id) REFERENCES travel_plans(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_order_number (order_number),
    INDEX idx_status (status)
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
DROP TABLE IF EXISTS community_posts;
CREATE TABLE community_posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    images TEXT,
    avatar VARCHAR(100),
    nickname VARCHAR(50) NOT NULL,
    bio VARCHAR(200),
    likes INT NOT NULL DEFAULT 0,
    comments INT NOT NULL DEFAULT 0,
    shares INT NOT NULL DEFAULT 0,
    tags TEXT,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO community_posts (title, description, images, avatar, nickname, bio, likes, comments, shares, tags, user_id, created_at, updated_at) VALUES
('东京5日游｜人均8000玩遍热门景点', '分享我去年东京自由行的详细攻略，包括交通、住宿、美食推荐，性价比超高！', '["https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?w=600&h=400&fit=crop"]', '👩', '旅行达人小美', '走过30+国家的旅行博主', 2341, 156, 89, '东京,自由行,日本,攻略', 1, NOW(), NOW()),
('云南大理｜洱海边上的慢生活', '在大理住了一个月，发现了很多本地人都不知道的小众景点', '["https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=600&h=400&fit=crop"]', '🧑', '背包客阿杰', '记录在路上的每一天', 1892, 123, 67, '大理,洱海,云南,慢生活', 1, NOW(), NOW()),
('新疆伊犁｜夏天必去的草原天堂', '那拉提草原真的太美了！随手一拍都是壁纸级别的风景', '["https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=400&h=500&fit=crop","https://images.unsplash.com/photo-1448375240586-882707db888b?w=400&h=300&fit=crop"]', '👨', '摄影师老李', '专注自然风光摄影', 3456, 234, 156, '新疆,伊犁,草原,自然风光', 2, NOW(), NOW()),
('成都美食攻略｜本地人私藏的10家小店', '作为一个成都人，分享我平时最爱去的苍蝇馆子，味道绝了！', '["https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=400&h=400&fit=crop"]', '👩', '吃货小雯', '美食探店博主', 2890, 345, 178, '成都,美食,川菜,探店', 2, NOW(), NOW()),
('西藏拉萨｜心灵之旅', '终于实现了去西藏的梦想，布达拉宫真的太震撼了！', '["https://images.unsplash.com/photo-1530521954074-e64f6810b32d?w=400&h=600&fit=crop"]', '🧔', '行者无疆', '一生必去一次西藏', 4567, 321, 234, '西藏,拉萨,布达拉宫,心灵之旅', 1, NOW(), NOW()),
('重庆洪崖洞｜现实版千与千寻', '晚上的洪崖洞真的太梦幻了，仿佛走进了宫崎骏的动画世界', '["https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?w=400&h=500&fit=crop"]', '👨', '山城漫步', '重庆土著带你逛山城', 3210, 267, 189, '重庆,洪崖洞,夜景,千与千寻', 3, NOW(), NOW());

-- Comments Table
DROP TABLE IF EXISTS comments;
CREATE TABLE comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    avatar VARCHAR(100),
    nickname VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (post_id) REFERENCES community_posts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_post_id (post_id),
    INDEX idx_user_id (user_id),
    INDEX idx_comments_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert Sample Comments
INSERT IGNORE INTO comments (post_id, user_id, content, avatar, nickname, created_at, updated_at) VALUES
(1, 2, '太棒了！收藏了，下次去东京就靠这份攻略了', '👤', '用户', NOW(), NOW()),
(1, 3, '请问交通卡是在哪里买的？', '👤', '用户', NOW(), NOW()),
(2, 1, '好想去大理住一段时间', '👩', '旅行达人小美', NOW(), NOW()),
(3, 2, '草原真的好美！', '👤', '用户', NOW(), NOW()),
(4, 1, '求店名！', '👩', '旅行达人小美', NOW(), NOW()),
(5, 3, '布达拉宫一定要去！', '👤', '用户', NOW(), NOW()),
(6, 1, '夜景确实很美', '👩', '旅行达人小美', NOW(), NOW());

-- Create index for better query performance
-- Note: These indexes may already exist from table creation
-- Skipping duplicate index creation to avoid errors