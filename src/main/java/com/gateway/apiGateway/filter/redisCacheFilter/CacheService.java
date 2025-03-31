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

import java.time.Duration;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

public class CacheService {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final ReactiveStringRedisTemplate redisTemplate;

    public CacheService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> save(String cacheKey, CachedResponse cachedResponse, Long ttl) {
        try {
            String jsonToCache = objectMapper.writeValueAsString(cachedResponse);
            return redisTemplate.opsForValue().setIfAbsent(cacheKey, jsonToCache, Duration.ofSeconds(ttl));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    public Mono<CachedResponse> get(String cacheKey) {
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(value -> {
                    try {
                        CachedResponse cachedResponse = objectMapper.readValue(value, CachedResponse.class);
                        return Mono.just(cachedResponse);
                    } catch (JsonProcessingException e) {
                        return Mono.error(e);
                    }
                });
    }
}
