package com.neo4flix.user.api;

import java.time.Instant;

public record TotpSetupResponse(String otpauthUri, String qrCodeDataUrl, Instant expiresAt) { }
