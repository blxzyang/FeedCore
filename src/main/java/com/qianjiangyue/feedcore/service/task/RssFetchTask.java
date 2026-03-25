package com.qianjiangyue.feedcore.service.task;

import com.qianjiangyue.feedcore.entity.RssArticle;
import com.qianjiangyue.feedcore.entity.RssSource;
import com.qianjiangyue.feedcore.repository.RssArticleRepository;
import com.qianjiangyue.feedcore.repository.RssSourceRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Component
public class RssFetchTask {

    private final RssSourceRepository sourceRepository;
    private final RssArticleRepository articleRepository;

    public RssFetchTask(RssSourceRepository sourceRepository, RssArticleRepository articleRepository) {
        this.sourceRepository = sourceRepository;
        this.articleRepository = articleRepository;
    }

    @Scheduled(initialDelay = 5000, fixedRate = 3600000)
    public void executeFetch() {
        List<RssSource> sources = sourceRepository.findAll();
        for (RssSource source : sources) {
            try {
                processSource(source);
            } catch (Exception e) {
                System.err.println("抓取失败: " + source.getName() + " -> " + e.getMessage());
            }
        }
    }

    private void processSource(RssSource source) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(source.getUrl()).openConnection();
        conn.setConnectTimeout(10000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        SyndFeed feed = new SyndFeedInput().build(new XmlReader(conn.getInputStream()));

        for (SyndEntry entry : feed.getEntries()) {
            String link = entry.getLink();

            // --- 第一层：URL 去重 ---
            if (articleRepository.existsByLink(link)) continue;

            // 提取并清洗正文
            String rawContent = entry.getDescription() != null ? entry.getDescription().getValue() : "";
            String cleanContent = rawContent.replaceAll("<[^>]+>", "").trim();

            // --- 第二层：文本指纹去重 ---
            // 使用 标题 + 清洗后的正文 计算 MD5
            String fingerPrint = DigestUtils.md5DigestAsHex((entry.getTitle() + cleanContent).getBytes());
            Optional<RssArticle> existingByHash = articleRepository.findFirstByContentHash(fingerPrint);

            RssArticle article = new RssArticle();
            article.setSourceId(source.getId());
            article.setTitle(entry.getTitle());
            article.setLink(link);
            article.setContent(cleanContent);
            article.setContentHash(fingerPrint);
            article.setFetchTime(LocalDateTime.now());

            if (existingByHash.isPresent()) {
                // 发现 Hash 重复，标记为重复并记录原件 ID
                article.setIsDuplicate(true);
                article.setOriginalId(existingByHash.get().getId());
                article.setProcessed(true); // 重复文章无需交给模型 A 浪费 Token
                System.out.println("🚫 发现重复内容（Hash匹配）: " + entry.getTitle());
            }

            if (entry.getPublishedDate() != null) {
                article.setPubDate(entry.getPublishedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            }
            
            //
            articleRepository.save(article);
            if (!article.getIsDuplicate()) {
                System.out.println("📥 新文章入库: " + article.getTitle());
            }
        }
    }
}