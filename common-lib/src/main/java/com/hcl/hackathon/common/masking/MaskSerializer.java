package com.hcl.hackathon.common.masking;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

public class MaskSerializer extends JsonSerializer<String> {
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || value.length() <= 4) {
            gen.writeString(value);
            return;
        }
        String masked = "****-****-****-" + value.substring(value.length() - 4);
        gen.writeString(masked);
    }
}
