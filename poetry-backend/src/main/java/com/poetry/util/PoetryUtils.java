package com.poetry.util;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PoetryUtils {

    public static final Map<String, String> EMOTION_MAP = new HashMap<>();
    
    public static final Set<String> ALLOWED_EMOTIONS = new HashSet<>(Arrays.asList(
        "悲愁", "思念", "喜悦", "闲适", "豪放", "孤独", "愤怒"
    ));

    public static final Set<String> IGNORE_DICT = new HashSet<>();

    static {
        // ================= 1. 豪放=================

        List<String> heroicWords = Arrays.asList(
            "豪放", "激昂", "壮志", "爱国", "雄壮", "慷慨", "热血", "豪情", "壮烈", 
            "狂", "壮", "气吞山河", "大气", "磅礴", "雄浑", "奔放", "杰出",
            "悲壮", "苍凉", "沉郁", "悲歌", // 这种带“悲”但实为“壮”的
            "豁达", "旷达", "超迈", "洒脱", "豪迈", // 苏
            "报国", "忠君", "忠愤", "义愤", "忧国", // 爱国
            "边塞", "沙场", "军旅", "征夫" 
        );
        heroicWords.forEach(w -> EMOTION_MAP.put(w, "豪放"));

        // ================= 2. 悲愁  =================
        List<String> sadWords = Arrays.asList(
            "悲伤", "悲痛", "哀愁", "忧愁", "愁苦", "凄凉", "凄清", "惆怅", "断肠", 
            "哀怨", "悲愤", "遗憾", "失落", "苦闷", "忧伤", "哀伤", "悲", "痛", 
            "愁", "泪", "哭", "伤心", "凄惨", "哀", "怨", "恨", "凄", "苦"
        );
        sadWords.forEach(w -> {
            // 防止覆盖已定义
            if (!EMOTION_MAP.containsKey(w)) {
                EMOTION_MAP.put(w, "悲愁");
            }
        });

        // ================= 3. 愤怒 =================
        List<String> angerWords = Arrays.asList(
            "愤怒", "愤懑", "怒", "气愤", "恼怒", "激愤", "不平", "怨恨", 
            "恼", "怒发冲冠", "怒火", "愤慨"
        );
        angerWords.forEach(w -> EMOTION_MAP.put(w, "愤怒"));

        // ================= 4. 思念 =================
        List<String> missWords = Arrays.asList(
            "思念", "相思", "怀旧", "怀古", "离愁", "离别", "惜别", "牵挂", 
            "眷恋", "追忆", "乡愁", "思乡", "忆", "别", "望乡", "念", "羁旅"
        );
        missWords.forEach(w -> EMOTION_MAP.put(w, "思念"));

        // ================= 5. 喜悦  =================
        List<String> joyWords = Arrays.asList(
            "喜悦", "高兴", "欢快", "愉悦", "欣喜", "快乐", "兴奋", "惬意", 
            "欢喜", "开心", "怡然", "乐", "欢", "笑", "喜", "欣",
            "赞美", "喜爱", "欣赏", "仰慕", "敬仰", "热爱", "爱", "赏", "敬", "赞" // 合并
        );
        joyWords.forEach(w -> EMOTION_MAP.put(w, "喜悦"));

        // ================= 6. 闲适  =================
        List<String> calmWords = Arrays.asList(
            "闲适", "恬淡", "平静", "宁静", "淡泊", "悠闲", "自在", "超脱", 
            "隐逸", "安详", "从容", "静", "闲", "逸", "幽", "禅意", "野趣"
        );
        calmWords.forEach(w -> EMOTION_MAP.put(w, "闲适"));

        // ================= 7. 孤独 =================
        List<String> lonelyWords = Arrays.asList(
            "孤独", "寂寞", "孤单", "冷清", "孤寂", "落寞", "独", "孤", "孑", "幽独"
        );
        lonelyWords.forEach(w -> EMOTION_MAP.put(w, "孤独"));

        // 屏蔽
        IGNORE_DICT.addAll(Arrays.asList("None", "null", "无", "未知", "其他", "unknown", "花", "植物", "复杂", "N/A"));
    }

    /**
     * 情感标准化
     */
    public String normalizeEmotion(String rawEmotion) {
        if (rawEmotion == null) return null;
        String clean = rawEmotion.replace("。", "").replace("，", "").trim();
        
        if (IGNORE_DICT.contains(clean) || clean.length() > 4) return null;
        
        // 1. 直通
        if (ALLOWED_EMOTIONS.contains(clean)) return clean;
        
        // 2. 字典映射
        if (EMOTION_MAP.containsKey(clean)) return EMOTION_MAP.get(clean);
        
        // 3. 包含匹配 (模糊匹配)
        
        for (Map.Entry<String, String> entry : EMOTION_MAP.entrySet()) {
            if (clean.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return null;
    }

    // --- 时间逻辑 (保持不变) ---
    public String getTimeLabel(String yearStr, String groupType) {
        try {
            int year = (int) Double.parseDouble(yearStr.trim());
            if ("5".equals(groupType)) return String.format("%d-%d", (year/5)*5, (year/5)*5+4);
            if ("20".equals(groupType)) return String.format("%d-%d", (year/20)*20, (year/20)*20+19);
            if ("50".equals(groupType)) return String.format("%d-%d", (year/50)*50, (year/50)*50+49);
            return mapYearToDynasty(year);
        } catch (Exception e) { return yearStr; }
    }

    public List<String> sortLabels(Set<String> labels, String groupType) {
        List<String> sortedLabels = new ArrayList<>(labels);
        if ("dynasty".equals(groupType)) {
             List<String> dynastyOrder = Arrays.asList("先秦", "汉", "魏晋", "南北朝", "隋", "初唐", "盛唐", "中唐", "晚唐", "五代", "北宋", "南宋", "宋", "元", "明", "清", "其他");
             sortedLabels.sort(Comparator.comparingInt(d -> {
                 int index = dynastyOrder.indexOf(d);
                 return index == -1 ? 999 : index;
             }));
        } else {
            sortedLabels.sort((a, b) -> {
                try { return Integer.parseInt(a.split("-")[0]) - Integer.parseInt(b.split("-")[0]); } 
                catch (Exception e) { return a.compareTo(b); }
            });
        }
        return sortedLabels;
    }
    
    private String mapYearToDynasty(int year) {
        if (year < 618) return "隋以前";
        if (year >= 618 && year <= 712) return "初唐";
        if (year >= 713 && year <= 765) return "盛唐";
        if (year >= 766 && year <= 835) return "中唐";
        if (year >= 836 && year <= 907) return "晚唐";
        if (year > 907 && year < 960) return "五代";
        if (year >= 960 && year <= 1127) return "北宋";
        if (year > 1127 && year <= 1279) return "南宋";
        if (year >= 1280 && year <= 1368) return "元";
        if (year >= 1368 && year <= 1644) return "明";
        if (year >= 1644) return "清";
        return "其他";
    }
}