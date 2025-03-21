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

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CachingResponseDecorator extends ServerHttpResponseDecorator {

    private static final Logger logger = LoggerFactory.getLogger(CachingResponseDecorator.class);

    private final @NonNull
    ServerHttpResponse originalResponse;
    private final @NonNull
    String cacheKey;
    private final @NonNull
    ReactiveStringRedisTemplate redisTemplate;
    private final @NonNull
    Duration CACHE_TTL;

    public CachingResponseDecorator(
            @NonNull ServerWebExchange exchange,
            @NonNull String cacheKey,
            @NonNull ReactiveStringRedisTemplate redisTemplate,
            long secondsTTL
    ) {
        super(exchange.getResponse());
        this.originalResponse = exchange.getResponse();
        this.cacheKey = cacheKey;
        this.redisTemplate = redisTemplate;
        this.CACHE_TTL = Duration.ofSeconds(secondsTTL);
        logger.info("Istanzio CachingResponseDecorator per la chiave: {}", cacheKey);
    }

    private Mono<DataBuffer> mergeBuffer(@NonNull org.reactivestreams.Publisher<? extends DataBuffer> body) {
        if (body instanceof Flux) {
            Flux<DataBuffer> fluxBody = Flux.from(body);
            return DataBufferUtils.join(fluxBody);
        } else if (body instanceof Mono) {
            return Mono.from(body);
        }
        return Mono.empty();
    }

    @Override
    @NonNull
    public Mono<Void> writeAndFlushWith(@NonNull Publisher<? extends Publisher<? extends DataBuffer>> body) {
        logger.info("Avvio writeAndFlushWith per key: {}", cacheKey);
        return writeWith(Flux.from(body).flatMapSequential(p -> p));
    }

    @Override
    @NonNull
    public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
        logger.info("Avvio writeWith per key: {}", cacheKey);

        if (!(body instanceof Flux) && !(body instanceof Mono)) {
            logger.info("La chiave non può essere salvata: {}.", cacheKey);
            return super.writeWith(body);
        }

        return mergeBuffer(body).flatMap(dataBuffer -> {
            byte[] content = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(content);
            DataBufferUtils.release(dataBuffer); // Libera il buffer

            String responseBody = new String(content, StandardCharsets.UTF_8);

            // Salva in cache prima di scrivere la risposta
            return redisTemplate.opsForValue()
                .set(this.cacheKey, responseBody, this.CACHE_TTL)
                .doOnSuccess(aVoid -> logger.info("Risposta memorizzata in cache con chiave: {}", cacheKey))
                .doOnError(throwable -> logger.error("Errore durante il salvataggio della risposta nella cache per la chiave: {}", cacheKey, throwable))
                .then(
                        super.writeWith(Mono.just(originalResponse.bufferFactory().wrap(content)))
                );
        });
    }

}
