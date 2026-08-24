package com.app.playerservicejava.service.chat;

import com.app.playerservicejava.model.Player;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.models.Model;
import io.github.ollama4j.models.OllamaResult;
import io.github.ollama4j.types.OllamaModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.ollama4j.utils.OptionsBuilder;
import io.github.ollama4j.utils.PromptBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

@Service
public class ChatClientService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatClientService.class);

    @Autowired
    private OllamaAPI ollamaAPI;

    public List<Model> listModels() throws OllamaBaseException, IOException, URISyntaxException, InterruptedException {
        List<Model> models = ollamaAPI.listModels();
        return models;
    }

    public String chat() throws OllamaBaseException, IOException, InterruptedException {
        String model = OllamaModelType.TINYLLAMA;

        // https://ollama4j.github.io/ollama4j/intro
        PromptBuilder promptBuilder =
                new PromptBuilder()
                        .addLine("Recite a haiku about recursion.");

        boolean raw = false;
        OllamaResult response = ollamaAPI.generate(model, promptBuilder.build(), raw, new OptionsBuilder().build());
        return response.getResponse();
    }

    public String generateNickname(
            Player player,
            String country)
            throws OllamaBaseException, IOException, InterruptedException {
        String model = OllamaModelType.TINYLLAMA;

        PromptBuilder promptBuilder =
                new PromptBuilder()
                        .addLine(
                                "Generate exactly one short and respectful nickname "
                                        + "for the baseball player below."
                        )
                        .addLine(
                                "Player: "
                                        + player.getFirstName()
                                        + " "
                                        + player.getLastName()
                        )
                        .addLine("Country: " + country)
                        .addLine(
                                "Return only the nickname. "
                                        + "Do not provide an explanation, list, "
                                        + "quotation marks, or additional text."
                        );

        OllamaResult response = ollamaAPI.generate(model, promptBuilder.build(), false, new OptionsBuilder().setNumPredict(20).build());
        return response.getResponse() == null
                ? ""
                : response.getResponse().trim();
    }

    }
