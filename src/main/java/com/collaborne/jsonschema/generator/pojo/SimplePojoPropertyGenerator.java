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

import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.collaborne.jsonschema.generator.java.Visibility;

class SimplePojoPropertyGenerator extends AbstractPojoPropertyGenerator {
	private final ClassName className;
	private final String propertyName;
	
	public SimplePojoPropertyGenerator(ClassName className, String propertyName) {
		this.className = className;
		this.propertyName = propertyName;
	}
	
	@Override
	public void generateImports(JavaWriter writer) throws IOException {
		writer.writeImport(className);
	}
	
	@Override
	public void generateFields(JavaWriter writer) throws IOException {
		writer.writeField(Visibility.PRIVATE, className, propertyName);
	}

	@Override
	public void generateGetter(JavaWriter writer) throws IOException {
		writer.writeMethodBodyStart(Visibility.PUBLIC, className, getPrefixedPropertyName("get", propertyName));
		writer.writeCode("return " + propertyName + ";");
		writer.writeMethodBodyEnd();
	}

	@Override
	public void generateSetter(JavaWriter writer) throws IOException {
		writer.writeMethodBodyStart(Visibility.PUBLIC, ClassName.VOID, getPrefixedPropertyName("set", propertyName), className, "value");
		writer.writeCode("this." + propertyName + " = value;");
		writer.writeMethodBodyEnd();
	}
}