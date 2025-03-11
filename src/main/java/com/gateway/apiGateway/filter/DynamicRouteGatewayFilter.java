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

import com.gateway.apiGateway.route_manager.model.RouteConfig;
import com.gateway.apiGateway.route_manager.service.RouteConfigService;

import reactor.core.publisher.Mono;

@Component
public class DynamicRouteGatewayFilter implements GlobalFilter, Ordered {

    private final RouteConfigService routeService;

    @Autowired
    public DynamicRouteGatewayFilter(RouteConfigService routeService) {
        this.routeService = routeService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();
        String method = exchange.getRequest().getMethod().toString(); 
        // Trova la rotta corrispondente in MongoDB
        RouteConfig route = routeService.getRouteByPathAndMethod(path, method);  // Usa il modello Route MongoDB
        if (route != null) {
            // Se troviamo la rotta, passiamo il controllo al prossimo filtro
            return chain.filter(exchange);
        } else {
            // Se non c'è una rotta corrispondente, restituiamo un errore 404
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return Mono.empty();
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
