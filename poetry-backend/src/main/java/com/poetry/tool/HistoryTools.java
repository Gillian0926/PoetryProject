package com.poetry.tool;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Configuration
public class HistoryTools {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 防止函数调用死循环：
     * 此前日志显示同一年份被反复查询（单次分析里重复十几次），
     * 每一轮都会把整段提示词 + 工具结果重新发给模型，token 成倍膨胀、响应也慢。
     * 这里做"同一年份 90 秒内只真正查询一次"，重复调用直接返回提示，让模型收尾。
     */
    private static final ConcurrentHashMap<Integer, Long> LAST_CALL = new ConcurrentHashMap<>();
    private static final long DEDUPE_WINDOW_MS = 90_000L;

    @Bean
    @Description("根据年份查询中国历史大事件。输入为年份(int)，返回该年份前后发生的重大历史事件描述。每次分析只需调用一次，不要重复调用。")
    public Function<HistoryRequest, HistoryResponse> searchHistory() {
        return request -> {
            long now = System.currentTimeMillis();
            Long last = LAST_CALL.put(request.year, now);
            if (last != null && now - last < DEDUPE_WINDOW_MS) {
                System.out.println("[AI 工具调用] 重复查询年份 " + request.year + "，已拦截（防止循环）");
                return new HistoryResponse("该年份的历史事件刚刚已经查询过了，结果见上一轮返回。请立即基于已有信息完成作答，不要再调用本工具。");
            }

            System.out.println("[AI 工具调用] 正在翻阅史书，查询年份: " + request.year);

            // 查前后 5 年的大事
            String sql = "SELECT year, event_name, description FROM history_events WHERE year BETWEEN ? AND ?";
            List<Map<String, Object>> events = jdbcTemplate.queryForList(sql, request.year - 5, request.year + 5);

            if (events.isEmpty()) {
                return new HistoryResponse("该年份附近暂无重大历史事件记录。请基于已有信息直接作答，不要再次调用工具。");
            }

            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> event : events) {
                sb.append(event.get("year")).append("年: ")
                  .append(event.get("event_name")).append(" - ")
                  .append(event.get("description")).append("; ");
            }
            sb.append("（以上为该年份前后五年的大事，请直接据此作答，不要重复调用工具。）");
            return new HistoryResponse(sb.toString());
        };
    }

    // 定义输入输出结构
    public record HistoryRequest(int year) {}
    public record HistoryResponse(String eventInfo) {}
}
