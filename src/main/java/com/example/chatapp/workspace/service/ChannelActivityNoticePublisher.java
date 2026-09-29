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
import java.util.List;

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
