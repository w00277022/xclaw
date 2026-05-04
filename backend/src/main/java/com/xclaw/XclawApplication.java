package com.xclaw;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xclaw.mapper")
public class XclawApplication {
    public static void main(String[] args) {
        SpringApplication.run(XclawApplication.class, args);
    }
}
