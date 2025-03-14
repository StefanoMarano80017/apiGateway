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

import java.time.Duration;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceDefinitionEntity {

    @Id
    private String id;

    private String serviceName; // Nome del servizio, es. "user-service"
    private String baseUrl; // URL del servizio, es. "http://user-service:8080"
    private boolean active; // Se il servizio è attivo o meno

    /*
     * Circuit breaker config 
     */
    private boolean CBenabled; // Se il Circuit Breaker è attivo
    private int failureRateThreshold; // Percentuale di errori prima di aprire il Circuit Breaker (es. 50)
    private int slowCallRateThreshold; // Percentuale di chiamate lente prima di aprire il Circuit Breaker (es. 75)
    private long waitDurationInOpenState; // Tempo di attesa in ms prima di passare da OPEN a HALF-OPEN (es. 5000 ms)
    private int permittedNumberOfCallsInHalfOpenState; // Numero di richieste per testare se il servizio è di nuovo disponibile
    private int slidingWindowSize; // Numero di chiamate da considerare per le statistiche

    public CircuitBreakerConfig getCircuitBreakerConfig() {
        if (this.CBenabled) {
            return new CircuitBreakerConfig.Builder()
                    .failureRateThreshold(this.failureRateThreshold)
                    .waitDurationInOpenState(Duration.ofMillis(this.waitDurationInOpenState))
                    .slidingWindowSize(this.slidingWindowSize)
                    .permittedNumberOfCallsInHalfOpenState(this.permittedNumberOfCallsInHalfOpenState)
                    .slowCallRateThreshold(this.slowCallRateThreshold)
                    .build();
        }
        return null;
    }
}
