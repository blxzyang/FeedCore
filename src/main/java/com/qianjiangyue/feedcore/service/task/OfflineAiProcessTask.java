package com.qianjiangyue.feedcore.service.task;

import com.qianjiangyue.feedcore.entity.RssArticle;
import com.qianjiangyue.feedcore.repository.RssArticleRepository;
import com.qianjiangyue.feedcore.service.ai.ZhipuAiService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 离线 AI 加工定时任务 (原 Model A)
 */
@Component // 这里使用 @Component 即可，因为它是一个任务组件，不需要 @Service
public class OfflineAiProcessTask {

    private final RssArticleRepository articleRepository;
    private final ZhipuAiService aiService;
    private final JdbcTemplate jdbcTemplate;

    public OfflineAiProcessTask(RssArticleRepository articleRepository, ZhipuAiService aiService, JdbcTemplate jdbcTemplate) {
        this.articleRepository = articleRepository;
        this.aiService = aiService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // 定时扫描未处理的文章（例如每 5 分钟执行一次）
    @Scheduled(initialDelay = 10000, fixedDelay = 300000)
    @Transactional
    public void processOfflineArticles() {
        // 获取待处理的文章（每次取 10 条，控制并发和 Token 消耗）
        List<RssArticle> pendingArticles = articleRepository.findTop10ByProcessedFalseAndIsDuplicateFalse();

        if (pendingArticles.isEmpty()) {
            return;
        }

        System.out.println("🤖 [Offline Task] 开始离线处理 " + pendingArticles.size() + " 篇文章...");

        for (RssArticle article : pendingArticles) {
            try {
                // 1. 生成大模型通用客观提取（分类、标签、摘要）
                ZhipuAiService.AiEvaluation evaluation = aiService.evaluateArticle(article.getTitle(), article.getContent());
                article.setAiCategory(evaluation.category());
                article.setAiTags(evaluation.tags());
                article.setAiSummary(evaluation.summary());

                // 2. 文本向量化：将核心信息转为 1024 维特征
                String featureText = "标题: " + article.getTitle() + "\n摘要: " + evaluation.summary();
                float[] vector = aiService.getEmbeddingVector(featureText);

                // 3. 标记为已处理
                article.setProcessed(true);
                articleRepository.save(article);

                // 4. JPA 默认对 PG 的 vector 支持有限，我们使用 JdbcTemplate 将向量硬写进去
                jdbcTemplate.update("UPDATE rss_articles SET summary_vector = ?::vector WHERE id = ?",
                        vectorToString(vector), article.getId());

                System.out.println("✅ [Offline Task] 加工完成并入库: " + article.getTitle());

            } catch (Exception e) {
                System.err.println("❌ [Offline Task] 处理失败: " + article.getTitle() + "，原因: " + e.getMessage());
            }
        }
    }

    // 辅助方法：将 float[] 转换为 PostgreSQL 可识别的向量字符串，如 "[0.1, 0.2, ...]"
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