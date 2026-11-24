package com.example.route_checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RedemptionIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ProjectService service;

    @Test
    void redeemAndCancelKeepBalanceAndStockConsistent() {
        String username = "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String productName = "测试商品_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
            INSERT INTO app_user(username,password_hash,nickname,account_status)
            VALUES(?,'test-only','测试用户','ACTIVE')
            """, username);
        long userId = jdbc.queryForObject(
            "SELECT user_id FROM app_user WHERE username=?", Long.class, username);
        jdbc.update("""
            INSERT INTO reward_product(product_name,points_price,stock_quantity,product_status)
            VALUES(?,10,1,'ON_SALE')
            """, productName);
        long productId = jdbc.queryForObject(
            "SELECT product_id FROM reward_product WHERE product_name=?", Long.class, productName);
        service.grantPoints(userId, 100, "测试初始积分");

        long orderId = service.redeem(productId, username, 1);
        assertEquals(90, service.balance(userId));
        assertEquals(0, jdbc.queryForObject(
            "SELECT stock_quantity FROM reward_product WHERE product_id=?", Integer.class, productId));

        service.cancelOrder(orderId, username);
        assertEquals(100, service.balance(userId));
        assertEquals(1, jdbc.queryForObject(
            "SELECT stock_quantity FROM reward_product WHERE product_id=?", Integer.class, productId));
    }
}
