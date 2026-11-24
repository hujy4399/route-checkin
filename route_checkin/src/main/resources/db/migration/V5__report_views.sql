-- Public view: only published routes and aggregate counts, no private account data.
CREATE VIEW v_public_route_stats AS
SELECT r.route_id, r.title, r.publisher_id, r.published_at,
       (SELECT COUNT(*) FROM route_like l
        WHERE l.route_id = r.route_id AND l.is_active = 1) AS like_count,
       (SELECT COUNT(*) FROM route_favorite f
        WHERE f.route_id = r.route_id AND f.is_active = 1) AS favorite_count,
       (SELECT COUNT(*) FROM route_checkin c
        WHERE c.route_id = r.route_id) AS checkin_count
FROM `route` r
WHERE r.route_status = 'PUBLISHED';

-- Management view: order handling details without password hashes.
CREATE VIEW v_admin_redemptions AS
SELECT o.redemption_id, o.order_status, o.quantity, o.unit_points_snapshot,
       o.redeemed_at, o.fulfilled_at, u.user_id, u.nickname, p.product_name
FROM redemption_order o
JOIN app_user u ON u.user_id = o.user_id
JOIN reward_product p ON p.product_id = o.product_id;
