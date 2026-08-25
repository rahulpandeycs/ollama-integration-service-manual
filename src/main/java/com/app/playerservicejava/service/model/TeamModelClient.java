package com.app.playerservicejava.service.model;

import com.app.playerservicejava.model.team.ModelTeamGenerateRequest;
import com.app.playerservicejava.model.team.ModelTeamGenerateResponse;
import com.app.playerservicejava.service.team.TeamGenerationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

@Service
public class TeamModelClient {

    private final RestTemplate restTemplate;
    private final String modelBaseUrl;

    public TeamModelClient(
            @Qualifier("modelRestTemplate") RestTemplate restTemplate,
            @Value("${player-service-model.base-url:http://localhost:5000}") String modelBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.modelBaseUrl = modelBaseUrl.endsWith("/")
                ? modelBaseUrl.substring(0, modelBaseUrl.length() - 1)
                : modelBaseUrl;
    }

    public ModelTeamGenerateResponse generateTeam(String playerId, int teamSize) {
        URI endpoint = fromUriString(modelBaseUrl)
                .path("/team/generate")
                .build()
                .toUri();

        try {
            ModelTeamGenerateResponse response = restTemplate.postForObject(
                    endpoint,
                    new ModelTeamGenerateRequest(playerId, teamSize),
                    ModelTeamGenerateResponse.class
            );

            if (response == null) {
                throw new TeamGenerationException(
                        HttpStatus.BAD_GATEWAY,
                        "Model service returned an empty response"
                );
            }

            return response;
        } catch (TeamGenerationException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new TeamGenerationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Model service is unavailable",
                    exception
            );
        } catch (HttpStatusCodeException exception) {
            throw new TeamGenerationException(
                    HttpStatus.BAD_GATEWAY,
                    "Model service returned HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new TeamGenerationException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not parse the model service response",
                    exception
            );
        }
    }
}
