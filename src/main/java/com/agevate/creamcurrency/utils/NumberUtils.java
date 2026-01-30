package com.agevate.creamcurrency.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NumberUtils {

    private static final Pattern PATTERN = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)([kKmMbBtT]?)$");

    public static double parseAmount(String input) throws NumberFormatException {
        if (input == null || input.isEmpty()) throw new NumberFormatException("Empty input");

        Matcher matcher = PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new NumberFormatException("Invalid format");
        }

        double amount = Double.parseDouble(matcher.group(1));
        String suffix = matcher.group(2).toLowerCase();

        switch (suffix) {
            case "k": return amount * 1_000;
            case "m": return amount * 1_000_000;
            case "b": return amount * 1_000_000_000;
            case "t": return amount * 1_000_000_000_000L;
            default: return amount;
        }
    }
}
