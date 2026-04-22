-- ============================================
-- Travel Planning System - MySQL Setup Script
-- ============================================
-- Run this script first to create the database and user

-- Create database
CREATE DATABASE IF NOT EXISTS travel_planning_db 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

-- Create user and grant privileges (optional, use if needed)
-- If you're using root user, skip this part
-- CREATE USER 'travelplan'@'localhost' IDENTIFIED BY 'your_password';
-- GRANT ALL PRIVILEGES ON travel_planning_db.* TO 'travelplan'@'localhost';
-- FLUSH PRIVILEGES;

-- Use the database
USE travel_planning_db;

-- Display confirmation
SELECT 'Database travel_planning_db created successfully!' AS status;
