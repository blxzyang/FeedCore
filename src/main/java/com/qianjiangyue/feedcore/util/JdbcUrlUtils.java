package com.qianjiangyue.feedcore.util;

import com.qianjiangyue.feedcore.config.DataSourceProperties;
import com.qianjiangyue.feedcore.model.dto.DbInfo;

import java.net.URI;

/**
 * @author QJY
 * @date 2026/3/25 11:04
 */
public class JdbcUrlUtils {

    public static String getHost(String url) {
        return URI.create(url.replace("jdbc:", "")).getHost();
    }

    public static int getPort(String url) {
        return URI.create(url.replace("jdbc:", "")).getPort();
    }

    public static String getDatabase(String url) {
        try {
            URI uri = URI.create(url.replace("jdbc:", ""));
            String path = uri.getPath(); 
            if (path == null) return null;
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JDBC URL: " + url, e);
        }
    }
    public static DbInfo parse(DataSourceProperties dataSourceProperties) {
        String url = dataSourceProperties.getUrl();
        URI uri = URI.create(url.replace("jdbc:", ""));

        DbInfo info = new DbInfo();
        info.setHost(uri.getHost());
        info.setPort(uri.getPort());
        info.setDatabase(uri.getPath().substring(1));
        
        info.setUsername(dataSourceProperties.getUsername());
        info.setPassword(dataSourceProperties.getPassword());
        info.setDriverClassName(dataSourceProperties.getDriverClassName());
        
        return info;
    }
}