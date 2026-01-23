package com.kooo.evcam.camera;

/**
 * 录制回调接口
 */
public interface RecordCallback {
    /**
     * 录制开始
     */
    void onRecordStart(String cameraId);

    /**
     * 录制停止
     */
    void onRecordStop(String cameraId);

    /**
     * 录制错误
     */
    void onRecordError(String cameraId, String error);

    /**
     * 分段切换（需要重新配置相机会话）
     */
    void onSegmentSwitch(String cameraId, int newSegmentIndex);
}
