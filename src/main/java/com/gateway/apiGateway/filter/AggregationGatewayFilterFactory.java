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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AggregationGatewayFilterFactory extends AbstractGatewayFilterFactory<AggregationGatewayFilterFactory.Config> {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(AggregationGatewayFilterFactory.class);


    public AggregationGatewayFilterFactory(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        super(Config.class);
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public static class Config {

        private Long Timeout=10L; //defualt 10 secondi di timeout
        private List<String> services;

        public List<String> getServices() {
            return services;
        }

        public void setServices(List<String> services) {
            this.services = services;
        }

        public Long getTimeout() {
            return Timeout;
        }

        public void setTimeout(Long Timeout) {
            this.Timeout = Timeout;
        }
    }

    /*
     * Chiamata GET a un servizio 
     */
    public Mono<String> AsycServiceCall(String serviceUrl, WebClient webClient, Long timeout) {
        logger.info("Chiamata al servizio: {}", serviceUrl);
        return webClient.get()
                .uri(serviceUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeout))
                .doOnSuccess(
                    response -> logger.info("Risposta ricevuta da {}: {}", serviceUrl, response)
                )
                .doOnError(
                    error -> logger.error("Errore nella chiamata a {}: {}", serviceUrl, error.getMessage())
                )
                .onErrorReturn("ERROR fallback Value");
    }

    /*
     *  Esegui tutte le richieste in parallelo e raccogli i risultati 
     */
    public Mono<Map<String, String>> AggregateCalls(List<String> urls, Long timeout) {
        WebClient webClient = webClientBuilder.build();
        return Flux.fromIterable(urls)
                    //Esegui le varie chiamate in parallelo 
                    .flatMap(url -> 
                        AsycServiceCall(url, webClient, timeout)
                        .map(response -> Map.entry(url, response)) // Associa l'URL alla risposta
                    )
                    // Raccogli tutte le risposte in una mappa e restituisci il Mono
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue);       
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            if (config.getServices() == null || config.getServices().isEmpty()) {
                logger.warn("Nessun servizio configurato per l'aggregazione");
                return chain.filter(exchange);
            }

            ServerHttpResponse response = exchange.getResponse();
            return AggregateCalls(config.getServices(), config.getTimeout()).flatMap(aggregatedData -> {
                try {
                    byte[] bytes = objectMapper.writeValueAsBytes(aggregatedData);
                    DataBuffer buffer = response.bufferFactory().wrap(bytes);
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    //Header per il Debug
                    response.getHeaders().add("X-Aggregation-Filter", "Applied");
                    return response.writeWith(Mono.just(buffer));
                } catch (JsonProcessingException e) {
                    return Mono.error(e);
                }
            });
        };
    }
}
