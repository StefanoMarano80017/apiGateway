/*
 *   Copyright (c) 2025 Stefano Marano https://github.com/StefanoMarano80017
 *   All rights reserved.
 */
package com.gateway.apiGateway.route_manager.service;

import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.apiGateway.route_manager.model.RouteDefinitionEntity;
import com.gateway.apiGateway.route_manager.model.ServiceDefinitionEntity;
import com.gateway.apiGateway.route_manager.repository.RouteDefinitionRepository;
import com.gateway.apiGateway.route_manager.repository.ServiceDefinitionRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RouteLoaderService {

    private final RouteDefinitionRepository routeRepository;
    private final ServiceDefinitionRepository serviceRepository;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private static final String ROUTES_DIRECTORY = "classpath:routes/";
    private static final String SERVICE_FILE = "classpath:services.json";

    @Autowired
    public RouteLoaderService(RouteDefinitionRepository routeRepository,
                                ServiceDefinitionRepository serviceRepository,
                                ResourceLoader resourceLoader,
                                ObjectMapper objectMapper) {
        this.routeRepository = routeRepository;
        this.serviceRepository = serviceRepository;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    /*
      * Carica i servizi da JSON -> List<ServiceDefinitionEntity>
     */
    private Mono<List<ServiceDefinitionEntity>> loadServicesJson(String filePath) {
        return Mono.fromCallable(() -> {
            Resource resource = resourceLoader.getResource(filePath);
            try (InputStream inputStream = resource.getInputStream()) {
                return objectMapper.readValue(inputStream, new TypeReference<List<ServiceDefinitionEntity>>() {
                });
            }
        });
    }

    /*
      * Carica le route da JSON -> List<RouteDefinitionEntity>
     */
    private Mono<List<RouteDefinitionEntity>> loadRoutesJson(Resource resource) {
        return Mono.fromCallable(() -> {
            try (InputStream inputStream = resource.getInputStream()) {
                return objectMapper.readValue(inputStream, new TypeReference<List<RouteDefinitionEntity>>() {
                });
            }
        });
    }

    /*
      * Carica tutte le route dalla directory e le salva in MongoDB
     */
    public Mono<Void> loadRoutes() {
        return Mono.fromCallable(() -> {
            // Usa PathMatchingResourcePatternResolver per ottenere tutti i file JSON dalla cartella
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(ROUTES_DIRECTORY + "*.json");
            return resources;
        })
        .flatMapMany(Flux::fromArray) // Converte gli array di risorse in Flux
        .flatMap(this::loadRoutesJson) // Carica i JSON in lista di RouteDefinitionEntity
        .flatMap(Flux::fromIterable) // Converte la lista in singoli elementi
        .flatMap(routeRepository::save) // Salva nel DB
        .then();
    }

    /*
      * Carica il file di servizi e salva in MongoDB
     */
    public Mono<Void> loadServices() {
        return loadServicesJson(SERVICE_FILE)
                .flatMapMany(Flux::fromIterable)
                .flatMap(serviceRepository::save)
                .then();
    }
}
