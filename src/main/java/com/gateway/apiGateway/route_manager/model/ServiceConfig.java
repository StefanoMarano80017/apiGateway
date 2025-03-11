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

package com.gateway.apiGateway.route_manager.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "services")
public class ServiceConfig {
    
    @Id
    private String id;

    private String name;               // Nome del servizio (es. "user-service")
    private String baseUrl;            // URL del servizio (es. "http://localhost:8081")
    private int timeout;               // Timeout per le richieste in millisecondi
    private int retryAttempts;         // Numero di tentativi in caso di fallimento
    private boolean rateLimiting;      // Se attivare il rate limiting
    private List<RouteConfig> routes;  // Rotte associate a questo servizio

    // Costruttori
    public ServiceConfig() {}

    public ServiceConfig(String name, String baseUrl, int timeout, int retryAttempts, boolean rateLimiting, List<RouteConfig> routes) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.retryAttempts = retryAttempts;
        this.rateLimiting = rateLimiting;
        this.routes = routes;
    }

    // Getters e Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    public int getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

    public boolean isRateLimiting() { return rateLimiting; }
    public void setRateLimiting(boolean rateLimiting) { this.rateLimiting = rateLimiting; }

    public List<RouteConfig> getRoutes() { return routes; }
    public void setRoutes(List<RouteConfig> routes) { this.routes = routes; }
}

