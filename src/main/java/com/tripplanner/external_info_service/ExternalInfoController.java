package com.tripplanner.external_info_service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanner.external_info_service.dto.ExternalInfoDto.GeocodingResult;
import com.tripplanner.external_info_service.dto.ExternalInfoDto.TripExternalInfo;
import com.tripplanner.external_info_service.ApiProxyServices.GeocodingApi;
import com.tripplanner.external_info_service.ApiProxyServices.TravelWarningApi;
import com.tripplanner.external_info_service.ApiProxyServices.WeatherApi;
import com.tripplanner.external_info_service.ApiProxyServices.ViatorApi;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/details")
@RequiredArgsConstructor
public class ExternalInfoController {

    private final GeocodingApi geocodingApi;
    private final TravelWarningApi travelWarningApi;
    private final WeatherApi weatherApi;
    private final ViatorApi viatorApi;

    @GetMapping("/search")
    public Mono<ResponseEntity<GeocodingResult>> search(@RequestParam String q) {
    return geocodingApi.searchLocation(q)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
}

    @GetMapping
    public Mono<TripExternalInfo> getAllDetails(
            @RequestParam String countryCode,
            @RequestParam String location,
            @RequestParam double lat,
            @RequestParam double lon) {

        return Mono.zip(
                travelWarningApi.getTravelWarning(countryCode),
                weatherApi.getWeather(lat, lon),
                viatorApi.getViatorTours(location, countryCode)
        ).map(tuple -> new TripExternalInfo(
            tuple.getT1(), 
            tuple.getT2(), 
            tuple.getT3()
        ));
    }
}