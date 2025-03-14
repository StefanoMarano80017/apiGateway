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
package com.gateway.apiGateway.route_manager.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.BooleanSpec;
import org.springframework.cloud.gateway.route.builder.Buildable;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.stereotype.Service;

import com.gateway.apiGateway.route_manager.model.RouteDefinitionEntity;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * Classe CustomRouteLocator che implementa {@link RouteLocator} per costruire
 * dinamicamente le rotte del gateway basandosi su una configurazione esterna.
 *
 * Questa classe recupera le rotte da un servizio esterno (`RouteService`) e le
 * trasforma in oggetti di tipo `Route` utilizzando il `RouteLocatorBuilder`.
 */
@RequiredArgsConstructor
@Service
public class CustomRouteLocator implements RouteLocator {

    // Builder fornito da Spring Cloud Gateway per costruire le rotte in modo programmatico.
    private final RouteLocatorBuilder routeLocatorBuilder;

    // Servizio che fornisce le configurazioni delle rotte (presumibilmente da database o altra fonte).
    private final RouteService routeService;

    private static final Logger log = LoggerFactory.getLogger(CustomRouteLocator.class);

    /**
     * Metodo che costruisce dinamicamente le rotte a partire dai dati forniti
     * dal servizio `routeService`.
     *
     * @return Un `Flux<Route>` contenente tutte le rotte dinamicamente
     * configurate.
     */
    @Override
    public Flux<Route> getRoutes() {
        // Inizializza il costruttore delle rotte
        RouteLocatorBuilder.Builder routesBuilder = routeLocatorBuilder.routes();
        // Recupera tutte le rotte dal servizio e le trasforma in definizioni di Spring Gateway
        return routeService.getAll().map(apiRoute -> {
            try{
                return routesBuilder.route(
                    String.valueOf(apiRoute.getRouteIdentifier()),   // Identificativo della rotta
                    predicateSpec -> setPredicateSpec(apiRoute, predicateSpec) // Configurazione della rotta
                );
            } catch(Exception e){
                log.error("Errore nella creazione della rotta: {}", apiRoute.getRouteIdentifier(), e);
                return null;
            }
        })
        .filter(route -> route != null) // Evita elementi nulli in caso di errore
        .collectList() // Colleziona le rotte in una lista
        .flatMapMany(builders -> routesBuilder.build().getRoutes()); // Costruisce il `RouteLocator` e restituisce il flusso di rotte
    }

    /**
     * Configura una singola rotta con i suoi predicati e la destinazione
     * finale.
     *
     * @param apiRoute Definizione della rotta da configurare
     * @param predicateSpec Specifica i predicati (condizioni di instradamento)
     * @return Costruzione della rotta con il predicato e l'URI finale
     */
    private Buildable<Route> setPredicateSpec(RouteDefinitionEntity apiRoute, PredicateSpec predicateSpec) {
        // Definisce un predicato basato sul path della rotta
        BooleanSpec booleanSpec = predicateSpec.path(apiRoute.getPath());
        // Se la richiesta ha un metodo HTTP specifico, aggiunge un ulteriore predicato
        if (!apiRoute.getMethod().isEmpty()) {
            booleanSpec.and().method(apiRoute.getMethod());
        }
        // Imposta la destinazione della rotta
        return booleanSpec.uri(apiRoute.getUri());
    }

    /**
     * Metodo opzionale ereditato da RouteLocator che permette di filtrare le
     * rotte per metadati.
     *
     * Questo metodo viene lasciato con l'implementazione di default della
     * superclasse.
     *
     * @param metadata Metadati per filtrare le rotte
     * @return Un `Flux<Route>` con le rotte filtrate
     */
    @Override
    public Flux<Route> getRoutesByMetadata(Map<String, Object> metadata) {
        return RouteLocator.super.getRoutesByMetadata(metadata);
    }
}
