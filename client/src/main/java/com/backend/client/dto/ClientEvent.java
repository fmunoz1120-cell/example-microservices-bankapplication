package com.backend.client.dto;

import java.util.UUID;

public record ClientEvent(
    int schemaVersion, UUID eventId, Long clientId, long version, String name, boolean active) {}
