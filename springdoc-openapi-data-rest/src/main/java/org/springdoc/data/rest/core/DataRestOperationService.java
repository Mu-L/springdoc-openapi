/*
 *
 *  *
 *  *  *
 *  *  *  * Copyright 2019-2020 the original author or authors.
 *  *  *  *
 *  *  *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  *  *  * you may not use this file except in compliance with the License.
 *  *  *  * You may obtain a copy of the License at
 *  *  *  *
 *  *  *  *      https://www.apache.org/licenses/LICENSE-2.0
 *  *  *  *
 *  *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  *  *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  *  * See the License for the specific language governing permissions and
 *  *  *  * limitations under the License.
 *  *  *
 *  *
 *
 *
 */

package org.springdoc.data.rest.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.MethodAttributes;
import org.springdoc.core.OperationService;
import org.springdoc.core.SpringDocAnnotationsUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.mapping.MethodResourceMapping;
import org.springframework.data.rest.core.mapping.ParameterMetadata;
import org.springframework.data.rest.core.mapping.ParametersMetadata;
import org.springframework.data.rest.core.mapping.ResourceDescription;
import org.springframework.data.rest.core.mapping.ResourceMetadata;
import org.springframework.data.rest.core.mapping.TypedResourceDescription;
import org.springframework.data.rest.webmvc.support.DefaultedPageable;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;

/**
 * The type Data rest operation builder.
 * 
 * @author bnasslahsen
 */
public class DataRestOperationService {

	/**
	 * The constant LOGGER.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(DataRestOperationService.class);

	/**
	 * The constant STRING_SEPARATOR.
	 */
	private static final String STRING_SEPARATOR = "-";

	/**
	 * The Data rest request builder.
	 */
	private DataRestRequestService dataRestRequestService;

	/**
	 * The Tags builder.
	 */
	private DataRestTagsService tagsBuilder;

	/**
	 * The Data rest response builder.
	 */
	private DataRestResponseService dataRestResponseService;

	/**
	 * The Operation service.
	 */
	private OperationService operationService;

	/**
	 * Instantiates a new Data rest operation builder.
	 * @param dataRestRequestService the data rest request builder
	 * @param tagsBuilder the tags builder
	 * @param dataRestResponseService the data rest response builder
	 */
	public DataRestOperationService(DataRestRequestService dataRestRequestService, DataRestTagsService tagsBuilder,
			DataRestResponseService dataRestResponseService, OperationService operationService) {
		this.dataRestRequestService = dataRestRequestService;
		this.tagsBuilder = tagsBuilder;
		this.dataRestResponseService = dataRestResponseService;
		this.operationService = operationService;
	}

	/**
	 * @param operationPath the operation path
	 * @param requestMethod the request method
	 * @param handlerMethod the handler method
	 * @param methodAttributes the method attributes
	 * @param resourceMetadata the resource metadata
	 * @param openAPI the open api
	 * @return the operation
	 */
	public Operation buildCustomOperation(String operationPath, RequestMethod requestMethod,
			HandlerMethod handlerMethod, MethodAttributes methodAttributes, ResourceMetadata resourceMetadata,
			OpenAPI openAPI) {
		Class<?> returnType = handlerMethod.getReturnType().getParameterType();

		Operation operation = initOperation(handlerMethod, returnType, requestMethod);

		dataRestRequestService.buildParameters(returnType, openAPI, handlerMethod, requestMethod, methodAttributes,
				operation, resourceMetadata);
		dataRestResponseService.buildEntityResponse(operation, handlerMethod, openAPI, requestMethod, operationPath,
				returnType, methodAttributes);
		tagsBuilder.buildCustomTags(operation, returnType, handlerMethod);

		addOperationDescription(operation, requestMethod, returnType.getSimpleName().toLowerCase());
		return operation;
	}

	/**
	 * Build operation operation.
	 * @param handlerMethod the handler method
	 * @param dataRestRepository the repository data rest
	 * @param openAPI the open api
	 * @param requestMethod the request method
	 * @param operationPath the operation path
	 * @param methodAttributes the method attributes
	 * @param resourceMetadata the resource metadata
	 * @param methodResourceMapping the method resource mapping
	 * @param controllerType the controller type
	 * @return the operation
	 */
	public Operation buildOperation(HandlerMethod handlerMethod, DataRestRepository dataRestRepository, OpenAPI openAPI,
			RequestMethod requestMethod, String operationPath, MethodAttributes methodAttributes,
			ResourceMetadata resourceMetadata, MethodResourceMapping methodResourceMapping,
			ControllerType controllerType) {
		Operation operation = null;
		if (ControllerType.ENTITY.equals(controllerType) || ControllerType.PROPERTY.equals(controllerType)
				|| ControllerType.SCHEMA.equals(controllerType)) {
			operation = buildEntityOperation(handlerMethod, dataRestRepository, openAPI, requestMethod, operationPath,
					methodAttributes, resourceMetadata);
		}
		else if (ControllerType.SEARCH.equals(controllerType)) {
			operation = buildSearchOperation(handlerMethod, dataRestRepository, openAPI, requestMethod,
					methodAttributes, methodResourceMapping);
		}
		return operation;
	}

	/**
	 * Build entity operation operation.
	 * @param handlerMethod the handler method
	 * @param dataRestRepository the repository data rest
	 * @param openAPI the open api
	 * @param requestMethod the request method
	 * @param operationPath the operation path
	 * @param methodAttributes the method attributes
	 * @param resourceMetadata the resource metadata
	 * @return the operation
	 */
	private Operation buildEntityOperation(HandlerMethod handlerMethod, DataRestRepository dataRestRepository,
			OpenAPI openAPI, RequestMethod requestMethod, String operationPath, MethodAttributes methodAttributes,
			ResourceMetadata resourceMetadata) {
		Class<?> domainType = null;
		if (dataRestRepository != null)
			domainType = dataRestRepository.getDomainType();
		Operation operation = initOperation(handlerMethod, domainType, requestMethod);
		dataRestRequestService.buildParameters(domainType, openAPI, handlerMethod, requestMethod, methodAttributes,
				operation, resourceMetadata);
		dataRestResponseService.buildEntityResponse(operation, handlerMethod, openAPI, requestMethod, operationPath,
				domainType, methodAttributes);
		tagsBuilder.buildEntityTags(operation, handlerMethod, dataRestRepository);
		if (domainType != null)
			addOperationDescription(operation, requestMethod, domainType.getSimpleName().toLowerCase());
		return operation;
	}

	/**
	 * Build search operation operation.
	 * @param handlerMethod the handler method
	 * @param dataRestRepository the repository data rest
	 * @param openAPI the open api
	 * @param requestMethod the request method
	 * @param methodAttributes the method attributes
	 * @param methodResourceMapping the method resource mapping
	 * @return the operation
	 */
	private Operation buildSearchOperation(HandlerMethod handlerMethod, DataRestRepository dataRestRepository,
			OpenAPI openAPI, RequestMethod requestMethod, MethodAttributes methodAttributes,
			MethodResourceMapping methodResourceMapping) {
		Class<?> domainType = dataRestRepository.getDomainType();
		Operation operation = initOperation(handlerMethod, domainType, requestMethod);

		// Add support for operation annotation
		io.swagger.v3.oas.annotations.Operation apiOperation = AnnotatedElementUtils
				.findMergedAnnotation(methodResourceMapping.getMethod(), io.swagger.v3.oas.annotations.Operation.class);

		if (apiOperation != null)
			operationService.parse(apiOperation, operation, openAPI, methodAttributes);

		ParametersMetadata parameterMetadata = methodResourceMapping.getParametersMetadata();
		Method method = methodResourceMapping.getMethod();

		if (!CollectionUtils.isEmpty(parameterMetadata.getParameterNames())) {
			HandlerMethod repositoryHandlerMethod = new HandlerMethod(
					methodResourceMapping.getMethod().getDeclaringClass(), methodResourceMapping.getMethod());
			MethodParameter[] parameters = repositoryHandlerMethod.getMethodParameters();
			for (MethodParameter methodParameter : parameters) {
				dataRestRequestService.buildCommonParameters(domainType, openAPI, requestMethod, methodAttributes,
						operation, new String[] { methodParameter.getParameterName() },
						new MethodParameter[] { methodParameter });
			}
		}

		for (ParameterMetadata parameterMetadatum : parameterMetadata) {
			String pName = parameterMetadatum.getName();
			ResourceDescription description = parameterMetadatum.getDescription();
			if (description instanceof TypedResourceDescription) {
				TypedResourceDescription typedResourceDescription = (TypedResourceDescription) description;
				Field fieldType = FieldUtils.getField(TypedResourceDescription.class, "type", true);
				Class<?> type;
				try {
					type = (Class<?>) fieldType.get(typedResourceDescription);
				}
				catch (IllegalAccessException e) {
					LOGGER.warn(e.getMessage());
					type = String.class;
				}
				io.swagger.v3.oas.models.media.Schema<?> schema = SpringDocAnnotationsUtils.resolveSchemaFromType(type,
						openAPI.getComponents(), null, null);
				Parameter parameter = getParameterFromAnnotations(openAPI, methodAttributes, method, pName);
				if (parameter == null)
					parameter = new Parameter().name(pName).in(ParameterIn.QUERY.toString()).schema(schema);
				operation.addParametersItem(parameter);
			}
		}

		if (methodResourceMapping.isPagingResource()) {
			MethodParameter[] parameters = handlerMethod.getMethodParameters();
			Arrays.stream(parameters)
					.filter(methodParameter -> DefaultedPageable.class.equals(methodParameter.getParameterType()))
					.findAny()
					.ifPresent(methodParameterPage -> dataRestRequestService.buildCommonParameters(domainType, openAPI,
							requestMethod, methodAttributes, operation,
							new String[] { methodParameterPage.getParameterName() },
							new MethodParameter[] { methodParameterPage }));
		}
		dataRestResponseService.buildSearchResponse(operation, handlerMethod, openAPI, methodResourceMapping,
				domainType, methodAttributes);
		tagsBuilder.buildSearchTags(operation, handlerMethod, dataRestRepository);
		return operation;
	}

	/**
	 * Update parameter from annotations parameter.
	 * @param openAPI the open api
	 * @param methodAttributes the method attributes
	 * @param method the method
	 * @param pName the p name
	 * @return the parameter
	 */
	private Parameter getParameterFromAnnotations(OpenAPI openAPI, MethodAttributes methodAttributes, Method method,
			String pName) {
		Parameter parameter = null;
		for (java.lang.reflect.Parameter reflectParameter : method.getParameters()) {
			Param paramAnnotation = reflectParameter.getAnnotation(Param.class);
			if (paramAnnotation != null && paramAnnotation.value().equals(pName)) {
				io.swagger.v3.oas.annotations.Parameter parameterDoc = AnnotatedElementUtils.findMergedAnnotation(
						AnnotatedElementUtils.forAnnotations(reflectParameter.getAnnotations()),
						io.swagger.v3.oas.annotations.Parameter.class);
				if (parameterDoc != null && (!parameterDoc.hidden() || parameterDoc.schema().hidden())) {
					parameter = dataRestRequestService.buildParameterFromDoc(parameterDoc, openAPI.getComponents(),
							methodAttributes.getJsonViewAnnotation());
					parameter.setName(pName);
				}
			}
		}
		return parameter;
	}

	/**
	 * Init operation operation.
	 * @param handlerMethod the handler method
	 * @param domainType the domain type
	 * @param requestMethod the request method
	 * @return the operation
	 */
	private Operation initOperation(HandlerMethod handlerMethod, Class<?> domainType, RequestMethod requestMethod) {
		Operation operation = new Operation();
		StringBuilder operationIdBuilder = new StringBuilder();
		operationIdBuilder.append(handlerMethod.getMethod().getName());
		if (domainType != null) {
			operationIdBuilder.append(STRING_SEPARATOR).append(domainType.getSimpleName().toLowerCase())
					.append(STRING_SEPARATOR).append(requestMethod.toString().toLowerCase());
		}
		operation.setOperationId(operationIdBuilder.toString());
		return operation;
	}

	/**
	 * Add operation description.
	 * @param operation the operation
	 * @param requestMethod the request method
	 * @param entity the entity
	 */
	private void addOperationDescription(Operation operation, RequestMethod requestMethod, String entity) {
		switch (requestMethod) {
		case GET:
			operation.setDescription("get-" + entity);
			break;
		case POST:
			operation.setDescription("create-" + entity);
			break;
		case DELETE:
			operation.setDescription("delete-" + entity);
			break;
		case PUT:
			operation.setDescription("update-" + entity);
			break;
		case PATCH:
			operation.setDescription("patch-" + entity);
			break;
		default:
			throw new IllegalArgumentException(requestMethod.name());
		}
	}

}
