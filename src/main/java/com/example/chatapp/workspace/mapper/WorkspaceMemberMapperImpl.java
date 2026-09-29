package com.example.chatapp.workspace.mapper;

import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.service.PresenceModule;
import com.example.chatapp.workspace.model.dto.WorkspaceMemberDto;
import com.example.chatapp.workspace.model.entity.WorkspaceMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class WorkspaceMemberMapperImpl implements WorkspaceMemberMapper {

    private static final String ONLINE = "ONLINE";
    private static final String OFFLINE = "OFFLINE";

    private final PresenceModule presenceModule;

    @Override
    public List<WorkspaceMemberDto> toDtos(Long viewerId, List<WorkspaceMember> members) {
        Set<Long> appearingOnline = presenceModule.findUsersAppearingOnlineTo(
                viewerId, members.stream().map(member -> member.getUser().getId()).toList());
        return members.stream()
                .map(member -> toDto(member, appearingOnline))
                .toList();
    }

    @Override
    public WorkspaceMemberDto toDto(Long viewerId, WorkspaceMember member) {
        return toDtos(viewerId, List.of(member)).get(0);
    }

    private WorkspaceMemberDto toDto(WorkspaceMember member, Set<Long> appearingOnline) {
        User user = member.getUser();
        return WorkspaceMemberDto.builder()
                .id(member.getId())
                .workspaceId(member.getWorkspace().getId())
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .presenceStatus(appearingOnline.contains(user.getId()) ? ONLINE : OFFLINE)
                .build();
    }
}
