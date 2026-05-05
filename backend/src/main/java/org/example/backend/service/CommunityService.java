// src/main/java/org/example/backend/service/CommunityService.java
package org.example.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.dto.CommunityPostRequest;
import org.example.backend.dto.CommunityPostResponse;
import org.example.backend.dto.CommentRequest;
import org.example.backend.dto.CommentResponse;
import org.example.backend.dto.ImageUploadResponse;
import org.example.backend.dto.PlanToPostRequest;
import org.example.backend.entity.Comment;
import org.example.backend.entity.CommunityPost;
import org.example.backend.entity.LikeRecord;
import org.example.backend.entity.TravelPlan;
import org.example.backend.repository.CommentRepository;
import org.example.backend.repository.CommunityPostRepository;
import org.example.backend.repository.LikeRecordRepository;
import org.example.backend.repository.TravelPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import java.io.File;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommunityService {

    @Autowired
    private CommunityPostRepository postRepository;

    @Autowired
    private TravelPlanRepository travelPlanRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private LikeRecordRepository likeRecordRepository;

    // 直接创建 ObjectMapper 实例，不依赖 Spring 自动装配
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 图片存储路径（从配置文件读取）
    @Value("${app.image.upload-dir:./uploads}")
    private String uploadDir;

    // 图片访问基础URL
    @Value("${app.image.base-url:http://localhost:8080/api/community/posts/images/}")
    private String imageBaseUrl;

    /**
     * 创建帖子
     */
    @Transactional
    public CommunityPostResponse createPost(Long userId, CommunityPostRequest request) {
        CommunityPost post = new CommunityPost();
        post.setTitle(request.getTitle());
        post.setDescription(request.getDescription());
        post.setImages(convertListToJson(request.getImages()));
        post.setAvatar(request.getAvatar() != null ? request.getAvatar() : "👤");
        post.setNickname(request.getNickname() != null ? request.getNickname() : "用户");
        post.setBio(request.getBio());
        post.setTags(convertListToCsv(request.getTags()));
        post.setUserId(userId);
        post.setLikes(0);
        post.setComments(0);
        post.setShares(0);

        CommunityPost savedPost = postRepository.save(post);
        return convertToResponse(savedPost);
    }

    /**
     * 根据ID查询帖子
     */
    public CommunityPostResponse getPostById(Long id) {
        CommunityPost post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("帖子不存在"));
        return convertToResponse(post);
    }

    /**
     * 查询所有帖子
     */
    public List<CommunityPostResponse> getAllPosts() {
        return postRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 根据用户ID查询帖子
     */
    public List<CommunityPostResponse> getPostsByUserId(Long userId) {
        return postRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 热门帖子
     */
    public List<CommunityPostResponse> getHotPosts() {
        return postRepository.findTop10ByOrderByLikesDesc()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 根据标签搜索
     */
    public List<CommunityPostResponse> searchByTag(String tag) {
        return postRepository.findByTagContaining(tag)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 根据分类查询帖子
     */
    public List<CommunityPostResponse> getPostsByCategory(String category) {
        // 分类映射：将前端分类转换为标签关键词
        String tagKeyword = convertCategoryToTag(category);

        if (tagKeyword == null) {
            // 如果是"all"或者未知分类，返回所有帖子
            return getAllPosts();
        }

        return postRepository.findByTagContaining(tagKeyword)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 将分类转换为对应的标签关键词
     */
    private String convertCategoryToTag(String category) {
        switch (category.toLowerCase()) {
            case "food":
                return "美食";
            case "sight":
                return "景点";
            case "hotel":
                return "住宿";
            case "route":
                return "路线";
            case "tips":
                return "攻略";
            case "nature":
                return "自然风光";
            case "city":
                return "城市";
            case "family":
                return "亲子";
            case "couple":
                return "情侣";
            case "overseas":
                return "出境";
            case "selfdrive":
                return "自驾";
            case "free":
                return "自由行";
            case "all":
            default:
                return null;
        }
    }

    /**
     * 搜索帖子
     */
    public List<CommunityPostResponse> search(String keyword) {
        return postRepository.searchByKeyword(keyword)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 更新帖子
     */
    @Transactional
    public CommunityPostResponse updatePost(Long id, CommunityPostRequest request) {
        CommunityPost post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("帖子不存在"));

        if (request.getTitle() != null)
            post.setTitle(request.getTitle());
        if (request.getDescription() != null)
            post.setDescription(request.getDescription());
        if (request.getImages() != null)
            post.setImages(convertListToJson(request.getImages()));
        if (request.getAvatar() != null)
            post.setAvatar(request.getAvatar());
        if (request.getNickname() != null)
            post.setNickname(request.getNickname());
        if (request.getBio() != null)
            post.setBio(request.getBio());
        if (request.getTags() != null)
            post.setTags(convertListToCsv(request.getTags()));

        CommunityPost updatedPost = postRepository.save(post);
        return convertToResponse(updatedPost);
    }

    /**
     * 删除帖子
     */
    @Transactional
    public void deletePost(Long id) {
        if (!postRepository.existsById(id)) {
            throw new RuntimeException("帖子不存在");
        }
        postRepository.deleteById(id);
    }

    /**
     * 点赞/取消点赞
     */
    @Transactional
    public CommunityPostResponse likePost(Long postId, Long userId) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("帖子不存在"));

        Optional<LikeRecord> existingLike = likeRecordRepository.findByUserIdAndPostId(userId, postId);

        if (existingLike.isPresent()) {
            // 已点赞，取消点赞
            likeRecordRepository.delete(existingLike.get());
            post.setLikes(post.getLikes() - 1);
        } else {
            // 未点赞，进行点赞
            LikeRecord likeRecord = new LikeRecord();
            likeRecord.setUserId(userId);
            likeRecord.setPostId(postId);
            likeRecordRepository.save(likeRecord);
            post.setLikes(post.getLikes() + 1);
        }

        CommunityPost updatedPost = postRepository.save(post);
        return convertToResponse(updatedPost);
    }

    /**
     * 检查用户是否已点赞
     */
    public boolean isLiked(Long postId, Long userId) {
        return likeRecordRepository.existsByUserIdAndPostId(userId, postId);
    }

    /**
     * 将旅行规划转换为社区帖子
     */
    @Transactional
    public CommunityPostResponse planToPost(Long userId, PlanToPostRequest request) {
        // 查询旅行规划
        TravelPlan plan = travelPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new RuntimeException("旅行规划不存在"));

        // 创建社区帖子请求
        CommunityPostRequest postRequest = new CommunityPostRequest();

        // 使用自定义标题或生成标题
        String title = request.getTitle();
        if (title == null || title.isEmpty()) {
            title = generatePostTitle(plan);
        }
        postRequest.setTitle(title);

        // 从规划中提取描述
        String description = generatePostDescription(plan);
        postRequest.setDescription(description);

        // 设置图片（从规划的images字段提取）
        List<String> images = extractImagesFromPlan(plan);
        postRequest.setImages(images);

        // 设置用户信息
        postRequest.setAvatar(request.getAvatar() != null ? request.getAvatar() : "👤");
        postRequest.setNickname(request.getNickname() != null ? request.getNickname() : "用户");
        postRequest.setBio(request.getBio());

        // 生成标签
        List<String> tags = generateTags(plan);
        postRequest.setTags(tags);

        // 创建帖子
        return createPost(userId, postRequest);
    }

    /**
     * 生成帖子标题
     */
    private String generatePostTitle(TravelPlan plan) {
        String destination = plan.getDestinationName() != null ? plan.getDestinationName() : "旅行";
        Integer days = plan.getDays();

        if (days != null && days > 0) {
            return destination + days + "日游｜AI智能规划";
        }
        return destination + "旅行攻略｜AI智能规划";
    }

    /**
     * 生成帖子描述
     */
    private String generatePostDescription(TravelPlan plan) {
        StringBuilder sb = new StringBuilder();

        // 基本信息
        if (plan.getDestinationName() != null) {
            sb.append("📍 ").append(plan.getDestinationName());
        }
        if (plan.getDays() != null && plan.getDays() > 0) {
            sb.append(" · ").append(plan.getDays()).append("日行程");
        }
        sb.append("\n\n");

        // 从itinerary中提取摘要
        if (plan.getItinerary() != null && !plan.getItinerary().isEmpty()) {
            String summary = extractSummaryFromMarkdown(plan.getItinerary());
            sb.append(summary);
        } else {
            sb.append("使用AI旅行规划助手生成的专属行程攻略，包含详细的每日行程安排、景点推荐和美食推荐！");
        }

        sb.append("\n\n✨ 生成工具：旅行计划助手");

        return sb.toString();
    }

    /**
     * 从Markdown中提取摘要
     */
    private String extractSummaryFromMarkdown(String markdown) {
        String plainText = markdown
                .replaceAll("#+\\s+", "")
                .replaceAll("\\*\\*|__", "")
                .replaceAll("\\*|_", "")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                .replaceAll("\\n+", " ")
                .replaceAll("\\s+", " ").trim();

        if (plainText.length() > 200) {
            return plainText.substring(0, 200) + "...";
        }
        return plainText;
    }

    /**
     * 从规划中提取图片
     */
    private List<String> extractImagesFromPlan(TravelPlan plan) {
        // TravelPlan中没有images字段，返回空列表
        // 可以在后续版本中添加图片支持
        return Collections.emptyList();
    }

    /**
     * 生成标签
     */
    private List<String> generateTags(TravelPlan plan) {
        List<String> tags = new ArrayList<>();

        // 添加目的地标签
        if (plan.getDestinationName() != null) {
            tags.add(plan.getDestinationName());
        }

        // 添加天数标签
        if (plan.getDays() != null && plan.getDays() > 0) {
            tags.add(plan.getDays() + "日游");
        }

        // 添加固定标签
        tags.add("旅行攻略");
        tags.add("AI规划");
        tags.add("行程推荐");

        return tags;
    }

    /**
     * 转换实体为响应DTO
     */
    private CommunityPostResponse convertToResponse(CommunityPost post) {
        CommunityPostResponse response = new CommunityPostResponse();
        response.setId(post.getId());
        response.setTitle(post.getTitle());
        response.setDescription(post.getDescription());
        response.setImages(convertJsonToList(post.getImages()));
        response.setAvatar(post.getAvatar());
        response.setNickname(post.getNickname());
        response.setBio(post.getBio());
        response.setLikes(post.getLikes());
        response.setComments(post.getComments());
        response.setShares(post.getShares());
        response.setTags(convertCsvToList(post.getTags()));
        response.setUserId(post.getUserId());
        response.setCreatedAt(post.getCreatedAt());
        response.setUpdatedAt(post.getUpdatedAt());
        return response;
    }

    /**
     * List转JSON字符串
     */
    private String convertListToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * JSON字符串转List
     */
    private List<String> convertJsonToList(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /**
     * List转CSV字符串
     */
    private String convertListToCsv(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join(",", list);
    }

    /**
     * CSV字符串转List
     */
    private List<String> convertCsvToList(String csv) {
        if (csv == null || csv.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(csv.split(","));
    }

    /**
     * 获取帖子评论
     */
    public List<CommentResponse> getComments(Long postId) {
        return commentRepository.findByPostIdOrderByCreatedAtDesc(postId)
                .stream()
                .map(this::convertCommentToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 添加评论
     */
    @Transactional
    public CommentResponse addComment(Long postId, Long userId, CommentRequest request) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("帖子不存在"));
        post.setComments(post.getComments() + 1);
        postRepository.save(post);

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(request.getContent());
        comment.setAvatar(request.getAvatar() != null ? request.getAvatar() : "👤");
        comment.setNickname(request.getNickname() != null ? request.getNickname() : "用户");

        Comment savedComment = commentRepository.save(comment);
        return convertCommentToResponse(savedComment);
    }

    private CommentResponse convertCommentToResponse(Comment comment) {
        CommentResponse response = new CommentResponse();
        response.setId(comment.getId());
        response.setContent(comment.getContent());
        response.setAvatar(comment.getAvatar());
        response.setNickname(comment.getNickname());
        response.setCreatedAt(comment.getCreatedAt().toString());
        return response;
    }

    /**
     * 上传单张图片
     */
    public ImageUploadResponse uploadImage(MultipartFile file) throws Exception {
        // 验证文件
        validateImageFile(file);

        // 确保上传目录存在
        File uploadPath = new File(uploadDir);
        if (!uploadPath.exists()) {
            uploadPath.mkdirs();
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String newFilename = UUID.randomUUID().toString() + "." + extension;

        // 保存文件
        File destFile = new File(uploadDir, newFilename);
        file.transferTo(destFile);

        // 返回响应
        return new ImageUploadResponse(
                imageBaseUrl + newFilename,
                originalFilename,
                file.getSize());
    }

    /**
     * 批量上传图片
     */
    public List<ImageUploadResponse> uploadImages(MultipartFile[] files) throws Exception {
        List<ImageUploadResponse> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                responses.add(uploadImage(file));
            }
        }
        return responses;
    }

    /**
     * 验证图片文件
     */
    private void validateImageFile(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new Exception("请选择要上传的图片");
        }

        // 验证文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new Exception("只支持图片格式");
        }

        // 验证文件大小（最大5MB）
        long maxSize = 5 * 1024 * 1024; // 5MB
        if (file.getSize() > maxSize) {
            throw new Exception("图片大小不能超过5MB");
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "jpg";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}