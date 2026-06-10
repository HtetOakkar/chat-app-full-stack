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
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BatchConfig {
    private final RedisMessageRepository redisMessageRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public ItemReader<MessageDto> redisItemReader() {
        return new RedisItemReader(redisMessageRepository);
    }

    @Bean
    public ItemProcessor<MessageDto, Message> messageProcessor() {
        return messageDto -> {
            User sender = userRepository.findById(messageDto.getSenderId())
                    .orElseThrow(() -> new NotFoundException("User not found with ID:" + messageDto.getSenderId()));

            User recipient = null;
            if (messageDto.getRecipientId() != null) {
                recipient = userRepository.findById(messageDto.getRecipientId())
                        .orElseThrow(() -> new NotFoundException("User not found with ID:" + messageDto.getRecipientId()));
            } else {
                recipient = userRepository.findByUsername("system")
                        .orElseThrow(() -> new NotFoundException("System user not found."));
            }

            Instant sentAt = messageDto.getTimestamp() == null ? Instant.now() : messageDto.getTimestamp();
            boolean isDelivered = messageDto.getIsDelivered() != null ? messageDto.getIsDelivered() : false;

            return Message.builder()
                    .sender(sender)
                    .recipient(recipient)
                    .content(messageDto.getContent())
                    .isRead(messageDto.getIsRead() != null ? messageDto.getIsRead() : false)
                    .isDelivered(isDelivered)
                    .messageType(messageDto.getMessageType() == null ? MessageType.TEXT : messageDto.getMessageType())
                    .sentAt(sentAt)
                    .deliveredAt(sentAt)
                    .build();
        };
    }

    @Bean
    public ItemWriter<Message> messageItemWriter() {
        log.info("Saving messages.....");
        return messageRepository::saveAll;
    }

    @Bean
    public Step redisToDbStep() {
        return new StepBuilder("redisToDbStep", jobRepository)
                .<MessageDto, Message>chunk(100, transactionManager)
                .reader(redisItemReader())
                .processor(messageProcessor())
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
