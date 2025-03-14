/*
 *   Copyright (c) 2025 Stefano Marano https://github.com/StefanoMarano80017
 *   All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.gateway.apiGateway.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Configurazione delle rotte API per il gateway.
 *
 * Questa classe definisce le route utilizzando il paradigma **funzionale
 * reattivo** di Spring WebFlux. Invece di usare i tradizionali controller
 * annotati con `@RestController`, utilizza `RouterFunctions` per mappare le
 * richieste a specifici handler.
 */
@Configuration
public class ApiRouteRouter {

    private final Logger log = LoggerFactory.getLogger(ApiRouteRouter.class);

    /**
     * Definisce le route del servizio API per la gestione dinamica delle rotte
     * del gateway.
     *
     * @param apiRouteHandler L'handler che gestisce la logica delle richieste.
     * @return Una `RouterFunction<ServerResponse>` che instrada le richieste
     * agli handler appropriati.
     */
    @Bean
    public RouterFunction<ServerResponse> route(ApiRouteHandler apiRouteHandler) {
        return RouterFunctions
                .route(POST("/routes").and(accept(MediaType.APPLICATION_JSON)), request -> {
                    log.info("Received POST /routes");
                    return apiRouteHandler.create(request);
                })
                .andRoute(GET("/routes/{routeId}").and(accept(MediaType.APPLICATION_JSON)), request -> {
                    log.info("Received GET /routes/{}", request.pathVariable("routeId"));
                    return apiRouteHandler.getById(request);
                })
                .andRoute(GET("/routes/refresh-routes").and(accept(MediaType.APPLICATION_JSON)), request -> {
                    log.info("Received GET /routes/refresh-routes");
                    return apiRouteHandler.refreshRoutes(request);
                });
    }
}
