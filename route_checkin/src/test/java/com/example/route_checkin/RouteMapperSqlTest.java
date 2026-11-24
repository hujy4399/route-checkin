package com.example.route_checkin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class RouteMapperSqlTest {
    @Test
    void listQueryParsesBothSearchFilters() {
        Configuration configuration = new Configuration();
        configuration.addMapper(RouteMapper.class);
        MappedStatement statement = configuration.getMappedStatement(
            RouteMapper.class.getName() + ".listPublished");
        String sql = statement.getBoundSql(
            Map.of("keyword", "散步", "q", "公园", "limit", 20, "offset", 0L)).getSql();
        assertTrue(sql.contains("JOIN keyword"));
        assertTrue(sql.contains("r.title LIKE"));
        assertTrue(sql.contains("LIMIT ? OFFSET ?"));
    }
}
