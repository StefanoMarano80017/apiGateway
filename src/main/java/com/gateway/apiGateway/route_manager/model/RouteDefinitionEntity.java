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

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteDefinitionEntity {

    @Id
    private String id;
    private String routeIdentifier;
    private String uri;
    private String method;
    private String path;
    private List<PredicateDefinition> predicates;
    private List<FilterDefinition> filters;
    private boolean active;

    @DBRef
    private ServiceDefinitionEntity service;

    /*
     * Ottieni nome del servizio 
     */
    public String getServiceName() {
        if (uri == null || uri.isEmpty()) {
            return "Unknown Service";
        }

        // Rimuove il protocollo (es. lb:// o http://)
        String withoutProtocol = uri.replaceFirst("^(https?|lb)://", "");

        // Prende solo la parte iniziale prima di ":" o "/"
        return withoutProtocol.split("[:/]")[0];
    }

}
