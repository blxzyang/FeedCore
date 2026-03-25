package com.qianjiangyue.feedcore.Service;

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

    public record AiEvaluation(int score, String reason, String summary) {}

    interface ArticleAnalyzer {
        @SystemMessage("""
            你是一个严谨的RSS信息过滤助手。请根据以下核心关注点，对输入的文章进行打分：
            1. 机器学习隐私保护（如联邦学习、机密计算等）。
            2. 计算机安全顶级会议（如NDSS）的最新动态。
            3. AI。
            4. Java技术。
            5. 音乐人文艺术（如Beyond乐队解析）。
            
            如果契合上述任何一点给高分（80-100）；泛泛而谈给中分（50-79）；广告无关给低分（0-49）。
            【重要指令】请务必全部使用中文（简体）来填写 reason 和 summary 字段！一句话核心总结请尽量精简。
            """)
        AiEvaluation analyze(@UserMessage String articleContent);
    }

    private ArticleAnalyzer analyzer;
    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;

    @PostConstruct
    public void init() {
        
        
        // 1. 初始化对话大模型 (用于打分和摘要)
        ChatLanguageModel chatModel = ZhipuAiChatModel.builder()
                .apiKey(apiKey)
                .model("glm-4-flash")
                .build();
        this.analyzer = AiServices.create(ArticleAnalyzer.class, chatModel);

        // 2. 初始化向量模型 (用于将文本转为多维浮点数)
        this.embeddingModel = ZhipuAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .model("embedding-2") // 智谱的通用向量模型
                .build();
    
        // 3. 初始化 PgVector 向量数据库 (LangChain4j 会自动在库中创建名为 rss_articles 的表)
        DbInfo info = JdbcUrlUtils.parse(properties);
        this.embeddingStore = PgVectorEmbeddingStore.builder()
                .host(info.getHost())
                .port(info.getPort())
                .database(info.getDatabase())
                .user(info.getUsername())
                .password(info.getPassword()) 
                .table("rss_articles")
                .dimension(1024) 
                .build();
    }

    // ======== 核心业务逻辑 ========

    // AI 打分评估
    public AiEvaluation evaluateArticle(String title, String content) {
        try {
            return analyzer.analyze("文章标题：" + title + "\n文章内容：" + content);
        } catch (Exception e) {
            System.err.println("AI 打分失败: " + e.getMessage());
            return new AiEvaluation(0, "请求异常", "无摘要");
        }
    }

    // 语义去重判断
    public boolean isDuplicate(String title, String description) {
        // 将标题和摘要组合作为计算相似度的特征文本
        String featureText = "标题: " + title + "\n内容: " + description;

        // 1. 调用大模型，将当前这段文字转化为 1024 维的向量
        Embedding newEmbedding = embeddingModel.embed(featureText).content();

        // 2. 去 PgVector 数据库中寻找最相似的历史文章
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(newEmbedding)
                .maxResults(1)       // 只找最相似的 1 条
                .minScore(0.85)      // 设定余弦相似度阈值（0.85 表示语义高度重合）
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // 3. 判断是否命中重复
        if (!searchResult.matches().isEmpty()) {
            double similarityScore = searchResult.matches().get(0).score();
            System.out.printf("🚨 [拦截] 发现重复文章！标题: %s, 相似度: %.4f%n", title, similarityScore);
            return true; // 判定为重复
        }

        // 4. 如果是全新内容，则将其向量特征存入数据库，供未来的文章对比
        embeddingStore.add(newEmbedding, TextSegment.from(featureText));
        System.out.println("✅ [放行] 全新文章入库: " + title);
        return false;
    }
}