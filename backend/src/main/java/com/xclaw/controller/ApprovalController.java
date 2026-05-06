package com.xclaw.controller;

import com.xclaw.entity.Approval;
import com.xclaw.entity.XclawInstance;
import com.xclaw.service.ApprovalService;
import com.xclaw.service.XclawInstanceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;
    private final XclawInstanceService instanceService;

    @GetMapping
    public ResponseEntity<List<Approval>> list(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(approvalService.listAll());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "仅管理员可审批"));
        }
        Long adminId = (Long) request.getAttribute("userId");
        String adminName = (String) request.getAttribute("username");
        Approval approval = approvalService.getById(id);
        if (approval == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "审批记录不存在"));
        }
        if (!"PENDING".equals(approval.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "该审批已处理"));
        }
        approval.setStatus("APPROVED");
        approval.setAdminId(adminId);
        approval.setAdminName(adminName);
        approvalService.updateById(approval);
        instanceService.approveAndStartInstance(approval.getInstanceId());
        return ResponseEntity.ok(Map.of("code", 200, "message", "已批准，实例正在启动"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "仅管理员可审批"));
        }
        Long adminId = (Long) request.getAttribute("userId");
        String adminName = (String) request.getAttribute("username");
        Approval approval = approvalService.getById(id);
        if (approval == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "审批记录不存在"));
        }
        if (!"PENDING".equals(approval.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "该审批已处理"));
        }
        approval.setStatus("REJECTED");
        approval.setAdminId(adminId);
        approval.setAdminName(adminName);
        approval.setRejectReason(body != null ? body.getOrDefault("reason", "") : "");
        approvalService.updateById(approval);
        XclawInstance instance = instanceService.getById(approval.getInstanceId());
        if (instance != null) {
            instance.setStatus("REJECTED");
            instance.setErrorMsg("审批被拒绝" + (approval.getRejectReason() != null && !approval.getRejectReason().isEmpty() ? "：" + approval.getRejectReason() : ""));
            instanceService.updateById(instance);
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "已拒绝"));
    }
}
