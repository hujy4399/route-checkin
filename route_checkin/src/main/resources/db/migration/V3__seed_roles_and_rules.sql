INSERT IGNORE INTO app_role (role_code, role_name, description) VALUES
('USER', '普通用户', '浏览、发布、互动、兑换'),
('OFFICIAL', '官方人员', '可发布限时活动路线'),
('ADMIN', '管理员', '审核、角色分配和商品管理');

INSERT IGNORE INTO point_rule (rule_code, rule_name, points, is_active) VALUES
('PUBLISH_LIKE', '路线首次获得点赞', 1, 1),
('PUBLISH_FAVORITE', '路线首次获得收藏', 2, 1),
('PUBLISH_CHECKIN', '路线首次获得打卡', 3, 1);
