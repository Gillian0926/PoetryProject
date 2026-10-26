package com.poetry.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.poetry.service.PoetryService;

import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiController {

    private final ChatClient chatClient;
    
    @Autowired
    private PoetryService poetryService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public AiController(ChatClient.Builder builder) {
        this.chatClient = builder.defaultFunctions("searchHistory").build();
    }
// Spring AI 的 Function Calling (工具调用) 机制

    @PostConstruct
    public void init() {
        try { jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ai_analysis_cache (cache_key VARCHAR(255) PRIMARY KEY, content TEXT)"); } catch (Exception e) {}
    }

    @PostMapping("/analyze")
    public Map<String, String> analyzeChart(@RequestBody Map<String, Object> requestData) {
        String era = (String) requestData.get("era");
        String type = (String) requestData.getOrDefault("type", "emotion");
        String peakYear = String.valueOf(requestData.get("peakYear"));
        
        // 获取具体的分析对象
        String target = (String) requestData.get("target"); 
        // 如果前端没传 target (比如全览模式)，给一个默认值或者 null，视 Service 逻辑而定
        // 在这里如果 target 是 null，Service 会生成全览摘要

        String cacheKey = String.format("ANALYSIS:%s:%s:%s", type, era, target == null ? "ALL" : target);

        try {
            String cachedContent = jdbcTemplate.queryForObject("SELECT content FROM ai_analysis_cache WHERE cache_key = ?", String.class, cacheKey);
            if (cachedContent != null && !cachedContent.isEmpty()) return Map.of("result", cachedContent);
        } catch (Exception e) {}

        String response = generateAiResponse(era, type, target, peakYear);

        if (response != null && !response.startsWith("AI 服务繁忙")) {
            try { jdbcTemplate.update("INSERT INTO ai_analysis_cache (cache_key, content) VALUES (?, ?) ON CONFLICT (cache_key) DO NOTHING", cacheKey, response); } catch (Exception e) {}
        }

        return Map.of("result", response);
    }

    private String generateAiResponse(String era, String type, String target, String peakYear) {
        String prompt;
        
        // ================= 分支逻辑优化 =================
        
        // 情况 A: 诗人画像 (不需要查数据库统计)
        if ("poet_profile".equals(type)) {
            prompt = String.format("""
                你是一位精通中国文学史的专家。请为唐宋诗人【%s】撰写一份人物小传。
                
                **【分析要求】**：
                1.  **生平简介**：简述其字号、主要经历（如仕途沉浮、流寓经历）。
                2.  **创作风格**：分析其诗词的核心风格（如豪放、婉约、沉郁）。大数据将其归类为该风格的原因？
                3.  **代表作赏析**：列举 1-2 首代表作，并简要点评。
                4.  **历史地位**：一句话总结他在文学史上的地位。
                5.  **输出风格**：半文半白，典雅优美，使用 Markdown 四级标题 (####) 分隔。
                """, target);
        
        // 情况 B: 诗人关系 (不需要查数据库统计)
        } else if ("relationship".equals(type)) {
             prompt = String.format("""
                你是一位文学史专家。请分析【%s】这两位诗人在**创作风格、人生经历或思想情感**上的关联与异同。
                
                要求：
                1. 解释为什么大数据算法会认为他们的风格相似？
                2. 结合历史事实（他们是否认识？是否处于同一时代？）。
                3. 字数 300 字左右，趣味性与学术性并重。
                """, target);

        
        }else if ("relationship".equals(type)) {
             prompt = String.format("""
                你是一位精通中国文学史与风格学的专家。
                大数据算法基于**文本向量（Embedding）**分析发现，【%s】这两位诗人的**创作风格高度相似**。
                
                请据此进行深度对比分析：
                
                1.  **风格共性解码**：
                    * 为什么算法觉得他们像？（例如：都具有豪放的边塞气象？还是都擅长婉约闺怨？亦或是用词习惯相近？）
                    
                2.  **历史关联（调用工具）**：
                    * 请调用工具查询他们所处的时代背景。
                    * 他们是否属于同一流派？是否有师承关系？或者只是跨越时空的“神交”？
                    
                3.  **同题/同类作品比对**：
                    * 如果可能，举例说明他们在处理相似题材时的异同。
                    
                4.  **输出要求**：
                    * 趣味性与学术性并重，300字左右，Markdown 分段。
                """, target);

        // 3. 花卉与情感 (点击河流图)
        } else {// 情况 C: 花卉或情感 (需要调用 Service 生成数据摘要！)
            // 只有这里才调用 generateTrendSummary，防止 SQL 报错
            String dataSummary = poetryService.generateTrendSummary(era, type, target);
            
            if ("flower".equals(type)) {
            if (target != null) {
                prompt = String.format("""
                你是一位精通**数字人文、中国文学史及物质文化史**的资深学者。
                现在，我们通过大数据统计了【%s】时期【%s】这一植物意象在诗词中的出现频率。
                请根据下方的【统计数据摘要】，撰写一份**深度学术分析报告**。
                
                === 数据趋势摘要 ===
                %s
                =======================
                
                **【深度解读维度】**：
                
                1.  **数据背后的文学地理与时代气象**：
                    * 不要仅描述数据涨跌，请分析数据的**峰值与低谷**对应了怎样的时代特征？
                    * *提示*：结合**历史工具查询**，考证该花卉的流行是否与**政治中心迁移**（如长安->洛阳->开封->杭州）、**栽培技术进步**或**特定帝王/名士的推崇**（如武则天与牡丹、林逋与梅花）有关？
                
                2.  **历史事件与文人心态的互证**：
                    * 请调用工具查询关键年份的大事件（如安史之乱、熙宁变法、靖康南渡等）。
                    * 分析外部世界的动荡或繁荣，是如何通过这一花卉意象投射到文学作品中的？它是作为“盛世的装点”，还是“乱世的慰藉”？
                
                3.  **审美范式与文化隐喻的演变**：
                    * 该花卉的象征意义在这一时期是否发生了**“语义偏移”**？（例如：是否从单纯的感官愉悦上升到了人格理想？是否从宫廷贵族走向了山林隐逸？）
                    * 请引用1-2位代表性诗人的名句作为论据，印证你的观点。
                
                4.  **输出要求**：
                    * 文风：**典雅、深刻、逻辑严密**，具有学术随笔的质感。
                    * 结构：请使用**Markdown四级标题 (####)** 分隔不同维度的论述。
                    * 篇幅：不限字数，请尽情展开论述，确保信息密度高。
                """, parseEraName(era), target, dataSummary);
            } else {
                // 全览模式 (Macro View)
                prompt = String.format("""
                    你是一位精通**中国美学史、物质文化与数字人文**的资深学者。
                    请根据下方基于大数据的【诗词花卉意象统计简报】，对【%s】时期的**植物审美范式转移**进行宏观、深度的学术综述。
                    
                    === 数据趋势摘要 ===
                    %s
                    =======================
                    
                    **【深度综述要求】**：
                    
                    1.  **植物界的“权力更迭”**：
                        * 根据统计数据，梳理出不同历史阶段的**“主导意象”**（Dominant Image）。
                        * 敏锐地捕捉**转折点**：哪一种花卉在何时开始衰落？哪一种又在何时异军突起？（例如：盛唐的牡丹热是如何过渡到宋代的梅花热/荷花热的？）
                    
                    2.  **历史与审美的互证（必须调用工具）**：
                        * 请调用历史工具查询关键时间节点的社会背景。
                        * 剖析**社会形态的变化**如何重塑了审美？
                        * *引导思考*：从唐代的“贵族审美”（重色、重富贵、重外在视觉冲击）到宋代的“士大夫审美”（重韵、重格调、重内在德行），这种转变是如何在数据中体现的？
                    
                    3.  **地缘与物产的影响**：
                        * 结合历史上的**经济重心南移**（如安史之乱后、靖康南渡后），分析南方特有植物（如梅、桂、荷）在文学版图中权重的上升。
                    
                    4.  **总结性论断**：
                        * 请用一段高度凝练的学术语言，总结这一时期中国花卉审美精神的内核变化。
                    
                    5.  **输出要求**：
                        * 逻辑严密，气势恢宏，不限字数。
                        * 使用 **Markdown 四级标题 (####)** 分割不同历史阶段或论点。
                    """, parseEraName(era), dataSummary);
            }
        } else {
           if (target != null) {
                // === 单项情感深度分析 (Deep Emotional Analysis) ===
                prompt = String.format("""
                    你是一位精通**数字人文、中国心态史（Psychohistory）及文学史**的资深学者。
                    请根据以下基于大数据的【诗词情感统计简报】，对【%s】时期文人群体中【%s】这一情感的爆发与流变进行深度剖析。
                    
                    === 数据趋势摘要 ===
                    %s
                    =======================
                    
                    **【深度剖析要求】**：
                    
                    1.  **情感的“脉动”与数据解读**：
                        * 请结合数据，精准定位该情感的**酝酿期、爆发期（峰值）与消退期**。
                        * 分析这是一种“时代的主旋律”（主流情感），还是一种“潜流”（小众但独特的情感）？
                    
                    2.  **历史与心灵的共振（必须调用工具）**：
                        * **关键步骤**：务必调用工具查询峰值年份前后的重大历史事件（如战乱、变法、党争、南渡等）。
                        * **深度归因**：分析外部世界的剧变（如安史之乱的战火、靖康之耻的屈辱、乌台诗案的政治迫害）是如何转化为文人笔下的情绪的？
                        * *引导思考*：这种情感是源于“家国之痛”（宏观），还是“身世之悲”（微观）？还是两者的交织？
                    
                    3.  **代表性声音**：
                        * 提及该时期该情感的**代表性诗人/词人**。
                        * 简述他们的际遇（如贬谪、流亡、报国无门）如何成为时代的注脚。
                    
                    4.  **输出要求**：
                        * 笔触要**深沉、有共情力**，既要有历史的厚度，又要有文学的温度。
                        * 不限字数，尽情展开论述。
                        * 使用 **Markdown 四级标题 (####)** 组织文章结构。
                    """, parseEraName(era), target, dataSummary);
           } else {
                // === 情感全览模式 (Macro Emotional Evolution) ===
                prompt = String.format("""
                    你是一位精通**中国文学史、心态史（Psychohistory）与数字人文**的资深学者。
                    请根据以下基于大数据的【诗词情感统计简报】，对【%s】时期的**整体情感基调演变（Emotional Zeitgeist）**进行宏观、深度的学术综述。
                    
                    === 数据趋势摘要 ===
                    %s
                    =======================
                    
                    **【深度综述要求】**：
                    
                    1.  **时代情绪的“光谱分析”**：
                        * 请依据数据，勾勒出该时期的**情感主曲线**。
                        * 分析不同阶段的主导情感是如何更替的？（例如：大唐是如何从“昂扬豪迈”跌落至“沉郁悲凉”的？宋代是如何在“婉约闲适”与“悲愤爱国”之间撕裂的？）
                    
                    2.  **历史巨变与集体记忆（必须调用工具）**：
                        * **核心要求**：请调用历史工具查询关键节点的大事件。
                        * 探讨**国运兴衰**（如开元盛世 vs 安史之乱，仁宗盛治 vs 靖康之难）如何重塑了士大夫群体的集体心理？
                        * 分析政治环境（如党争、贬谪文化）如何内化为文学作品中的底色？
                    
                    3.  **文学史的宏观定位**：
                        * 总结这一时期的情感特征在中国文学长河中的独特地位。
                        * 列举 1-2 位最具代表性的诗人及其风格变化作为佐证（如杜甫的“诗史”性质）。
                    
                    4.  **输出要求**：
                        * 文风：**大气磅礴、深刻犀利**，具有历史纵深感。
                        * 不限字数，尽情展开论述。
                        * 使用 **Markdown 四级标题 (####)** 划分不同历史阶段或论点。
                    """, parseEraName(era), dataSummary);
            }
        }
}
        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            System.err.println("[AI 调用失败] " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            return "AI 服务繁忙，请稍后再试。";
        }
    }
    
    

// 2. 【新增】诗人画像批量预热接口
    // 访问地址: http://localhost:8080/api/ai/preheat/poets
    @GetMapping("/preheat/poets")
    public String preheatPoets() {
        CompletableFuture.runAsync(() -> {
            System.out.println("🔥 开始【诗人画像】预热...");
            
            // 1. 从数据库获取所有诗人名单 (这些是我们算出来的 Top 诗人)
            List<String> authors = jdbcTemplate.queryForList("SELECT author FROM poet_profiles", String.class);
            
            System.out.printf("📜 找到 %d 位诗人，准备为他们立传...%n", authors.size());
            
            int current = 0;
            for (String author : authors) {
                current++;
                // 构造缓存 Key：前端传的是 era='唐宋', type='poet_profile'
                String key = String.format("ANALYSIS:poet_profile:唐宋:%s", author);
                
                if (!isCached(key)) {
                    System.out.printf("正在撰写 [%d/%d]: %s 的传记...%n", current, authors.size(), author);
                    
                    // 调用 AI 生成
                    String content = generateAiResponse("唐宋", "poet_profile", author, "");
                    
                    // 存入缓存
                    saveCache(key, content);
                    
                    // 休息一下防止 API 限流
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                } else {
                    System.out.println("✅ 已存在(跳过): " + author);
                }
            }
            System.out.println("🎉 诗人传记全部撰写完成！");
        });
        return "诗人画像预热已启动，请查看后台日志。";
    }
@GetMapping("/preheat") 
    public String preheatAiAnalysis() {
        CompletableFuture.runAsync(() -> {
            System.out.println(" 开始分析预热...");
            List<String> eras = Arrays.asList("tang", "song", "all", "high_tang", "late_tang", "north_song", "south_song");
            List<String> emotions = Arrays.asList("悲愁", "豪放", "喜悦", "思念", "闲适", "孤独", "愤怒");
            List<String> flowers = Arrays.asList("梅花", "荷花", "菊花", "牡丹", "海棠", "柳絮", "桂花", "桃花", "杏花", "杨花");

            int total = eras.size() * (emotions.size() + flowers.size());
            int current = 0;

            for (String era : eras) {
                for (String emo : emotions) {
                    String key = String.format("ANALYSIS:emotion:%s:%s", era, emo);
                    if (!isCached(key)) {
                        System.out.printf("正在生成 [%d/%d]: %s ...%n", ++current, total, key);
                        String mockYear = era.contains("song") ? "1127" : "755";
                        // 【核心修复】传 3 个参数
                        String content = generateAiResponse(era, "emotion", emo, mockYear);
                        saveCache(key, content);
                        try { Thread.sleep(2000); } catch (InterruptedException e) {}
                    }
                }
                for (String flower : flowers) {
                    String key = String.format("ANALYSIS:flower:%s:%s", era, flower);
                    if (!isCached(key)) {
                        System.out.printf("正在生成 [%d/%d]: %s ...%n", ++current, total, key);
                        String mockYear = "750";
                        // 【核心修复】传 3 个参数
                        String content = generateAiResponse(era, "flower", flower, mockYear);
                        saveCache(key, content);
                        try { Thread.sleep(2000); } catch (InterruptedException e) {}
                    }
                }
            }
            System.out.println("AI 预热全部完成！");
        });

        return "AI 预热任务已在后台启动，请查看控制台日志。";
    }

    private boolean isCached(String key) {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM ai_analysis_cache WHERE cache_key = ?", Integer.class, key);
            return count != null && count > 0;
        } catch (Exception e) { return false; }
    }

    private void saveCache(String key, String content) {
        if (content == null || content.length() < 10) return;
        try {
            jdbcTemplate.update("INSERT INTO ai_analysis_cache (cache_key, content) VALUES (?, ?) ON CONFLICT (cache_key) DO NOTHING", key, content);
        } catch (Exception e) {}
    }

    private String parseEraName(String code) {
        if ("tang".equals(code)) return "唐代";
        if ("song".equals(code)) return "宋代";
        if ("high_tang".equals(code)) return "盛唐";
        if ("late_tang".equals(code)) return "晚唐";
        if ("north_song".equals(code)) return "北宋";
        if ("south_song".equals(code)) return "南宋";
        return "唐宋全貌";
    }
}
// 在 AiController中，注入了 ChatClient。Spring AI 最核心的接口，用于与大模型对话

// 使用了 .defaultFunctions("searchHistory")。---Spring AI 的 Function Calling (工具调用) 机制。