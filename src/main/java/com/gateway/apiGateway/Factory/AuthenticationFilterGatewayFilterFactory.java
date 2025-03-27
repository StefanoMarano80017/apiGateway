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

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import com.gateway.apiGateway.filter.authenticationFilter.AuthenticationFilter;

/*
 * Spring cerca automaticamente un filtro con il nome "AuthenticationFilter", 
 * rimuovendo il suffisso "GatewayFilterFactory".
 */
public class AuthenticationFilterGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthenticationFilterGatewayFilterFactory.Config>{

    private final ReactiveStringRedisTemplate redisTemplate;
    private final WebClient.Builder webClientBuilder;

    public AuthenticationFilterGatewayFilterFactory(ReactiveStringRedisTemplate redisTemplate, 
                                             WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new AuthenticationFilter(redisTemplate, config, webClientBuilder);
    }

    public static class Config {
        /*
         * Valori di default 
         */
        private String cachePrefix = "AuthCache";
        private String authServiceUrl = "http://t23-controller:8082/validateToken"; 
        private long BUFFER_TIME_SECONDS = 600;  // Durata rimanente minima del token per essere cachato 
        private long CACHE_TTL_THRESHOLD = 60;   // Soglia da levare al tempo rimanente del token per avere un ttl 

        public String getCachePrefix() {
            return cachePrefix;
        }

        public void setCachePrefix(String cachePrefix){
            this.cachePrefix = cachePrefix;
        }
        
        public long getBUFFER_TIME_SECONDS() {
            return BUFFER_TIME_SECONDS;
        }

        public void setBUFFER_TIME_SECONDS(long BUFFER_TIME_SECONDS){
            this.BUFFER_TIME_SECONDS = BUFFER_TIME_SECONDS;
        }

        public long getCACHE_TTL_THRESHOLD() {
            return CACHE_TTL_THRESHOLD;
        }

        public void setCACHE_TTL_THRESHOLD(long CACHE_TTL_THRESHOLD){
            this.CACHE_TTL_THRESHOLD = CACHE_TTL_THRESHOLD;
        }

        public String getAuthServiceUrl() {
            return authServiceUrl;
        }

        public void setAuthServiceUrl(String authServiceUrl) {
            this.authServiceUrl = authServiceUrl;
        }
    }
}
