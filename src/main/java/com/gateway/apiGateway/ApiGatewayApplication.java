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
package com.gateway.apiGateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.gateway.apiGateway.route_manager.service.CustomRouteLocator;
import com.gateway.apiGateway.route_manager.service.RouteLoaderService;

@SpringBootApplication(scanBasePackages = "com.gateway.apiGateway")
public class ApiGatewayApplication implements CommandLineRunner {

    private final RouteLoaderService routeLoader;
    private final CustomRouteLocator customRouteLocator;

    @Autowired
    public ApiGatewayApplication(RouteLoaderService routeLoader, CustomRouteLocator customRouteLocator){
        this.routeLoader = routeLoader;
        this.customRouteLocator  = customRouteLocator;
    }

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // Inserisci le rotte all'avvio
        System.out.println("Carico route da json");
        routeLoader.loadRoutes();
        routeLoader.loadServices();
        customRouteLocator.getRoutes();
    }
}
