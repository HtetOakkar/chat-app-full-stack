package com.example.chatapp.message.util;

import com.example.chatapp.message.model.dto.MessageDto;
import com.example.chatapp.message.repository.RedisMessageRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@RequiredArgsConstructor
public class RedisItemReader implements ItemReader<MessageDto> {

    private final RedisMessageRepository redisMessageRepository;
    private final Deque<MessageDto> messagesCache = new ArrayDeque<>();

    @Override
    @Nullable
    public MessageDto read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (messagesCache.isEmpty()) {
            List<MessageDto> messageDtos = redisMessageRepository.getMessages(100);
            if (messageDtos == null || messageDtos.isEmpty()) {
                return null;
            }
            messagesCache.addAll(messageDtos);
        }

        return messagesCache.removeFirst();
    }
}
