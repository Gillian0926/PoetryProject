package com.poetry.tool;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Configuration
public class HistoryTools {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 定义工具：根据年份查历史
    @Bean
    @Description("根据年份查询中国历史大事件。输入为年份(int)，返回该年份前后发生的重大历史事件描述。")
    public Function<HistoryRequest, HistoryResponse> searchHistory() {
        return request -> {
            System.out.println("🤖 [AI 工具调用] 正在翻阅史书，查询年份: " + request.year);
            
            // 查前后 5 年的大事
            String sql = "SELECT year, event_name, description FROM history_events WHERE year BETWEEN ? AND ?";
            List<Map<String, Object>> events = jdbcTemplate.queryForList(sql, request.year - 5, request.year + 5);
            
            if (events.isEmpty()) {
                return new HistoryResponse("该年份附近暂无重大历史事件记录。");
            }
            
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> event : events) {
                sb.append(event.get("year")).append("年: ")
                  .append(event.get("event_name")).append(" - ")
                  .append(event.get("description")).append("; ");
            }
            return new HistoryResponse(sb.toString());
        };
    }

    // 定义输入输出结构
    public record HistoryRequest(int year) {}
    public record HistoryResponse(String eventInfo) {}
}