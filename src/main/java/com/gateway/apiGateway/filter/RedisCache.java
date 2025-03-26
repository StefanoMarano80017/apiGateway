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
package com.gateway.apiGateway.filter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.gateway.apiGateway.Factory.RedisCacheGatewayFilterFactory.Config;
import com.gateway.apiGateway.utils.CachingServerHttpResponseDecorator;

import reactor.core.publisher.Mono;

@Component
public class RedisCache implements GatewayFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RedisCache.class);
    /*
     * Cache params 
     */
    private final ReactiveStringRedisTemplate redisTemplate;
    private final Config config;

    /*
     * Ordine d'esecuzione del filtro, 
     * deve essere prima di netty sennò non può eseguire
     */
    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }

    public RedisCache(ReactiveStringRedisTemplate redisTemplate, Config config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String method = exchange.getRequest().getMethod().toString();
        // Se il metodo non è nella configurazione, non usiamo cache        
        if (!config.isCacheable(method)) {
            return chain.filter(exchange);
        }

        Mono<String> cacheKeyMono = generateCacheKey(exchange);
        return cacheKeyMono.flatMap(cacheKey -> {
            CachingServerHttpResponseDecorator cachedResponse = new CachingServerHttpResponseDecorator(
                    exchange.getResponse(), cacheKey, redisTemplate, config.getTtl()
            );

            return redisTemplate.opsForValue().get(cacheKey).flatMap(cachedValue -> {
                logger.info("Cache hit for key: {}", cacheKey);
                return cachedResponse.writeWithCachedResponse(cachedValue);
            }).switchIfEmpty(
                    chain.filter(
                            exchange.mutate().response(cachedResponse).build()
                    )
            );
        });
    }

    private String hashSHA256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Errore nel calcolo dell'hash SHA-256", e);
        }
    }

    private Mono<String> generateCacheKey(ServerWebExchange exchange) {
        String cachePrefix = config.getCachePrefix();
        String path = exchange.getRequest().getURI().getPath();
        /*
         *  scrivo i parametri ordinandoli per chiave -> consistenza genero una chiave sempre uguale per redis 
         */
        String params = exchange
                .getRequest()
                .getQueryParams()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining("&"));
        /* 
        * se è una GET non devo elaborare il body 
         */
        String method = exchange.getRequest().getMethod().toString();
        if ("GET".equalsIgnoreCase(method)) {
            return Mono.just(cachePrefix + path + ":" + params);
        }
        // Per POST e PUT, leggiamo il body per generare un hash
        return exchange.getRequest().getBody()
                .collectList()
                .map(dataBuffers -> {
                    byte[] bodyBytes = dataBuffers.stream()
                            .flatMap(dataBuffer -> {
                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(bytes);
                                return java.util.stream.Stream.of(bytes);
                            })
                            .reduce(new byte[0], (a, b) -> {
                                byte[] combined = new byte[a.length + b.length];
                                System.arraycopy(a, 0, combined, 0, a.length);
                                System.arraycopy(b, 0, combined, a.length, b.length);
                                return combined;
                            });

                    String bodyHash = hashSHA256(new String(bodyBytes, StandardCharsets.UTF_8));
                    return cachePrefix + path + ":" + params + ":" + bodyHash;
                });
    }

}
