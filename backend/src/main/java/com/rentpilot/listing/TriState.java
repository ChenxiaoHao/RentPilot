package com.rentpilot.listing;

public enum TriState {
    YES, NO, UNKNOWN;

    public static TriState fromCsv(String value) {
        return switch (value) {
            case "是" -> YES;
            case "否" -> NO;
            case "未知" -> UNKNOWN;
            default -> throw new IllegalArgumentException("设施必须为是、否或未知");
        };
    }
}
