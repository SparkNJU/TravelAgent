package org.example.backend.service;

import org.example.backend.dto.UpdateProfileRequest;
import org.example.backend.entity.User;
import org.example.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public Optional<User> authenticate(String username, String password) {
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isPresent() && password.equals(user.get().getPassword())) {
            return user;
        }
        return Optional.empty();
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public boolean usernameExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    public User registerUser(String username, String password, String email, String phone) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        user.setPhone(phone);
        return userRepository.save(user);
    }

    public User updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (req.getUsername() != null && !req.getUsername().equals(user.getUsername())) {
            if (usernameExists(req.getUsername())) {
                throw new RuntimeException("用户名已存在");
            }
            user.setUsername(req.getUsername());
        }

        if (req.getEmail() != null && !req.getEmail().equals(user.getEmail())) {
            if (req.getEmail().length() > 0 && emailExists(req.getEmail())) {
                throw new RuntimeException("邮箱已被使用");
            }
            user.setEmail(req.getEmail());
        }

        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }

        return userRepository.save(user);
    }

    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!user.getPassword().equals(oldPassword)) {
            return false;
        }
        user.setPassword(newPassword);
        userRepository.save(user);
        return true;
    }

    public void updateProfilePicUrl(Long userId, String url) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setProfilePicUrl(url);
        userRepository.save(user);
    }
}
