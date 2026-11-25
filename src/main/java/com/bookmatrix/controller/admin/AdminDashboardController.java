package com.bookmatrix.controller.admin;

import com.bookmatrix.dto.Views.AuditView;
import com.bookmatrix.dto.Views.Dashboard;
import com.bookmatrix.dto.Views.InventoryView;
import com.bookmatrix.service.DashboardService;
import com.bookmatrix.service.EventService;
import com.bookmatrix.util.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Everything under /api/admin requires role ADMIN (enforced by AuthInterceptor). */
@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final EventService eventService;

    public AdminDashboardController(DashboardService dashboardService, EventService eventService) {
        this.dashboardService = dashboardService;
        this.eventService = eventService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Dashboard> dashboard() {
        return ApiResponse.ok(dashboardService.build());
    }

    @GetMapping("/inventory")
    public ApiResponse<List<InventoryView>> inventory(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return ApiResponse.ok(eventService.inventory(includeArchived));
    }

    @GetMapping("/activity")
    public ApiResponse<List<AuditView>> activity(@RequestParam(defaultValue = "50") int limit,
                                                 @RequestParam(required = false) String entityType,
                                                 @RequestParam(required = false) Long entityId) {
        if (entityType != null && entityId == null) {
            entityType = null;
        }
        return ApiResponse.ok(dashboardService.activity(limit, entityType == null ? null : entityType.toUpperCase(), entityId));
    }
}
