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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HexFormat;

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
import org.springframework.http.server.reactive.ServerHttpRequest;

import com.gateway.apiGateway.Factory.RedisCacheFilterGatewayFilterFactory.Config;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class RedisCacheFilter implements GatewayFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheFilter.class);
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

    public RedisCacheFilter(ReactiveStringRedisTemplate redisTemplate, Config config) {
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

        return generateCacheKey(exchange).flatMap(tuple -> {
            ServerWebExchange newExchange = tuple.getT1();
            String cacheKey = tuple.getT2();

            CachingServerHttpResponseDecorator cachedResponse = new CachingServerHttpResponseDecorator(
                newExchange.getResponse(), cacheKey, redisTemplate, config.getTtl()
            );

            return redisTemplate.opsForValue().get(cacheKey)
            .flatMap(cachedValue -> {
                logger.info("Cache hit for key: {}", cacheKey);
                return cachedResponse.writeWithCachedResponse(cachedValue);
            })
            .switchIfEmpty(Mono.defer(() -> {
                logger.info("Cache miss for key: {}", cacheKey);
                return chain.filter(newExchange.mutate().response(cachedResponse).build());
            }));
        });
    }

    // Metodo per calcolare l'hash a partire da un array di byte


    private String hashSHA256Bytes(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);  // Usa HexFormat per la conversione
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Errore nel calcolo dell'hash SHA-256", e);
        }
    }

    private Mono<Tuple2<ServerWebExchange, String>> generateCacheKey(ServerWebExchange exchange) {
        String cachePrefix = config.getCachePrefix();
        String path = exchange.getRequest().getURI().getPath();
    
        // Parametri ordinati per consistenza nella cache
        String params = exchange.getRequest().getQueryParams()
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
            .collect(Collectors.joining("&"));
    
        String method = exchange.getRequest().getMethod().toString();
        if ("GET".equalsIgnoreCase(method)) {
            return Mono.just(Tuples.of(exchange, cachePrefix + path + ":" + params));
        }
    
        // Per POST e PUT, gestiamo il body con il decoratore
        return cacheAndHashRequestBody(exchange).map(tuple -> {
                ServerWebExchange newExchange = tuple.getT1();
                String bodyHash = tuple.getT2();
                String cacheKey = cachePrefix + path + ":" + params + ":" + bodyHash;
                return Tuples.of(newExchange, cacheKey);
        });
    }
    

    private Mono<Tuple2<ServerWebExchange, String>> cacheAndHashRequestBody(ServerWebExchange exchange) {
        return DataBufferUtils
                .join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer); // Libera il buffer per evitare memory leak

                    String bodyHash = hashSHA256Bytes(bodyBytes);

                    // Creiamo il decoratore della richiesta con il body letto
                    ServerHttpRequest newRequest = new CachedBodyRequestDecorator(exchange.getRequest(), bodyBytes);
                    ServerWebExchange newExchange = exchange.mutate().request(newRequest).build();

                    return Mono.just(Tuples.of(newExchange, bodyHash));
                });
    }

}
