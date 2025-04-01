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

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.gateway.apiGateway.Factory.RedisCacheFilterGatewayFilterFactory.Config;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class RedisCacheFilter implements GatewayFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheFilter.class);
    private final ReactiveStringRedisTemplate redisTemplate;
    private final Config config;

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }

    public RedisCacheFilter(ReactiveStringRedisTemplate redisTemplate, Config config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String method = exchange.getRequest().getMethod().toString();
        String path = exchange.getRequest().getURI().getPath();
        logger.info("[RedisCacheFilter] Processing request: method={}, path={}", method, path);

        if (!config.isCacheable(method)) {
            logger.info("[RedisCacheFilter] Method {} is not cacheable. Proceeding without cache.", method);
            return chain.filter(exchange);
        }

        return generateCacheKey(exchange).flatMap(tuple -> {
            ServerWebExchange newExchange = tuple.getT1();
            String cacheKey = tuple.getT2();

            logger.info("[RedisCacheFilter] Generated cache key: {}", cacheKey);

            CachingServerHttpResponseDecorator cachedResponse = new CachingServerHttpResponseDecorator(
                    newExchange.getResponse(), cacheKey, redisTemplate, config.getTtl()
            );

            return redisTemplate
                    .hasKey(cacheKey)
                    .flatMap(exists -> {
                        if (exists) {
                            logger.info("[RedisCacheFilter] Cache hit for key: {}", cacheKey);
                            return redisTemplate.opsForValue().get(cacheKey).flatMap(cachedValue -> {
                                return cachedResponse.writeWithCachedResponse(cachedValue);
                            });
                        } else {
                            logger.info("[RedisCacheFilter] Cache miss for key: {}", cacheKey);
                            return chain.filter(newExchange.mutate().response(cachedResponse).build());
                        }
                    })
                    .doOnError(error -> logger.error("[RedisCacheFilter] Error accessing cache: ", error));
        });
    }

    /**
     * Estrae il percorso (path) dalla richiesta.
     *
     * @param exchange il ServerWebExchange corrente
     * @return il path della richiesta
     */
    private String extractPath(ServerWebExchange exchange) {
        return exchange.getRequest().getURI().getPath();
    }

    /**
     * Estrae e formatta i parametri della query ordinandoli per chiave.
     *
     * @param exchange il ServerWebExchange corrente
     * @return una stringa formattata con i parametri
     */
    private String extractQueryParams(ServerWebExchange exchange) {
        return exchange.getRequest().getQueryParams().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private Mono<Tuple2<ServerWebExchange, String>> generateCacheKey(ServerWebExchange exchange) {
        /*
        * Genero le costanti 
         */
        String cachePrefix = config.getCachePrefix();
        String path = extractPath(exchange);
        String params = extractQueryParams(exchange);
        String keyBase = cachePrefix + path + ":" + params;
        // Se la richiesta è di tipo GET, non serve l'hash del body
        if ("GET".equalsIgnoreCase(exchange.getRequest().getMethod().toString())) {
            return Mono.just(Tuples.of(exchange, keyBase));
        }
        // Per altre tipologie, aggiungiamo l'hash del body
        return DataBufferUtils
            .join(exchange.getRequest().getBody())
            .flatMap(dataBuffer -> {
                byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bodyBytes);
                DataBufferUtils.release(dataBuffer);
                // Creiamo il decorator per accedere alla request
                CachedBodyRequestDecorator decoratedRequest = new CachedBodyRequestDecorator(exchange.getRequest(), bodyBytes);
                // Creiamo un nuovo ServerWebExchange con la request decorata
                ServerWebExchange mutatedExchange = exchange.mutate().request(decoratedRequest).build();
                // Combiniamo la key base con l'hash ottenuto dal decorator
                String finalKey = keyBase + ":" + decoratedRequest.getBodyHash();
                return Mono.just(Tuples.of(mutatedExchange, finalKey));
            });
    }

}
