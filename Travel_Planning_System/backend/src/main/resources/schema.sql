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
INSERT INTO users (username, password, email) VALUES 
('admin', '$2a$10$NV2o.nQkK8p/swp2g1S6s.zlAduL5vLRn1T06cKJLzVk8u8cH3DJO', 'admin@example.com'),
('user1', 'password123', 'user1@example.com');

INSERT INTO destinations (name, description, rating, review_count, image_url, country, region) VALUES
('巴黎', '浪漫之都，艺术与文化的结晶。参观埃菲尔铁塔，卢浮宫博物馆，欣赏塞纳河美景。', 4.8, 2350, 'https://picsum.photos/400/200?random=1', '法国', '欧洲'),
('东京', '现代与传统的完美融合。探索繁华街道、古寺庙宇和美食天堂。', 4.7, 1820, 'https://picsum.photos/400/200?random=2', '日本', '亚洲'),
('杭州', '人间天堂。西湖秀色、茶文化、丝绸之路的起点。', 4.6, 1550, 'https://picsum.photos/400/200?random=3', '中国', '亚洲'),
('巴厘岛', '热带天堂。美丽的海滩、古老的神庙和忠诚的文化体验。', 4.7, 2100, 'https://picsum.photos/400/200?random=4', '印度尼西亚', '亚洲'),
('纽约', '不夜城。摩天大楼、百老汇、美食街、人文艺术。', 4.6, 1890, 'https://picsum.photos/400/200?random=5', '美国', '北美'),
('新加坡', '狮城明珠。现代化城市，美食天堂，购物天地。', 4.8, 2050, 'https://picsum.photos/400/200?random=6', '新加坡', '亚洲');

-- Create index for better query performance
CREATE INDEX idx_created_at ON travel_plans(created_at DESC);
CREATE INDEX idx_updated_at ON destinations(updated_at DESC);
