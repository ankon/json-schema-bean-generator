package com.collaborne.jsonschema.generator.pojo;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Before;
import org.junit.Test;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonNodeReader;
import com.github.fge.jsonschema.core.load.SchemaLoader;
import com.github.fge.jsonschema.core.tree.SchemaTree;

public class PojoStringGeneratorTest {
	private JsonNodeReader jsonNodeReader;
	private SchemaLoader schemaLoader;
	
	@Before
	public void setUp() {
		jsonNodeReader = new JsonNodeReader();
		schemaLoader = new SchemaLoader();
	}
	
	@Test
	public void generateNoEnumReturnsString() throws IOException, CodeGenerationException {
		JsonNode schemaNode = jsonNodeReader.fromReader(new StringReader("{\"type\": \"string\"}"));
		SchemaTree schema = schemaLoader.load(schemaNode);
		PojoStringGenerator generator = new PojoStringGenerator();
		ClassName generated = generator.generate(null, schema, null);
		assertEquals(ClassName.create(String.class), generated);
	}
}
