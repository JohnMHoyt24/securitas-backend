package com.securitas.backend.ws;

import com.securitas.backend.domain.Alert;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlertBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public AlertBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(Alert alert) {
        messagingTemplate.convertAndSend("/topic/alerts", alert);
    }
}
