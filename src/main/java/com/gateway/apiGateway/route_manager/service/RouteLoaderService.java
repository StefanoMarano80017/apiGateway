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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.apiGateway.route_manager.model.ServiceConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;

@Service
public class RouteLoaderService {

    @Autowired
    private MongoTemplate mongoTemplate;

    private final String routesDirectory = "routes/";

    public void loadRoutes() throws IOException {
        // Leggi tutti i file dalla cartella "routes"
        File folder = new File(routesDirectory);
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                if (file.isFile() && file.getName().endsWith(".json")) {
                    // Carica il file JSON in un oggetto ServiceConfig
                    loadServiceFromFile(file);
                }
            }
        }
    }

    private void loadServiceFromFile(File file) throws IOException {
        // Usa ObjectMapper per convertire il file JSON in un oggetto ServiceConfig
        ObjectMapper objectMapper = new ObjectMapper();
        ServiceConfig serviceConfig = objectMapper.readValue(file, ServiceConfig.class);
        // Salva il servizio in MongoDB
        mongoTemplate.save(serviceConfig);
    }
}
