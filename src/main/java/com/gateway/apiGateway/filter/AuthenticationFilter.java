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
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.gateway.apiGateway.utils.JwtUtil;

import reactor.core.publisher.Mono;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final JwtUtil jwtUtil;
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Autowired
    public AuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public static class Config {
        // Configurazioni future se necessarie
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String token;
            try {
                token = jwtUtil.extractToken(request);
                if (token == null) {
                    logger.warn("Token mancante nella richiesta per l'utente: {}", request.getRemoteAddress());
                    return unauthorized(exchange);
                }
            } catch (Exception e) {
                logger.error("Errore nell'estrazione del token per l'utente {}", request.getRemoteAddress(), e);
                return unauthorized(exchange);
            }

            return jwtUtil.validateToken(token).flatMap(isValid -> {
                if (!isValid) {
                    logger.warn("Token non valido ricevuto dalla richiesta: {}", request.getRemoteAddress());
                    return unauthorized(exchange);
                }
                // Estrai informazioni dal token e aggiungile all'header
                String userId = jwtUtil.extractUserId(token);
                logger.info("Token valido per l'utente: {} - UserId: {}", request.getRemoteAddress(), userId);

                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                        .header("X-Authenticated-UserId", userId)
                        .build();
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            }).onErrorResume(e -> {
                logger.error("Errore nella validazione del token: {}", e.getMessage(), e);
                return unauthorized(exchange);
            });
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

}
