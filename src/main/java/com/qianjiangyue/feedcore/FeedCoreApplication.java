package com.qianjiangyue.feedcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FeedCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeedCoreApplication.class, args);
    }

}
