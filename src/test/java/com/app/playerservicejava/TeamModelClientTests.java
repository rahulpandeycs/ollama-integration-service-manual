package com.app.playerservicejava;

import com.app.playerservicejava.model.team.ModelTeamGenerateResponse;
import com.app.playerservicejava.service.model.TeamModelClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TeamModelClientTests {

    @Test
    void sendsModelContractAndMapsResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        TeamModelClient client = new TeamModelClient(restTemplate, "http://localhost:5000/");

        server.expect(requestTo("http://localhost:5000/team/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"seed_id\":\"aaronha01\",\"team_size\":5}"))
                .andRespond(withSuccess(
                        """
                        {
                          "seed_id": "aaronha01",
                          "prediction_id": "prediction-1",
                          "model_version": "similarity-test",
                          "team_size": 2,
                          "member_ids": ["aaronha01", "member-1"]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        ModelTeamGenerateResponse response = client.generateTeam("aaronha01", 5);

        assertEquals("aaronha01", response.seedId());
        assertEquals("prediction-1", response.predictionId());
        assertEquals("similarity-test", response.modelVersion());
        assertEquals(2, response.teamSize());
        assertEquals(2, response.memberIds().size());
        server.verify();
    }
}
