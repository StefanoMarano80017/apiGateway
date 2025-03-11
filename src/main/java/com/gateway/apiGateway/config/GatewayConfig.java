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
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gateway.apiGateway.route_manager.service.RouteConfigService;

@Configuration
public class GatewayConfig {

    private final RouteConfigService routeService;

    @Autowired
    public GatewayConfig(RouteConfigService routeService) {
        this.routeService = routeService;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        RouteLocatorBuilder.Builder routeBuilder = builder.routes();
        /*
        *   Per ogni route salvata nel DB
        */
        routeService.loadRoutes().forEach(route -> {
            // Aggiungi ogni rotta dinamicamente
            routeBuilder.route(r -> r
                    .path(route.getPath()) // Percorso della rotta (ad esempio "/users/{id}")
                    .and()
                    .method(route.getMethod()) // Metodo HTTP (GET, POST, ecc.)
                    .uri(route.getTargetService()) // Servizio target (ad esempio "http://localhost:8080")
            );
        });

        return routeBuilder.build();
    }
}
