package com.tripplanner.external_info_service.ApiProxyServices;

    import org.springframework.stereotype.Service;
    import org.springframework.web.reactive.function.client.WebClient;
    
    import com.tripplanner.external_info_service.dto.ExternalInfoDto.GeocodingResult;
    
    import lombok.extern.slf4j.Slf4j;
    import reactor.core.publisher.Mono;
    import tools.jackson.databind.JsonNode;

    @Slf4j
    @Service
    public class GeocodingApi {

        private final WebClient webClient;
    
        public GeocodingApi(WebClient.Builder webClientBuilder) {
            this.webClient = webClientBuilder.build();
        }
    
        // NOMINATIM API
        public Mono<GeocodingResult> searchLocation(String query) {
            return webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("nominatim.openstreetmap.org")
                    .path("/search")
                    .queryParam("q", query)
                    .queryParam("format", "json")
                    .queryParam("addressdetails", 1)
                    .queryParam("limit", 1)
                    .queryParam("accept-language", "eng")
                    .build())
                .header("User-Agent", "TripPlannerUniversityProject/1.0")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    if (node.isArray() && !node.isEmpty()) {
                        JsonNode firstResult = node.get(0);
                        return new GeocodingResult(
                            firstResult.path("displayName").asText(), 
                            firstResult.path("lat").asDouble(),
                            firstResult.path("lon").asDouble(),
                            firstResult.path("address").path("country_code").asText().toUpperCase()
                        );
                    }
                    return null;
                })
                .onErrorResume(e -> {
                    log.error("Geocoding Fehler: {}", e.getMessage());
                    return Mono.empty();
                });
        }
    }
    
