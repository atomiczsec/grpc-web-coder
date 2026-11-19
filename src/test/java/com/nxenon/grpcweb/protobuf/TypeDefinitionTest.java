package com.nxenon.grpcweb.protobuf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeDefinitionTest {

    @Test
    void copyPreservesFieldsAndIsolatesNestedMutations() {
        TypeDefinition original = new TypeDefinition();
        TypeDefinition.FieldDefinition message = original.define(3, FieldType.MESSAGE);
        message.setName("nested");
        message.setAlternativeTypes(List.of(FieldType.STRING, FieldType.BYTES));
        TypeDefinition nested = new TypeDefinition();
        nested.define(2, FieldType.INT).setName("count");
        message.setMessageTypeDefinition(nested);
        original.define(1, FieldType.STRING);

        TypeDefinition copy = original.copy();

        assertNotSame(original, copy);
        assertNotSame(message, copy.get(3));
        assertNotSame(nested, copy.get(3).messageTypeDefinition());
        assertEquals(List.of(3, 1), List.copyOf(copy.fields().keySet()));
        assertEquals(JsonCodec.typeDefinitionToJson(original), JsonCodec.typeDefinitionToJson(copy));
        assertNull(copy.get(1).messageTypeDefinition());

        copy.get(3).setName("changed");
        copy.get(3).setAlternativeTypes(List.of(FieldType.BYTES));
        copy.get(3).messageTypeDefinition().get(2).setType(FieldType.BOOL);
        nested.define(4, FieldType.STRING);
        copy.define(5, FieldType.INT);

        assertEquals("nested", message.name());
        assertEquals(List.of(FieldType.STRING, FieldType.BYTES), message.alternativeTypes());
        assertEquals(FieldType.INT, nested.get(2).type());
        assertNull(copy.get(3).messageTypeDefinition().get(4));
        assertNull(original.get(5));
    }

    @Test
    void emptyDefinitionCopiesIndependently() {
        TypeDefinition original = new TypeDefinition();
        TypeDefinition copy = original.copy();
        assertNotSame(original, copy);
        assertTrue(copy.isEmpty());
        copy.define(1, FieldType.INT);
        assertTrue(original.isEmpty());
    }
}
