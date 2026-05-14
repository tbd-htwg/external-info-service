package com.tripplanner.external_info_service.ApiProxyServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.tripplanner.external_info_service.dto.ExternalInfoDto.Tour;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


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
    log.info("DEBUG: Viator Key is: '{}'", (viatorApiKey != null && viatorApiKey.length() > 5) ? viatorApiKey.substring(0, 5) + "***" : "NICHT GELADEN");
    
    Map<String, Object> requestBody = Map.of(
        "filtering", Map.of(
            "destination", "648" // "648" ist Paris. Für das Projekt reicht dieser statische Wert zum Testen!
        ),
        "currency", "EUR"
    );

    return webClient.post() // UMSTELLUNG AUF POST
        .uri("https://api.sandbox.viator.com/partner/products/search")
        .header("exp-api-key", viatorApiKey)
        .header("Accept", "application/json;version=2.0")
        .header("Content-Type", "application/json") 
        .bodyValue(requestBody) 
        .retrieve()
        .bodyToMono(Map.class)
        .map(response -> {
            log.info("Viator Antwort erhalten: {}", response != null ? "Ja" : "Nein");
            
            List<Map<String, Object>> products = (List<Map<String, Object>>) response.get("products");
            List<Tour> tours = new ArrayList<>();
            if (products != null) {
                for (Map<String, Object> p : products) {
                    Map<String, Object> pricing = (Map<String, Object>) p.get("pricing");
                    Map<String, Object> summary = (pricing != null) ? (Map<String, Object>) pricing.get("summary") : null;
                    Object minPrice = (summary != null) ? summary.get("minPrice") : "0.00";

                    tours.add(new Tour(
                        p.get("productCode").toString(),
                        p.get("title").toString(),
                        minPrice.toString() + " €", 
                        p.get("productUrl") != null ? p.get("productUrl").toString() : "#"
                    ));
                }
            }
            return tours;
        })
        .onErrorResume(e -> {
            log.warn("Viator API Error für {}: {}", location, e.getMessage());
            return Mono.just(List.of(new Tour("MOCK", "Discovery Tour " + location, "0.00 €", "#")));
        });
    }

}
