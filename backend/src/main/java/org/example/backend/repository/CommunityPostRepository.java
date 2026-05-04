// src/main/java/org/example/backend/repository/CommunityPostRepository.java
package org.example.backend.repository;

import org.example.backend.entity.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    // 根据用户ID查询帖子
    List<CommunityPost> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 热门帖子（按点赞数排序）
    List<CommunityPost> findTop10ByOrderByLikesDesc();

    // 根据标签搜索
    @Query("SELECT p FROM CommunityPost p WHERE p.tags LIKE %:tag% ORDER BY p.createdAt DESC")
    List<CommunityPost> findByTagContaining(@Param("tag") String tag);

    // 搜索帖子（标题或描述包含关键词）
    @Query("SELECT p FROM CommunityPost p WHERE p.title LIKE %:keyword% OR p.description LIKE %:keyword% ORDER BY p.createdAt DESC")
    List<CommunityPost> searchByKeyword(@Param("keyword") String keyword);

    // 查询所有帖子（按时间倒序）
    List<CommunityPost> findAllByOrderByCreatedAtDesc();
}