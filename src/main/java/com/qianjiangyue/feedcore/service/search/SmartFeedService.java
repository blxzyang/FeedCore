package com.qianjiangyue.feedcore.service.search;

import com.qianjiangyue.feedcore.entity.RssArticle;
import com.qianjiangyue.feedcore.repository.RssArticleRepository;
import com.qianjiangyue.feedcore.service.ai.ZhipuAiService;
import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.SyndFeedOutput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class SmartFeedService {

    private final ZhipuAiService aiService;
    private final JdbcTemplate jdbcTemplate;
    private final RssArticleRepository articleRepository;

    public SmartFeedService(ZhipuAiService aiService, JdbcTemplate jdbcTemplate, RssArticleRepository articleRepository) {
        this.aiService = aiService;
        this.jdbcTemplate = jdbcTemplate;
        this.articleRepository = articleRepository;
    }

    public String generatePersonalizedFeed(String strategy) {
        // 1. 将用户的提示词策略转化为 1024 维向量
        float[] promptVector = aiService.getEmbeddingVector(strategy);
        String vectorStr = vectorToString(promptVector);

        // 2. 【向量粗筛】利用 PgVector 的 <=> 算子计算余弦距离，捞取最相关的 20 篇文章
        // 注意：只搜索已经经过 Model A 处理过的文章 (processed = true)
        String sql = """
                SELECT id 
                FROM rss_articles 
                WHERE processed = true AND is_duplicate = false 
                ORDER BY summary_vector <=> ?::vector 
                LIMIT 20
                """;

        List<Long> candidateIds = jdbcTemplate.queryForList(sql, Long.class, vectorStr);

        if (candidateIds.isEmpty()) {
            return buildEmptyRss("没有找到符合条件的文章");
        }

        List<RssArticle> candidates = articleRepository.findAllById(candidateIds);
        List<SyndEntry> matchedEntries = new ArrayList<>();

        // 3. 【大模型精筛】遍历候选文章，进行精准打击
        System.out.println("🔍 开始根据策略【" + strategy + "】精筛 " + candidates.size() + " 篇文章...");

        for (RssArticle article : candidates) {
            ZhipuAiService.ModelBEvaluation eval = aiService.evaluateByStrategy(
                    strategy,
                    article.getTitle(),
                    article.getAiSummary(),
                    article.getAiTags()
            );

            if (eval.isMatch()) {
                System.out.println("✅ [命中] " + article.getTitle() + " -> " + eval.reason());
                matchedEntries.add(convertToSyndEntry(article, eval.reason()));
            } else {
                System.out.println("拦截: " + article.getTitle() + " -> " + eval.reason());
            }
        }

        // 4. 打包为 RSS XML
        return buildRssXml(strategy, matchedEntries);
    }

    // --- 辅助方法 ---

    private SyndEntry convertToSyndEntry(RssArticle article, String matchReason) {
        SyndEntry entry = new SyndEntryImpl();
        entry.setTitle(article.getTitle());
        entry.setLink(article.getLink());
        if (article.getPubDate() != null) {
            entry.setPublishedDate(Date.from(article.getPubDate().atZone(ZoneId.systemDefault()).toInstant()));
        }

        SyndContent description = new SyndContentImpl();
        description.setType("text/html");
        String htmlContent = String.format("""
                <div style='padding: 10px; background-color: #f8f9fa; border-left: 4px solid #28a745; margin-bottom: 10px;'>
                    <strong>🎯 命中理由:</strong> %s<br/>
                    <strong>🏷️ 原文标签:</strong> %s<br/>
                    <strong>📝 AI摘要:</strong> %s
                </div>
                <div>%s</div>
                """, matchReason, article.getAiTags(), article.getAiSummary(), article.getContent());

        description.setValue(htmlContent);
        entry.setDescription(description);
        return entry;
    }

    private String buildRssXml(String strategy, List<SyndEntry> entries) {
        try {
            SyndFeed feed = new SyndFeedImpl();
            feed.setFeedType("rss_2.0");
            feed.setTitle("FeedCore 专属智能流");
            feed.setLink("http://localhost:8080");
            feed.setDescription("当前过滤策略: " + strategy);
            feed.setEntries(entries);
            return new SyndFeedOutput().outputString(feed);
        } catch (Exception e) {
            return buildEmptyRss("RSS 生成失败: " + e.getMessage());
        }
    }

    private String buildEmptyRss(String message) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><error>" + message + "</error>";
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}