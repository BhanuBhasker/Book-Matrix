package com.bookmatrix.controller.admin;

import com.bookmatrix.demo.DemoCatalog;
import com.bookmatrix.dto.Views.DemoInfo;
import com.bookmatrix.dto.Views.DemoOutput;
import com.bookmatrix.util.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** "Java Lab": lets the admin run each syllabus demonstration from the browser during the viva. */
@RestController
@RequestMapping("/api/admin/demos")
public class AdminDemoController {

    private final DemoCatalog catalog;

    public AdminDemoController(DemoCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ApiResponse<List<DemoInfo>> list() {
        return ApiResponse.ok(catalog.list());
    }

    @PostMapping("/{name}/run")
    public ApiResponse<DemoOutput> run(@PathVariable String name) {
        return ApiResponse.ok(catalog.run(name));
    }
}
