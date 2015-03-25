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

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.collaborne.jsonschema.generator.AbstractGenerator;
import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.MissingSchemaException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.collaborne.jsonschema.generator.java.Kind;
import com.collaborne.jsonschema.generator.model.Mapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jackson.jsonpointer.JsonPointerException;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.SchemaLoader;
import com.github.fge.jsonschema.core.tree.SchemaTree;
import com.google.common.annotations.VisibleForTesting;

// XXX: URI vs JsonRef vs SchemaKey
public class PojoGenerator extends AbstractGenerator {
	/** The name of the package to use for anonymous types */
	public static final Feature<String> FEATURE_DEFAULT_PACKAGE_NAME = new Feature<>("http://json-schema-bean-generator.collaborne.com/features/LATEST/pojo/package-name", String.class, "anonymous");
	/** Attempt to write code even when there are some types missing */
	public static final Feature<Boolean> FEATURE_IGNORE_MISSING_TYPES = new Feature<>("http://json-schema-bean-generator.collaborne.com/features/LATEST/pojo/ignore-missing-types", Boolean.class, Boolean.FALSE);

	public enum AnonymousClassNameGenerator {
		CAMEL_CASE {
			@Override
			public String createClassName(URI type) {
				StringBuilder classNameBuilder = new StringBuilder();
				if (type.getFragment().isEmpty()) {
					classNameBuilder.append("Type");
				} else {
					// FIXME: This should be a different generator?
					String fragment = type.getFragment().replace(':', '/');
					for (String fragmentStep : fragment.split("/")) {
						// Skip some typical steps to make the names a bit easier to read
						// - "": happens due to JSON pointer always starting with '/'
						// - "properties": any reference to an anonymous type inside an "object"
						// - "definitions": suggested node for keeping type definitions
						if (fragmentStep.isEmpty() || "properties".equals(fragmentStep) || "definitions".equals(fragmentStep)) {
							continue;
						}
						
						// Replace all characters not valid for Java
						char fragmentStepFirstCharacter = Character.toUpperCase(fragmentStep.charAt(0));
						String fragmentStepRemainingCharacters;
						if (Character.isJavaIdentifierStart(fragmentStepFirstCharacter)) {
							fragmentStepRemainingCharacters = fragmentStep.substring(1);
						} else {
							fragmentStepFirstCharacter = '_';
							fragmentStepRemainingCharacters = fragmentStep;
						}
						fragmentStepRemainingCharacters = fragmentStepRemainingCharacters.replaceAll("[^\\p{javaJavaIdentifierPart}]", "");
						
						classNameBuilder.append(fragmentStepFirstCharacter);
						classNameBuilder.append(fragmentStepRemainingCharacters);
					}
				}

				return classNameBuilder.toString();
			}
		}, 
		DOLLAR {
			@Override
			public String createClassName(URI type) {
				throw new UnsupportedOperationException("AnonymousClassNameGenerator#appendClassName() is not implemented");
			}
		};
		
		public abstract String createClassName(URI type);
	}
	public static final Feature<AnonymousClassNameGenerator> FEATURE_CLASS_NAME_GENERATOR = new Feature<>("http://json-schema-bean-generator.collaborne.com/features/LATEST/pojo/class-name-generator", AnonymousClassNameGenerator.class, AnonymousClassNameGenerator.CAMEL_CASE);
	/** Whether to ignore constraints (enum-ness, min/max value, etc) on non-"object" types */
	public static final Feature<Boolean> FEATURE_USE_SIMPLE_PLAIN_TYPES = new Feature<>("http://json-schema-bean-generator.collaborne.com/features/LATEST/pojo/simple-plain-types", Boolean.class, Boolean.TRUE);	
	/** Whether to generate Java 5 {@code enum}s or 'class-with-constants' for JSON schema 'enum's */
	public static final Feature<Kind> FEATURE_ENUM_STYLE = new Feature<>("http://json-schema-bean-generator.collaborne.com/features/LATEST/pojo/enum-style", Kind.class, Kind.ENUM);
	
	private static class SimplePojoTypeGenerator implements PojoTypeGenerator {
		private final ClassName className;
		
		public SimplePojoTypeGenerator(ClassName className) {
			this.className = className;
		}
		
		@Override
		public ClassName generate(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter javaWriter) {
			return className;
		}
	}
	
	@VisibleForTesting
	protected static class Buffer extends ByteArrayOutputStream {
		public InputStream getInputStream() {
			return new ByteArrayInputStream(buf);
		}
	}
	
	private static final List<String> PRIMITIVE_TYPE_NAMES = Arrays.asList(
		Boolean.TYPE.getName(),
		Character.TYPE.getName(),
		Byte.TYPE.getName(),
		Short.TYPE.getName(),
		Integer.TYPE.getName(),
		Long.TYPE.getName(),
		Float.TYPE.getName(),
		Double.TYPE.getName()
	);

	private final Logger logger = LoggerFactory.getLogger(PojoGenerator.class);
	
	private final Map<String, PojoTypeGenerator> typeGenerators = new HashMap<>();
	private final Map<URI, ClassName> generatedClassNames = new HashMap<>();
	private final Set<URI> nullTypes = new HashSet<>();
	
	@Inject
	@VisibleForTesting
	protected PojoGenerator(PojoClassGenerator classGenerator, PojoArrayGenerator arrayGenerator, PojoStringGenerator stringGenerator) {
		this.typeGenerators.put("object", classGenerator);
		this.typeGenerators.put("array", arrayGenerator);
		if (getFeature(FEATURE_USE_SIMPLE_PLAIN_TYPES)) {
			// TODO: if additional restrictions are given on these types we can either implement specific
			//       types (for example we provide a base library for each of the plain types, and configure them
			//       to check the restrictions), or we could simply ignore those.
			this.typeGenerators.put("string", new SimplePojoTypeGenerator(ClassName.create(String.class)));
		} else {
			this.typeGenerators.put("string", stringGenerator);
		}
		this.typeGenerators.put("integer", new SimplePojoTypeGenerator(ClassName.create(Integer.TYPE)));
		this.typeGenerators.put("number", new SimplePojoTypeGenerator(ClassName.create(Double.TYPE))); 
		this.typeGenerators.put("boolean", new SimplePojoTypeGenerator(ClassName.create(Boolean.TYPE)));
	}
	
	@Override
	public ClassName generate(URI type) throws CodeGenerationException {
		if (nullTypes.contains(type)) {
			return null;
		}
		
		ClassName generatedClassName = generatedClassNames.get(type);
		if (generatedClassName != null) {
			return generatedClassName;
		}
		
		// Find or create the mapping for this type
		Mapping mapping = getMapping(type);
		if (mapping == null) {
			logger.debug("{}: No mapping defined", type);
			mapping = generateMapping(type);
			addMapping(type, mapping);
		}
		
		try {
			generatedClassName = generateInternal(type, mapping);
		} catch (CodeGenerationException e) {
			if (getFeature(FEATURE_IGNORE_MISSING_TYPES)) {
				// Assume that a class would have been created.
				generatedClassName = mapping.getClassName();
				logger.warn("{}: Ignoring creation failure, assuming class {} would have been created", generatedClassName, e);
			} else {
				throw e;
			}
		}
		
		if (generatedClassName == null) {
			nullTypes.add(type);
		} else {
			generatedClassNames.put(type, generatedClassName);
		}
		return generatedClassName;
	}
	
	/**
	 * Generate code for the {@code type} using the provided {@code mapping}.
	 * 
	 * @param type
	 * @param mapping
	 * @return the name of the class to use for this type, or {@code null} if no class is available for this type
	 * @throws CodeGenerationException
	 */
	protected ClassName generateInternal(URI type, Mapping mapping) throws CodeGenerationException {
		// If the mapping wants a primitive type or existing type, do that (ignoring whatever the schema does)
		if (isPrimitive(mapping.getClassName()) || isExistingClass(mapping.getClassName())) {
			return mapping.getClassName();
		}

		try {
			// 1. Find the schema for the type
			SchemaTree schema = getSchema(getSchemaLoader(), type);
			if (schema == null || schema.getNode() == null) {
				throw new MissingSchemaException(type);
			}

			// 2. Determine the type of the schema
			String schemaType;
			JsonNode schemaTypeNode = schema.getNode().get("type");
			if (schemaTypeNode == null) {
				// FIXME: hyper-schema!
				logger.warn("{}: Missing type keyword, assuming 'object'", type);
				schemaType = "object";
			} else {
				schemaType = schemaTypeNode.textValue();
			}
			
			if ("null".equals(schemaType)) {
				// All good, nothing to be done.
				return null;
			}
			
			// 3. Find the correct generator and run the code generation, this might recurse back into the generator
			PojoTypeGenerator typeGenerator = typeGenerators.get(schemaType);
			if (typeGenerator == null) {
				throw new CodeGenerationException(type, "Cannot handle type '" + type + "' ('" + schemaType + "')");
			}
			
			PojoCodeGenerationContext codeGenerationContext = new PojoCodeGenerationContext(this, mapping);
			
			// Generate into a buffer
			// If the generator doesn't actually produce output (for example because it resolved the class differently),
			// then we do not have to do anything further.
			ClassName className;
			Buffer buffer = new Buffer();
			try (JavaWriter writer = new JavaWriter(new BufferedWriter(new OutputStreamWriter(buffer)))) {
				className = typeGenerator.generate(codeGenerationContext, schema, writer);
			}

			if (buffer.size() > 0) {
				writeSource(type, className, buffer);
			}
			
			return className;
		} catch (ProcessingException|JsonPointerException|IOException e) {
			throw new CodeGenerationException(type, e);
		}
	}

	@VisibleForTesting
	protected boolean isPrimitive(ClassName className) {
		if (!className.getPackageName().isEmpty()) {
			return false;
		}
		return PRIMITIVE_TYPE_NAMES.contains(className.getRawClassName());
	}

	@VisibleForTesting
	protected boolean isExistingClass(ClassName className) {
		String fqcn = className.getPackageName();
		if (!fqcn.isEmpty()) {
			fqcn += ".";
		}
		fqcn += className.getRawClassName();
		try {
			Class.forName(fqcn);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	@VisibleForTesting
	protected void writeSource(URI type, ClassName className, Buffer buffer) throws IOException {
		// Create the file based on the className in the mapping
		Path outputFile = getClassSourceFile(className);
		logger.info("{}: Writing {}", type, outputFile);

		// Write stuff into it
		Files.createDirectories(outputFile.getParent());
		Files.copy(buffer.getInputStream(), outputFile, StandardCopyOption.REPLACE_EXISTING);
	}
	
	protected Path getClassSourceFile(ClassName className) {
		StringBuilder fqcnBuilder = new StringBuilder();
		if (!className.getPackageName().isEmpty()) {
			fqcnBuilder.append(className.getPackageName());
			fqcnBuilder.append(".");
		}
		fqcnBuilder.append(className.getRawClassName());
		String classFileName = fqcnBuilder.toString().replace('.', '/') + ".java";
		return getOutputDirectory().resolve(classFileName);
	}
	
	@VisibleForTesting
	protected Mapping generateMapping(URI type) {
		Mapping mapping = new Mapping();
		mapping.setTarget(type);
		
		// In theory the type could point to a schema that has an 'id' value, which is
		// fairly easy to abuse. So, we generate a name based on the position in the 
		// tree, if the user doesn't like it they can just provide a specific mapping for it.
		// TODO: would be nice to use the same package as the schema that lead to the generation of that type.
		// FIXME: should be global setting or such, definitely a constant though
		String packageName = getFeature(FEATURE_DEFAULT_PACKAGE_NAME);
		// One cannot "import" packages from the default package, so we need them to be somewhere ... 
		assert packageName != null && !packageName.isEmpty();
		
		// TODO: should produce ClassName directly
		AnonymousClassNameGenerator classNameGenerator = getFeature(FEATURE_CLASS_NAME_GENERATOR);
		String rawClassName = classNameGenerator.createClassName(type);
		ClassName className = new ClassName(packageName, rawClassName);
		while (generatedClassNames.containsValue(className)) {
			// Make the name reasonably unique by adding a timestamp
			className = new ClassName(packageName, rawClassName + "$" + System.nanoTime());
		}
		mapping.setClassName(className);
		return mapping;
	}

	/**
	 * Get the {@link SchemaTree} for the given {@code uri}.
	 * 
	 * This is similar to {@link SchemaLoader#get(URI)}, but allows {@code uri} to contain a fragment.
	 * 
	 * @param uri
	 * @return
	 * @throws ProcessingException 
	 * @throws JsonPointerException 
	 */
	// XXX: Should this be the default behavior of SchemaLoader#get()?
	// XXX: review exceptions
	@VisibleForTesting
	protected SchemaTree getSchema(SchemaLoader schemaLoader, URI uri) throws ProcessingException, JsonPointerException {
		String fragment = uri.getFragment();
		if (fragment == null) {
			return schemaLoader.get(uri);
		} else {
			try {
				URI schemaTreeUri = new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null);
				JsonPointer pointer = new JsonPointer(fragment);
				SchemaTree schema = schemaLoader.get(schemaTreeUri);
				return schema.setPointer(pointer);
			} catch (URISyntaxException e) {
				assert false : "Was a URI before, we just removed the fragment";
				throw new RuntimeException(e);
			}
		}
	}
}
