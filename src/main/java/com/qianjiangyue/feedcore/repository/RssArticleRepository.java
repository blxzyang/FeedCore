package com.qianjiangyue.feedcore.repository;

import com.qianjiangyue.feedcore.entity.RssArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RssArticleRepository extends JpaRepository<RssArticle, Long> {

    // Level 1: URL 去重
    boolean existsByLink(String link);

    // Level 2: 根据内容 Hash 查找去重
    Optional<RssArticle> findFirstByContentHash(String contentHash);

    // 【新增】Level 3 / Model A 离线加工使用：分批拉取尚未被 AI 处理，且非重复的文章
    List<RssArticle> findTop10ByProcessedFalseAndIsDuplicateFalse();
}