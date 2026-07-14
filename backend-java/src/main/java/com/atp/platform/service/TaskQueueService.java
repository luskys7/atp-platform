package com.atp.platform.service;

public interface TaskQueueService {
    long size();
    void enqueue(String item);
    String dequeue();
}
