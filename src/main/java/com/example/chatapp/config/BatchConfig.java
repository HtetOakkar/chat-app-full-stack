package com.example.chatapp.config;

import com.example.chatapp.exception.NotFoundException;
import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.model.entity.Message;
import com.example.chatapp.message.model.entity.MessageType;
import com.example.chatapp.message.repository.MessageRepository;
import com.example.chatapp.message.repository.RedisMessageRepository;
import com.example.chatapp.message.util.RedisItemReader;
import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BatchConfig {
    private final RedisMessageRepository redisMessageRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final com.example.chatapp.workspace.repository.WorkspaceMemberRepository workspaceMemberRepository;
    private final com.example.chatapp.workspace.repository.ConversationRepository conversationRepository;

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public RedisItemReader redisItemReader() {
        return new RedisItemReader(redisMessageRepository);
    }

    @Bean
    public ItemProcessor<MessageDto, Message> messageProcessor() {
        return messageDto -> mapToMessageEntity(messageDto);
    }

    @Bean
    public ItemWriter<MessageDto> messageItemWriter() {
        return chunk -> {
            List<? extends MessageDto> messageDtos = chunk.getItems();
            if (messageDtos.isEmpty()) {
                return;
            }

            log.info("Processing batch of {} messages...", messageDtos.size());
            List<Message> messages = new ArrayList<>();
            for (MessageDto dto : messageDtos) {
                Message msg = mapToMessageEntity(dto);
                if (msg != null) {
                    messages.add(msg);
                }
            }
            if (!messages.isEmpty()) {
                messageRepository.saveAll(messages);
            }
        };
    }

    private Message mapToMessageEntity(MessageDto dto) {
        if (dto.getConversationId() == null) {
            return null;
        }
        var conv = conversationRepository.findById(dto.getConversationId()).orElse(null);
        if (conv == null) {
            return null;
        }

        com.example.chatapp.workspace.model.entity.WorkspaceMember senderMember = null;
        if (dto.getSenderMemberId() != null) {
            senderMember = workspaceMemberRepository.findById(dto.getSenderMemberId()).orElse(null);
        } else if (dto.getSenderId() != null && conv.getWorkspace() != null) {
            senderMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(conv.getWorkspace().getId(), dto.getSenderId()).orElse(null);
        }

        Instant sentAt = dto.getTimestamp() == null ? Instant.now() : dto.getTimestamp();

        return Message.builder()
                .conversation(conv)
                .senderMember(senderMember)
                .content(dto.getContent() != null ? dto.getContent() : "")
                .isRead(dto.getIsRead() != null ? dto.getIsRead() : false)
                .isDeleted(dto.getIsDeleted() != null ? dto.getIsDeleted() : false)
                .messageType(dto.getMessageType() == null ? MessageType.TEXT : dto.getMessageType())
                .sentAt(sentAt)
                .callOutcome(dto.getCallOutcome())
                .callDuration(dto.getCallDuration())
                .videoUsed(dto.getVideoUsed())
                .build();
    }

    @Bean
    public Step redisToDbStep() {
        return new StepBuilder("redisToDbStep", jobRepository)
                .<MessageDto, MessageDto>chunk(100, transactionManager)
                .reader(redisItemReader())
                .writer(messageItemWriter())
                .build();
    }

    @Bean
    public Job redisToDbJob() {
        return new JobBuilder("redisToDbJob", jobRepository)
                .start(redisToDbStep())
                .build();
    }
}
