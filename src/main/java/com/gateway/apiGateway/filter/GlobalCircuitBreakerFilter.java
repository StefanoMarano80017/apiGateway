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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.gateway.apiGateway.route_manager.repository.ServiceDefinitionRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import reactor.core.publisher.Mono;

@Component
public class GlobalCircuitBreakerFilter implements GlobalFilter, Ordered {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ServiceDefinitionRepository serviceRepository;

    @Autowired
    public GlobalCircuitBreakerFilter(CircuitBreakerRegistry circuitBreakerRegistry, 
                                        ServiceDefinitionRepository serviceRepository) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.serviceRepository = serviceRepository;
    }

    /*
     * recupera o crea una configurazione CB
     */
    public Mono<CircuitBreaker> getCircuitBreaker(String serviceId) {
        /*
         * Recupero i dati del servizio 
         */
        return serviceRepository.findById(serviceId).flatMap(service -> {
            /*
            * Se non c'è il config, uso le impostazioni di default
             */
            if (service.getCircuitBreakerConfig() == null) {
                return Mono.just(circuitBreakerRegistry.circuitBreaker(serviceId));
            }
            CircuitBreakerConfig config = service.getCircuitBreakerConfig();
            return Mono.just(circuitBreakerRegistry.circuitBreaker(serviceId, config));
        });
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Esegui la logica del Circuit Breaker su ogni richiesta
        String serviceId = "prova";
        return getCircuitBreaker(serviceId).flatMap( cb -> {
            if(cb.getState() ==  CircuitBreaker.State.OPEN){
                 // Se il Circuit Breaker è aperto, restituisci una risposta di fallback
                 exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                 return exchange.getResponse().setComplete();
            }
            // In caso contrario, prosegui con la catena dei filtri
            return chain.filter(exchange);
        });
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE; //è il primo filtro ad esser valutato 
    }
}
