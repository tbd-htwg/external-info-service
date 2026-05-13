package com.tripplanner.external_info_service.ApiProxyServices;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.tripplanner.external_info_service.dto.ExternalInfoDto.TravelWarning;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
public class TravelWarningApi {

    private final WebClient webClient;
    
    public TravelWarningApi(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    // AUSWÄRTIGES AMT API
    private final Map<String, String> dynamicCountryMap = new ConcurrentHashMap<>();

    private Mono<Map<String, String>> refreshCountryMap() {
        if (!dynamicCountryMap.isEmpty()) return Mono.just(dynamicCountryMap);
        
        return webClient.get()
            .uri("https://www.auswaertiges-amt.de/opendata/travelwarning")
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(node -> {
                JsonNode rows = node.path("response");
                if (rows.isObject()) {
                    rows.forEachEntry((id, countryData) -> {
                        // asText() fängt MissingNode ab
                        String isoCode = countryData.path("countryCode").asText();
                        if (!isoCode.isBlank()) {
                            dynamicCountryMap.put(isoCode.toUpperCase(), id);
                        }
                    });
                }
                log.info("Dynamic mapping loaded: {} Länder", dynamicCountryMap.size());
                return dynamicCountryMap;
            });
    }

    @Cacheable(value = "warnings", key = "#countryCode")
    public Mono<TravelWarning> getTravelWarning(String countryCode) {
    return refreshCountryMap().flatMap(map -> {
        String aaId = map.get(countryCode.toUpperCase());
        if (aaId == null) {
            return Mono.just(new TravelWarning(countryCode, "Info", "No data found."));
        }

        return webClient.get()
            .uri("https://www.auswaertiges-amt.de/opendata/travelwarning/" + aaId)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(node -> {
                JsonNode data = node.path("response").path(aaId);
                boolean isWarning = data.path("warning").asBoolean();
                
                // Content oder Titel holen
                String rawMsg = isWarning ? data.path("content").asText() : data.path("title").asText();
                String cleanMsg = rawMsg.replaceAll("<[^>]*>", "");
                
                // 1. Text intelligent am Punkt abschneiden (Limit 250 Zeichen)
                String shortMsg = truncateAtSentenceEnd(cleanMsg, 250);
                
                // 2. Dynamischen Link generieren
                // Das AA leitet IDs innerhalb dieses Pfades automatisch zum richtigen Land weiter
                String aaUrl = "https://www.auswaertiges-amt.de/de/service/laender/display-node/id-" + aaId;
                
                String finalMessage = shortMsg + " More info: " + aaUrl;

                return new TravelWarning(countryCode, isWarning ? "Warning" : "Safety Info", finalMessage);
            });
    });
}

/**
 * Schneidet den Text beim letzten Punkt innerhalb des maxLength-Bereichs ab.
 */
    private String truncateAtSentenceEnd(String message, int maxLength) {
    if (message == null || message.length() <= maxLength) {
        return message;
    }

    // Erster Schnitt beim Limit
    String sub = message.substring(0, maxLength);
    
    // Suche den letzten Punkt im abgeschnittenen Teil
    int lastDot = sub.lastIndexOf(".");
    
    if (lastDot > 50) { // Wir wollen nicht nach nur 50 Zeichen abschneiden
        return sub.substring(0, lastDot + 1);
    } else {
        // Falls kein Punkt gefunden wurde, schneide beim letzten Leerzeichen ab
        int lastSpace = sub.lastIndexOf(" ");
        return (lastSpace > 0 ? sub.substring(0, lastSpace) : sub) + "...";
    }
}

}
