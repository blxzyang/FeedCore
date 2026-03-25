package com.qianjiangyue.feedcore.model.dto;

import lombok.Data;

/**
 * @author QJY
 * @date 2026/3/25 11:13
 */
@Data
public class DbInfo {
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private String driverClassName;
}
