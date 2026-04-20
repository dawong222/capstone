package com.capstone.capstone.mqtt;

import com.capstone.capstone.service.DataProcessingService;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
public class MqttSubscriber {

    private final DataProcessingService service;

    public MqttSubscriber(DataProcessingService service) {
        this.service = service;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    //mqttInputChannel로 들어온 메시지를 이 메서드로 전달
    public void handleMessage(String payload){
        service.process(payload);
    }


}
