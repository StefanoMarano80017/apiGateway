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
package com.gateway.apiGateway.filter.authenticationFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import com.gateway.apiGateway.Factory.AuthenticationFilterGatewayFilterFactory.Config;

import reactor.core.publisher.Mono;

@Component
public class AuthenticationFilter implements GatewayFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);
    private final AuthTokenService authTokenService;

    /*
     * Ordine d'esecuzione del filtro, 
     */
    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 2;
    }

    public AuthenticationFilter(ReactiveStringRedisTemplate redisTemplate,
            Config config, WebClient.Builder webClientBuilder) {
        this.authTokenService = new AuthTokenService(webClientBuilder,
                config.getAuthServiceUrl(),
                config.getCachePrefix(),
                config.getBUFFER_TIME_SECONDS(),
                config.getCACHE_TTL_THRESHOLD(),
                redisTemplate);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String token;
        try {
            token = authTokenService.extractToken(request);
            if (token == null) {
                logger.warn("Token mancante nella richiesta per l'utente: {}", request.getRemoteAddress());
                return unauthorized(exchange);
            }
        } catch (Exception e) {
            logger.error("Errore nell'estrazione del token per l'utente {}", request.getRemoteAddress(), e);
            return unauthorized(exchange);
        }

        return authTokenService.validateToken(token).flatMap(isValid -> {
            if (!isValid) {
                logger.warn("Token non valido ricevuto dalla richiesta: {}", request.getRemoteAddress());
                return unauthorized(exchange);
            }
            // Estrai informazioni dal token e aggiungile all'header
            String userId = authTokenService.extractUserId(token);
            logger.info("Avvio autenticazione per utente {}", userId);

            // Creare un nuovo exchange con la richiesta mutata
            ServerWebExchange mutatedExchange = exchange.mutate()
                                                .request(
                                                    request.mutate()
                                                        .header("X-Authenticated-UserId", userId)
                                                        .build()
                                                )
                                                .build();
            return chain.filter(mutatedExchange);
        }).onErrorResume(e -> {
            logger.error("Errore nella validazione del token: {}", e.getMessage(), e);
            return unauthorized(exchange);
        });
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set("Content-Length", "0"); // Evita che il client aspetti dati
        exchange.getResponse().getHeaders().set("Connection", "close"); // Chiude la connessione
        return exchange.getResponse().setComplete();
    }
}
