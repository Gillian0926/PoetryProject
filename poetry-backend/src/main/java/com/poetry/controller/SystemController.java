package com.poetry.controller;

import com.poetry.service.PoetryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Autowired
    private PoetryService poetryService;

    // GET /api/system/preheat
    @GetMapping("/preheat")
    public String preheatCache() {
        CompletableFuture.runAsync(() -> {
            // ... 这里可以调用预热逻辑，或者void ...
            System.out.println("开始后台预热...");
        });
        return "预热任务已启动";
    }
}