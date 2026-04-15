-- Insert test user data
INSERT INTO users (username, password, email, phone) VALUES
('admin', '123456', 'admin@example.com', '+86-13800000000'),
('user1', '123456', 'user1@example.com', '+86-13800000001'),
('user2', '123456', 'user2@example.com', '+86-13800000002');

-- Insert destination data
INSERT INTO destinations (name, description, rating, review_count, image_url, country, region) VALUES
('巴黎', '浪漫之都，艺术与文化的结晶。参观埃菲尔铁塔，卢浮宫博物馆，欣赏塞纳河美景。', 4.8, 2350, 'https://picsum.photos/400/200?random=1', '法国', '欧洲'),
('东京', '现代与传统的完美融合。探索繁华街道、古寺庙宇和美食天堂。', 4.7, 1820, 'https://picsum.photos/400/200?random=2', '日本', '亚洲'),
('杭州', '人间天堂。西湖秀色、茶文化、丝绸之路的起点。', 4.6, 1550, 'https://picsum.photos/400/200?random=3', '中国', '亚洲'),
('巴厘岛', '热带天堂。美丽的海滩、古老的神庙和忠诚的文化体验。', 4.7, 2100, 'https://picsum.photos/400/200?random=4', '印度尼西亚', '亚洲'),
('纽约', '不夜城。摩天大楼、百老汇、美食街、人文艺术。', 4.6, 1890, 'https://picsum.photos/400/200?random=5', '美国', '北美'),
('新加坡', '狮城明珠。现代化城市，美食天堂，购物天地。', 4.8, 2050, 'https://picsum.photos/400/200?random=6', '新加坡', '亚洲');
