package com.github.streletsa.protocols.bridge.app.dto;

import java.io.Serializable;

public class TestDto implements Serializable {
    private String value;

    public static TestDto from(String v) {
        TestDto testDto = new TestDto();
        testDto.setValue(v);

        return testDto;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
