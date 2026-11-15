package com.example.changeflow.domain;

import java.util.UUID;

/** Original client intent; never refreshed or reset by the service. */
public record ExpectedVersion(UUID id, long version) { }
