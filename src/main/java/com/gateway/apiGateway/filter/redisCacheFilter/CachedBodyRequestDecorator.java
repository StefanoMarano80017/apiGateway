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

package com.gateway.apiGateway.filter.redisCacheFilter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;

import reactor.core.publisher.Flux;

/**
 * Decoratore per clonare il body di una richiesta e permettere la rilettura nei filtri successivi.
 */
public class CachedBodyRequestDecorator extends ServerHttpRequestDecorator {

    private final byte[] cachedBodyBytes;
    private final String bodyHash;

    public CachedBodyRequestDecorator(ServerHttpRequest delegate, byte[] bodyBytes) {
        super(delegate);
        this.cachedBodyBytes = bodyBytes;
        this.bodyHash = hashSHA256Bytes(bodyBytes);
    }

    @SuppressWarnings("null")
    @Override
    public Flux<DataBuffer> getBody() {
        return Flux.just(new DefaultDataBufferFactory().wrap(cachedBodyBytes));
    }

    @SuppressWarnings("null")
    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(super.getHeaders());
        headers.setContentLength(cachedBodyBytes.length);
        return headers;
    }
    
    private String hashSHA256Bytes(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error computing SHA-256 hash", e);
        }
    }

    public String getBodyHash() {
        return bodyHash;
    }
}
