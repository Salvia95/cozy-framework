package io.salvia95.cozy.reference.activity.api;

/**
 * 최근 활동 한 줄 (화면용).
 *
 * @param message 무엇이 있었는지
 * @param at 언제 (yyyy-MM-dd HH:mm)
 */
public record ActivityItem(String message, String at) {
}
