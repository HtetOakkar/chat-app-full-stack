package com.example.chatapp.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @Test
    void unexpectedErrorsReturnGenericResponseBody() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/throws"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.message", not(containsString("database password leaked"))));
    }

    @Test
    void missingStaticResourceReturnsGenericNotFoundWithoutEchoingPath() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/missing-asset"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found."))
                .andExpect(content().string(not(containsString("missing-asset"))))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/throws")
        String throwsUnexpectedException() {
            throw new IllegalStateException("database password leaked");
        }

        @GetMapping("/missing-asset")
        String throwsNoResourceFound() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "missing-asset<script>alert(1)</script>");
        }
    }
}
