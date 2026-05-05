package com.xclaw.controller;

import com.xclaw.entity.User;
import com.xclaw.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "仅管理员可查看用户列表"));
        }
        List<User> users = userService.list();
        // Hide passwords
        users.forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "仅管理员可添加用户"));
        }
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        String displayName = (String) body.getOrDefault("displayName", username);
        String userRole = (String) body.getOrDefault("role", "USER");
        Boolean canCreateOpenclaw = body.containsKey("canCreateOpenclaw") ? (Boolean) body.get("canCreateOpenclaw") : true;
        Boolean canCreateHermes = body.containsKey("canCreateHermes") ? (Boolean) body.get("canCreateHermes") : false;
        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名和密码不能为空"));
        }
        if (userService.findByUsername(username.trim()) != null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名已存在"));
        }
        User user = userService.createUser(username.trim(), password, displayName, userRole, canCreateOpenclaw, canCreateHermes);
        user.setPassword(null);
        return ResponseEntity.ok(Map.of("code", 200, "message", "用户创建成功", "user", user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "仅管理员可删除用户"));
        }
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId != null && currentUserId.equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "不能删除自己"));
        }
        User user = userService.getById(id);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }
        if ("admin".equals(user.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "不能删除默认管理员"));
        }
        userService.removeById(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "用户已删除"));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "仅管理员可修改角色"));
        }
        User user = userService.getById(id);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }
        String newRole = body.get("role");
        if (!"ADMIN".equals(newRole) && !"USER".equals(newRole)) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "角色必须为 ADMIN 或 USER"));
        }
        user.setRole(newRole);
        userService.updateById(user);
        return ResponseEntity.ok(Map.of("code", 200, "message", "角色已更新"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePermissions(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "仅管理员可修改用户信息"));
        }
        User user = userService.getById(id);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }
        if (body.containsKey("canCreateOpenclaw")) {
            user.setCanCreateOpenclaw((Boolean) body.get("canCreateOpenclaw"));
        }
        if (body.containsKey("canCreateHermes")) {
            user.setCanCreateHermes((Boolean) body.get("canCreateHermes"));
        }
        if (body.containsKey("displayName")) {
            user.setDisplayName((String) body.get("displayName"));
        }
        userService.updateById(user);
        return ResponseEntity.ok(Map.of("code", 200, "message", "用户信息已更新"));
    }
}
