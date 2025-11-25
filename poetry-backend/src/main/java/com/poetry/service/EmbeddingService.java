package com.poetry.service;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.utils.Constants;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            String sql = "CREATE TABLE IF NOT EXISTS term_embeddings (term VARCHAR(255) PRIMARY KEY, embedding vector(1536))";
            jdbcTemplate.execute(sql);
            System.out.println("✅ 向量缓存表检查完毕");
        } catch (Exception e) {
            System.err.println("⚠️ 缓存表检查警告: " + e.getMessage());
        }
    }

    /**
     * 获取文本向量 (优先查库，无则调API并存库)
     */
    public List<Double> getEmbeddingWithCache(String text) {
        // 1. 查库
        try {
            String vecStr = jdbcTemplate.queryForObject("SELECT cast(embedding as text) FROM term_embeddings WHERE term = ?", String.class, text);
            if (vecStr != null) return parseVectorString(vecStr);
        } catch (Exception e) {}

        // 2. 调 API
        List<Double> apiVector = callDashScopeApi(text);

        // 3. 存库
        if (apiVector != null) {
            try {
                jdbcTemplate.update("INSERT INTO term_embeddings VALUES (?, ?::vector) ON CONFLICT DO NOTHING", text, apiVector.toString());
            } catch (Exception e) { System.err.println("缓存写入失败: " + e.getMessage()); }
        }
        return apiVector;
    }

    private List<Double> callDashScopeApi(String text) {
        try {
            String key = System.getenv("DASHSCOPE_API_KEY");
            if (key == null) return null;
            Constants.apiKey = key;
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(TextEmbedding.Models.TEXT_EMBEDDING_V1)
                    .texts(Collections.singletonList(text)).build();
            return new TextEmbedding().call(param).getOutput().getEmbeddings().get(0).getEmbedding();
        } catch (Exception e) { 
            System.err.println("API Error: " + e.getMessage());
            return null; 
        }
    }

    private List<Double> parseVectorString(String vecStr) {
        return Arrays.stream(vecStr.replace("[", "").replace("]", "").split(",")).map(String::trim).map(Double::parseDouble).collect(Collectors.toList());
    }
}