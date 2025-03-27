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

import java.util.List;
import java.util.Map;

public class CachedResponse {
    private String contentType;
    private String body;
    private Map<String, List<String>> headers;

    public CachedResponse() {}

    public CachedResponse(String contentType, String body, Map<String, List<String>> headers) {
        this.contentType = contentType;
        this.body = body;
        this.headers = headers;
    }

    // Getters & setters
    public String getContentType() {
        return contentType;
    }
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    public String getBody() {
        return body;
    }
    public void setBody(String body) {
        this.body = body;
    }
    public Map<String, List<String>> getHeaders() {
        return headers;
    }
    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }
}