package com.xchange.platform.controller;

import com.xchange.platform.common.Result;
import com.xchange.platform.component.OrphanFileCleanupTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {
    private final OrphanFileCleanupTask cleanupTask;

    @PostMapping("/cleanup")
    public Result<String> triggerCleanup() {
        cleanupTask.cleanupOrphanFiles();
        return Result.success("清理任务已手动触发");
    }
}