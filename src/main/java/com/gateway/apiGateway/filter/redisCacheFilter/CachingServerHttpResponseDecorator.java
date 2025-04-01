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
package com.gateway.apiGateway.filter.redisCacheFilter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.lang.NonNull;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CachingServerHttpResponseDecorator extends ServerHttpResponseDecorator {

    private static final Logger logger = LoggerFactory.getLogger(CachingServerHttpResponseDecorator.class);

    private final String cacheKey;
    private final Long ttl;
    private final CacheService cacheService;

    public CachingServerHttpResponseDecorator(ServerHttpResponse delegate, String cacheKey,
            ReactiveStringRedisTemplate redisTemplate, Long ttl) {
        super(delegate);
        this.cacheKey = cacheKey;
        this.ttl = ttl;
        this.cacheService = new CacheService(redisTemplate);
    }

    @Override
    @NonNull
    public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
        if (!(body instanceof Flux)) {
            logger.error("Impossibile salvare la risposta in cache per la chiave: {}", cacheKey);
            return super.writeWith(body);
        }

        HttpStatusCode statusCode = getStatusCode();
        if (statusCode != null && !statusCode.is2xxSuccessful()) {
            return super.writeWith(body);
        }

        // Processa e cache la risposta
        return processAndCacheResponse(body);
    }

    private Mono<Void> processAndCacheResponse(Publisher<? extends DataBuffer> body) {
        return aggregateBody(body)
                .doOnNext(content -> {  // Esegue in background senza bloccare
                    CreateCachedResponse(content)
                    .flatMap(cachedResponse -> cacheService.save(cacheKey, cachedResponse, ttl))
                    .doOnError(e -> logger.error("Errore nella serializzazione della risposta per la cache", e))
                    .subscribe();
                })
                .flatMap(content -> {
                    // Crea un nuovo DataBuffer per la risposta
                    DataBuffer newBuffer = getDelegate().bufferFactory().wrap(content);
                    return super.writeWith(Flux.just(newBuffer));
                });
    }

    private Mono<CachedResponse> CreateCachedResponse(byte[] content) {
        String responseBody = new String(content, StandardCharsets.UTF_8);
        Map<String, List<String>> headersMap = extractHeaders();
        String status = extractStatusCode().toString();
        return Mono.just(
                new CachedResponse(responseBody, headersMap, status)
        );
    }

    public Mono<Void> writeWithCachedResponse(String cachedValue) {
        if (cachedValue == null) {
            logger.warn("Nessuna risposta trovata in cache per la chiave: {}", cacheKey);
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(cachedValue, CachedResponse.class);
        })
        .flatMap(cachedResponse -> {
            // Imposta gli header dalla cache
            cachedResponse.getHeaders().forEach((key, valueList) -> {
                getDelegate().getHeaders().put(key, new ArrayList<>(valueList));
            });
            
            // imposta stato HTTP
            HttpStatusCode statusCode = HttpStatus.valueOf(cachedResponse.getStatusCode());
            getDelegate().setStatusCode(statusCode);

            // Crea DataBuffer per il body dalla cache
            DataBuffer buffer = getDelegate().bufferFactory()
                    .wrap(cachedResponse.getBody().getBytes(StandardCharsets.UTF_8));
            return super.writeWith(Mono.just(buffer));
        })
        .onErrorResume(e -> {
            logger.error("Errore nel parsing della risposta cache", e);
            return Mono.empty();
        });
    }

    private Mono<byte[]> aggregateBody(Publisher<? extends DataBuffer> body) {
        return DataBufferUtils.join(Flux.from(body))
                .flatMap(buffer -> {
                    try {
                        byte[] content = new byte[buffer.readableByteCount()];
                        buffer.read(content);
                        return Mono.just(content);
                    } finally {
                        DataBufferUtils.release(buffer);  // Rilascia il buffer per evitare memory leak
                    }
                });
    }

    private Map<String, List<String>> extractHeaders() {
        Map<String, List<String>> headersMap = new HashMap<>();
        getDelegate().getHeaders().forEach(
                (key, valueList) -> headersMap.put(key, new ArrayList<>(valueList))
        );
        return headersMap;
    }

    /**
     * Estrae lo status code dalla response delegata.
     *
     * @return lo status code corrente, oppure null se non è stato ancora impostato
     */
    public HttpStatusCode extractStatusCode() {
        // Accediamo al delegate per ottenere lo status code corrente
        return getDelegate().getStatusCode();
    }
}
