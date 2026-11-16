package com.brisastock.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Publica un {@link Usuario} relacionado solo como su username.
 * Así el JSON de una venta dice "usuario": "admin" y nunca expone el resto
 * de los datos de la cuenta.
 */
public class UsernameSerializer extends JsonSerializer<Usuario> {

    @Override
    public void serialize(Usuario usuario, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeString(usuario.getUsername());
    }
}
