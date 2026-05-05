package com.xclaw.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xclaw.entity.User;
import com.xclaw.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    public User createUser(String username, String password, String displayName, String role) {
        return createUser(username, password, displayName, role, true, false);
    }

    public User createUser(String username, String password, String displayName, String role,
                           Boolean canCreateOpenclaw, Boolean canCreateHermes) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setRole(role != null ? role : "USER");
        user.setCanCreateOpenclaw(canCreateOpenclaw != null ? canCreateOpenclaw : true);
        user.setCanCreateHermes(canCreateHermes != null ? canCreateHermes : false);
        save(user);
        return user;
    }

    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
