package it.cozy.app.activity.internal.infra;

import java.time.Instant;

public record ActivityRow(long id, String message, Instant createdAt) {
}
