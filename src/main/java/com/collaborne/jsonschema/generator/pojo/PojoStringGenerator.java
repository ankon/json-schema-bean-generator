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
import java.util.function.Consumer;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.collaborne.jsonschema.generator.java.Kind;
import com.collaborne.jsonschema.generator.java.Visibility;
import com.github.fge.jsonschema.core.tree.SchemaTree;

public class PojoStringGenerator extends AbstractPojoTypeGenerator {
	private interface EnumGenerator {
		void generateEnumValue(SchemaTree schemaTree, JavaWriter writer) throws IOException;
		void generateAdditionalCode(JavaWriter writer) throws IOException;
	}

	private static class ClassEnumGenerator implements EnumGenerator {
		@Override
		public void generateEnumValue(SchemaTree schemaTree, JavaWriter writer) {
			throw new UnsupportedOperationException("PojoStringGenerator.EnumGenerator#generateEnumValue() is not implemented");
		}

		@Override
		public void generateAdditionalCode(JavaWriter writer) {
			throw new UnsupportedOperationException("PojoStringGenerator.EnumGenerator#generateAdditionalCode() is not implemented");
		}
	}

	private static class EnumEnumGenerator implements EnumGenerator {
		@Override
		public void generateEnumValue(SchemaTree schemaTree, JavaWriter writer) throws IOException {
			throw new UnsupportedOperationException("PojoStringGenerator.EnumGenerator#generateEnumValue() is not implemented");
		}

		@Override
		public void generateAdditionalCode(JavaWriter writer) throws IOException {
			writer.writeCode(";");
		}
	}

	@Override
	public ClassName generate(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		if (!schema.getNode().hasNonNull("enum")) {
			// Not an enum-ish string, so just map it to that.
			return ClassName.create(String.class);
		}

		return super.generate(context, schema, writer);
	}

	@Override
	protected void generateType(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		EnumGenerator enumGenerator;
		Kind enumStyle = context.getGenerator().getFeature(PojoGenerator.FEATURE_ENUM_STYLE);
		switch (enumStyle) {
		case CLASS:
			enumGenerator = new ClassEnumGenerator();
			break;
		case ENUM:
			enumGenerator = new EnumEnumGenerator();
			break;
		default:
			throw new CodeGenerationException(context.getType(), new IllegalArgumentException("Invalid enum style: " + enumStyle));
		}

		writer.writeClassStart(context.getMapping().getClassName(), enumStyle, Visibility.PUBLIC);
		try {
			iterateEnumValues(schema, valueSchema -> enumGenerator.generateEnumValue(valueSchema, writer));
			enumGenerator.generateAdditionalCode(writer);
		} finally {
			writer.writeClassEnd();
		}
	}

	protected void iterateEnumValues(SchemaTree schemaTree, Consumer<SchemaTree> consumer) throws IOException {
		// TODO
	}
}
