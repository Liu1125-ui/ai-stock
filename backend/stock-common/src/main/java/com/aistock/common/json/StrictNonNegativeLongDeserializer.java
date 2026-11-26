package com.aistock.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** Rejects coercion at a trusted JSON boundary; zero is valid, missing/null is not. */
public final class StrictNonNegativeLongDeserializer extends StdDeserializer<Long> {
    public StrictNonNegativeLongDeserializer() { super(Long.class); }

    @Override public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            throw JsonMappingException.from(parser, "Expected an explicit non-negative JSON integer");
        }
        long value = parser.getLongValue();
        if (value < 0) throw JsonMappingException.from(parser, "Expected a non-negative integer");
        return value;
    }

    @Override public Long getNullValue(DeserializationContext context) throws JsonMappingException {
        throw JsonMappingException.from(context.getParser(), "Required integer must not be null");
    }
}
