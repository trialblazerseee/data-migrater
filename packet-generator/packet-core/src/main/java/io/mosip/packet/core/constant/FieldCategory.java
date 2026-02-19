package io.mosip.packet.core.constant;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum FieldCategory {
    DEMO,
    BIO,
    DOC,
    DYN;

    @JsonCreator
    public static FieldCategory fromJson(String value) {
        switch (value) {
            case "DEMO":
                return DEMO;
            case "BIO":
                return BIO;
            case "DOC":
                return DOC;
            default:
                throw new IllegalArgumentException("Invalid FieldCategory value: " + value);
        }
    }
}
