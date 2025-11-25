package com.poetry.controller;

import com.poetry.service.PoetryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/chart") // 统一前缀
@CrossOrigin(origins = "*")
public class ChartController {

    @Autowired
    private PoetryService poetryService;

    // 获取花卉河流图数据
    // GET /api/chart/flower?groupType=dynasty
    @GetMapping("/flower")
    public Map<String, Object> getFlowerChart(@RequestParam(defaultValue = "dynasty") String groupType)
    {
        return poetryService.getRiverData("flower", groupType);
    }

    // 获取情感河流图数据
    // GET /api/chart/emotion?groupType=dynasty
    @GetMapping("/emotion")
    public Map<String, Object> getEmotionChart(@RequestParam(defaultValue = "dynasty") String groupType) {
        return poetryService.getRiverData("emotion", groupType);
    }
}