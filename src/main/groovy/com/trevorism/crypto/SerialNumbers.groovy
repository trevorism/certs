package com.trevorism.crypto

class SerialNumbers {

    static String normalize(String serial) {
        if (!serial) {
            return null
        }
        String trimmed = serial.trim().toLowerCase()
        String sign = trimmed.startsWith("-") ? "-" : ""
        String stripped = trimmed.replaceAll("[^0-9a-f]", "").replaceFirst("^0+", "")
        return stripped ? "${sign}${stripped}" : "0"
    }

    static boolean same(String left, String right) {
        String a = normalize(left)
        String b = normalize(right)
        return a != null && a == b
    }
}
