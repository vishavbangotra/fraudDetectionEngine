package com.vishavbangotra.fraud_detection_system.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.vishavbangotra.fraud_detection_system.streaming.LiveFeedService;
import com.vishavbangotra.fraud_detection_system.streaming.LiveFeedService.Stream;

@WebMvcTest(controllers = LiveFeedController.class)
class LiveFeedControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    LiveFeedService liveFeedService;

    @Test
    void transactionsEndpointReturnsSseEmitter() throws Exception {
        when(liveFeedService.register(Stream.SCORED)).thenReturn(new SseEmitter());

        mvc.perform(get("/api/stream/transactions").accept("text/event-stream"))
                .andExpect(status().isOk());

        verify(liveFeedService).register(Stream.SCORED);
    }

    @Test
    void alertsEndpointReturnsSseEmitter() throws Exception {
        when(liveFeedService.register(Stream.FLAGGED)).thenReturn(new SseEmitter());

        mvc.perform(get("/api/stream/alerts").accept("text/event-stream"))
                .andExpect(status().isOk());

        verify(liveFeedService).register(Stream.FLAGGED);
    }
}
