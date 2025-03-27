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
package com.gateway.apiGateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import com.gateway.apiGateway.Factory.AuthenticationFilterGatewayFilterFactory;
import com.gateway.apiGateway.Factory.RedisCacheFilterGatewayFilterFactory;
import com.gateway.apiGateway.filter.LoggingFilter;

@Configuration
public class FilterConfig {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final WebClient.Builder webClientBuilder;

    @Autowired
    public FilterConfig(ReactiveStringRedisTemplate redisTemplate, WebClient.Builder webClientBuilder) {
        this.redisTemplate = redisTemplate;
        this.webClientBuilder = webClientBuilder;
    }

    @Bean
    public LoggingFilter loggingFilter() {
        return new LoggingFilter();
    }

    @Bean
    public AuthenticationFilterGatewayFilterFactory authenticationFilterGatewayFilter(){
        return new AuthenticationFilterGatewayFilterFactory(redisTemplate, webClientBuilder);
    }

    @Bean
    public AuthenticationFilterGatewayFilterFactory.Config authenticationFilterConfig(){
        return new AuthenticationFilterGatewayFilterFactory.Config();
    }

    @Bean
    public RedisCacheFilterGatewayFilterFactory redisCacheGatewayFilterFactory() {
        return new RedisCacheFilterGatewayFilterFactory(redisTemplate);
    }

    @Bean
    public RedisCacheFilterGatewayFilterFactory.Config redisCacheConfig() {
        return new RedisCacheFilterGatewayFilterFactory.Config();
    }

}
