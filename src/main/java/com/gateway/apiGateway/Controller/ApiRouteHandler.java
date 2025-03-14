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
package com.gateway.apiGateway.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.gateway.apiGateway.route_manager.model.RouteDefinitionEntity;
import com.gateway.apiGateway.route_manager.service.GatewayRouteRefresher;
import com.gateway.apiGateway.route_manager.service.RouteService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Component
public class ApiRouteHandler {

    private final RouteService routeService;
    private final GatewayRouteRefresher gatewayRoutesRefresher;

    public Mono<ServerResponse> create(ServerRequest serverRequest) {
        Mono<RouteDefinitionEntity> apiRoute = serverRequest.bodyToMono(RouteDefinitionEntity.class);
        return apiRoute.flatMap(route -> ServerResponse.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(routeService.create(route), RouteDefinitionEntity.class));
    }

    public Mono<ServerResponse> getById(ServerRequest serverRequest) {
        final String apiId = serverRequest.pathVariable("routeId");
        Mono<RouteDefinitionEntity> apiRoute = routeService.getById(apiId);

        return apiRoute.flatMap(
            route -> ServerResponse.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).bodyValue(route)
        ).switchIfEmpty(
            ServerResponse.status(HttpStatus.NOT_FOUND).build()
        );
    }

    public Mono<ServerResponse> refreshRoutes(ServerRequest serverRequest) {
        gatewayRoutesRefresher.refreshRoutes();
        return ServerResponse.ok().bodyValue("Routes reloaded successfully");
    }
}
