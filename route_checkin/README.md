# 城市路线打卡后端

Java 17、Spring Boot 4.0、MyBatis、MySQL 8.0、Flyway。数据库设计见上级目录的《路线打卡.md》。

## 启动

1. 启动本机 MySQL，创建 UTF-8 数据库：`CREATE DATABASE route_checkin CHARACTER SET utf8mb4;`。
2. 在 PowerShell 中进入项目目录，设置环境变量。首次启动需提供管理员初始化凭据：

   ```powershell
   $env:DB_PASSWORD="你的 MySQL 密码"
   $env:APP_ADMIN_USERNAME="admin"
   $env:APP_ADMIN_PASSWORD="自定的强密码"
   .\mvnw.cmd spring-boot:run
   ```

   启动时 Flyway 顺序执行 V1～V5；V3 加入角色和积分规则。已有管理员后，之后启动不再需要 `APP_ADMIN_USERNAME` 和 `APP_ADMIN_PASSWORD`。不要把密码写入仓库、截图或分享终端历史。

3. 打开 `http://localhost:8080/` 使用前端；`GET http://localhost:8080/hello` 可检查服务。浏览器或 API 工具访问受保护接口时用 HTTP Basic：用户名和密码为平台用户凭据。首次管理员由上一步环境变量创建。普通用户注册后状态为 `PENDING`，管理员审核通过才能认证。

## 前端使用

前端位于 `src/main/resources/static/`，由同一个 Spring Boot 服务提供，无须安装 Node 或配置跨域。页面覆盖路线搜索、详情、点赞收藏与打卡、地图点位、路线发布、积分商城、三个榜单、个人路线与订单，以及管理员审核和商城管理。前端提交的数据直接调用下述 `/api` 接口，空数据时显示空状态。

先以管理员账号登录，添加关键词与商品，审核注册用户。用户添加至少两个地图点后可发布路线；审核通过才出现在首页。地图选点使用 Leaflet 和 OpenStreetMap 在线资源，无法联网时可直接填写经纬度。登录密码只存于当前页面内存，刷新页面后需要重新登录；本地开发请使用 `localhost`，正式部署应启用 HTTPS。

## 接口

所有路径以 `/api` 开头。分页 `page` 从 0 开始，`size` 最大 100。写接口使用 JSON；HTTP Basic 在每次受保护请求中传入凭据，生产部署必须使用 HTTPS。

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 用户 | `POST /auth/register` | 注册；请求 `username,password,nickname` |
| 用户 | `GET /auth/me` | 验证登录并查看角色、积分余额 |
| 目录 | `GET /map-points`、`GET /keywords` | 可选点位和关键词 |
| 点位 | `POST /map-points` | 登录用户添加地图点 |
| 路线 | `GET /routes?keyword=散步&q=公园&page=0&size=20` | 已发布路线列表，按关键词和文本筛选 |
| 路线 | `GET /routes/{id}` | 详情、点位和关键词 |
| 路线 | `POST /routes` | 发布待审核路线 |
| 路线 | `GET /my/routes`、`POST /routes/{id}/close` | 我的路线、关闭路线 |
| 互动 | `POST/DELETE /routes/{id}/like` | 点赞／取消 |
| 互动 | `POST/DELETE /routes/{id}/favorite` | 收藏／取消 |
| 互动 | `POST /routes/{id}/checkins` | 每人每路线一次打卡 |
| 商城 | `GET /products` | 在售商品 |
| 商城 | `POST /products/{id}/redemptions` | 请求 `{"quantity":1}`，积分兑换 |
| 商城 | `GET /my/redemptions`、`POST /redemptions/{id}/cancel` | 我的订单、取消待领取订单 |
| 排行 | `GET /rankings/routes`、`/users`、`/points` | 收藏、余额、点位热度榜 |
| 管理 | `GET /admin/users/pending`、`POST /admin/users/{id}/review?approve=true` | 审核用户 |
| 管理 | `GET /admin/users`、`GET /admin/products`、`GET /admin/redemptions` | 管理页面列表（最多显示最近 100 条） |
| 管理 | `POST /admin/users/{id}/roles` | 请求 `{"role":"OFFICIAL"}` |
| 管理 | `POST /admin/users/{id}/points` | 请求 `{"points":100,"reason":"活动预算"}` |
| 管理 | `GET /admin/routes/pending`、`POST /admin/routes/{id}/review?approve=true` | 审核路线 |
| 管理 | `POST /admin/keywords` | 请求 `{"name":"散步"}` |
| 管理 | `POST /admin/products` | 创建商品 |
| 管理 | `POST /admin/products/{id}/restock`、`/off-sale` | 补货、下架 |
| 管理 | `POST /admin/redemptions/{id}/fulfill` | 确认领取 |
| 报表 | `GET /admin/reports/rankings.docx` | 下载三个排行榜的 Word 报表 |

路线发布示例：

```json
{
  "title": "城市公园散步",
  "description": "从南门步行至湖边",
  "routeKind": "NORMAL",
  "recommendedMinutes": 60,
  "checkinBonusPoints": 2,
  "pointIds": [1, 2, 3],
  "keywordIds": [1]
}
```

官方限时活动将 `routeKind` 设为 `OFFICIAL_EVENT`，并增加 `availableFrom`、`availableUntil`，例如 `2026-12-01T09:00:00`。发布者须有 `OFFICIAL` 角色。

## 实现路径与课程要求

- `ApiController` 接受请求，`ProjectService` 验证规则并开启事务，`RouteMapper` 用 MyBatis 执行路线查询。写业务使用 `JdbcTemplate` 的参数化 SQL，避免拼接用户输入。
- V1/V2 创建 15 张表。V3 加入角色和固定积分规则。V4 包含余额函数 `fn_user_points`、关闭路线存储过程 `sp_close_published_route`、点赞更新时间触发器 `tr_route_like_updated`。V5 提供公开路线统计视图和后台兑换视图；接口访问权限由 Spring Security 的角色规则控制。报表接口用 Apache POI 导出 Word 文件。
- 点赞／收藏首次操作才给发布者发积分；取消后再次操作不重复发奖。打卡唯一约束保证每人每路线一次。积分余额由流水求和。兑换在同一事务内检查余额及库存、创建订单、扣库存并写积分流水；取消时退款并恢复库存。
- MySQL 中已执行的 Flyway 脚本不应原地修改；后续结构调整继续新增更高版本迁移文件。

## 手工演示顺序

管理员创建关键词；用户注册；管理员审核用户；用户添加至少两个点位并发布路线；管理员审核路线；另一用户点赞、收藏、打卡；管理员创建商品并给官方账号配置积分；用户兑换商品；查看三个排行榜和积分余额；管理员确认领取。

执行 `.\mvnw.cmd test` 需要 MySQL 运行且当前终端设有 `DB_PASSWORD`，因为默认的上下文测试会启动应用并执行 Flyway。数据库连接和迁移应先于接口演示验证。
