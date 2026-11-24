package com.example.route_checkin;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {
    private final JdbcTemplate jdbc;
    private final RouteMapper routes;
    private final PasswordEncoder encoder;

    public ProjectService(JdbcTemplate jdbc, RouteMapper routes, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.routes = routes;
        this.encoder = encoder;
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Map<String, Object> row(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return rows.get(0);
    }

    private long number(Object value) {
        if (value instanceof Boolean b) return b ? 1 : 0;
        return ((Number) value).longValue();
    }

    private long insert(String sql, Object... args) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("插入后未返回主键");
        return key.getKey().longValue();
    }

    public long userId(String username) {
        return jdbc.queryForObject("SELECT user_id FROM app_user WHERE username = ?", Long.class, username);
    }

    public Map<String, Object> me(String username) {
        long id = userId(username);
        Map<String, Object> result = new HashMap<>(row(
            "SELECT user_id AS userId, username, nickname, account_status AS accountStatus FROM app_user WHERE user_id = ?", id));
        result.put("roles", jdbc.queryForList(
            "SELECT r.role_code FROM user_role ur JOIN app_role r ON r.role_id = ur.role_id WHERE ur.user_id = ?",
            String.class, id));
        result.put("pointsBalance", balance(id));
        return result;
    }

    @Transactional
    public long register(ApiController.RegisterRequest request) {
        if (request.username() == null || !request.username().matches("[A-Za-z0-9_]{3,50}")
                || request.password() == null || request.password().length() < 8
                || request.nickname() == null || request.nickname().isBlank()) {
            throw bad("用户名需为3到50位字母数字或下划线，密码至少8位，昵称不能为空");
        }
        try {
            long id = insert(
                "INSERT INTO app_user(username,password_hash,nickname,account_status) VALUES(?,?,?,'PENDING')",
                request.username(), encoder.encode(request.password()), request.nickname());
            jdbc.update("INSERT INTO user_role(user_id,role_id) SELECT ?,role_id FROM app_role WHERE role_code='USER'", id);
            return id;
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
    }

    @Transactional
    public void bootstrapAdmin(String username, String password) {
        if (username == null || password == null || username.isBlank() || password.isBlank()) return;
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_role ur JOIN app_role r ON r.role_id=ur.role_id WHERE r.role_code='ADMIN'",
            Long.class);
        if (count != null && count > 0) return;
        List<Long> existing = jdbc.queryForList("SELECT user_id FROM app_user WHERE username=?", Long.class, username);
        long id = existing.isEmpty()
            ? insert("INSERT INTO app_user(username,password_hash,nickname,account_status) VALUES(?,?,?,'ACTIVE')",
                username, encoder.encode(password), "管理员")
            : existing.get(0);
        jdbc.update("UPDATE app_user SET password_hash=?,account_status='ACTIVE' WHERE user_id=?",
            encoder.encode(password), id);
        jdbc.update("INSERT IGNORE INTO user_role(user_id,role_id) SELECT ?,role_id FROM app_role WHERE role_code='ADMIN'", id);
    }

    public List<Map<String, Object>> listRoutes(String keyword, String q, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw bad("page需≥0，size需为1到100");
        if (q != null && q.length() > 100) throw bad("搜索词最多100字");
        return routes.listPublished(keyword, q, size, (long) page * size);
    }

    public Map<String, Object> routeDetail(long id) {
        Map<String, Object> detail = routes.publishedDetail(id);
        if (detail == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "路线不存在");
        Map<String, Object> result = new HashMap<>(detail);
        result.put("points", routes.points(id));
        result.put("keywords", routes.keywords(id));
        return result;
    }

    public List<Map<String, Object>> mapPoints() {
        return jdbc.queryForList("""
            SELECT point_id AS pointId,point_name AS pointName,longitude,latitude,
                   address_text AS addressText FROM map_point
            WHERE point_status='ACTIVE' ORDER BY point_id
            """);
    }

    public List<Map<String, Object>> keywords() {
        return jdbc.queryForList(
            "SELECT keyword_id AS keywordId,keyword_name AS keywordName FROM keyword WHERE is_active=1 ORDER BY keyword_id");
    }

    public List<Map<String, Object>> myRoutes(String username) {
        return jdbc.queryForList("""
            SELECT route_id AS routeId,title,route_kind AS routeKind,route_status AS routeStatus,
                   created_at AS createdAt FROM `route`
            WHERE publisher_id=? ORDER BY created_at DESC,route_id DESC
            """, userId(username));
    }

    public List<Map<String, Object>> pendingRoutes() {
        return jdbc.queryForList("""
            SELECT route_id AS routeId,title,publisher_id AS publisherId,created_at AS createdAt
            FROM `route` WHERE route_status='PENDING' ORDER BY created_at
            """);
    }

    public List<Map<String, Object>> pendingUsers() {
        return jdbc.queryForList("""
            SELECT user_id AS userId,username,nickname,registered_at AS registeredAt
            FROM app_user WHERE account_status='PENDING' ORDER BY registered_at
            """);
    }

    public List<Map<String, Object>> adminUsers() {
        return jdbc.queryForList("""
            SELECT user_id AS userId,username,nickname,account_status AS accountStatus
            FROM app_user ORDER BY user_id DESC LIMIT 100
            """);
    }

    public List<Map<String, Object>> adminProducts() {
        return jdbc.queryForList("""
            SELECT product_id AS productId,product_name AS productName,
                   points_price AS pointsPrice,stock_quantity AS stockQuantity,
                   product_status AS productStatus
            FROM reward_product ORDER BY product_id DESC LIMIT 100
            """);
    }

    public List<Map<String, Object>> adminOrders() {
        return jdbc.queryForList("""
            SELECT redemption_id AS redemptionId,order_status AS orderStatus,
                   quantity,unit_points_snapshot AS unitPointsSnapshot,
                   redeemed_at AS redeemedAt,user_id AS userId,nickname,
                   product_name AS productName
            FROM v_admin_redemptions ORDER BY redeemed_at DESC,redemption_id DESC LIMIT 100
            """);
    }

    @Transactional
    public long createMapPoint(String username, ApiController.MapPointRequest request) {
        if (request.name() == null || request.name().isBlank() || request.longitude() == null
                || request.latitude() == null || request.longitude() < -180 || request.longitude() > 180
                || request.latitude() < -90 || request.latitude() > 90) throw bad("点位名称和合法经纬度必填");
        return insert("""
            INSERT INTO map_point(point_name,longitude,latitude,address_text,created_by,point_status)
            VALUES(?,?,?,?,?,'ACTIVE')
            """, request.name(), request.longitude(), request.latitude(), request.address(), userId(username));
    }

    @Transactional
    public long createKeyword(String name) {
        if (name == null || name.isBlank() || name.length() > 50) throw bad("关键词长度需为1到50");
        try {
            return insert("INSERT INTO keyword(keyword_name) VALUES(?)", name);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "关键词已存在");
        }
    }

    @Transactional
    public long createRoute(String username, ApiController.RouteRequest request) {
        long publisher = userId(username);
        if (request.title() == null || request.title().isBlank()
                || request.description() == null || request.description().isBlank()
                || request.title().length() > 120
                || (request.coverUrl() != null && request.coverUrl().length() > 500)
                || request.recommendedMinutes() == null || request.recommendedMinutes() <= 0
                || request.pointIds() == null || request.pointIds().size() < 2
                || request.pointIds().size() > 100
                || new HashSet<>(request.pointIds()).size() != request.pointIds().size()
                || request.pointIds().contains(null)) throw bad("路线标题、介绍、时长及至少两个不重复点位必填");
        String kind = request.routeKind() == null ? "NORMAL" : request.routeKind();
        if (!kind.equals("NORMAL") && !kind.equals("OFFICIAL_EVENT")) throw bad("路线类型无效");
        int bonus = request.checkinBonusPoints() == null ? 0 : request.checkinBonusPoints();
        if (bonus < 0 || bonus > 1000) throw bad("额外奖励需为0到1000积分");
        if (request.availableFrom() != null && request.availableUntil() != null
                && !request.availableUntil().isAfter(request.availableFrom())) throw bad("结束时间必须晚于开始时间");
        if (kind.equals("OFFICIAL_EVENT")) {
            if (request.availableFrom() == null || request.availableUntil() == null) throw bad("官方活动必须设置有效期");
            Integer official = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_role ur JOIN app_role r ON r.role_id=ur.role_id WHERE ur.user_id=? AND r.role_code='OFFICIAL'",
                Integer.class, publisher);
            if (official == null || official == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要官方人员角色");
        }
        for (Long pointId : request.pointIds()) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM map_point WHERE point_id=? AND point_status='ACTIVE'", Integer.class, pointId);
            if (count == null || count == 0) throw bad("点位不存在或已停用: " + pointId);
        }
        List<Long> keywords = request.keywordIds() == null ? List.of() : request.keywordIds();
        if (keywords.size() > 20 || keywords.contains(null)
                || new HashSet<>(keywords).size() != keywords.size()) throw bad("关键词不能重复且最多20个");
        for (Long keywordId : keywords) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM keyword WHERE keyword_id=? AND is_active=1", Integer.class, keywordId);
            if (count == null || count == 0) throw bad("关键词不存在或已停用: " + keywordId);
        }
        long id = insert("""
            INSERT INTO `route`(title,description,cover_url,publisher_id,route_kind,route_status,
            recommended_minutes,available_from,available_until,checkin_bonus_points)
            VALUES(?,?,?,?,?,'PENDING',?,?,?,?)
            """, request.title(), request.description(), request.coverUrl(), publisher, kind, request.recommendedMinutes(),
            request.availableFrom() == null ? null : Timestamp.valueOf(request.availableFrom()),
            request.availableUntil() == null ? null : Timestamp.valueOf(request.availableUntil()), bonus);
        for (int i = 0; i < request.pointIds().size(); i++) {
            jdbc.update("INSERT INTO route_point(route_id,sequence_no,point_id) VALUES(?,?,?)",
                id, i + 1, request.pointIds().get(i));
        }
        for (Long keywordId : keywords) {
            jdbc.update("INSERT INTO route_keyword(route_id,keyword_id) VALUES(?,?)", id, keywordId);
        }
        return id;
    }

    @Transactional
    public void reviewRoute(long routeId, String admin, boolean approve) {
        String status = (String) row("SELECT route_status FROM `route` WHERE route_id=? FOR UPDATE", routeId)
            .get("route_status");
        if (!"PENDING".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "仅待审核路线可审核");
        int points = jdbc.queryForObject("SELECT COUNT(*) FROM route_point WHERE route_id=?", Integer.class, routeId);
        if (approve && points < 2) throw bad("路线至少需要两个点位");
        jdbc.update("""
            UPDATE `route` SET route_status=?, reviewed_by=?, reviewed_at=NOW(),
                published_at=CASE WHEN ?='PUBLISHED' THEN NOW() ELSE NULL END WHERE route_id=?
            """, approve ? "PUBLISHED" : "REJECTED", userId(admin),
            approve ? "PUBLISHED" : "REJECTED", routeId);
    }

    @Transactional
    public void reviewUser(long targetId, String admin, boolean approve) {
        int n = jdbc.update("""
            UPDATE app_user SET account_status=?,reviewed_by=?,reviewed_at=NOW()
            WHERE user_id=? AND account_status='PENDING'
            """, approve ? "ACTIVE" : "DISABLED", userId(admin), targetId);
        if (n == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "仅待审核用户可审核");
    }

    @Transactional
    public void grantRole(long targetId, String role, String admin) {
        if (!List.of("USER", "OFFICIAL", "ADMIN").contains(role)) throw bad("角色无效");
        if (!jdbc.queryForList("SELECT user_id FROM app_user WHERE user_id=?", Long.class, targetId).isEmpty()) {
            jdbc.update("""
                INSERT IGNORE INTO user_role(user_id,role_id,assigned_by)
                SELECT ?,role_id,? FROM app_role WHERE role_code=?
                """, targetId, userId(admin), role);
        } else throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
    }

    private Map<String, Object> publishedRoute(long id) {
        Map<String, Object> route = row(
            "SELECT route_id,publisher_id,route_status,available_from,available_until,checkin_bonus_points FROM `route` WHERE route_id=?",
            id);
        if (!"PUBLISHED".equals(route.get("route_status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "路线尚未发布");
        return route;
    }

    private void ledger(long userId, int delta, String type, String key, String rule,
                        Long likeId, Long favoriteId, Long checkinId, Long orderId, String remark) {
        if (delta == 0) return;
        jdbc.update("""
            INSERT INTO point_ledger(user_id,change_points,event_type,event_key,rule_code,
                like_id,favorite_id,checkin_id,redemption_id,remark)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            """, userId, delta, type, key, rule, likeId, favoriteId, checkinId, orderId, remark);
    }

    private void award(long publisher, String code, String key, Long likeId, Long favoriteId, Long checkinId) {
        Map<String, Object> rule = row("SELECT points,is_active FROM point_rule WHERE rule_code=?", code);
        if (number(rule.get("is_active")) == 1 && number(rule.get("points")) > 0) {
            ledger(publisher, ((Number) rule.get("points")).intValue(), "RULE_REWARD", key,
                code, likeId, favoriteId, checkinId, null, null);
        }
    }

    @Transactional
    public void setInteraction(long routeId, String username, boolean like, boolean active) {
        long userId = userId(username);
        Map<String, Object> route = publishedRoute(routeId);
        long publisher = number(route.get("publisher_id"));
        if (publisher == userId) throw bad("不能给自己的路线点赞或收藏");
        String table = like ? "route_like" : "route_favorite";
        String keyColumn = like ? "like_id" : "favorite_id";
        String firstColumn = like ? "first_liked_at" : "first_favorited_at";
        if (!active) {
            jdbc.update("UPDATE " + table + " SET is_active=0,updated_at=NOW() WHERE user_id=? AND route_id=?",
                userId, routeId);
            return;
        }
        int added = jdbc.update("INSERT IGNORE INTO " + table
            + "(user_id,route_id,is_active," + firstColumn + ",updated_at) VALUES(?,?,1,NOW(),NOW())",
            userId, routeId);
        if (added == 0) {
            jdbc.update("UPDATE " + table + " SET is_active=1,updated_at=NOW() WHERE user_id=? AND route_id=?",
                userId, routeId);
        } else {
            long actionId = jdbc.queryForObject("SELECT " + keyColumn + " FROM " + table
                + " WHERE user_id=? AND route_id=?", Long.class, userId, routeId);
            award(publisher, like ? "PUBLISH_LIKE" : "PUBLISH_FAVORITE",
                (like ? "LIKE:" : "FAVORITE:") + actionId,
                like ? actionId : null, like ? null : actionId, null);
        }
    }

    public long balance(long userId) {
        Long total = jdbc.queryForObject(
            "SELECT COALESCE(SUM(change_points),0) FROM point_ledger WHERE user_id=?", Long.class, userId);
        return total == null ? 0 : total;
    }

    @Transactional
    public long checkin(long routeId, String username, String note) {
        if (note != null && note.length() > 300) throw bad("心得最多300字");
        long userId = userId(username);
        Map<String, Object> route = publishedRoute(routeId);
        LocalDateTime now = LocalDateTime.now();
        Timestamp from = (Timestamp) route.get("available_from");
        Timestamp until = (Timestamp) route.get("available_until");
        if ((from != null && now.isBefore(from.toLocalDateTime()))
                || (until != null && !now.isBefore(until.toLocalDateTime())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "路线当前不可打卡");
        long checkinId;
        try {
            checkinId = insert("INSERT INTO route_checkin(user_id,route_id,note) VALUES(?,?,?)",
                userId, routeId, note);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这条路线已经打卡");
        }
        long publisher = number(route.get("publisher_id"));
        if (publisher != userId) {
            jdbc.queryForObject("SELECT user_id FROM app_user WHERE user_id=? FOR UPDATE", Long.class, publisher);
            award(publisher, "PUBLISH_CHECKIN", "CHECKIN_RULE:" + checkinId, null, null, checkinId);
            int bonus = ((Number) route.get("checkin_bonus_points")).intValue();
            if (bonus > 0 && balance(publisher) >= bonus) {
                ledger(publisher, -bonus, "BONUS_DEBIT", "BONUS_OUT:" + checkinId,
                    null, null, null, checkinId, null, null);
                ledger(userId, bonus, "BONUS_CREDIT", "BONUS_IN:" + checkinId,
                    null, null, null, checkinId, null, null);
            }
        }
        return checkinId;
    }

    public List<Map<String, Object>> products() {
        return jdbc.queryForList("""
            SELECT product_id AS productId,product_name AS productName,description,
                   points_price AS pointsPrice,stock_quantity AS stockQuantity
            FROM reward_product WHERE product_status='ON_SALE'
            ORDER BY points_price,product_id
            """);
    }

    public List<Map<String, Object>> myOrders(String username) {
        return jdbc.queryForList("""
            SELECT o.redemption_id AS redemptionId,p.product_name AS productName,
                   o.quantity,o.unit_points_snapshot AS unitPoints,o.order_status AS orderStatus,
                   o.pickup_code AS pickupCode,o.redeemed_at AS redeemedAt
            FROM redemption_order o JOIN reward_product p ON p.product_id=o.product_id
            WHERE o.user_id=? ORDER BY o.redeemed_at DESC,o.redemption_id DESC
            """, userId(username));
    }

    @Transactional
    public void closeRoute(long id, String username) {
        Map<String, Object> route = row(
            "SELECT publisher_id,route_status FROM `route` WHERE route_id=? FOR UPDATE", id);
        if (number(route.get("publisher_id")) != userId(username))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能关闭自己发布的路线");
        if (!"PUBLISHED".equals(route.get("route_status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅已发布路线可关闭");
        jdbc.update("CALL sp_close_published_route(?,?)", id, userId(username));
    }

    @Transactional
    public void offSaleProduct(long id) {
        int n = jdbc.update("UPDATE reward_product SET product_status='OFF_SALE' WHERE product_id=?", id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
    }

    @Transactional
    public void restockProduct(long id, int quantity) {
        if (quantity < 1 || quantity > 100000) throw bad("补货数量需为1到100000");
        int n = jdbc.update("UPDATE reward_product SET stock_quantity=stock_quantity+? WHERE product_id=?",
            quantity, id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
    }

    @Transactional
    public long createProduct(ApiController.ProductRequest request) {
        if (request.name() == null || request.name().isBlank()
                || request.pointsPrice() == null || request.pointsPrice() <= 0
                || request.stock() == null || request.stock() < 0) throw bad("商品名称、正数价格和非负库存必填");
        return insert("""
            INSERT INTO reward_product(product_name,description,points_price,stock_quantity,product_status)
            VALUES(?,?,?,?,'ON_SALE')
            """, request.name(), request.description(), request.pointsPrice(), request.stock());
    }

    @Transactional
    public long redeem(long productId, String username, int quantity) {
        if (quantity < 1 || quantity > 100) throw bad("数量需为1到100");
        long userId = userId(username);
        jdbc.queryForObject("SELECT user_id FROM app_user WHERE user_id=? FOR UPDATE", Long.class, userId);
        Map<String, Object> product = row(
            "SELECT points_price,stock_quantity,product_status FROM reward_product WHERE product_id=? FOR UPDATE",
            productId);
        if (!"ON_SALE".equals(product.get("product_status"))) throw bad("商品已下架");
        long total = number(product.get("points_price")) * quantity;
        if (total > Integer.MAX_VALUE) throw bad("本次兑换积分超出允许范围");
        if (number(product.get("stock_quantity")) < quantity || balance(userId) < total)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "库存或积分不足");
        jdbc.update("UPDATE reward_product SET stock_quantity=stock_quantity-? WHERE product_id=?",
            quantity, productId);
        long orderId = insert("""
            INSERT INTO redemption_order(user_id,product_id,quantity,unit_points_snapshot,
                order_status,pickup_code) VALUES(?,?,?,?,'PENDING_PICKUP',?)
            """, userId, productId, quantity, product.get("points_price"),
            UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        ledger(userId, Math.toIntExact(-total), "REDEEM_DEBIT", "REDEEM:" + orderId,
            null, null, null, null, orderId, null);
        return orderId;
    }

    @Transactional
    public void cancelOrder(long orderId, String username) {
        long userId = userId(username);
        jdbc.queryForObject("SELECT user_id FROM app_user WHERE user_id=? FOR UPDATE", Long.class, userId);
        Map<String, Object> order = row("""
            SELECT user_id,product_id,quantity,unit_points_snapshot,order_status
            FROM redemption_order WHERE redemption_id=? FOR UPDATE
            """, orderId);
        if (number(order.get("user_id")) != userId)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能取消其他人的订单");
        if (!"PENDING_PICKUP".equals(order.get("order_status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单不能取消");
        jdbc.update("UPDATE redemption_order SET order_status='CANCELLED' WHERE redemption_id=?", orderId);
        jdbc.update("UPDATE reward_product SET stock_quantity=stock_quantity+? WHERE product_id=?",
            order.get("quantity"), order.get("product_id"));
        int refund = Math.toIntExact(number(order.get("quantity")) * number(order.get("unit_points_snapshot")));
        ledger(userId, refund, "REDEEM_REFUND", "REFUND:" + orderId,
            null, null, null, null, orderId, null);
    }

    @Transactional
    public void fulfillOrder(long orderId) {
        int changed = jdbc.update("""
            UPDATE redemption_order SET order_status='FULFILLED',fulfilled_at=NOW()
            WHERE redemption_id=? AND order_status='PENDING_PICKUP'
            """, orderId);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "订单不存在或不可领取");
    }

    @Transactional
    public void grantPoints(long targetId, int points, String reason) {
        if (points < 1 || points > 100000 || reason == null || reason.isBlank())
            throw bad("积分需为1到100000，并填写原因");
        row("SELECT user_id FROM app_user WHERE user_id=?", targetId);
        ledger(targetId, points, "ADMIN_ADJUST", "ADMIN:" + UUID.randomUUID(),
            null, null, null, null, null, reason);
    }

    public List<Map<String, Object>> rankings(String kind) {
        return switch (kind) {
            case "routes" -> jdbc.queryForList("""
                SELECT r.route_id AS routeId,r.title,COUNT(f.favorite_id) AS score
                FROM `route` r LEFT JOIN route_favorite f
                  ON f.route_id=r.route_id AND f.is_active=1
                WHERE r.route_status='PUBLISHED'
                GROUP BY r.route_id,r.title ORDER BY score DESC,r.route_id LIMIT 20
                """);
            case "users" -> jdbc.queryForList("""
                SELECT u.user_id AS userId,u.nickname,
                       COALESCE(SUM(l.change_points),0) AS score
                FROM app_user u LEFT JOIN point_ledger l ON l.user_id=u.user_id
                WHERE u.account_status='ACTIVE'
                  AND EXISTS (SELECT 1 FROM user_role ur JOIN app_role r ON r.role_id=ur.role_id
                              WHERE ur.user_id=u.user_id AND r.role_code='USER')
                GROUP BY u.user_id,u.nickname ORDER BY score DESC,u.user_id LIMIT 20
                """);
            case "points" -> jdbc.queryForList("""
                SELECT p.point_id AS pointId,p.point_name AS pointName,
                       COUNT(c.checkin_id) AS score
                FROM map_point p LEFT JOIN route_point rp ON rp.point_id=p.point_id
                LEFT JOIN route_checkin c ON c.route_id=rp.route_id
                WHERE p.point_status='ACTIVE'
                GROUP BY p.point_id,p.point_name ORDER BY score DESC,p.point_id LIMIT 20
                """);
            default -> throw bad("排行类型需为 routes、users 或 points");
        };
    }
}
