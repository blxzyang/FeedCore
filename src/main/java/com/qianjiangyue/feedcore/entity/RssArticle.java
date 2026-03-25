package com.qianjiangyue.feedcore.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "rss_articles")
public class RssArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sourceId;      // 关联源ID
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;     // 原始清洗后的正文

    @Column(unique = true)
    private String link;        // 第一层：URL唯一约束

    private String contentHash; // 第二层：MD5指纹

    private LocalDateTime pubDate;
    private LocalDateTime fetchTime;

    // --- 模型 A 加工状态控制 ---
    private Boolean processed = false;    // 核心开关：是否已由AI处理
    private Boolean isDuplicate = false;  // 第三层：是否语义重复
    private Long originalId;              // 如果重复，指向母本ID

    // --- 模型 A 产出的结构化结果 ---
    private String aiCategory;

    @Column(columnDefinition = "TEXT")
    private String aiTags;      // 存储为 "Java,并发,Redis" 格式

    @Column(columnDefinition = "TEXT")
    private String aiSummary;   // AI 生成的摘要

    // --- 向量字段 (PgVector) ---
    // 注意：JPA 默认不支持 vector 类型。
    // 在后续模型 A 的代码中，我们会用 JdbcTemplate 直接执行 SQL 来写入这个字段
    @Column(name = "summary_vector", columnDefinition = "vector(1024)", insertable = false, updatable = false)
    private Object summaryVector;
}