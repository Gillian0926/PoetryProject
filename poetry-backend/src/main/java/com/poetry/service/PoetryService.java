package com.poetry.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.poetry.util.PoetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PoetryService {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private PoetryUtils poetryUtils;

    private final Map<String, Map<String, Object>> riverCache = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    @PostConstruct
    public void init() {
        try {
            String sql = "CREATE TABLE IF NOT EXISTS poem_details_cache (cache_key VARCHAR(255) PRIMARY KEY, data JSONB)";
            jdbcTemplate.execute(sql);
        } catch (Exception e) {}
    }

    // 1. 河流图 (保持不变)
    public Map<String, Object> getRiverData(String field, String groupType) {
        String cacheKey = "RIVER:" + field + ":" + groupType;
        if (riverCache.containsKey(cacheKey)) return riverCache.get(cacheKey);

        String sql = String.format("""
            SELECT p.dynasty as year_str, t.%s as category, COUNT(t.id) as cnt 
            FROM tags t JOIN poems p ON t.poem_id = p.id 
            WHERE t.%s IS NOT NULL AND length(t.%s) > 0 
            GROUP BY p.dynasty, t.%s
        """, field, field, field, field);

        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        Map<String, Map<String, Integer>> aggregatedData = new HashMap<>();
        Map<String, Integer> globalCount = new HashMap<>();
        Set<String> activeLabels = new HashSet<>();

        for (Map<String, Object> row : list) {
            Object yearObj = row.get("year_str");
            String val = (String) row.get("category");
            Number cNum = (Number) row.get("cnt");
            int c = cNum != null ? cNum.intValue() : 0;

            if (yearObj == null || val == null) continue;
            String timeLabel = poetryUtils.getTimeLabel(yearObj.toString(), groupType);
            
            String cleanVal = val;
            if (field.equals("emotion")) {
                cleanVal = poetryUtils.normalizeEmotion(val);
                if (cleanVal == null) continue;
            } else {
                cleanVal = val.replace("。", "").replace("，", "").trim();
                if (PoetryUtils.IGNORE_DICT.contains(cleanVal) || cleanVal.length() > 4) continue;
            }

            globalCount.put(cleanVal, globalCount.getOrDefault(cleanVal, 0) + c);
            activeLabels.add(timeLabel);
            aggregatedData.putIfAbsent(timeLabel, new HashMap<>());
            aggregatedData.get(timeLabel).put(cleanVal, aggregatedData.get(timeLabel).getOrDefault(cleanVal, 0) + c);
        }

        Set<String> topCategories = globalCount.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue()).limit(20).map(Map.Entry::getKey).collect(Collectors.toSet());
        
        Map<String, Object> result = Map.of("dynasties", poetryUtils.sortLabels(activeLabels, groupType), "flowers", topCategories, "data", aggregatedData);
        riverCache.put(cacheKey, result);
        return result;
    }

    // 2. 详情查询
    public List<Map<String, Object>> getPoemsDetail(String field, String keyword, String timeLabel, String groupType) {
        String cacheKey = String.format("DETAIL:%s:%s:%s:%s", field, groupType, timeLabel, keyword);
        return getCachedOrCompute(cacheKey, () -> {
            String prompt = field.equals("flower") ? "描写" + keyword + "的古诗" : "表达" + keyword + "情感的古诗";
            return computeWithVector(prompt, timeLabel, groupType, field.equals("flower") ? keyword : null);
        });
    }

    // 3. 搜索
    public List<Map<String, Object>> searchPoems(String query, String timeLabel, String groupType) {
        String cacheKey = String.format("SEARCH:%s:%s:%s", groupType, timeLabel, query);
        return getCachedOrCompute(cacheKey, () -> computeWithVector(query, timeLabel, groupType, null));
    }

    // 4. 趋势摘要
    public String generateTrendSummary(String era, String field, String targetName) {
        Map<String, Object> riverData = getRiverData(field, "5"); 
        List<String> timelines = (List<String>) riverData.get("dynasties");
        Map<String, Map<String, Integer>> data = (Map<String, Map<String, Integer>>) riverData.get("data");
        
        String subject = field.equals("flower") ? "花卉" : "情感";
        StringBuilder summary = new StringBuilder();
        
        if (targetName != null) {
            summary.append("【").append(era).append(" · ").append(targetName).append("】演变趋势：\n");
        } else {
            summary.append("【").append(era).append(subject).append("演变总览】：\n");
        }

        boolean foundData = false;
        int countPoints = 0;
        for (String time : timelines) {
            if (isEraMatch(era, time)) {
                Map<String, Integer> counts = data.get(time);
                if (counts == null || counts.isEmpty()) continue;

                if (targetName != null) {
                    Integer count = counts.get(targetName);
                    if (count != null && count > 0) {
                        summary.append("- ").append(time).append(": ").append(count).append("首\n");
                        foundData = true;
                        countPoints++;
                    }
                } else {
                    List<Map.Entry<String, Integer>> top = counts.entrySet().stream()
                        .sorted((a, b) -> b.getValue() - a.getValue()).limit(1).collect(Collectors.toList());
                    if (!top.isEmpty()) {
                        summary.append("- ").append(time).append(": 主导是“").append(top.get(0).getKey()).append("”\n");
                        foundData = true;
                    }
                }
            }
        }
        if (countPoints > 50) summary.append("(数据点较多，仅展示部分...)");
        return foundData ? summary.toString() : "该时期暂无相关数据。";
    }

    private List<Map<String, Object>> getCachedOrCompute(String key, java.util.function.Supplier<List<Map<String, Object>>> computer) {
        try {
            String json = jdbcTemplate.queryForObject("SELECT data::text FROM poem_details_cache WHERE cache_key = ?", String.class, key);
            if (json != null) return gson.fromJson(json, new TypeToken<List<Map<String, Object>>>(){}.getType());
        } catch (Exception e) {}
        List<Map<String, Object>> result = computer.get();
        if (!result.isEmpty()) {
            try { jdbcTemplate.update("INSERT INTO poem_details_cache (cache_key, data) VALUES (?, ?::jsonb) ON CONFLICT (cache_key) DO NOTHING", key, gson.toJson(result)); } catch (Exception e) {}
        }
        return result;
    }

    // ================= 【251123修复】更宽容的匹配逻辑 =================
    private List<Map<String, Object>> computeWithVector(String prompt, String timeLabel, String groupType, String filterKeyword) {
        System.out.printf("🔎 计算 -> Prompt:[%s], 时间:[%s], 过滤词:[%s]%n", prompt, timeLabel, filterKeyword);
        List<Double> queryVector = embeddingService.getEmbeddingWithCache(prompt);
        if (queryVector == null) return new ArrayList<>();

        int[] yearRange = parseTimeRange(timeLabel);
        String vectorStr = queryVector.toString();
        List<Map<String, Object>> candidates = new ArrayList<>();

        // --- 1. 宽泛化关键词 (Smart Keyword) ---
        // 如果搜 "梅花"，自动放宽为 "梅"；
        String smartKeyword = filterKeyword;
        if (filterKeyword != null && filterKeyword.length() == 2 && filterKeyword.endsWith("花")) {
            smartKeyword = filterKeyword.substring(0, 1); // "梅花" -> "梅"
        }

        // 场景 A: 通用搜索 (用户搜句子/词)
        if (filterKeyword == null) {
            String cleanPrompt = prompt.replace("，", "").replace("。", "").trim();
            String bestKeyword = extractLongestSegment(prompt); 
            
            String exactSql = """
                SELECT p.title, p.author, p.content, p.dynasty as year_str, 0.0 as distance
                FROM tags t JOIN poems p ON t.poem_id = p.id
                WHERE (p.title ILIKE ? OR p.content ILIKE ?) 
                LIMIT 50
            """;
            List<Map<String, Object>> exactMatches = jdbcTemplate.queryForList(exactSql, "%" + cleanPrompt + "%", "%" + cleanPrompt + "%");
            
            if (exactMatches.isEmpty() && bestKeyword.length() > 1) {
                exactMatches = jdbcTemplate.queryForList(exactSql, "%" + bestKeyword + "%", "%" + bestKeyword + "%");
            }
            
            String semanticSql = """
                SELECT p.title, p.author, p.content, p.dynasty as year_str, (t.embedding <-> ?::vector) as distance
                FROM tags t JOIN poems p ON t.poem_id = p.id
                WHERE t.embedding IS NOT NULL
                ORDER BY distance ASC 
                LIMIT 100
            """;
            List<Map<String, Object>> semanticMatches = jdbcTemplate.queryForList(semanticSql, vectorStr);
            
            Set<String> seen = new HashSet<>();
            for (Map<String, Object> row : exactMatches) {
                String key = row.get("title") + "_" + row.get("author");
                if (!seen.contains(key)) { row.put("is_exact", true); candidates.add(row); seen.add(key); }
            }
            for (Map<String, Object> row : semanticMatches) {
                String key = row.get("title") + "_" + row.get("author");
                if (!seen.contains(key)) { row.put("is_exact", false); candidates.add(row); seen.add(key); }
            }
            
        } else {
            // 场景 B: 详情点击 (使用 smartKeyword 扩大召回)
            // SQL 逻辑：标签包含 '梅'，或者 标签包含 '梅花'
            String sql = String.format("""
                SELECT p.title, p.author, p.content, p.dynasty as year_str, (t.embedding <-> ?::vector) as distance
                FROM tags t JOIN poems p ON t.poem_id = p.id 
                WHERE t.embedding IS NOT NULL 
                  AND (t.flower ILIKE ? OR t.flower ILIKE ?) -- 只要沾边都算
                ORDER BY distance ASC 
                LIMIT 1000  -- 【修改】扩大限制到 1000
            """);
            
            // 针对时间范围的优化查询
            if (yearRange != null) {
                String rangeSql = String.format("""
                    SELECT p.title, p.author, p.content, p.dynasty as year_str, (t.embedding <-> ?::vector) as distance
                    FROM tags t JOIN poems p ON t.poem_id = p.id 
                    WHERE t.embedding IS NOT NULL 
                      AND p.dynasty ~ '^\\d+$' AND CAST(p.dynasty AS INTEGER) BETWEEN ? AND ? 
                      AND (t.flower ILIKE ? OR t.flower ILIKE ?) 
                    ORDER BY distance ASC 
                    LIMIT 1000
                """);
                candidates = jdbcTemplate.queryForList(rangeSql, vectorStr, yearRange[0], yearRange[1], "%" + filterKeyword.trim() + "%", "%" + smartKeyword + "%");
            } else {
                candidates = jdbcTemplate.queryForList(sql, vectorStr, "%" + filterKeyword.trim() + "%", "%" + smartKeyword + "%");
            }
        }

        // --- 最后的过滤与排序 ---
        List<Map<String, Object>> finalResult = new ArrayList<>();
        String cleanPrompt = prompt.replace("，", "").replace("。", "").trim();
        
        // 计算打分匹配用的关键词 (也是用 smartKeyword)
        String scoringKeyword = (filterKeyword != null && smartKeyword != null) ? smartKeyword : cleanPrompt;

        for (Map<String, Object> row : candidates) {
            Object yearObj = row.get("year_str");
            if (yearObj == null) continue;
            
            Map<String, Object> newRow = new HashMap<>(row);
            String formattedLabel = poetryUtils.getTimeLabel(yearObj.toString(), groupType);
            newRow.put("formatted_year", formattedLabel);

            // 时间过滤
            if (yearRange != null) {
                try {
                    int y = Integer.parseInt(yearObj.toString().trim());
                    if (y < yearRange[0] || y > yearRange[1]) continue;
                } catch(Exception e) { continue; }
            } else if (!timeLabel.equals("all") && !formattedLabel.trim().equals(timeLabel.trim())) {
                continue;
            }

            // --- 打分逻辑 ---
            double score = 0.0;
            
            String content = (String) row.get("content");
            String title = (String) row.get("title");
            
            // 检查是否包含关键词 (使用 smartKeyword，比如 "梅")
            boolean isTextMatch = (content != null && content.contains(scoringKeyword)) 
                               || (title != null && title.contains(scoringKeyword));
            
            if (Boolean.TRUE.equals(row.get("is_exact")) || isTextMatch) {
                score = 1.0; // 满分！
            } else {
                Object distObj = row.get("distance");
                double dist = distObj != null ? ((Number) distObj).doubleValue() : 100.0;
                score = 1.0 / (1.0 + dist);
            }
            
            newRow.put("match_score", score);
            finalResult.add(newRow);
            
            if (finalResult.size() >= 500) break; // 返回最多 500 条
        }
        
        finalResult.sort((a, b) -> Double.compare((Double) b.get("match_score"), (Double) a.get("match_score")));

        return finalResult;
    }

    private String extractLongestSegment(String text) {
        String[] segments = text.split("[，。！？,!.?\\s]+");
        String longest = "";
        for (String seg : segments) {
            if (seg.length() > longest.length()) longest = seg;
        }
        return longest.length() < 2 ? text.trim() : longest;
    }

    private int[] parseTimeRange(String label) {
        if (label == null || label.equals("all")) return null;
        try {
            if (label.contains("-")) {
                String[] parts = label.split("-");
                return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
            }
            switch (label) {
                case "初唐": return new int[]{618, 712};
                case "盛唐": return new int[]{713, 765};
                case "中唐": return new int[]{766, 835};
                case "晚唐": return new int[]{836, 907};
                case "唐": case "唐代": return new int[]{618, 907};
                case "五代": return new int[]{908, 959};
                case "北宋": return new int[]{960, 1127};
                case "南宋": return new int[]{1127, 1279};
                case "宋": case "宋代": return new int[]{960, 1279};
                case "元": return new int[]{1280, 1368};
                case "明": return new int[]{1368, 1644};
                case "清": return new int[]{1644, 1911};
            }
        } catch (Exception e) {}
        return null;
    }

    // ... (上面的代码不用动) ...

    // 【251124修复】更智能的朝代匹配逻辑
    private boolean isEraMatch(String targetEra, String timeLabel) {
        // 1. 如果标签包含汉字 (如 "北宋")，直接用包含匹配
        boolean hasChinese = timeLabel.matches(".*[\\u4e00-\\u9fa5].*");
        
        if ("all".equals(targetEra) || "唐宋".equals(targetEra)) {
            if (hasChinese) return timeLabel.contains("唐") || timeLabel.contains("宋") || timeLabel.contains("五代");
            // 如果是数字区间 (1120-1124)，判断是否在 618-1279 之间
            return isNumberInEra(618, 1279, timeLabel);
        }
        
        if ("tang".equals(targetEra) || "唐代".equals(targetEra)) {
            if (hasChinese) return timeLabel.contains("唐") || timeLabel.contains("五代");
            return isNumberInEra(618, 959, timeLabel); // 唐+五代
        }
        
        if ("song".equals(targetEra) || "宋代".equals(targetEra)) {
            if (hasChinese) return timeLabel.contains("宋");
            return isNumberInEra(960, 1279, timeLabel); // 宋
        }
        
        // 细分朝代支持
        if ("high_tang".equals(targetEra)) return timeLabel.equals("盛唐") || isNumberInEra(713, 765, timeLabel);
        // ... 其他细分略，只要上面的大朝代对了，全景分析就没问题
        
        return true;
    }
    
    // 【251124修复】解析 "1120-1124" 这种字符串，判断起始年份是否在范围内
    private boolean isNumberInEra(int start, int end, String label) {
        try {
            // 处理 "1120-1124" 或 "1120"
            String yearStr = label.split("-")[0].trim(); 
            int y = Integer.parseInt(yearStr);
            // 只要起始年份在范围内，就算匹配
            return y >= start && y <= end;
        } catch (Exception e) {
            return false; // 解析失败(非数字)，不算匹配
        }
    }
}
