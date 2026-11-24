package com.example.route_checkin;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RouteMapper {
    @Select("""
        <script>
        SELECT r.route_id AS routeId, r.title, r.recommended_minutes AS recommendedMinutes,
               r.published_at AS publishedAt, u.nickname AS publisherNickname,
               (SELECT COUNT(*) FROM route_favorite f
                WHERE f.route_id = r.route_id AND f.is_active = 1) AS favoriteCount
        FROM `route` r JOIN app_user u ON u.user_id = r.publisher_id
        WHERE r.route_status = 'PUBLISHED'
        <if test="keyword != null and keyword != ''">
          AND EXISTS (SELECT 1 FROM route_keyword rk JOIN keyword k
                      ON k.keyword_id = rk.keyword_id
                      WHERE rk.route_id = r.route_id AND k.keyword_name = #{keyword})
        </if>
        <if test="q != null and q != ''">
          AND (r.title LIKE CONCAT('%', #{q}, '%')
               OR r.description LIKE CONCAT('%', #{q}, '%'))
        </if>
        ORDER BY r.published_at DESC, r.route_id DESC
        LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
    List<Map<String, Object>> listPublished(@Param("keyword") String keyword,
                                             @Param("q") String q,
                                             @Param("limit") int limit,
                                             @Param("offset") long offset);

    @Select("""
        SELECT route_id AS routeId, title, description, cover_url AS coverUrl,
               publisher_id AS publisherId, route_kind AS routeKind,
               recommended_minutes AS recommendedMinutes,
               available_from AS availableFrom, available_until AS availableUntil,
               published_at AS publishedAt
        FROM `route` WHERE route_id = #{id} AND route_status = 'PUBLISHED'
        """)
    Map<String, Object> publishedDetail(@Param("id") long id);

    @Select("""
        SELECT rp.sequence_no AS sequenceNo, p.point_id AS pointId, p.point_name AS pointName,
               p.longitude, p.latitude, rp.stay_minutes AS stayMinutes, rp.note
        FROM route_point rp JOIN map_point p ON p.point_id = rp.point_id
        WHERE rp.route_id = #{id} ORDER BY rp.sequence_no
        """)
    List<Map<String, Object>> points(@Param("id") long id);

    @Select("""
        SELECT k.keyword_name FROM route_keyword rk JOIN keyword k
        ON k.keyword_id = rk.keyword_id WHERE rk.route_id = #{id}
        ORDER BY k.keyword_name
        """)
    List<String> keywords(@Param("id") long id);
}
