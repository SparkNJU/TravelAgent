package org.example.backend.controller;

import org.example.backend.dto.*;
import org.example.backend.entity.User;
import org.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = "*")
public class ProfileController {

    @Autowired
    private UserService userService;

    @Value("${app.avatar.upload-dir:./uploads/avatars}")
    private String avatarUploadDir;

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        try {
            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            UserProfileResponse profile = new UserProfileResponse(
                    user.getId(), user.getUsername(), user.getEmail(),
                    user.getPhone(), user.getProfilePicUrl(), user.getCreatedAt());
            return ResponseEntity.ok(ApiResponse.success(profile));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody UpdateProfileRequest req) {
        try {
            User user = userService.updateProfile(userId, req);
            UserProfileResponse profile = new UserProfileResponse(
                    user.getId(), user.getUsername(), user.getEmail(),
                    user.getPhone(), user.getProfilePicUrl(), user.getCreatedAt());
            return ResponseEntity.ok(ApiResponse.success(profile, "资料更新成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody ChangePasswordRequest req) {
        if (req.getOldPassword() == null || req.getNewPassword() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("请填写完整信息"));
        }
        if (req.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body(ApiResponse.error("新密码长度不能少于6位"));
        }
        boolean success = userService.changePassword(userId, req.getOldPassword(), req.getNewPassword());
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("密码修改成功"));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("旧密码不正确"));
    }

    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<String>> uploadAvatar(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("请选择图片"));
            }
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(ApiResponse.error("只支持图片格式"));
            }
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(ApiResponse.error("图片大小不能超过5MB"));
            }

            File uploadPath = new File(avatarUploadDir);
            if (!uploadPath.exists()) {
                uploadPath.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "jpg";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
            }
            String newFilename = UUID.randomUUID().toString() + "." + extension;
            file.transferTo(new File(avatarUploadDir, newFilename));

            String avatarUrl = "/api/profile/avatars/" + newFilename;
            userService.updateProfilePicUrl(userId, avatarUrl);

            return ResponseEntity.ok(ApiResponse.success(avatarUrl, "头像上传成功"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("头像上传失败"));
        }
    }

    @GetMapping("/avatars/{filename}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        File file = new File(avatarUploadDir, filename);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                .body(resource);
    }
}
