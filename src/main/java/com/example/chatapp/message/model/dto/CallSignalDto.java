package com.example.chatapp.message.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CallSignalDto {
    private Long senderId;
    private String senderUsername;
    private String senderFullName;
    private Long recipientId;
    private String type;       // offer, answer, ice, cancel, reject, hangup, busy
    private String callType;   // AUDIO or VIDEO
    private String sdp;
    private Object candidate;  // ICE candidate
}

