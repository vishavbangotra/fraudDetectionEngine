package com.vishavbangotra.fraud_detection_system.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.vishavbangotra.fraud_detection_system.streaming.LiveFeedService;
import com.vishavbangotra.fraud_detection_system.streaming.LiveFeedService.Stream;

@RestController
@RequestMapping("/api/stream")
public class LiveFeedController {

    private final LiveFeedService liveFeedService;

    public LiveFeedController(LiveFeedService liveFeedService) {
        this.liveFeedService = liveFeedService;
    }

    @GetMapping(value = "/transactions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTransactions() {
        return liveFeedService.register(Stream.SCORED);
    }

    @GetMapping(value = "/alerts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlerts() {
        return liveFeedService.register(Stream.FLAGGED);
    }
}
