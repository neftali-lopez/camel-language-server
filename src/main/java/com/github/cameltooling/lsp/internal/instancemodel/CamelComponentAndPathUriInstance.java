/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cameltooling.lsp.internal.instancemodel;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.catalog.CamelCatalog;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.TextDocumentItem;

import com.github.cameltooling.lsp.internal.completion.CamelComponentSchemesCompletionsFuture;
import com.github.cameltooling.model.ComponentModel;

/**
 * For a Camel component and path URI "timer:timerName?delay=10s", it represents "timer:timerName"
 * 
 * @author lheinema
 */
public class CamelComponentAndPathUriInstance extends CamelUriElementInstance {

	private static final String CAMEL_PATH_SEPARATOR_REGEX = ":|/";
	
	private CamelURIInstance parent;
	private CamelComponentURIInstance component;
	private Set<PathParamURIInstance> pathParams = new HashSet<>();
	
	public CamelComponentAndPathUriInstance(CamelURIInstance parent, String schemeAndPath, int endPosition) {
		super(0, endPosition);
		this.parent = parent;
		init(schemeAndPath);
	}

	private void init(String schemeAndPath) {
		int posDoubleDot = schemeAndPath.indexOf(':');
		if (posDoubleDot > 0) {
			component = new CamelComponentURIInstance(this, schemeAndPath.substring(0, posDoubleDot), posDoubleDot);
			int posEndofPathParams = getPosEndOfPathParams(posDoubleDot, schemeAndPath);
			initPathParams(schemeAndPath, posDoubleDot, posEndofPathParams);
		} else {
			component = new CamelComponentURIInstance(this, schemeAndPath, schemeAndPath.length());
		}
	}

	private void initPathParams(String uriToParse, int posDoubleDot, int posEndofPathParams) {
		String[] allPathParams = uriToParse.substring(posDoubleDot + 1, posEndofPathParams).split(CAMEL_PATH_SEPARATOR_REGEX);
		int currentPosition = posDoubleDot + 1;
		for (String pathParam : allPathParams) {
			pathParams.add(new PathParamURIInstance(this, pathParam, currentPosition, currentPosition+pathParam.length()));
			currentPosition += pathParam.length() + 1;
		}
	}
	
	private int getPosEndOfPathParams(int posDoubleDot, String uriToParse) {
		int questionMarkPosition = uriToParse.indexOf('?', posDoubleDot);
		if (questionMarkPosition > 0) {
			return questionMarkPosition;
		} else {
			return uriToParse.length();
		}
	}
	
	public CamelComponentURIInstance getComponent() {
		return component;
	}

	public Set<PathParamURIInstance> getPathParams() {
		return pathParams;
	}

	public CamelUriElementInstance getSpecificElement(int position) {
		if (component != null && component.isInRange(position)) {
			return component;
		} else {
			for (PathParamURIInstance pathParamURIInstance : pathParams) {
				if(pathParamURIInstance.isInRange(position)) {
					return pathParamURIInstance;
				}
			}
		}
		return this;
	}
	
	@Override
	public CompletableFuture<List<CompletionItem>> getCompletions(CompletableFuture<CamelCatalog> camelCatalog, int positionInCamelUri, TextDocumentItem docItem) {
		if(getStartPositionInUri() <= positionInCamelUri && positionInCamelUri <= getEndPositionInUri()) {
			return camelCatalog.thenApply(new CamelComponentSchemesCompletionsFuture(this, getFilter(positionInCamelUri), docItem));
		} else {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
	}

	/**
	 * returns the filter string to be applied on the list of all completions
	 * 
	 * @param positionInUri	the position
	 * @return	the filter string or null if not to be filtered
	 */
	private String getFilter(int positionInUri) {
		String componentName = getComponent().getComponentName();
		if (componentName != null && componentName.trim().length()>0 && getStartPositionInUri() != positionInUri) {
			String filter = componentName.substring(getStartPositionInUri(), positionInUri < componentName.length() ? positionInUri : componentName.length());
			// if cursor is behind the ":" then we add it to the filter to exclude other components with the same starting chars like ahc and ahc-https
			if (positionInUri-getStartPositionInUri() > componentName.length()) {
				filter += ":";
			}
			return filter;
		}		
		return null;
	}
	
	@Override
	public String getComponentName() {
		return component != null ? component.getComponentName() : null;
	}

	@Override
	public String getDescription(ComponentModel componentModel) {
		return null;
	}
	
	@Override
	public CamelURIInstance getCamelUriInstance() {
		return parent.getCamelUriInstance();
	}
}
