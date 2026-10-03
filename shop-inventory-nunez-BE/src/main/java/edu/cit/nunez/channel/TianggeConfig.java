package edu.cit.nunez.channel;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class TianggeConfig {

    private final String instanceId;

    TianggeConfig(@Qualifier("appInstanceId") String instanceId) {
        this.instanceId = instanceId;
    }

    String getInstanceId() {
        return instanceId;
    }

    @Bean
    RestClient tianggeRestClient(
            @Value("${TIANGGE_URL:https://legacysupply.onrender.com/tiangge/v1}") String baseUrl,
            @Value("${LS_CLIENT_ID:23-1498-418}") String clientId,
            @Value("${LS_API_KEY:LSK-D7CCC0B6BF5993F1C549}") String apiKey) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(15000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Client-Id", clientId)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("X-Client-Instance", instanceId)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
