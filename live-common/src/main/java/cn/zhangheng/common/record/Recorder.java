package cn.zhangheng.common.record;

import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Task;
import com.zhangheng.file.FileUtil;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/06/01 星期日 20:13
 * @version: 1.0
 * @description:
 */
public abstract class Recorder extends Task {
    protected Runnable runnable = null;
    @Getter
    protected final String downloadUrl;//下载地址
    @Getter
    protected final String saveFilePath;//保存文件路径
    @Getter
    protected final String definition;//清晰度
    @Setter
    protected Room room;
    @Setter
    protected ProgressCallback progressCallback = null;
    @Setter
    protected int timeoutSeconds = 0;

    protected Recorder(String downloadUrl, String saveFilePath, String definition) {
        this.downloadUrl = downloadUrl;
        this.saveFilePath = saveFilePath;
        this.definition = definition;
    }

    public abstract long getTimeMs();

    public abstract long getDownloadSize();

    public abstract String getProgressMsg();

    public abstract void download() throws Exception;

    protected void initRunnable() {
        this.runnable = () -> {
            ScheduledExecutorService executor = null;
            try {
                String threadName = getThreadName();
                Thread.currentThread().setName(threadName);
                // 设置超时控制
                if (timeoutSeconds > 0) {
                    executor = Executors.newSingleThreadScheduledExecutor();
                    executor.schedule(() -> {
                        Thread.currentThread().setName(threadName);
                        stop(false);
                    }, timeoutSeconds, TimeUnit.SECONDS);
                }
                isRunning.set(true);
                startTime = System.currentTimeMillis();
                if (progressCallback != null)
                    progressCallback.onStart(getDownloadUrl(), getSaveFilePath(), getDefinition());
                download();
                endTime = System.currentTimeMillis();
                if (progressCallback != null)
                    progressCallback.onComplete(getSaveFilePath(), getDownloadSize(), getTimeMs());
            } catch (Exception e) {
                if (progressCallback != null) {
                    progressCallback.onError(e);
                } else {
                    throw new RuntimeException(e);
                }
            } finally {
                if (executor != null) {
                    executor.shutdownNow();
                }
                isRunning.set(false);
            }
        };
    }

    private String getThreadName() {
        String threadName;
        if (room != null) {
            threadName = room.getOwner() + "-recorder-" + Thread.currentThread().getId();
        } else if (saveFilePath.indexOf("[") < saveFilePath.indexOf("]")) {
            String owner = saveFilePath.substring(saveFilePath.indexOf("[") + 1, saveFilePath.indexOf("]"));
            threadName = owner + "-recorder-" + Thread.currentThread().getId();
        } else {
            threadName = "recorder-" + Thread.currentThread().getId();
        }
        return threadName;
    }

    @Override
    public void run(boolean isAsync) throws ExecutionException {
        initRunnable();
        Future<?> future = mainExecutors.submit(runnable);
        if (!isAsync) {
            try {
                future.get();
            } catch (InterruptedException e) {
                log.error("录制主任务中断：{}", ThrowableUtil.getAllCauseMessage(e));
            } finally {
                isRunning.set(false);
            }
        }
        mainExecutors.shutdown();
    }

    @Override
    public void stop(boolean force) {
        isRunning.set(false);
        if (force) {
            mainExecutors.shutdownNow();
        } else {
            mainExecutors.shutdown();
        }
    }

    public interface ProgressCallback {
        Logger log = LoggerFactory.getLogger(ProgressCallback.class);

        default void onStart(String url, String saveFilePath, String definition) {
            log.info("下载录制已开始! 【{}】{} >>> {}", definition, url, saveFilePath);
        }

        default void onComplete(String saveFilePath, long totalBytes, long totalDurationMS) {
            log.info("下载录制已结束! 用时:{},大小:{},位置:{}", TimeUtil.formatMSToCn((int) totalDurationMS), FileUtil.fileSizeStr(totalBytes), saveFilePath);
        }

        default void onError(Throwable throwable) {
            log.error("下载录制发生异常! {}", ThrowableUtil.getAllCauseMessage(throwable), throwable);
        }
    }
}
