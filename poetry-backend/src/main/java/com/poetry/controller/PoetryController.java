package com.poetry.controller;

import com.poetry.service.PoetryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;










@RestController
@RequestMapping("/api/poems")
@CrossOrigin(origins = "*")
public class PoetryController {

    @Autowired
    private PoetryService poetryService;
    @Autowired
    private JdbcTemplate jdbcTemplate; // 注入 JdbcTemplate 用于原生查询



    // 【新增】根据作者名，查询其所有作品
    // GET /api/poems/author?name=李清照
    @GetMapping("/author")
    public Map<String, Object> getPoetWorks(@RequestParam String name) {
        // 1. 查基本信息 (朝代, 总数) —— 同时兼容繁体原名与简体查询
        String infoSql = """
            SELECT dynasty, COUNT(*) as cnt 
            FROM poems 
            WHERE author = ? OR author_simple = ?
            GROUP BY dynasty 
            ORDER BY cnt DESC 
            LIMIT 1
        """;
        
        List<Map<String, Object>> info = jdbcTemplate.queryForList(infoSql, name, name);
        
        if (info.isEmpty()) {
            return Map.of("found", false);
        }
        
        String dynasty = (String) info.get(0).get("dynasty");
        Long count = ((Number) info.get(0).get("cnt")).longValue();

        // 2. 查代表作 (取前 20 首)
        String poemsSql = "SELECT author, title, content FROM poems WHERE author = ? OR author_simple = ? LIMIT 20";
        List<Map<String, Object>> poems = jdbcTemplate.queryForList(poemsSql, name, name);

        // 返回库里真实的作者名（繁体），便于前端显示
        String canonical = name;
        if (!poems.isEmpty() && poems.get(0).get("author") != null) {
            canonical = (String) poems.get(0).get("author");
        }

        return Map.of(
            "found", true,
            "author", canonical,
            "query", name,
            "dynasty", dynasty != null ? dynasty : "未知",
            "total_count", count,
            "poems", poems
        );
    }

    // 根据花卉获取详情
    // GET /api/poems/by-flower
    @GetMapping("/by-flower")
    public List<Map<String, Object>> getPoemsByFlower(
            @RequestParam String flower, 
            @RequestParam String timeLabel, 
            @RequestParam(defaultValue = "dynasty") String groupType) {
        return poetryService.getPoemsDetail("flower", flower, timeLabel, groupType);
    }

    // 根据情感获取详情
    // GET /api/poems/by-emotion
    @GetMapping("/by-emotion")
    public List<Map<String, Object>> getPoemsByEmotion(
            @RequestParam String emotion, 
            @RequestParam String timeLabel, 
            @RequestParam(defaultValue = "dynasty") String groupType) {
        return poetryService.getPoemsDetail("emotion", emotion, timeLabel, groupType);
    }

    // 通用语义搜索
    // GET /api/poems/search
    @GetMapping("/search")
    public List<Map<String, Object>> searchPoems(
            @RequestParam String query, 
            @RequestParam(defaultValue = "all") String timeLabel, 
            @RequestParam(defaultValue = "dynasty") String groupType) {
        return poetryService.searchPoems(query, timeLabel, groupType);
    }
}