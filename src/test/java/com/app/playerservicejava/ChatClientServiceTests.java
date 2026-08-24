package com.app.playerservicejava;

import com.app.playerservicejava.model.Player;
import com.app.playerservicejava.service.chat.ChatClientService;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.OllamaResult;
import io.github.ollama4j.types.OllamaModelType;
import io.github.ollama4j.utils.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatClientServiceTests {

    @Mock
    private OllamaAPI ollamaAPI;

    @InjectMocks
    private ChatClientService chatClientService;

    @Test
    void generatesNicknameUsingPlayerAndCountry() throws Exception {
        when(ollamaAPI.generate(
                eq(OllamaModelType.TINYLLAMA),
                anyString(),
                eq(false),
                any(Options.class)
        )).thenReturn(
                new OllamaResult("  The Hammer  ", 100L, 200)
        );

        Player player = new Player();
        player.setFirstName("Hank");
        player.setLastName("Aaron");

        String result = chatClientService.generateNickname(
                player,
                "USA"
        );

        assertEquals("The Hammer", result);

        ArgumentCaptor<String> promptCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(ollamaAPI).generate(
                eq(OllamaModelType.TINYLLAMA),
                promptCaptor.capture(),
                eq(false),
                any(Options.class)
        );

        String prompt = promptCaptor.getValue();

        org.junit.jupiter.api.Assertions.assertTrue(
                prompt.contains("Hank Aaron")
        );

        org.junit.jupiter.api.Assertions.assertTrue(
                prompt.contains("USA")
        );
    }
}