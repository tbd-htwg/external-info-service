package com.tripplanner.external_info_service.ApiProxyServices;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.tripplanner.external_info_service.WeatherDescription;
import com.tripplanner.external_info_service.dto.ExternalInfoDto.DailyForecast;
import com.tripplanner.external_info_service.dto.ExternalInfoDto.WeatherData;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
public class WeatherApi {

    private final WebClient webClient;
    
        public WeatherApi(WebClient.Builder webClientBuilder) {
            this.webClient = webClientBuilder.build();
        }

    // WEATHER API (Open-Meteo)
    @Cacheable(value = "weather", key = "#lat + '-' + #lon")
    public Mono<WeatherData> getWeather(double lat, double lon) {
    return webClient.get()
        .uri(uriBuilder -> uriBuilder
            .scheme("https").host("api.open-meteo.com").path("/v1/forecast")
            .queryParam("latitude", lat)
            .queryParam("longitude", lon)
            .queryParam("current_weather", true)
            .queryParam("daily", "weathercode,temperature_2m_max,temperature_2m_min")
            .queryParam("timezone", "auto")
            .build())
        .retrieve()
        .bodyToMono(JsonNode.class)
        .map(node -> {
            // Aktuelles Wetter
            JsonNode current = node.path("current_weather");
            int curCode = current.path("weathercode").asInt();
            double curTemp = current.path("temperature").asDouble();

            // 7-Tage-Vorschau extrahieren
            List<DailyForecast> forecasts = new ArrayList<>();
            JsonNode daily = node.path("daily");
            JsonNode times = daily.path("time");
            JsonNode maxTemps = daily.path("temperature_2m_max");
            JsonNode minTemps = daily.path("temperature_2m_min");
            JsonNode codes = daily.path("weathercode");

            for (int i = 0; i < times.size(); i++) {
                int dailyCode = codes.get(i).asInt();
                String rawDate = times.get(i).asText();
    
                forecasts.add(new DailyForecast(
                    convertToWeekday(rawDate), // Hier wird das Datum zum Wochentag
                    maxTemps.get(i).asDouble(),
                    minTemps.get(i).asDouble(),
                    dailyCode,
                    WeatherDescription.getDescriptionByCode(dailyCode)
                ));
            }

            return new WeatherData(
                curTemp, 
                curCode, 
                WeatherDescription.getDescriptionByCode(curCode),
                forecasts
            );
        })
        .onErrorResume(e -> {
            log.error("Weather API Error: {}", e.getMessage());
            return Mono.just(new WeatherData(0.0, 0, "Unavailable", List.of()));
        });
    }

    private String convertToWeekday(String dateString) {
        try {
            LocalDate date = LocalDate.parse(dateString);
         return date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        } catch (Exception e) {
            return dateString; // Fallback auf Originaldatum
        }
    }
}
