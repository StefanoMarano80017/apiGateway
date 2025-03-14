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
package com.gateway.apiGateway.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Component
public class JwtUtil {

    private final WebClient webClient;
    private static final String AUTH_SERVICE_URL = "http://auth-service/auth/validateToken";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    public JwtUtil(WebClient.Builder webClientBuilder) {
        this.webClient = WebClient.create(AUTH_SERVICE_URL);
    }

    public String extractToken(ServerHttpRequest request) {
        HttpHeaders header = request.getHeaders();
        // Controllo se l'header Authorization è presente e contiene un token Bearer
        if (!header.containsKey(HttpHeaders.AUTHORIZATION)) {
            return null;
        }

        List<String> authHeaders = header.get(HttpHeaders.AUTHORIZATION);
        return (authHeaders == null || authHeaders.isEmpty()) 
                    ? null : authHeaders.get(0).replace("Bearer ", "");
    }
            
    public Mono<Boolean> validateToken(String token) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.queryParam("jwt", token).build()) // Passa il token come parametro
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorReturn(true); // Se il servizio di autenticazione non risponde, assume token non valido
    }

    public String extractUserId(String jwt){
        try {
            if (jwt == null || jwt.split("\\.").length < 2) {
                throw new IllegalArgumentException("JWT non valido: formato errato");
            }

            String payload = jwt.split("\\.")[1];
            
            // Decodifica la parte payload (Base64 URL-safe)
            byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
            String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);
            
            // Deserializza il JSON nel payload
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = OBJECT_MAPPER.readValue(decodedJson, Map.class);
            Object userId = payloadMap.get("userId");

            if (userId == null) {
                throw new IllegalArgumentException("JWT non valido: userId mancante");
            }

            return userId.toString();
        } catch (IllegalArgumentException | JsonProcessingException e) {
            return null;
        } 
    }

}

