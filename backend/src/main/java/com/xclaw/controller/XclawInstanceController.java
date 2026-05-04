package com.xclaw.controller;

import com.xclaw.dto.CreateXclawRequest;
import com.xclaw.entity.XclawInstance;
import com.xclaw.service.XclawInstanceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/xclaw")
@RequiredArgsConstructor
public class XclawInstanceController {

    private final XclawInstanceService instanceService;

    @PostMapping
    public ResponseEntity<XclawInstance> create(@RequestBody CreateXclawRequest req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        String username = (String) request.getAttribute("username");
        return ResponseEntity.ok(instanceService.createInstance(req, userId, role, username));
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
}
