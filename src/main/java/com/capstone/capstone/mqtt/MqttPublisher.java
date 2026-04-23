package com.capstone.capstone.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MqttPublisher {

    private final MessageChannel mqttOutboundChannel;

    public MqttPublisher(MessageChannel mqttOutboundChannel) {
        this.mqttOutboundChannel = mqttOutboundChannel;
    }

    public void publish(String topic, String payload) {
        log.info("[MQTT 발행] topic={}, payload={}", topic, payload);
        mqttOutboundChannel.send(
                MessageBuilder.withPayload(payload)
                        .setHeader("mqtt_topic", topic)
                        .build()
        );
    }
}
