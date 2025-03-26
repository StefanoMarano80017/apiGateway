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

package com.gateway.apiGateway.utils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.lang.NonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CachingServerHttpResponseDecorator extends ServerHttpResponseDecorator {

    private static final Logger logger = LoggerFactory.getLogger(CachingServerHttpResponseDecorator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String cacheKey;
    private final Long ttl;
    private final ReactiveStringRedisTemplate redisTemplate;

    public CachingServerHttpResponseDecorator(ServerHttpResponse delegate, String cacheKey,
            ReactiveStringRedisTemplate redisTemplate, Long ttl) {
        super(delegate);
        this.cacheKey = cacheKey;
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    @NonNull
    public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
        if (!(body instanceof Flux)) {
            logger.error("Impossibile salvare la risposta in cache per la chiave: {}", cacheKey);
            return super.writeWith(body);
        }

        DataBufferFactory bufferFactory = getDelegate().bufferFactory();

        return mergeBuffer(body)
                .flatMap(dataBuffer -> {
                    byte[] content = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(content);
                    DataBufferUtils.release(dataBuffer);

                    String responseBody = new String(content, StandardCharsets.UTF_8);
                    String contentType = getContentTypeOrDefault();

                    Map<String, String> cacheEntry = Map.of(
                            "contentType", contentType,
                            "body", responseBody
                    );

                    return cacheResponse(cacheEntry)
                            .onErrorResume(e -> {
                                logger.error("Errore nella serializzazione della risposta per la cache", e);
                                return Mono.empty();
                            })
                            .then(super.writeWith(Mono.just(bufferFactory.wrap(content))));
                });
    }

    private Mono<DataBuffer> mergeBuffer(@NonNull Publisher<? extends DataBuffer> body) {
        return DataBufferUtils.join(Flux.from(body))
                .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
    }

    private String getContentTypeOrDefault() {
        MediaType contentType = getDelegate().getHeaders().getContentType();
        return contentType != null ? contentType.toString() : "application/json";
    }

    private Mono<Boolean> cacheResponse(Map<String, String> cacheEntry) {
        try {
            String jsonToCache = objectMapper.writeValueAsString(cacheEntry);
            return redisTemplate.opsForValue().set(cacheKey, jsonToCache, Duration.ofSeconds(ttl));
        } catch (JsonProcessingException e) {
            logger.error("Errore nella conversione in JSON per la cache", e);
            return Mono.just(false);
        }
    }

    public Mono<Void> writeWithCachedResponse(String cachedValue) {
        if (cachedValue == null) {
            logger.warn("Nessuna risposta trovata in cache per la chiave: {}", cacheKey);
            return Mono.empty();
        }

        return Mono.fromCallable(() -> objectMapper.readValue(cachedValue, new TypeReference<Map<String, String>>() {}))
            .flatMap(cacheEntry -> {
                /*
                * Recupero body e content type dalla cache 
                */
                String contentType = cacheEntry.get("contentType");
                String body = cacheEntry.get("body");
                /*
                * Setto il content type
                */
                getDelegate().getHeaders().setContentType(MediaType.parseMediaType(contentType));
                DataBuffer buffer = getDelegate().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
                return super.writeWith(Mono.just(buffer));
            }).onErrorResume(e -> {
                logger.error("Errore nel parsing della risposta cache", e);
                return Mono.empty();
            });
    }
}
