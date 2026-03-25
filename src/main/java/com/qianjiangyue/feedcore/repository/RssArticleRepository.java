package com.qianjiangyue.feedcore.repository;

import com.qianjiangyue.feedcore.entity.RssArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RssArticleRepository extends JpaRepository<RssArticle, Long> {
    boolean existsByLink(String link);

    // 用于第二层：根据内容 Hash 查找
    Optional<RssArticle> findFirstByContentHash(String contentHash);
}