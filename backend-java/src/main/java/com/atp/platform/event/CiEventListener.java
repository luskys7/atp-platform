package com.atp.platform.event;

import com.atp.platform.service.CiService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CiEventListener {

    private final CiService ciService;

    @Async
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        ciService.notifyTaskCompleted(event.taskId());
    }
}
