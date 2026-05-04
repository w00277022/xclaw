package com.xclaw.controller;

import com.xclaw.entity.User;
import com.xclaw.service.UserService;
import com.xclaw.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名和密码不能为空"));
        }
        User user = userService.findByUsername(username);
        if (user == null || !userService.verifyPassword(password, user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "用户名或密码错误"));
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "登录成功",
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "displayName", user.getDisplayName(),
                        "role", user.getRole()
                )
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String displayName = body.getOrDefault("displayName", username);
        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名和密码不能为空"));
        }
        if (username.trim().length() < 3) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名至少3个字符"));
        }
        if (userService.findByUsername(username.trim()) != null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名已存在"));
        }
        User user = userService.createUser(username.trim(), password, displayName, "USER");
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "注册成功",
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "displayName", user.getDisplayName(),
                        "role", user.getRole()
                )
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        User user = userService.getById(userId);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "displayName", user.getDisplayName(),
                        "role", user.getRole()
                )
        ));
    }
}
