package com.omni.gateway.protocol.gb28181;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight MANSCDP+xml field extraction (no external XML parser).
 */
public final class Gb28181Xml {

    private static final Pattern CMD_TYPE = Pattern.compile(
            "<\\s*CmdType\\s*>\\s*([^<]+?)\\s*</\\s*CmdType\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEVICE_ID = Pattern.compile(
            "<\\s*DeviceID\\s*>\\s*([^<]+?)\\s*</\\s*DeviceID\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SN = Pattern.compile(
            "<\\s*SN\\s*>\\s*([^<]+?)\\s*</\\s*SN\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOT = Pattern.compile(
            "<\\s*(Notify|Response|Query|Control)\\s*>", Pattern.CASE_INSENSITIVE);

    private Gb28181Xml() {
    }

    public static Optional<String> cmdType(String xml) {
        return extract(xml, CMD_TYPE);
    }

    public static Optional<String> deviceId(String xml) {
        return extract(xml, DEVICE_ID);
    }

    public static Optional<String> sn(String xml) {
        return extract(xml, SN);
    }

    public static Optional<String> rootElement(String xml) {
        return extract(xml, ROOT);
    }

    private static Optional<String> extract(String xml, Pattern pattern) {
        if (xml == null || xml.isBlank()) {
            return Optional.empty();
        }
        Matcher m = pattern.matcher(xml);
        if (m.find()) {
            return Optional.of(m.group(1).trim());
        }
        return Optional.empty();
    }
}
