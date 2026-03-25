package com.qianjiangyue.feedcore.Service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedOutput;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class RssProcessorService {

    private final ZhipuAiService aiService;

    // 自定义一个专门用于请求 AI 接口的线程池，避免耗尽 Tomcat 核心线程
    // 假设我们允许最多 10 个文章同时去请求大模型
    private final ExecutorService aiThreadPool = Executors.newFixedThreadPool(10);

    public RssProcessorService(ZhipuAiService aiService) {
        this.aiService = aiService;
    }

    public List<ProcessedArticle> processFeed(String rssUrl) {
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        try {
            // 1. 构建 URL 对象
            URL feedUrl = new URL(rssUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) feedUrl.openConnection();

            // 2. 核心伪装：设置浏览器 User-Agent，假装自己是 Chrome 浏览器
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            // 可以选填：告诉服务器接受的语言和格式
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");

            // 3. 设置合理的超时时间（极其重要，防止单个死链接卡死整个线程池）
            connection.setConnectTimeout(10000); 
            // 连接超时：10秒
            connection.setReadTimeout(10000);    
            // 读取超时：10秒

            // 4. 发起连接
            connection.connect();

            // 5. 将获取到的输入流交给 ROME 解析
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(connection.getInputStream()));

            // 为了测试并发效果，我们这次取前 5 篇文章（如果源里有这么多的话）
            List<SyndEntry> entries = feed.getEntries();
            int limit = Math.min(entries.size(), 5);
            List<SyndEntry> targetEntries = entries.subList(0, limit);

            System.out.println("开始并发处理 " + limit + " 篇文章...");

            // 核心改造：将每篇文章的处理包装成一个异步任务 (CompletableFuture)
            List<CompletableFuture<ProcessedArticle>> futures = targetEntries.stream()
                    .map(entry -> CompletableFuture.supplyAsync(() -> {
                        String title = entry.getTitle();
                        // 提取纯文本描述
                        String description = entry.getDescription() != null ?
                                entry.getDescription().getValue().replaceAll("<[^>]+>", "") : "";

                        // ... 前面获取 title 和 description 的代码保持不变 ...

                        System.out.println("线程 [" + Thread.currentThread().getName() + "] 正在处理: " + title);

                        // ================= 新增：前置语义去重拦截 =================
                        // 如果判定为重复文章，直接返回一个特殊的 ProcessedArticle，不再调用 GLM-4 打分
                        if (aiService.isDuplicate(title, description)) {
                            return new ProcessedArticle(title, entry.getLink(), -1, "【系统拦截】", "语义重复，已自动过滤");
                        }
                        // ==========================================================

                        // 如果不重复，才正式调用大模型消耗 Token 进行深度打分和总结
                        ZhipuAiService.AiEvaluation eval = aiService.evaluateArticle(title, description);

                        return new ProcessedArticle(title, entry.getLink(), eval.score(), eval.reason(), eval.summary());
                    }, aiThreadPool)) // 传入我们自定义的线程池
                    .toList();

            // 等待所有的异步任务全部执行完毕
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.join(); // 阻塞主线程，直到上面的并发任务全跑完

            // 收集所有异步任务的结果
            List<ProcessedArticle> resultList = futures.stream()
                    .map(CompletableFuture::join) // join此时不会阻塞，因为前面已经保证全执行完了
                    .collect(Collectors.toList());

            long endTime = System.currentTimeMillis();
            System.out.println("✅ 并发处理完成！总耗时: " + (endTime - startTime) + " ms");

            return resultList;

        } catch (Exception e) {
            e.printStackTrace();
            return List.of(new ProcessedArticle("解析失败", rssUrl, 0, "系统异常", e.getMessage()));
        }
    }

    // 在 Spring 容器销毁时，优雅地关闭线程池
    @PreDestroy
    public void onDestroy() {
        if (aiThreadPool != null && !aiThreadPool.isShutdown()) {
            aiThreadPool.shutdown();
        }
    }

    public record ProcessedArticle(String title, String link, int score, String aiReason, String aiSummary) {}

    public String generateRssXml(List<ProcessedArticle> articles) {
        try {
            // 1. 创建一个新的 RSS Feed 对象
            SyndFeed feed = new SyndFeedImpl();
            feed.setFeedType("rss_2.0"); // 指定生成 RSS 2.0 标准
            feed.setTitle("我的 AI 智能过滤源");
            feed.setLink("http://localhost:8080");
            feed.setDescription("这是经过大模型与向量数据库去重过滤后的高价值信息流");

            // 2. 遍历我们刚才并发处理出的文章列表
            List<SyndEntry> entries = articles.stream()
                    // 【核心过滤逻辑】：过滤掉系统拦截的重复文章(score=-1) 和 毫无价值的低分文章(score<60)
                    .filter(article -> article.score() >= 60)
                    .map(article -> {
                        SyndEntry entry = new SyndEntryImpl();
                        entry.setTitle(article.title());
                        entry.setLink(article.link());

                        // 将 AI 的总结和打分理由塞进 RSS 的正文描述里
                        SyndContent description = new SyndContentImpl();
                        description.setType("text/html");
                        String htmlContent = String.format("""
                                <div style='padding: 10px; background-color: #f0f4f8; border-left: 4px solid #007bff; margin-bottom: 10px;'>
                                    <strong>🤖 AI 评分:</strong> <span style='color: red; font-size: 1.2em;'>%d</span><br/>
                                    <strong>📝 一句话总结:</strong> %s<br/>
                                    <strong>💡 判断理由:</strong> %s
                                </div>
                                """, article.score(), article.aiSummary(), article.aiReason());

                        description.setValue(htmlContent);
                        entry.setDescription(description);

                        return entry;
                    })
                    .toList();

            feed.setEntries(entries);

            // 3. 输出为 XML 字符串
            SyndFeedOutput output = new SyndFeedOutput();
            return output.outputString(feed);

        } catch (Exception e) {
            e.printStackTrace();
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><error>生成 RSS 失败</error>";
        }
    }

}