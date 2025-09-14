package cn.zhangheng.common.bean;

import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/05/26 星期一 21:37
 * @version: 1.0
 * @description:
 */
public abstract class RoomService<T extends Room> {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final T room;

    protected RoomService(T room) {
        this.room = room;
        //注意：子类调用super()方法后，需要调用refresh方法初始化room对象
    }

    public void refresh() {
        refresh(false);
    }

    /**
     * 刷新直播间数据
     *
     * @param force 是否强制更新直播流信息
     */
    public abstract void refresh(boolean force);

    public HttpRequest get(String url) {
        return HttpRequest.get(url)
                .timeout(30_000)
                .header(Header.USER_AGENT, Constant.User_Agent)
                ;
    }

    public String getRequest(String urlString, Map<String, String> headers) {
        HttpURLConnection connection =null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            // 设置请求方法
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            if (headers != null&& !headers.isEmpty()) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            // 获取响应代码
            int responseCode = connection.getResponseCode();
            System.out.println("响应代码: " + responseCode);
            // 读取响应内容
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String response = reader.lines().collect(Collectors.joining("\n"));
                System.out.println("响应内容: " + response);
                return response;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

}
