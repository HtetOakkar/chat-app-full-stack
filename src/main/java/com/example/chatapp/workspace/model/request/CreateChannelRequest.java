package com.example.chatapp.workspace.model.request;

import com.example.chatapp.workspace.model.entity.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChannelRequest {

    @NotBlank(message = "Channel name is required")
    @Size(min = 2, max = 100, message = "Channel name must be between 2 and 100 characters")
    private String name;

    @Size(max = 100, message = "Slug cannot exceed 100 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug can only contain lowercase letters, numbers, and hyphens")
    private String slug;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Builder.Default
    private ChannelType type = ChannelType.PUBLIC;
}
