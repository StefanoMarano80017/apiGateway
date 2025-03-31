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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
import org.springframework.http.server.reactive.ServerHttpRequest;
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

            return redisTemplate.hasKey(cacheKey)
                    .flatMap(exists -> {
                        if (exists) {
                            logger.info("[RedisCacheFilter] Cache hit for key: {}", cacheKey);
                            return redisTemplate.opsForValue().get(cacheKey).flatMap(cachedValue ->{
                                return cachedResponse.writeWithCachedResponse(cachedValue);
                            });
                        }else{
                            logger.info("[RedisCacheFilter] Cache miss for key: {}", cacheKey);
                            return chain.filter(newExchange.mutate().response(cachedResponse).build());
                        }
                    })
                    .doOnError(error -> logger.error("[RedisCacheFilter] Error accessing cache: ", error));
        });
    }

    private Mono<Tuple2<ServerWebExchange, String>> generateCacheKey(ServerWebExchange exchange) {
        String cachePrefix = config.getCachePrefix();
        String path = exchange.getRequest().getURI().getPath();

        String params = exchange.getRequest().getQueryParams()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining("&"));

        String method = exchange.getRequest().getMethod().toString();
        logger.info("[RedisCacheFilter] Generating cache key for method={}, path={}, params={}", method, path, params);

        if ("GET".equalsIgnoreCase(method)) {
            return Mono.just(Tuples.of(exchange, cachePrefix + path + ":" + params));
        }

        return cacheAndHashRequestBody(exchange).map(tuple -> {
            ServerWebExchange newExchange = tuple.getT1();
            String bodyHash = tuple.getT2();
            String cacheKey = cachePrefix + path + ":" + params + ":" + bodyHash;
            logger.info("[RedisCacheFilter] Computed cache key with body hash: {}", cacheKey);
            return Tuples.of(newExchange, cacheKey);
        });
    }

    private Mono<Tuple2<ServerWebExchange, String>> cacheAndHashRequestBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer);

                    String bodyHash = hashSHA256Bytes(bodyBytes);
                    logger.info("[RedisCacheFilter] Computed body hash: {}", bodyHash);

                    ServerHttpRequest newRequest = new CachedBodyRequestDecorator(exchange.getRequest(), bodyBytes);
                    ServerWebExchange newExchange = exchange.mutate().request(newRequest).build();
                    return Mono.just(Tuples.of(newExchange, bodyHash));
                })
                .doOnError(error -> logger.error("[RedisCacheFilter] Error hashing request body: ", error));
    }

    private String hashSHA256Bytes(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("[RedisCacheFilter] Error computing SHA-256 hash", e);
            throw new RuntimeException("Error computing SHA-256 hash", e);
        }
    }
}
