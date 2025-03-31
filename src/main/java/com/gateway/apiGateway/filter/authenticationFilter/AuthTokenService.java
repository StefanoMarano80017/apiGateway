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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

public class AuthTokenService {

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenService.class);
    
    private final WebClient webClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final String cachePrefix;
    private final long BUFFER_TIME_SECONDS;
    private final long CACHE_TTL_THRESHOLD;

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
        String token = Optional.ofNullable(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .map(header -> header.replace("Bearer ", ""))
                .orElse(null);
        logger.debug("Estratto token: {}", token);
        return token;
    }

    public Mono<Boolean> validateToken(String token) {
        if (token == null || token.isEmpty()) {
            logger.warn("Token assente o vuoto");
            return Mono.just(false);
        }
        String userId = extractUserId(token);
        String cacheKey = cachePrefix + userId;
        logger.info("Verifica token per userId: {}", userId);

        return getTokenFromCache(cacheKey)
                .hasElement()
                .flatMap(isCached -> {
                    if (isCached) {
                        logger.info("Token trovato in cache per userId: {}", userId);
                    } else {
                        logger.info("Token non in cache, validazione remota per userId: {}", userId);
                    }
                    return isCached ? Mono.just(true) : validateAndSave(token);
                })
                .switchIfEmpty(Mono.error(new IllegalStateException("validateToken ha restituito un Mono vuoto")));
    }

    private Mono<Boolean> validateCall(String token) {
        logger.debug("Chiamata a servizio di validazione per token");
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.queryParam("jwt", token).build())
                .retrieve()
                .bodyToMono(Boolean.class)
                .doOnError(e -> logger.error("Errore durante la validazione del token", e))
                .defaultIfEmpty(false)       // Assicura che un Mono vuoto venga convertito in `false`
                .onErrorReturn(false);  // Gestisce eventuali errori restituendo `false`
    }

    private Mono<Boolean> validateAndSave(String token) {
        return validateCall(token).flatMap(valid -> {
            if (!valid) {
                logger.warn("Token non valido");
                return Mono.just(false);
            }
            return extractExpiration(token)
                    .map(expTime -> isCachable(expTime).flatMap(isCachable -> {
                        if (!isCachable) {
                            logger.info("Token valido ma non cacheabile");
                            return Mono.just(true);
                        }
                        String userId = extractUserId(token);
                        return cacheToken(token, userId, expTime).thenReturn(true);
                    }))
                    .orElse(Mono.just(true));
        });
    }
    
    private Mono<Boolean> isCachable(Long expirationTime) {
        long currentTime = Instant.now().getEpochSecond();
        boolean cachable = expirationTime - currentTime > BUFFER_TIME_SECONDS;
        logger.debug("Il token è cacheabile: {}", cachable);
        return Mono.just(cachable);
    }

    private Mono<Boolean> cacheToken(String token, String userId, Long expirationTime) {
        long ttl = Math.max(expirationTime - Instant.now().getEpochSecond() - CACHE_TTL_THRESHOLD, 1);
        logger.info("Caching token per userId: {}, TTL: {} secondi", userId, ttl);
        return redisTemplate.opsForValue()
                .set(cachePrefix + userId, token, Duration.ofSeconds(ttl))
                .thenReturn(true);
    }

    private Mono<String> getTokenFromCache(String cacheKey) {
        logger.debug("Recupero token dalla cache per chiave: {}", cacheKey);
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
            
            String claimValue = Optional.ofNullable(payloadMap.get(claimName)).map(Object::toString).orElse(null);
            logger.debug("Claim '{}' estratto dal token: {}", claimName, claimValue);
            return Optional.ofNullable(claimValue);
        } catch (Exception e) {
            logger.error("Errore nell'estrazione del claim '{}' dal token", claimName, e);
            return Optional.empty();
        }
    }
}