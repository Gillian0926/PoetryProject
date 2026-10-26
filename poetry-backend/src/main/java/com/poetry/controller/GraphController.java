package com.poetry.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/graph")
@CrossOrigin(origins = "*")
public class GraphController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/data")
    public Map<String, Object> getGraphData() {
        // 1. 定义分类（顺序！！！！！！！！！！！）
        // 索引 0 = 唐代 (红色)
        // 索引 1 = 宋代 (蓝色)
        // 索引 2 = 其他 (灰色)
        List<Map<String, String>> categories = new ArrayList<>();
        categories.add(Map.of("name", "唐代"));
        categories.add(Map.of("name", "宋代"));
        categories.add(Map.of("name", "其他"));

        // 2. 获取节点
        String sqlNodes = "SELECT author, dynasty, poem_count FROM poet_profiles";
        List<Map<String, Object>> nodes = jdbcTemplate.queryForList(sqlNodes);
        
        List<Map<String, Object>> formattedNodes = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            Map<String, Object> n = new HashMap<>();
            String author = (String) node.get("author");
            String dynasty = (String) node.get("dynasty");
            Integer count = (Integer) node.get("poem_count");

            n.put("id", author);   // 唯一标识
            n.put("name", author); // 显示名称
            // 节点大小：取对数防止太大，底数设为5保证最小值
            n.put("symbolSize", Math.log(count + 1) * 8 + 5); 
            n.put("value", count); // 鼠标悬停显示的数值
            
            // 设置分类索引
            n.put("category", getCategoryIndex(dynasty));
            
            formattedNodes.add(n);
        }


        // 3. 获取连线
        String sqlLinks = """
            SELECT a.author as source, b.author as target, (a.style_vector <=> b.style_vector) as distance
            FROM poet_profiles a
            JOIN LATERAL (
                SELECT author, style_vector 
                FROM poet_profiles b 
                WHERE a.author != b.author 
                ORDER BY a.style_vector <=> b.style_vector ASC 
                LIMIT 2  -- 每个人找最像的2个朋友
            ) b ON true
            
            WHERE (a.style_vector <=> b.style_vector) < 0.35
        """;
        List<Map<String, Object>> links = jdbcTemplate.queryForList(sqlLinks);

        return Map.of(
            "nodes", formattedNodes,
            "links", links,
            "categories", categories
        );
    }
    
    // 辅助方法：根据朝代返回索引
    private int getCategoryIndex(String dynasty) {
        if (dynasty == null) return 2;
        if (dynasty.contains("唐")) return 0; // 对应 categories 第1个
        if (dynasty.contains("宋")) return 1; // 对应 categories 第2个
        return 2;                             // 对应 categories 第3个
    }
}