-- The function is used by reports to calculate the current available balance.
CREATE FUNCTION fn_user_points(p_user_id BIGINT)
RETURNS BIGINT
READS SQL DATA
RETURN (SELECT COALESCE(SUM(change_points), 0)
        FROM point_ledger WHERE user_id = p_user_id);

-- Closing a route keeps its historical check-ins and rewards intact.
CREATE PROCEDURE sp_close_published_route(IN p_route_id BIGINT, IN p_publisher_id BIGINT)
UPDATE `route` SET route_status = 'CLOSED'
WHERE route_id = p_route_id
  AND publisher_id = p_publisher_id
  AND route_status = 'PUBLISHED';

-- The database maintains this timestamp even if a future client forgets it.
CREATE TRIGGER tr_route_like_updated
BEFORE UPDATE ON route_like
FOR EACH ROW SET NEW.updated_at = CURRENT_TIMESTAMP;
