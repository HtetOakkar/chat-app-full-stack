package com.example.chatapp.workspace.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The result of a Channel Member addition, whether one user ID or many was requested. Every
 * distinct requested user ID appears in exactly one category, in request order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAddChannelMembersResponse {
    /** User IDs that became Channel Members. */
    private List<Long> added;
    /** User IDs that were already Channel Members. */
    private List<Long> alreadyMembers;
    /** User IDs that are not Workspace Members, so cannot join the Channel. */
    private List<Long> notWorkspaceMembers;
}
