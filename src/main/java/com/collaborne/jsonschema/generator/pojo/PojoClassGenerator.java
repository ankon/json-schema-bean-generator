/* Licensed to Collaborne B.V. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Collaborne licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.collaborne.jsonschema.generator.pojo;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.collaborne.jsonschema.generator.java.Kind;
import com.collaborne.jsonschema.generator.java.Visibility;
import com.collaborne.jsonschema.generator.model.Mapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.core.tree.SchemaTree;
import com.google.common.annotations.VisibleForTesting;

/**
 * Generator for a "class" with properties.
 */
class PojoClassGenerator extends AbstractPojoTypeGenerator {
	// FIXME: exception handling ... 
	private interface PropertyVisitor<T extends Exception> {
		// FIXME: should be a SchemaTree, not a JsonNode
		void visitProperty(String propertyName, URI type, JsonNode schemaNode) throws T;
		void visitProperty(String propertyName, URI type) throws T;
	}
	
	private final Logger logger = LoggerFactory.getLogger(PojoClassGenerator.class);
	
	@Inject
	@VisibleForTesting
	protected PojoClassGenerator() {
		super(Kind.CLASS, Visibility.PUBLIC);
	}

	protected <T extends Exception> boolean visitProperties(SchemaTree schema, PojoClassGenerator.PropertyVisitor<T> visitor) throws T {
		JsonPointer pointer = schema.getPointer().append("properties");
		
		JsonNode propertiesNode = schema.getNode().get("properties");
		if (propertiesNode == null || !propertiesNode.isContainerNode()) {
			return false;
		}
		
		for (Iterator<Map.Entry<String, JsonNode>> fieldIterator = propertiesNode.fields(); fieldIterator.hasNext(); ) {
			Map.Entry<String, JsonNode> field = fieldIterator.next();
			PojoClassGenerator.SchemaVisitor<T> schemaVisitor = new PojoClassGenerator.SchemaVisitor<T>() {
				@Override
				public void visitSchema(URI type, JsonNode schemaNode) throws T {
					visitor.visitProperty(field.getKey(), type, schemaNode);
				}
				
				@Override
				public void visitSchema(URI type) throws T {
					visitor.visitProperty(field.getKey(), type);
				}
			};
			
			JsonPointer propertyPointer = pointer.append(field.getKey());
			// XXX: what about "relative" references, won't adding a '#' break resolving those? Are those legal?
			URI elementUri = schema.getLoadingRef().getLocator().resolve("#" + propertyPointer.toString());
			if (!visitSchema(elementUri, field.getValue(), schemaVisitor)) {
				// XXX: can there be meta information here?
				// XXX: context information missing here
				logger.warn("{}: not a container value");
			}
		}
		
		return true;
	}
	
	protected Set<URI> getRequiredTypes(SchemaTree schema) {
		Set<URI> requiredTypes = new HashSet<>();
		
		SchemaVisitor<RuntimeException> schemaVisitor = new SchemaVisitor<RuntimeException>() {
			@Override
			public void visitSchema(URI type, JsonNode schemaNode) {
				visitSchema(type);
			}
			
			@Override
			public void visitSchema(URI type) {
				requiredTypes.add(type);
			}
		}; 
		
		visitProperties(schema, new PropertyVisitor<RuntimeException>() {
			@Override
			public void visitProperty(String propertyName, URI type, JsonNode schemaNode) {
				visitProperty(propertyName, type);
			}
			
			@Override
			public void visitProperty(String propertyName, URI type) {
				schemaVisitor.visitSchema(type);
			}
		});
		
		// Check if "additionalProperties" is a schema as well, if so we need to be able to resolve that one.
		if (schema.getNode().hasNonNull("additionalProperties")) {
			JsonPointer additionalPropertiesPointer = schema.getPointer().append("additionalProperties");
			URI additionalPropertiesUri = schema.getLoadingRef().getLocator().resolve("#" + additionalPropertiesPointer.toString());
			visitSchema(additionalPropertiesUri, schema.getNode().get("additionalProperties"), schemaVisitor);
		}
		return requiredTypes;
	}
	
	@Override
	public void generateType(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		Mapping mapping = context.getMapping();
		
		// Process the properties into PropertyGenerators
		List<PojoPropertyGenerator> propertyGenerators = new ArrayList<>();

		visitProperties(schema, new PropertyVisitor<CodeGenerationException>() {
			@Override
			public void visitProperty(String propertyName, URI type, JsonNode schemaNode) throws CodeGenerationException {
				visitProperty(propertyName, type);
			}

			@Override
			public void visitProperty(String propertyName, URI type) throws CodeGenerationException {
				propertyGenerators.add(context.createPropertyGenerator(type, propertyName));
			}
		});
		
		for (PojoPropertyGenerator propertyGenerator : propertyGenerators) {
			propertyGenerator.generateImports(writer);
		}
		
		writer.writeClassStart(mapping.getClassName(), getKind(), getVisibility());
		try {
			// Write properties
			for (PojoPropertyGenerator propertyGenerator : propertyGenerators) {
				propertyGenerator.generateFields(writer);
			}
			
			// Write accessors
			// TODO: style to create them: pairs, or ordered? 
			// TODO: whether to generate setters in the first place, or just getters
			for (PojoPropertyGenerator propertyGenerator : propertyGenerators) {
				propertyGenerator.generateGetter(writer);
				propertyGenerator.generateSetter(writer);
			}
		} finally {
			writer.writeClassEnd();
		}
	}
}