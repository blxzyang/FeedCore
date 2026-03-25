package com.qianjiangyue.feedcore.controller;

import com.qianjiangyue.feedcore.Service.RssProcessorService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class DemoController {

    private final RssProcessorService rssProcessorService;

    public DemoController(RssProcessorService rssProcessorService) {
        this.rssProcessorService = rssProcessorService;
    }

    // 测试访问: http://localhost:8080/api/demo/process?url=某个RSS源的链接
    @GetMapping("/api/demo/process")
    public List<RssProcessorService.ProcessedArticle> testProcess(
            @RequestParam(defaultValue = "https://planet.emacs-china.org/atom.xml") String url) {
        return rssProcessorService.processFeed(url);
    }

    // 【新增】真正的 RSS 订阅接口，返回标准的 XML！
    @GetMapping(value = "/api/feed/my-smart-feed", produces = MediaType.APPLICATION_XML_VALUE)
    public String getSmartFeed(
            @RequestParam(defaultValue = "https://feed.cnblogs.com/blog/sitehome/rss") String url) {

        // 1. 调用并发处理逻辑，拉取并使用 AI 打分过滤
        List<RssProcessorService.ProcessedArticle> processedArticles = rssProcessorService.processFeed(url);

        // 2. 将处理结果转换为标准的 RSS XML
        return rssProcessorService.generateRssXml(processedArticles);
    }
}