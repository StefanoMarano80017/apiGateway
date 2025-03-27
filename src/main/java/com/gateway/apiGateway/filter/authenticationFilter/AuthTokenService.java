/*
 *   Copyright (c) 2025 Stefano Marano https://github.com/StefanoMarano80017
 *   All rights reserved.

 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at

 *   http://www.apache.org/licenses/LICENSE-2.0

 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.gateway.apiGateway.filter.authenticationFilter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

public class AuthTokenService {

    private final WebClient webClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final String cachePrefix;
    private final long BUFFER_TIME_SECONDS;  // Durata rimanente minima del token per essere cachato 
    private final long CACHE_TTL_THRESHOLD;  // Soglia da levare al tempo rimanente del token per avere un ttl

    /*
    *     LOGICA DI EXPIRE CONTROLL: 
     *    Token per essere cachato => Durata token > BUFFER_TIME_SECONDS 
     *    TTL CACHE TOKEN = DURATA - CACHE_TTL_THRESHOLD
     */

    public AuthTokenService(WebClient.Builder webClientBuilder,
                            String authServiceUrl,
                            String cachePrefix,
                            long BUFFER_TIME_SECONDS,
                            long CACHE_TTL_THRESHOLD,
                            ReactiveStringRedisTemplate redisTemplate) {

        this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
        this.redisTemplate = redisTemplate;
        this.cachePrefix = cachePrefix;
        this.BUFFER_TIME_SECONDS = BUFFER_TIME_SECONDS;
        this.CACHE_TTL_THRESHOLD = CACHE_TTL_THRESHOLD;
    }

    public String extractToken(ServerHttpRequest request) {
        return Optional.ofNullable(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .map(header -> header.replace("Bearer ", ""))
                .orElse(null);
    }

    public Mono<Boolean> validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return Mono.just(false);
        }
        String userId = extractUserId(token);
        String cacheKey = cachePrefix + userId;

        return getTokenFromCache(cacheKey)
                .hasElement()
                .flatMap(isCached -> isCached ? Mono.just(true) : validateAndSave(token));
    }

    private Mono<Boolean> validateCall(String token) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.queryParam("jwt", token).build())
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorReturn(false);
    }

    private Mono<Boolean> validateAndSave(String token) {
        return validateCall(token).flatMap(valid -> {
            if (!valid) return Mono.just(false);
            return extractExpiration(token)
                    .map(expTime -> isCachable(expTime).flatMap(isCachable -> {
                        if (!isCachable) return Mono.just(true);
                        String userId = extractUserId(token);
                        return cacheToken(token, userId, expTime).thenReturn(true);
                    }))
                    .orElse(Mono.just(true));
        });
    }
    
    private Mono<Boolean> isCachable(Long expirationTime) {
        long currentTime = Instant.now().getEpochSecond();
        System.out.println("current time: " + currentTime);
        return Mono.just(expirationTime - currentTime > BUFFER_TIME_SECONDS);
    }

    private Mono<Boolean> cacheToken(String token, String userId, Long expirationTime) {
        long ttl = Math.max(expirationTime - Instant.now().getEpochSecond() - CACHE_TTL_THRESHOLD, 1);
        System.out.println("ttl key: " + ttl);
        return redisTemplate.opsForValue()
                .set(cachePrefix + userId, token, Duration.ofSeconds(ttl))
                .thenReturn(true);
    }

    private Mono<String> getTokenFromCache(String cacheKey) {
        return redisTemplate.opsForValue().get(cacheKey);
    }

    private Optional<Long> extractExpiration(String jwt) {
        return extractClaim(jwt, "exp").map(Long::parseLong);
    }

    public String extractUserId(String jwt) {
        return extractClaim(jwt, "userId").orElse(null);
    }

    private static Optional<String> extractClaim(String jwt, String claimName) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return Optional.empty();

            byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
            String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);
            Map<String, Object> payloadMap = OBJECT_MAPPER.readValue(decodedJson, Map.class);
            
            return Optional.ofNullable(payloadMap.get(claimName)).map(Object::toString);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
