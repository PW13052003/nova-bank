package com.novabank;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class Database {

    private static Properties loadConfig() throws IOException {
        Properties props = new Properties();
        try (InputStream input = Database.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IOException("application.properties not found on classpath");
            }
            props.load(input);
        }
        return props;
    }

    public static Connection getConnection() throws SQLException, IOException {
        Properties props = loadConfig();
        String url = props.getProperty("spring.datasource.url");
        String username = props.getProperty("spring.datasource.username");
        String password = props.getProperty("spring.datasource.password");
        return DriverManager.getConnection(url, username, password);
    }
}