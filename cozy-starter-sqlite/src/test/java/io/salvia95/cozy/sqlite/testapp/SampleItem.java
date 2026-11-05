package io.salvia95.cozy.sqlite.testapp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SampleItem(Long id, String name, Instant createdAt, LocalDateTime localDt, LocalDate localD, UUID uid,
		boolean flag, Boolean maybe) {
}
