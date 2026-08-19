package com.example.chatapp.workspace.model.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAddChannelMembersRequest {
    @NotEmpty(message = "userIds must not be empty")
    private List<Long> userIds;
}
