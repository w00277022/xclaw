package com.xclaw.controller;

import com.xclaw.entity.XclawNode;
import com.xclaw.service.XclawNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
public class XclawNodeController {

    private final XclawNodeService nodeService;

    @GetMapping
    public ResponseEntity<List<XclawNode>> list() {
        return ResponseEntity.ok(nodeService.list());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody XclawNode node) {
        if (node.getName() == null || node.getHost() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "name and host are required"));
        }
        if (node.getPort() == null) node.setPort(22);
        if (node.getStatus() == null) node.setStatus("ONLINE");
        if (node.getIsLocal() == null) node.setIsLocal(false);
        nodeService.save(node);
        return ResponseEntity.ok(node);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody XclawNode updated) {
        XclawNode node = nodeService.getById(id);
        if (node == null) return ResponseEntity.notFound().build();
        if (updated.getName() != null) node.setName(updated.getName());
        if (updated.getHost() != null) node.setHost(updated.getHost());
        if (updated.getPort() != null) node.setPort(updated.getPort());
        if (updated.getSshUser() != null) node.setSshUser(updated.getSshUser());
        if (updated.getSshKey() != null) node.setSshKey(updated.getSshKey());
        if (updated.getSshPassword() != null) node.setSshPassword(updated.getSshPassword());
        if (updated.getStatus() != null) node.setStatus(updated.getStatus());
        nodeService.updateById(node);
        return ResponseEntity.ok(node);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        XclawNode node = nodeService.getById(id);
        if (node == null) return ResponseEntity.notFound().build();
        if (node.getIsLocal() != null && node.getIsLocal()) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能删除本机节点"));
        }
        nodeService.removeById(id);
        return ResponseEntity.ok(Map.of("status", "DELETED"));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<?> testConnection(@PathVariable Long id) {
        XclawNode node = nodeService.getById(id);
        if (node == null) return ResponseEntity.notFound().build();
        boolean ok = nodeService.testConnection(node);
        node.setStatus(ok ? "ONLINE" : "OFFLINE");
        nodeService.updateById(node);
        return ResponseEntity.ok(Map.of("connected", ok, "status", node.getStatus()));
    }
}
