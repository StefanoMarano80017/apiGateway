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
package com.gateway.apiGateway.Factory;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.gateway.apiGateway.filter.redisCacheFilter.RedisCacheFilter;

public class RedisCacheFilterGatewayFilterFactory extends AbstractGatewayFilterFactory<RedisCacheFilterGatewayFilterFactory.Config> {

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisCacheFilterGatewayFilterFactory(ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new RedisCacheFilter(redisTemplate, config);
    }

    public static class Config {
        /*
         * Valori di default 
         */
        private Long ttl = 10L;
        private String cachePrefix = "DataCache:";
        private Set<String> methods = Set.of("GET"); // solo GET

        public Long getTtl() {
            return ttl;
        }

        public void setTtl(Long ttl) {
            this.ttl = ttl;
        }

        public String getCachePrefix() {
            return cachePrefix;
        }

        public void setCachePrefix(String cachePrefix) {
            this.cachePrefix = cachePrefix;
        }

        public Set<String> getMethods() {
            return methods;
        }

        public void setMethods(String methods) {
            this.methods = Stream.of(methods.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
        }

        public boolean isCacheable(String method) {
            return methods.contains(method.toUpperCase());
        }

    }
}
