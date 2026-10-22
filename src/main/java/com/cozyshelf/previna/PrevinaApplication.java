package com.cozyshelf.previna;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PrevinaApplication {
    public static void main(String[] args) {
        SpringApplication.run(PrevinaApplication.class, args);
    }
}
