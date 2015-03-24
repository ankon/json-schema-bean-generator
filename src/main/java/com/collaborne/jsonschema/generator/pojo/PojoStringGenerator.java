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

import javax.inject.Inject;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.collaborne.jsonschema.generator.java.Kind;
import com.collaborne.jsonschema.generator.java.Visibility;
import com.github.fge.jsonschema.core.tree.SchemaTree;
import com.google.common.annotations.VisibleForTesting;

public class PojoStringGenerator extends AbstractPojoTypeGenerator {
	@Inject
	@VisibleForTesting
	protected PojoStringGenerator() {
		// XXX: Should use Kind.CLASS for old-school enums
		super(Kind.ENUM, Visibility.PUBLIC);
	}
	
	@Override
	protected void generateType(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer)
			throws IOException, CodeGenerationException {
		// TODO Auto-generated method stub

	}

}
