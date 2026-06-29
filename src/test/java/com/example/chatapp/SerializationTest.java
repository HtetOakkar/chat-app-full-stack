package com.example.chatapp;
import com.example.chatapp.message.model.dto.TypingIndicatorDto;
import com.fasterxml.jackson.databind.ObjectMapper;
public class SerializationTest {
    public static void main(String[] args) throws Exception {
        TypingIndicatorDto dto = new TypingIndicatorDto(2L, true);
        dto.setSenderId(1L);
        System.out.println(new ObjectMapper().writeValueAsString(dto));
    }
}
