/**
 * Copyright (c) 2016 LARUS Business Automation [http://www.larus-ba.it]
 * <p>
 * This file is part of the "LARUS Integration Framework for Neo4j".
 * <p>
 * The "LARUS Integration Framework for Neo4j" is licensed
 * under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.larusba.integration.neo4j.jsonloader.transformer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import it.larusba.integration.common.document.bean.JsonDocument;
import it.larusba.integration.neo4j.jsonloader.bean.DocumentNode;
import it.larusba.integration.neo4j.jsonloader.support.CypherGenerator;
import it.larusba.integration.neo4j.jsonloader.util.JsonObjectDescriptorHelper;

/**
 * @author Riccardo Birello
 */
public class DomainDrivenJsonTransformer implements JsonTransformer<String> {

	/*
	 * (non-Javadoc)
	 * 
	 * @see it.larusba.integration.neo4j.jsonloader.transformer.JsonTransformer#
	 * transform(it.larusba.integration.neo4j.jsonloader.bean.JsonDocument)
	 */
	@Override
	public String transform(JsonDocument jsonDocument) throws JsonParseException, JsonMappingException, IOException {
		Map<String, Object> documentMap = new ObjectMapper().readValue(jsonDocument.getContent(),
		    new TypeReference<Map<String, Object>>() {
		    });
		JsonObjectDescriptorHelper jsonObjectDescriptorHelper = new JsonObjectDescriptorHelper(
		    jsonDocument.getObjectDescriptors());
		int startIndex = 0;
		DocumentNode node = transform(jsonDocument.getId(), jsonDocument.getType(), documentMap,
		    jsonObjectDescriptorHelper, startIndex);
		return getAllStatementsAsString(getAllStatements(Arrays.asList(node)));
	}

	/**
	 * The method transforms the JSON map.
	 * 
	 * @param documentId
	 *          the document ID
	 * @param documentType
	 *          the type of the document
	 * @param documentMap
	 *          the document's map
	 * @param objectDescriptorHelper
	 *          the descriptor helper
	 * @param index the index
	 * @return the node
	 */
	private DocumentNode transform(String documentId, String documentType, Map<String, Object> documentMap,
	    JsonObjectDescriptorHelper objectDescriptorHelper, int index) {
		String nodeLabel = buildNodeLabel(documentType, documentMap, objectDescriptorHelper);
		String nodeName = nodeLabel.toLowerCase(Locale.ITALY);
		DocumentNode node = new DocumentNode(documentId, documentType, nodeName, nodeLabel);
		node.setDepth(index);
		index++;
		for (String attributeName : documentMap.keySet()) {
			Object attributeValue = documentMap.get(attributeName);
			if (attributeValue instanceof Map) {
				handleMap(documentId, objectDescriptorHelper, node, attributeName, attributeValue, index);
			} else if (attributeValue instanceof List) {
				handleList(documentId, objectDescriptorHelper, node, attributeName, attributeValue, index);
			} else {
				handleSimpleAttribute(objectDescriptorHelper, node, attributeName, attributeValue);
			}
		}
		node.setName(node.getQualifiedName());
		return node;
	}

	/**
	 * The method handles a simple attribute.
	 * 
	 * @param objectDescriptorHelper
	 *          the descriptor helper
	 * @param node
	 *          the current node
	 * @param attributeName
	 *          the attribute name
	 * @param attributeValue
	 *          the attribute value
	 */
	private void handleSimpleAttribute(JsonObjectDescriptorHelper objectDescriptorHelper, DocumentNode node,
	    String attributeName, Object attributeValue) {
		if (objectDescriptorHelper.isAttributeInUniqueKey(node.getType(), attributeName)) {
			node.addKey(attributeName, attributeValue);
		} else {
			node.addAttribute(attributeName, attributeValue);
		}
	}

	/**
	 * The method handleList.
	 * 
	 * @param documentId
	 *          the document ID
	 * @param objectDescriptorHelper
	 *          the descriptor helper
	 * @param node
	 *          the current node
	 * @param attributeName
	 *          the attribute name
	 * @param attributeValue
	 *          the attribute value
	 * @param index the index
	 */
	@SuppressWarnings("unchecked")
	private void handleList(String documentId, JsonObjectDescriptorHelper objectDescriptorHelper, DocumentNode node,
	    String attributeName, Object attributeValue, int index) {
		List<Object> list = (List<Object>) attributeValue;
		if (!list.isEmpty()) {
			for (Object object : list) {
				if (object instanceof Map) {
					handleMap(documentId, objectDescriptorHelper, node, attributeName, object, index);
				} else {
					node.addListAttribute(attributeName, object);
				}
			}
		}
	}

	/**
	 * The method handleMap.
	 * 
	 * @param documentId
	 *          the document ID
	 * @param objectDescriptorHelper
	 *          the descriptor helper
	 * @param node
	 *          the current node
	 * @param attributeName
	 *          the attribute name
	 * @param attributeValue
	 *          the attribute value
	 * @param index the index
	 */
	@SuppressWarnings("unchecked")
	private void handleMap(String documentId, JsonObjectDescriptorHelper objectDescriptorHelper, DocumentNode node,
	    String attributeName, Object attributeValue, int index) {
		String type = buildNodeLabel(attributeName, (Map<String, Object>) attributeValue, objectDescriptorHelper);
		DocumentNode childNode = transform(documentId, type, (Map<String, Object>) attributeValue, objectDescriptorHelper, index);
		childNode.setParentPropertyName(attributeName);
        node.addOutgoingRelation(childNode);
	}

	/**
	 * The method builds the label of the node.
	 * 
	 * @param documentType
	 *          the type of document
	 * @param documentMap
	 *          the document's map
	 * @param objectDescriptorHelper
	 *          the descriptor helper
	 * @return the label of the node
	 */
	private String buildNodeLabel(String documentType, Map<String, Object> documentMap,
	    JsonObjectDescriptorHelper objectDescriptorHelper) {
		String typeAttribute = (String) documentMap.get(objectDescriptorHelper.getTypeAttribute(documentType));
		if (StringUtils.isBlank(typeAttribute)) {
			typeAttribute = StringUtils.lowerCase(documentType);
		}
		return StringUtils.capitalize(typeAttribute);
	}

	/**
	 * The method get all node's statements.
	 * 
	 * @param nodes
	 *          the nodes list
	 * @return the statements
	 */
	private Set<String> getAllNodeStatements(List<DocumentNode> nodes) {
		Set<String> nodesSet = new HashSet<>();
		for (DocumentNode node : nodes) {
			nodesSet.add(CypherGenerator.generateNodeStatement(node));
			nodesSet.addAll(getAllNodeStatements(node.getOutgoingRelations()));
		}
		return nodesSet;
	}

	/**
	 * The method get all relations statements.
	 * 
	 * @param nodes
	 *          the nodes list
	 * @return the statements
	 */
	private Set<String> getAllRelationsStatements(List<DocumentNode> nodes) {
		Set<String> nodesRelationsSet = new HashSet<>();
		for (DocumentNode node : nodes) {
			Set<String> stringOutgoingRelations = CypherGenerator.generateOutgoingRelationsStatements(node);
			if (stringOutgoingRelations != null) {
				nodesRelationsSet.addAll(stringOutgoingRelations);
			}
			nodesRelationsSet.addAll(getAllRelationsStatements(node.getOutgoingRelations()));
		}
		return nodesRelationsSet;
	}

	/**
	 * The method gets all statements, ordering first the nodes statements e after
	 * the relations.
	 * 
	 * @param nodes
	 *          the node's list
	 * @return the ordered statements list
	 */
	private List<String> getAllStatements(List<DocumentNode> nodes) {
		Set<String> nodesSet = getAllNodeStatements(nodes);
		Set<String> nodesRelationsSet = getAllRelationsStatements(nodes);
		ArrayList<String> result = new ArrayList<>(nodesSet);
		result.addAll(nodesRelationsSet);
		return result;
	}

	/**
	 * This method builds the final generated statement.
	 * 
	 * @param statements
	 *          the ordered statements list
	 * @return the final cypher statement
	 */
	private String getAllStatementsAsString(List<String> statements) {
		String cypher = "";
		for (String statement : statements) {
			cypher += statement + "\n";
		}
		return cypher;
	}
}
