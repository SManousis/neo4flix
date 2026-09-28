package com.neo4flix.platform.common.web;

import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestId {

    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private RequestId() {
    }

    public static String resolve(String candidate) {
        if (candidate != null && VALID_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
