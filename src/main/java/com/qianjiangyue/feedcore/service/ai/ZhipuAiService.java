package com.qianjiangyue.feedcore.service.ai;

import com.qianjiangyue.feedcore.config.DataSourceProperties;
import com.qianjiangyue.feedcore.model.dto.DbInfo;
import com.qianjiangyue.feedcore.util.JdbcUrlUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.model.zhipu.ZhipuAiEmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ZhipuAiService {

    @Value("${zhipu.api-key}")
    private String apiKey;

    @Autowired
    private DataSourceProperties properties;

    // 对应 RssArticle 表中的 aiCategory, aiTags, aiSummary 字段
    public record AiEvaluation(String category, String tags, String summary) {}

    interface ArticleAnalyzer {
        @SystemMessage("""
            你是一个严谨、客观的信息提取助手。你的任务是对输入的RSS文章进行结构化分析，为后续的向量数据库检索做准备。
            
            请根据文章内容，客观地输出以下三项信息：
            1. category (分类): 用一个词概括文章所属的大类（如：后端、前端、人工智能、网络安全、生活、商业等）。
            2. tags (标签): 提取3-5个最核心的技术或主题关键词，用逗号分隔。
            3. summary (摘要): 用一到两句话，简明扼要地总结文章的核心事实或观点。
            
            【重要指令】必须保持绝对的中立和客观，不夹带任何主观评价。必须全部使用中文（简体）输出。
            """)
        AiEvaluation analyze(@UserMessage String articleContent);
    }

    private ArticleAnalyzer analyzer;
    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;

    @PostConstruct
    public void init() {
        // 1. 初始化对话大模型 (用于 Model A 离线客观信息提取)
        ChatLanguageModel chatModel = ZhipuAiChatModel.builder()
                .apiKey(apiKey)
                .model("glm-4-flash")
                .build();
        this.analyzer = AiServices.create(ArticleAnalyzer.class, chatModel);

        // 2. 初始化向量模型 (用于生成 1024 维 Embedding)
        this.embeddingModel = ZhipuAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .model("embedding-2")
                .build();

        // 3. 初始化 PgVector 向量数据库
        DbInfo info = JdbcUrlUtils.parse(properties);
        this.embeddingStore = PgVectorEmbeddingStore.builder()
                .host(info.getHost())
                .port(info.getPort())
                .database(info.getDatabase())
                .user(info.getUsername())
                .password(info.getPassword())
                .dimension(1024)
                .build();

        this.strategyFilter = AiServices.create(StrategyFilter.class, chatModel);
    }

    // ======== 核心业务逻辑 ========

    /**
     * 【Model A】离线结构化提取：生成客观的分类、标签和摘要
     */
    public AiEvaluation evaluateArticle(String title, String content) {
        try {
            return analyzer.analyze("文章标题：" + title + "\n文章内容：" + content);
        } catch (Exception e) {
            System.err.println("AI 提取失败: " + e.getMessage());
            return new AiEvaluation("未知", "无", "提取异常: " + e.getMessage());
        }
    }

    /**
     * 【Model A】文本向量化：将文章特征转化为 1024 维数组供入库
     */
    public float[] getEmbeddingVector(String text) {
        return embeddingModel.embed(text).content().vector();
    }

    /**
     * 【Level 3 去重】语义级防重复拦截
     */
    public boolean isDuplicate(String title, String description) {
        String featureText = "标题: " + title + "\n内容: " + description;
        Embedding newEmbedding = embeddingModel.embed(featureText).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(newEmbedding)
                .maxResults(1)
                .minScore(0.85) // 余弦相似度阈值
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        if (!searchResult.matches().isEmpty()) {
            double similarityScore = searchResult.matches().get(0).score();
            System.out.printf("🚨 [语义拦截] 发现高度重复文章！标题: %s, 相似度: %.4f%n", title, similarityScore);
            return true;
        }

        embeddingStore.add(newEmbedding, TextSegment.from(featureText));
        return false;
    }
    // ==========================================
    // ======== Model B 在线个性化检索能力 ========
    // ==========================================

    public record ModelBEvaluation(boolean isMatch, String reason) {}

    interface StrategyFilter {
        @SystemMessage("""
            你是一个严苛的个性化信息流推荐专家。
            当前用户的专属阅读偏好/过滤策略是：【{{strategy}}】
            
            请评估以下文章是否高度符合该偏好。如果只是稍微沾边，请果断拒绝。
            请根据判断结果，客观地输出以下两项信息：
            1. isMatch: 如果文章高度符合用户偏好，输出 true；否则输出 false。
            2. reason: 用一句话简短解释为什么放行或拦截。
            """)
        ModelBEvaluation filter(
                @dev.langchain4j.service.V("strategy") String strategy,
                @UserMessage String articleInfo
        );
    }

    private StrategyFilter strategyFilter;
    
    /**
     * 【Model B】在线精筛：根据用户的具体策略，判断文章是否符合
     */
    public ModelBEvaluation evaluateByStrategy(String userStrategy, String articleTitle, String articleSummary, String tags) {
        try {
            String articleInfo = String.format("标题：%s\n标签：%s\n摘要：%s", articleTitle, tags, articleSummary);
            return strategyFilter.filter(userStrategy, articleInfo);
        } catch (Exception e) {
            System.err.println("Model B 精筛异常: " + e.getMessage());
            // 发生异常时默认拦截，宁缺毋滥
            return new ModelBEvaluation(false, "大模型判断异常");
        }
    }
}