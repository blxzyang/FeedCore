package com.qianjiangyue.feedcore.controller;

import com.qianjiangyue.feedcore.service.search.SmartFeedService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedController {

    private final SmartFeedService smartFeedService;

    public FeedController(SmartFeedService smartFeedService) {
        this.smartFeedService = smartFeedService;
    }

    /**
     * 获取个性化 RSS 订阅源
     * 测试示例: http://localhost:8080/api/feed/my?strategy=重点关注机器学习中的隐私保护技术（如联邦学习、机密计算），以及NDSS等安全顶会的最新论文动态
     */
    @GetMapping(value = "/api/feed/my", produces = MediaType.APPLICATION_XML_VALUE)
    public String getPersonalizedFeed(@RequestParam String strategy) {
        return smartFeedService.generatePersonalizedFeed(strategy);
    }
}