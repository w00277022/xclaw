package com.xclaw.controller;

import com.xclaw.dto.CreateXclawRequest;
import com.xclaw.entity.User;
import com.xclaw.entity.XclawInstance;
import com.xclaw.service.UserService;
import com.xclaw.service.XclawInstanceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/xclaw")
@RequiredArgsConstructor
public class XclawInstanceController {

    private final XclawInstanceService instanceService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateXclawRequest req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        String username = (String) request.getAttribute("username");
        try {
            return ResponseEntity.ok(instanceService.createInstance(req, userId, role, username));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<XclawInstance>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        return ResponseEntity.ok(instanceService.listAll(userId, role));
    }

    @GetMapping("/{id}")
    public ResponseEntity<XclawInstance> get(@PathVariable Long id) {
        return ResponseEntity.ok(instanceService.getById(id));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, String>> start(@PathVariable Long id) {
        instanceService.startInstance(id);
        return ResponseEntity.ok(Map.of("status", "STARTED"));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Map<String, String>> stop(@PathVariable Long id) {
        instanceService.stopInstance(id);
        return ResponseEntity.ok(Map.of("status", "STOPPED"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        instanceService.deleteInstance(id);
        return ResponseEntity.ok(Map.of("status", "DELETED"));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<Map<String, String>> syncStatus(@PathVariable Long id) {
        instanceService.syncStatus(id);
        return ResponseEntity.ok(Map.of("status", "SYNCED"));
    }

    @GetMapping("/allowed-types")
    public ResponseEntity<List<Map<String, Object>>> allowedTypes(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");

        List<Map<String, Object>> types = new ArrayList<>();

        // OpenClaw type
        Map<String, Object> openclaw = new LinkedHashMap<>();
        openclaw.put("type", "openclaw");
        openclaw.put("label", "OpenClaw");
        openclaw.put("icon", "🦞");
        openclaw.put("description", "标准 OpenClaw 智能助手实例");

        // Hermes type
        Map<String, Object> hermes = new LinkedHashMap<>();
        hermes.put("type", "hermes");
        hermes.put("label", "Hermes-Agent");
        hermes.put("icon", "🤖");
        hermes.put("description", "容器化 Hermes-Agent 实例");

        if ("ADMIN".equals(role)) {
            // Admin can create all types
            openclaw.put("allowed", true);
            hermes.put("allowed", true);
        } else {
            // Check user permissions
            boolean canOc = true;
            boolean canHe = false;
            if (userId != null) {
                User user = userService.getById(userId);
                if (user != null) {
                    canOc = user.getCanCreateOpenclaw() != null && user.getCanCreateOpenclaw();
                    canHe = user.getCanCreateHermes() != null && user.getCanCreateHermes();
                }
            }
            openclaw.put("allowed", canOc);
            hermes.put("allowed", canHe);
        }

        types.add(openclaw);
        types.add(hermes);
        return ResponseEntity.ok(types);
    }
}
