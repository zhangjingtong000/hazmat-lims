package com.qingyan.hazmat;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan({"com.qingyan.hazmat.system.mapper", "com.qingyan.hazmat.hazmat.mapper"})
public class HazmatApplication {
    public static void main(String[] args) {
        SpringApplication.run(HazmatApplication.class, args);
    }
}
