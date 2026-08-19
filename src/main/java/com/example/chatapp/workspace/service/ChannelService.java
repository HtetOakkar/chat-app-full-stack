package com.example.chatapp.workspace.service;

import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.dto.MessagePage;
import com.example.chatapp.workspace.model.dto.ChannelDto;
import com.example.chatapp.workspace.model.dto.WorkspaceMemberDto;
import com.example.chatapp.workspace.model.request.CreateChannelRequest;

import java.util.List;

public interface ChannelService {
    ChannelDto createChannel(Long workspaceId, Long userId, CreateChannelRequest request);
    List<ChannelDto> getWorkspaceChannels(Long workspaceId, Long userId);
    ChannelDto getChannelById(Long channelId, Long userId);
    void addChannelMember(Long channelId, Long requestingUserId, Long targetUserId);
    void removeChannelMember(Long channelId, Long requestingUserId, Long targetUserId);
    List<WorkspaceMemberDto> getChannelMembers(Long channelId, Long userId);
    MessagePage getChannelMessages(Long channelId, Long userId, String cursor, int limit);
    MessageDto sendChannelMessage(Long channelId, Long userId, MessageDto messageDto);
}
