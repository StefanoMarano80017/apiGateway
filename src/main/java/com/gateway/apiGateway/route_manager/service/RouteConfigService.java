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
package com.gateway.apiGateway.route_manager.service;

import com.gateway.apiGateway.route_manager.model.RouteConfig;
import com.gateway.apiGateway.route_manager.repository.RouteConfigRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class RouteConfigService {

    private final RouteConfigRepository routeRepository;

    @Autowired
    public RouteConfigService(RouteConfigRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    // Carica tutte le rotte dal database
    public List<RouteConfig> loadRoutes() {
        return routeRepository.findAll();
    }

    // Carica una rotta specifica in base al percorso e al metodo
    public RouteConfig getRouteByPathAndMethod(String path, String method) {
        return routeRepository.findByPathAndMethod(path, method);
    }

}
