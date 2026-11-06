package io.salvia95.cozy.reference.activity.internal;

import java.time.Instant;

public record ActivityRow(long id, String message, Instant createdAt) {
}
