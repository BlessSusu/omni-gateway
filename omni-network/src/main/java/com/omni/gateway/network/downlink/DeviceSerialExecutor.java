package com.omni.gateway.network.downlink;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeviceSerialExecutor {

    private final ConcurrentHashMap<String, ExecutorService> executors = new ConcurrentHashMap<>();

    public void execute(String deviceId, Runnable task) {
        executors.computeIfAbsent(deviceId, id -> Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "omni-downlink-" + id);
            t.setDaemon(true);
            return t;
        })).execute(task);
    }
}
