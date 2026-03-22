package com.example.ipchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IpChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(IpChatApplication.class, args);
    }
}
