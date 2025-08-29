package cn.zhangheng.kuaishou.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/08/28 星期四 17:43
 * @version: 1.0
 * @description:
 */
public class InitialStateExtractor {
    // 提取window.__INITIAL_STATE__对象
    public static String extractInitialState(String html) {
        Document doc = Jsoup.parse(html);
        Elements scripts = doc.select("script");

        // 匹配window.__INITIAL_STATE__的正则表达式
        Pattern pattern = Pattern.compile("window\\.__INITIAL_STATE__\\s*=\\s*(\\{[^;]+});");

        for (Element script : scripts) {
            String scriptContent = script.html();
            if (scriptContent.contains("window.__INITIAL_STATE__")) {
                Matcher matcher = pattern.matcher(scriptContent);
                if (matcher.find()) {
                    return matcher.group(1); // 返回匹配到的JSON对象部分
                }
            }
        }
        return null;
    }

    // 从INITIAL_STATE中提取指定属性
    public static Object getAttributeFromInitialState(String initialStateJson, String attributePath)
            throws ScriptException {
        // 创建Nashorn引擎
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("nashorn");

        // 将JSON转换为JS对象并执行
        engine.eval("var state = " + initialStateJson + ";");

        // 提取属性值（支持嵌套路径，如"user.name"）
        return engine.eval("state." + attributePath);
    }
}
