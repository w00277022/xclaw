package com.xclaw.config;

import com.xclaw.service.UserService;
import com.xclaw.service.XclawNodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserService userService;
    private final XclawNodeService nodeService;

    @Override
    public void run(String... args) {
        if (userService.findByUsername("admin") == null) {
            userService.createUser("admin", "admin123", "系统管理员", "ADMIN", true, true);
            log.info("Default admin user created: admin/admin123");
        } else {
            log.info("Admin user already exists");
        }
        nodeService.ensureLocalNode();
    }
}
