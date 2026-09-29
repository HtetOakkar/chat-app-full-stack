package com.example.chatapp.workspace.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    /** The most user IDs one Channel Member addition may carry. */
    public static final int MAX_USER_IDS = 50;

    @NotEmpty(message = "userIds must not be empty")
    @Size(max = MAX_USER_IDS, message = "You can add at most " + MAX_USER_IDS + " colleagues at a time")
    private List<@NotNull(message = "userIds must not contain null") Long> userIds;
}
