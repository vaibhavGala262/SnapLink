package com.vaibhavgala.url_shortner.service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaibhavgala.url_shortner.service.events.ClickEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaClickProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private KafkaClickProducer producer;

    @Test
    @DisplayName("Serializes the click event and sends it to the click-events topic")
    void sendClickEvent_serializesAndSends() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any(ClickEvent.class)))
                .thenReturn("{\"shortCode\":\"abc\"}");

        producer.sendClickEvent("abc", "1.2.3.4", "UA", null);

        verify(kafkaTemplate).send("click-events", "{\"shortCode\":\"abc\"}");
    }

    @Test
    @DisplayName("Serialization failures are logged without sending and without crashing")
    void sendClickEvent_serializationFailure_noSend_noThrow() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any(ClickEvent.class)))
                .thenThrow(new RuntimeException("serialize boom"));

        assertDoesNotThrow(() -> producer.sendClickEvent("abc", "1.2.3.4", null, null));

        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }
}