package com.example.chatapp.workspace.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchInvitationResponse {
    private List<BatchInvitationItemResult> results;
    private int sentCount;
    private int failedCount;
}
