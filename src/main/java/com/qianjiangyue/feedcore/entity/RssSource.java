package com.qianjiangyue.feedcore.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "rss_sources")
public class RssSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;      // 源名称，如：掘金后端
    private String url;       // RSS 地址
    private String category;  // 预定义的分类，方便模型B缩小范围
    private LocalDateTime lastFetchTime; // 上次抓取时间，用于增量更新
}

