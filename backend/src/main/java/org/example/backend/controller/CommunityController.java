// src/main/java/org/example/backend/controller/CommunityController.java
package org.example.backend.controller;

import org.example.backend.dto.ApiResponse;
import org.example.backend.dto.CommunityPostRequest;
import org.example.backend.dto.CommunityPostResponse;
import org.example.backend.dto.CommentRequest;
import org.example.backend.dto.CommentResponse;
import org.example.backend.dto.PlanToPostRequest;
import org.example.backend.dto.ImageUploadResponse;
import org.example.backend.service.CommunityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

@RestController
@RequestMapping("/api/community")
@CrossOrigin(origins = "*")
public class CommunityController {

    @Autowired
    private CommunityService communityService;

    /**
     * 创建帖子
     */
    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<CommunityPostResponse>> createPost(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody CommunityPostRequest request) {
        try {
            CommunityPostResponse response = communityService.createPost(userId, request);
            return ResponseEntity.ok(ApiResponse.success(response, "创建成功"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 查询所有帖子
     */
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<List<CommunityPostResponse>>> getAllPosts() {
        try {
            List<CommunityPostResponse> posts = communityService.getAllPosts();
            return ResponseEntity.ok(ApiResponse.success(posts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取失败"));
        }
    }

    /**
     * 根据ID查询帖子
     */
    @GetMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<CommunityPostResponse>> getPostById(@PathVariable Long id) {
        try {
            CommunityPostResponse post = communityService.getPostById(id);
            return ResponseEntity.ok(ApiResponse.success(post));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 根据用户ID查询帖子
     */
    @GetMapping("/posts/user/{userId}")
    public ResponseEntity<ApiResponse<List<CommunityPostResponse>>> getPostsByUserId(
            @PathVariable Long userId) {
        try {
            List<CommunityPostResponse> posts = communityService.getPostsByUserId(userId);
            return ResponseEntity.ok(ApiResponse.success(posts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取失败"));
        }
    }

    /**
     * 热门帖子
     */
    @GetMapping("/posts/hot")
    public ResponseEntity<ApiResponse<List<CommunityPostResponse>>> getHotPosts() {
        try {
            List<CommunityPostResponse> posts = communityService.getHotPosts();
            return ResponseEntity.ok(ApiResponse.success(posts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取失败"));
        }
    }

    /**
     * 搜索帖子
     */
    @GetMapping("/posts/search")
    public ResponseEntity<ApiResponse<List<CommunityPostResponse>>> searchPosts(
            @RequestParam String keyword) {
        try {
            List<CommunityPostResponse> posts = communityService.search(keyword);
            return ResponseEntity.ok(ApiResponse.success(posts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("搜索失败"));
        }
    }

    /**
     * 根据标签搜索
     */
    @GetMapping("/posts/tag/{tag}")
    public ResponseEntity<ApiResponse<List<CommunityPostResponse>>> searchByTag(
            @PathVariable String tag) {
        try {
            List<CommunityPostResponse> posts = communityService.searchByTag(tag);
            return ResponseEntity.ok(ApiResponse.success(posts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取失败"));
        }
    }

    /**
     * 根据分类查询帖子
     */
    @GetMapping("/posts/category/{category}")
    public ResponseEntity<ApiResponse<List<CommunityPostResponse>>> getPostsByCategory(
            @PathVariable String category) {
        try {
            List<CommunityPostResponse> posts = communityService.getPostsByCategory(category);
            return ResponseEntity.ok(ApiResponse.success(posts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取失败"));
        }
    }

    /**
     * 更新帖子
     */
    @PutMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<CommunityPostResponse>> updatePost(
            @PathVariable Long id,
            @RequestBody CommunityPostRequest request) {
        try {
            CommunityPostResponse response = communityService.updatePost(id, request);
            return ResponseEntity.ok(ApiResponse.success(response, "更新成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 删除帖子
     */
    @DeleteMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable Long id) {
        try {
            communityService.deletePost(id);
            return ResponseEntity.ok(ApiResponse.success(null, "删除成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 点赞
     */
    @PostMapping("/posts/{id}/like")
    public ResponseEntity<ApiResponse<CommunityPostResponse>> likePost(@PathVariable Long id) {
        try {
            CommunityPostResponse response = communityService.likePost(id);
            return ResponseEntity.ok(ApiResponse.success(response, "点赞成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 将旅行规划转换为社区帖子
     */
    @PostMapping("/posts/from-plan")
    public ResponseEntity<ApiResponse<CommunityPostResponse>> planToPost(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody PlanToPostRequest request) {
        try {
            CommunityPostResponse response = communityService.planToPost(userId, request);
            return ResponseEntity.ok(ApiResponse.success(response, "发布成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("发布失败：" + e.getMessage()));
        }
    }

    /**
     * 批量上传图片
     */
    @PostMapping(value = "/posts/images/upload/batch", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<List<ImageUploadResponse>>> uploadImages(
            @RequestParam("files") MultipartFile[] files) {
        try {
            List<ImageUploadResponse> responses = communityService.uploadImages(files);
            return ResponseEntity.ok(ApiResponse.success(responses, "上传成功"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("上传失败：" + e.getMessage()));
        }
    }

    /**
     * 获取帖子评论
     */
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(@PathVariable Long postId) {
        try {
            List<CommentResponse> comments = communityService.getComments(postId);
            return ResponseEntity.ok(ApiResponse.success(comments));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取评论失败"));
        }
    }

    /**
     * 添加评论
     */
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable Long postId,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody CommentRequest request) {
        try {
            CommentResponse response = communityService.addComment(postId, userId, request);
            return ResponseEntity.ok(ApiResponse.success(response, "评论成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("评论失败"));
        }
    }
}