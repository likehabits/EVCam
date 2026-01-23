package com.kooo.evcam.dingtalk;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 钉钉 Stream 客户端
 * 通过 WebSocket 长连接接收钉钉推送的消息
 */
public class DingTalkStreamClient extends WebSocketListener {
    private static final String TAG = "DingTalkStreamClient";
    private static final int RECONNECT_DELAY_MS = 5000;

    private final DingTalkApiClient apiClient;
    private final Gson gson;
    private final OkHttpClient httpClient;
    private final MessageCallback callback;

    private WebSocket webSocket;
    private boolean isRunning = false;

    public interface MessageCallback {
        void onConnected();
        void onDisconnected();
        void onMessageReceived(String conversationId, String conversationType, String senderUserId, String text);
        void onError(String error);
    }

    public DingTalkStreamClient(DingTalkApiClient apiClient, MessageCallback callback) {
        this.apiClient = apiClient;
        this.callback = callback;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS) // 长连接不设置读超时
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS) // 心跳
                .build();
    }

    /**
     * 启动 Stream 连接
     */
    public void start() {
        if (isRunning) {
            Log.w(TAG, "Stream 客户端已在运行");
            return;
        }

        isRunning = true;
        new Thread(this::connect).start();
    }

    /**
     * 停止 Stream 连接
     */
    public void stop() {
        isRunning = false;
        if (webSocket != null) {
            webSocket.close(1000, "客户端主动关闭");
            webSocket = null;
        }
    }

    /**
     * 建立 WebSocket 连接
     */
    private void connect() {
        try {
            // 获取 Stream 连接信息
            DingTalkApiClient.StreamConnection connection = apiClient.getStreamConnection();
            Log.d(TAG, "正在连接到: " + connection.endpoint);

            // 构建 WebSocket URL，添加 ticket 参数
            String wsUrl = connection.endpoint + "?ticket=" + connection.ticket;
            Log.d(TAG, "WebSocket URL: " + wsUrl);

            Request request = new Request.Builder()
                    .url(wsUrl)
                    .build();

            webSocket = httpClient.newWebSocket(request, this);

        } catch (Exception e) {
            Log.e(TAG, "连接失败", e);
            callback.onError("连接失败: " + e.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * 定时重连
     */
    private void scheduleReconnect() {
        if (!isRunning) {
            return;
        }

        Log.d(TAG, "将在 " + RECONNECT_DELAY_MS + "ms 后重连");
        new Thread(() -> {
            try {
                Thread.sleep(RECONNECT_DELAY_MS);
                if (isRunning) {
                    connect();
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "重连被中断", e);
            }
        }).start();
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.d(TAG, "WebSocket 连接已建立");
        callback.onConnected();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Log.d(TAG, "收到消息: " + text);

        try {
            JsonObject message = gson.fromJson(text, JsonObject.class);

            // 解析消息类型
            if (message.has("type")) {
                String type = message.get("type").getAsString();

                if ("SYSTEM".equals(type)) {
                    // 系统消息（如连接成功）
                    handleSystemMessage(message);
                } else if ("CALLBACK".equals(type)) {
                    // 回调消息（机器人消息）
                    handleCallbackMessage(message);
                }
            }

            // 发送 ACK 确认
            if (message.has("messageId")) {
                sendAck(message.get("messageId").getAsString());
            }

        } catch (Exception e) {
            Log.e(TAG, "处理消息失败", e);
        }
    }

    /**
     * 处理系统消息
     */
    private void handleSystemMessage(JsonObject message) {
        if (message.has("headers")) {
            JsonObject headers = message.getAsJsonObject("headers");
            if (headers.has("topic")) {
                String topic = headers.get("topic").getAsString();
                Log.d(TAG, "系统消息 topic: " + topic);
            }
        }
    }

    /**
     * 处理回调消息（机器人消息）
     */
    private void handleCallbackMessage(JsonObject message) {
        try {
            Log.d(TAG, "处理回调消息，完整消息: " + message.toString());

            if (!message.has("data")) {
                Log.w(TAG, "消息中没有 data 字段");
                return;
            }

            String dataStr = message.get("data").getAsString();
            Log.d(TAG, "data 字段内容: " + dataStr);

            JsonObject data = gson.fromJson(dataStr, JsonObject.class);
            Log.d(TAG, "解析后的 data: " + data.toString());

            // 检查是否是机器人被 @ 的消息
            if (data.has("conversationType") && data.has("text")) {
                String conversationId = data.get("conversationId").getAsString();
                String conversationType = data.get("conversationType").getAsString();
                String senderUserId = data.has("senderStaffId") ?
                    data.get("senderStaffId").getAsString() : "unknown";

                JsonObject textObj = data.getAsJsonObject("text");
                String text = textObj.get("content").getAsString();

                Log.d(TAG, "收到机器人消息 - conversationId: " + conversationId);
                Log.d(TAG, "收到机器人消息 - conversationType: " + conversationType);
                Log.d(TAG, "收到机器人消息 - senderUserId: " + senderUserId);
                Log.d(TAG, "收到机器人消息 - text: " + text);

                // 检查是否包含 @机器人
                if (data.has("atUsers")) {
                    Log.d(TAG, "消息包含 @机器人，触发回调");
                    callback.onMessageReceived(conversationId, conversationType, senderUserId, text);
                } else {
                    Log.w(TAG, "消息不包含 atUsers 字段，忽略");
                }
            } else {
                Log.w(TAG, "消息缺少必要字段 - conversationType: " + data.has("conversationType") +
                    ", text: " + data.has("text"));
            }

        } catch (Exception e) {
            Log.e(TAG, "处理回调消息失败", e);
            e.printStackTrace();
        }
    }

    /**
     * 发送 ACK 确认
     */
    private void sendAck(String messageId) {
        JsonObject ack = new JsonObject();
        ack.addProperty("messageId", messageId);
        ack.addProperty("code", "200");
        ack.addProperty("message", "OK");

        String ackJson = gson.toJson(ack);
        webSocket.send(ackJson);
        Log.d(TAG, "发送 ACK: " + messageId);
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "WebSocket 正在关闭: " + code + " - " + reason);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "WebSocket 已关闭: " + code + " - " + reason);
        callback.onDisconnected();

        if (isRunning) {
            scheduleReconnect();
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "WebSocket 连接失败", t);
        callback.onError("连接失败: " + t.getMessage());
        callback.onDisconnected();

        if (isRunning) {
            scheduleReconnect();
        }
    }
}
