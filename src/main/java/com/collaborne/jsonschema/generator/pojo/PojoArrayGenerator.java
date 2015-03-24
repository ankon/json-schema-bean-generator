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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.collaborne.jsonschema.generator.java.Kind;
import com.collaborne.jsonschema.generator.java.Visibility;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.core.tree.SchemaTree;
import com.google.common.annotations.VisibleForTesting;

public class PojoArrayGenerator extends AbstractPojoTypeGenerator {
	@Inject
	@VisibleForTesting
	protected PojoArrayGenerator() {
		super(Kind.CLASS, Visibility.PUBLIC);
	}

	@Override
	public ClassName generate(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		// In the easy case we just have type=array, items=SCHEMA, which means we produce a List<SCHEMA-TYPE> reference
		// In other cases we might have to also produce a class extending AbstractList implementing the restrictions given
		// XXX: for now we just basically ignore the other restrictions
		JsonNode itemsNode = schema.getNode().get("items");
		if (itemsNode != null) {
			JsonPointer itemsPointer = schema.getPointer().append("items");
			URI elementUri = schema.getLoadingRef().getLocator().resolve("#" + itemsPointer.toString());
			AtomicReference<ClassName> elementClassName = new AtomicReference<>();
			visitSchema(elementUri, itemsNode, new SchemaVisitor<CodeGenerationException>() {
				@Override
				public void visitSchema(URI type, JsonNode schemaNode) throws CodeGenerationException {
					visitSchema(type);
				}

				@Override
				public void visitSchema(URI type) throws CodeGenerationException {
					elementClassName.set(context.getGenerator().generate(type));
				}
			});
			
			if (elementClassName.get() == null) {
				throw new CodeGenerationException(context.getType(), "Unknown element type: cannot create array type");
			}
			
			return ClassName.create(List.class, elementClassName.get());
		}
		
		return super.generate(context, schema, writer);
	}
	
	@Override
	protected void generateType(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		throw new CodeGenerationException(context.getType(), new UnsupportedOperationException("Cannot generate for non-items-based arrays"));
	}
}
