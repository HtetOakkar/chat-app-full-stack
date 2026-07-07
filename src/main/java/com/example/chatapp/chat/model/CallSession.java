package com.example.chatapp.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CallSession {
    private Long callerId;
    private Long calleeId;
    private String callType; // AUDIO or VIDEO
    private Instant startedAt;
    private String status;   // dialing, active, ended
}
