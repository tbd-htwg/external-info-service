package com.tripplanner.external_info_service.ApiProxyServices;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.tripplanner.external_info_service.dto.ExternalInfoDto.Tour;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;


@Slf4j
@Service
public class ViatorApi {

    private final WebClient webClient;
    
        public ViatorApi(WebClient.Builder webClientBuilder) {
            this.webClient = webClientBuilder.build();
        }

    // TOURS (Viator) 
    @Value("${external-api.viator.api-key}")
    private String viatorApiKey;

    @Cacheable(value = "tours", key = "#location + '-' + #countryCode")
    public Mono<List<Tour>> getViatorTours(String location, String countryCode) {
        String combinedSearch = location + ", " + countryCode;

        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .scheme("https")
                .host("api.viator.com") // KORREKTUR: Kein "https://" im Host-Feld!
                .path("/partner/products/search")
                .queryParam("searchTerm", combinedSearch)
                .queryParam("count", 5)
                .queryParam("currency", "EUR")
                .build())
            .header("exp-api-key", viatorApiKey)
            .header("Accept-Language", "en-US") 
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(node -> {
                List<Tour> tours = new ArrayList<>();
                JsonNode products = node.path("products"); // path() ist sicherer als get()
                if (products.isArray()) {
                    for (JsonNode p : products) {
                        tours.add(new Tour(
                            p.path("productCode").asText(),
                            p.path("title").asText(),
                            p.path("pricing").path("summary").path("minPrice").asText() + " €", 
                            p.path("productUrl").asText()
                        ));
                    }
                }
                return tours;
            })
            .onErrorResume(e -> {
                log.warn("Viator Fallback active: {}", e.getMessage());
                return Mono.just(List.of(new Tour("MOCK", "Discovery Tour " + location, "0.00 €", "#")));
            });
    }

}
