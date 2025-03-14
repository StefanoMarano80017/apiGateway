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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.gateway.apiGateway.utils.JwtUtil;

import reactor.core.publisher.Mono;

@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil JwtUtil;
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Autowired
    public AuthenticationFilter(JwtUtil JwtUtil) {
        this.JwtUtil = JwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        /*
         * Estraggo il token dal header della richiesta 
         */
        String token;
        try{
            token = JwtUtil.extractToken(request);
            if (token == null) {
                logger.warn("Token mancante nella richiesta per l'utente: {}", request.getRemoteAddress());
                return unauthorized(exchange);
            }
        }catch(Exception e){
            logger.error("Errore nell'estrazione del token per l'utente {}", request.getRemoteAddress(), e);
            return unauthorized(exchange); 
        }

        return JwtUtil.validateToken(token).flatMap(isValid -> {
            if (!isValid) {
                logger.warn("Token non valido ricevuto dalla richiesta: {}", request.getRemoteAddress());
                return unauthorized(exchange);
            }
            // Estrai le informazioni dal token e propagale ai microservizi
            String userId = JwtUtil.extractUserId(token);
            logger.info("Token valido per l'utente: {} - UserId: {}", request.getRemoteAddress(), userId);

            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header("X-Authenticated-UserId", userId)
                    .build();
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        }).onErrorResume(e -> {
            logger.error("Errore nella validazione del token: {}", e.getMessage(), e);
            return unauthorized(exchange);
        });
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;  
    }
}
