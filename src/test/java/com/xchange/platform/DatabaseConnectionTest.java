package com.xchange.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import javax.sql.DataSource;
import java.sql.Connection;

@SpringBootTest
class DatabaseConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void testConnection() throws Exception {
        Connection conn = dataSource.getConnection();
        System.out.println("✅ 数据库连接成功: " + conn.getMetaData().getURL());
        conn.close();
    }
}