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
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.gateway.apiGateway.filter.AuthenticationFilter;
import com.gateway.apiGateway.filter.LoggingFilter;
import com.gateway.apiGateway.filter.RedisCacheGatewayFilterFactory;
import com.gateway.apiGateway.utils.JwtUtil;

@Configuration
public class FilterConfig {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory;


    @Autowired
    public FilterConfig(ReactiveStringRedisTemplate redisTemplate, 
                        ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory){
        this.redisTemplate = redisTemplate;
        this.modifyResponseBodyGatewayFilterFactory = modifyResponseBodyGatewayFilterFactory;
    }

    @Bean
    public LoggingFilter loggingFilter() {
        return new LoggingFilter();
    }

    @Bean
    public AuthenticationFilter AuthFilter(){
        JwtUtil jwtUtil = new JwtUtil(null);
        return new AuthenticationFilter(jwtUtil);
    }

    @Bean 
    public GatewayFilterFactory<RedisCacheGatewayFilterFactory.Config> RedisCache(){
        return new RedisCacheGatewayFilterFactory(redisTemplate, modifyResponseBodyGatewayFilterFactory);
    }

}
