package com.example.chatapp.workspace.service;

import com.example.chatapp.message.mapper.MessageMapper;
import com.example.chatapp.message.model.entity.Message;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.websocket.MessageBroker;
import com.example.chatapp.workspace.model.entity.Channel;
import com.example.chatapp.workspace.model.entity.WorkspaceMember;
import com.example.chatapp.workspace.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and publishes every Channel Activity Notice: the system-generated entry recording a
 * Channel membership transition. It is the single owner of notice wording and of the Channel
 * topic format, so Channel membership changes and Invitation acceptance read the same way.
 *
 * <p>Each notice is saved to the Channel's conversation (so it appears in history) and published
 * to the Channel topic (so connected Channel Members see it live). It is attributed to the
 * {@code actor} passed in, which becomes the message's sender.
 */
@Component
@RequiredArgsConstructor
public class ChannelActivityNoticePublisher {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final MessageBroker messageBroker;

    /**
     * The removed User's personal queue for "removed from channel" notices. It is not scoped to a
     * Workspace, so the removed User is told wherever they are in the app.
     */
    public static final String CHANNEL_REMOVAL_QUEUE = "/queue/channel-removals";

    /** The STOMP topic carrying a Channel's messages, including its Channel Activity Notices. */
    public static String channelTopic(Long workspaceId, Long channelId) {
        return String.format("/topic/workspace.%d.channel.%d", workspaceId, channelId);
    }

    /**
     * One consolidated notice for Members added to a Channel, e.g.
     * "@alice added @bob, @carol, and @dave to the channel". Posts nothing when
     * {@code addedMembers} is empty. Each added Member's User must be loaded.
     */
    public void membersAdded(Channel channel, WorkspaceMember addedBy, List<WorkspaceMember> addedMembers) {
        if (addedMembers.isEmpty()) {
            return;
        }
        List<String> addedUsernames = addedMembers.stream().map(ChannelActivityNoticePublisher::username).toList();
        publish(channel, addedBy, "@" + username(addedBy) + " added " + joinMentions(addedUsernames) + " to the channel");
    }

    /** "@member joined the channel", for a Member who joined through an Invitation. */
    public void memberJoined(Channel channel, WorkspaceMember joiner) {
        publish(channel, joiner, "@" + username(joiner) + " joined the channel");
    }

    /**
     * "@member left the channel". Used for both voluntary departures and removals: a removal
     * deliberately reads exactly like a departure and never names the remover.
     */
    public void memberLeft(Channel channel, WorkspaceMember departedMember) {
        publish(channel, departedMember, "@" + username(departedMember) + " left the channel");
    }

    /**
     * {@code remover} removed {@code removedMember}. The Channel gets exactly the notice a
     * voluntary departure produces ("@member left the channel"). Only the removed User learns
     * who removed them, through a "removed from channel" notice on their personal queue.
     */
    public void memberRemoved(Channel channel, WorkspaceMember removedMember, WorkspaceMember remover) {
        memberLeft(channel, removedMember);

        var removerUser = remover.getUser();
        Map<String, Object> notice = new LinkedHashMap<>();
        notice.put("type", "REMOVED_FROM_CHANNEL");
        notice.put("workspaceId", channel.getWorkspace().getId());
        notice.put("channelId", channel.getId());
        notice.put("channelName", channel.getName());
        notice.put("removedByUserId", removerUser.getId());
        notice.put("removedByUsername", removerUser.getUsername());
        notice.put("removedByFullName", removerUser.getFullName() != null ? removerUser.getFullName() : removerUser.getUsername());
        messageBroker.publishToUser(removedMember.getUser().getId().toString(), CHANNEL_REMOVAL_QUEUE, notice);
    }

    private void publish(Channel channel, WorkspaceMember actor, String content) {
        var conversation = conversationRepository.findByChannelId(channel.getId());
        if (conversation.isEmpty()) {
            return;
        }

        Message notice = messageRepository.save(Message.builder()
                .conversation(conversation.get())
                .senderMember(actor)
                .content(content)
                .messageType(MessageType.SYSTEM)
                .isRead(false)
                .isDeleted(false)
                .sentAt(Instant.now())
                .build());

        messageBroker.publishToTopic(channelTopic(channel.getWorkspace().getId(), channel.getId()),
                messageMapper.toMessageDto(notice));
    }

    private static String username(WorkspaceMember member) {
        return member.getUser().getUsername();
    }

    /** "@a", "@a and @b", or "@a, @b, and @c". */
    private static String joinMentions(List<String> usernames) {
        List<String> mentions = usernames.stream().map(u -> "@" + u).toList();
        if (mentions.size() == 1) {
            return mentions.get(0);
        }
        if (mentions.size() == 2) {
            return mentions.get(0) + " and " + mentions.get(1);
        }
        return String.join(", ", mentions.subList(0, mentions.size() - 1)) + ", and " + mentions.get(mentions.size() - 1);
    }
}
