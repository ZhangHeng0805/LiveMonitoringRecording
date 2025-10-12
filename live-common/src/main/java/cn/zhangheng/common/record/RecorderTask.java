package cn.zhangheng.common.record;

import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.task.AsyncTaskQueue;
import cn.zhangheng.common.task.TaskCallback;
import cn.zhangheng.common.util.LogUtil;
import com.zhangheng.file.FileUtil;
import com.zhangheng.util.TimeUtil;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/07/21 星期一 10:39
 * @version: 1.0
 * @description: 录制任务类
 */
public class RecorderTask {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final Setting setting;
    private final AsyncTaskQueue asyncTaskQueue;
    @Setter
    private ActionListener actionListener;

    public RecorderTask(Setting setting) {
        this.asyncTaskQueue = new AsyncTaskQueue(1, 10);
        asyncTaskQueue.start();
        this.setting = setting;
    }

    protected Recorder getRecorder(Room room, boolean isConvert) {
        Map<String, String> streams = room.getStreams();
        if (streams == null || streams.isEmpty()) {
            throw new IllegalArgumentException("房间没有可用的流信息");
        }
        Map.Entry<String, String> stream = streams.entrySet().iterator().next();
        String definition = stream.getKey();//清晰度
        String flvUrl = stream.getValue();
        String fileName = "【" + FileUtil.filterFileName(room.getNickname()) + "】" + room.getPlatform().getName() + "直播录制" + TimeUtil.toTime(new Date(), "yyyy-MM-dd HH-mm-ss") + "[" + FileUtil.filterFileName(room.getTitle()) + "].flv";
        String path = Paths.get(LogUtil.getBasePathStr(room), fileName).toFile().getPath();
        Recorder streamRecorder;
        if (setting.getRecordType() == 1) {
            try {
                String ffmpegPath = setting.getFfmpegPath();
                streamRecorder = new FFmpegFlvRecorder(flvUrl, path, definition, ffmpegPath);
            } catch (IllegalArgumentException e) {
                log.warn(e.getMessage());
                streamRecorder = new FlvStreamRecorder(flvUrl, path, definition);
            }
        } else {
            streamRecorder = new FlvStreamRecorder(flvUrl, path, definition);
        }
        Recorder.ProgressCallback progressCallback = new Recorder.ProgressCallback() {
            @Override
            public void onStart(String url, String saveFilePath, String definition) {
                Recorder.ProgressCallback.super.onStart(url, saveFilePath, definition);
                if (actionListener != null) {
                    actionListener.recorderStart(url, saveFilePath, definition);
                }
            }

            @Override
            public void onComplete(String saveFilePath, long totalBytes, long totalDurationMS) {
                Recorder.ProgressCallback.super.onComplete(saveFilePath, totalBytes, totalDurationMS);
                if (actionListener != null) {
                    actionListener.recorderComplete(saveFilePath, totalBytes, totalDurationMS);
                }
                if (isConvert) {
                    videoConvert(saveFilePath);
                }
            }

            @Override
            public void onError(Throwable throwable, String saveFilePath) {
                Recorder.ProgressCallback.super.onError(throwable, saveFilePath);
                if (actionListener != null) {
                    actionListener.recorderError(throwable, saveFilePath);
                }
                if (isConvert) {
                    videoConvert(saveFilePath);
                }
            }
        };

        streamRecorder.setProgressCallback(progressCallback);
        streamRecorder.setRoom(room);
        return streamRecorder;
    }

    private void videoConvert(String saveFilePath) {
        //flv转mp4
    }


    public void run(Room room) {
        try {
            asyncTaskQueue.submit(() -> {
                Recorder recorder = getRecorder(room, setting.isConvertFlvToMp4());
                if (actionListener != null) {
                    actionListener.onRecorderCreated(recorder);
                }
                recorder.run(false);
                return recorder;
            }, new TaskCallback<Recorder>() {
                @Override
                public void onSuccess(Recorder result) {
                    log.info("录制结束！[{}]:{}", result.getDefinition(), result.getSaveFilePath());
                }

                @Override
                public void onFailure(Throwable e) {
                    if (actionListener != null) {
                        actionListener.onFailure(e);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("任务提交失败，队列已满", e);
            if (actionListener != null) {
                actionListener.onFailure(e); // 通知外部提交失败
            }
        }
    }

    public void shutdown() {
        if (asyncTaskQueue != null) {
            try {
                asyncTaskQueue.shutdown(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.warn("任务队列关闭被中断", e);
                asyncTaskQueue.forceShutdown(); // 强制关闭
            }
        }
    }

    public interface ActionListener {
        void recorderStart(String url, String saveFilePath, String definition);

        void recorderComplete(String saveFilePath, long totalBytes, long totalDurationMS);

        void recorderError(Throwable throwable, String saveFilePath);

        void onFailure(Throwable e);

        void onRecorderCreated(Recorder recorder);
    }

}
