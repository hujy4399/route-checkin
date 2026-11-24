package com.example.route_checkin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final ProjectService service;

    public ApiController(ProjectService service) {
        this.service = service;
    }

    public record RegisterRequest(String username, String password, String nickname) {}
    public record RouteRequest(String title, String description, String coverUrl, String routeKind,
                               Integer recommendedMinutes, LocalDateTime availableFrom,
                               LocalDateTime availableUntil, Integer checkinBonusPoints,
                               List<Long> pointIds, List<Long> keywordIds) {}
    public record MapPointRequest(String name, Double longitude, Double latitude, String address) {}
    public record ProductRequest(String name, String description, Integer pointsPrice, Integer stock) {}
    public record QuantityRequest(Integer quantity) {}
    public record NoteRequest(String note) {}
    public record RoleRequest(String role) {}
    public record PointsRequest(Integer points, String reason) {}
    public record KeywordRequest(String name) {}

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody RegisterRequest request) {
        return Map.of("userId", service.register(request), "status", "PENDING");
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me(Authentication auth) {
        return service.me(auth.getName());
    }

    @GetMapping("/my/routes")
    public List<Map<String, Object>> myRoutes(Authentication auth) {
        return service.myRoutes(auth.getName());
    }

    @GetMapping("/my/redemptions")
    public List<Map<String, Object>> myOrders(Authentication auth) {
        return service.myOrders(auth.getName());
    }

    @GetMapping("/routes")
    public List<Map<String, Object>> routes(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.listRoutes(keyword, q, page, size);
    }

    @GetMapping("/routes/{id}")
    public Map<String, Object> route(@PathVariable long id) {
        return service.routeDetail(id);
    }

    @PostMapping("/routes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createRoute(Authentication auth, @RequestBody RouteRequest request) {
        return Map.of("routeId", service.createRoute(auth.getName(), request), "status", "PENDING");
    }

    @PostMapping("/routes/{id}/close")
    public void closeRoute(Authentication auth, @PathVariable long id) {
        service.closeRoute(id, auth.getName());
    }

    @GetMapping("/map-points")
    public List<Map<String, Object>> mapPoints() {
        return service.mapPoints();
    }

    @PostMapping("/map-points")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createMapPoint(Authentication auth, @RequestBody MapPointRequest request) {
        return Map.of("pointId", service.createMapPoint(auth.getName(), request));
    }

    @GetMapping("/keywords")
    public List<Map<String, Object>> keywords() {
        return service.keywords();
    }

    @PostMapping("/admin/keywords")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createKeyword(@RequestBody KeywordRequest request) {
        return Map.of("keywordId", service.createKeyword(request.name()));
    }

    @PostMapping("/routes/{id}/like")
    public void like(Authentication auth, @PathVariable long id) {
        service.setInteraction(id, auth.getName(), true, true);
    }

    @DeleteMapping("/routes/{id}/like")
    public void unlike(Authentication auth, @PathVariable long id) {
        service.setInteraction(id, auth.getName(), true, false);
    }

    @PostMapping("/routes/{id}/favorite")
    public void favorite(Authentication auth, @PathVariable long id) {
        service.setInteraction(id, auth.getName(), false, true);
    }

    @DeleteMapping("/routes/{id}/favorite")
    public void unfavorite(Authentication auth, @PathVariable long id) {
        service.setInteraction(id, auth.getName(), false, false);
    }

    @PostMapping("/routes/{id}/checkins")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> checkin(Authentication auth, @PathVariable long id,
                                        @RequestBody(required = false) NoteRequest request) {
        return Map.of("checkinId", service.checkin(id, auth.getName(), request == null ? null : request.note()));
    }

    @GetMapping("/products")
    public List<Map<String, Object>> products() {
        return service.products();
    }

    @PostMapping("/products/{id}/redemptions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> redeem(Authentication auth, @PathVariable long id,
                                       @RequestBody QuantityRequest request) {
        return Map.of("redemptionId", service.redeem(id, auth.getName(),
            request.quantity() == null ? 0 : request.quantity()));
    }

    @PostMapping("/redemptions/{id}/cancel")
    public void cancel(Authentication auth, @PathVariable long id) {
        service.cancelOrder(id, auth.getName());
    }

    @GetMapping("/rankings/{kind}")
    public List<Map<String, Object>> rankings(@PathVariable String kind) {
        return service.rankings(kind);
    }

    @PostMapping("/admin/users/{id}/review")
    public void reviewUser(Authentication auth, @PathVariable long id,
                           @RequestParam(defaultValue = "true") boolean approve) {
        service.reviewUser(id, auth.getName(), approve);
    }

    @GetMapping("/admin/users/pending")
    public List<Map<String, Object>> pendingUsers() {
        return service.pendingUsers();
    }

    @GetMapping("/admin/users")
    public List<Map<String, Object>> adminUsers() {
        return service.adminUsers();
    }

    @GetMapping("/admin/products")
    public List<Map<String, Object>> adminProducts() {
        return service.adminProducts();
    }

    @GetMapping("/admin/redemptions")
    public List<Map<String, Object>> adminOrders() {
        return service.adminOrders();
    }

    @GetMapping("/admin/routes/pending")
    public List<Map<String, Object>> pendingRoutes() {
        return service.pendingRoutes();
    }

    @PostMapping("/admin/users/{id}/roles")
    public void grantRole(Authentication auth, @PathVariable long id, @RequestBody RoleRequest request) {
        service.grantRole(id, request.role(), auth.getName());
    }

    @PostMapping("/admin/users/{id}/points")
    public void grantPoints(@PathVariable long id, @RequestBody PointsRequest request) {
        service.grantPoints(id, request.points() == null ? 0 : request.points(), request.reason());
    }

    @PostMapping("/admin/routes/{id}/review")
    public void reviewRoute(Authentication auth, @PathVariable long id,
                            @RequestParam(defaultValue = "true") boolean approve) {
        service.reviewRoute(id, auth.getName(), approve);
    }

    @PostMapping("/admin/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createProduct(@RequestBody ProductRequest request) {
        return Map.of("productId", service.createProduct(request));
    }

    @PostMapping("/admin/products/{id}/off-sale")
    public void offSale(@PathVariable long id) {
        service.offSaleProduct(id);
    }

    @PostMapping("/admin/products/{id}/restock")
    public void restock(@PathVariable long id, @RequestBody QuantityRequest request) {
        service.restockProduct(id, request.quantity() == null ? 0 : request.quantity());
    }

    @PostMapping("/admin/redemptions/{id}/fulfill")
    public void fulfill(@PathVariable long id) {
        service.fulfillOrder(id);
    }
}
