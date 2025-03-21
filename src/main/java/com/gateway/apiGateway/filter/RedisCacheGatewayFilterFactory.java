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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

public class RedisCacheGatewayFilterFactory extends AbstractGatewayFilterFactory<RedisCacheGatewayFilterFactory.Config> {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheGatewayFilterFactory.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory;

    public RedisCacheGatewayFilterFactory(ReactiveStringRedisTemplate redisTemplate,
            ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
        this.modifyResponseBodyGatewayFilterFactory = modifyResponseBodyGatewayFilterFactory;
    }

    public static class Config {

        private Long ttl = 10L; // TTL in secondi, valore predefinito di 10 secondi

        public Long getTtl() {
            return ttl;
        }

        public void setTtl(Long ttl) {
            this.ttl = ttl;
        }
    }

    @Override
    public GatewayFilter apply(Config config) {
        return modifyResponseBodyGatewayFilterFactory.apply(Modconfig -> {
            Modconfig.setRewriteFunction(String.class, String.class, (exchange, originalBody) -> {
                String cacheKey = generateCacheKey(exchange);
                logger.info("Applico Cache, key {}", cacheKey);
                return redisTemplate.opsForValue().get(cacheKey)
                        .flatMap(cachedResponse -> {
                            logger.info("Cache HIT per la chiave: {}", cacheKey);
                            // Aggiungi un header alla risposta
                            exchange.getResponse().getHeaders().add("X-Cache-Status", "HIT");
                            return Mono.just(cachedResponse);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            logger.info("Cache MISS per la chiave: {}", cacheKey);
                            return redisTemplate.opsForValue().set(cacheKey, originalBody, config.getTtl())
                                    .thenReturn(originalBody)
                                    .doOnTerminate(() -> {
                                        exchange.getResponse().getHeaders().add("X-Cache-Status", "MISS");
                                    });
                        }));
            });
        });
    }

    private String generateCacheKey(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        String params = exchange.getRequest().getQueryParams().toString();
        return path + ":" + params;
    }
}
