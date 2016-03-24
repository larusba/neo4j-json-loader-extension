package it.larusba.integration.neo4j.jsonloader.transformer.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.junit.Test;

import it.larusba.integration.neo4j.jsonloader.bean.JsonDocument;
import it.larusba.integration.neo4j.jsonloader.bean.JsonObjectDescriptor;
import it.larusba.integration.neo4j.jsonloader.mapping.JsonMappingStrategy;

public class NodeJsonTransformerTest {

	@Test
	public void test() throws Exception {
		String content = IOUtils.toString(getClass().getResourceAsStream("/json/album-mini.json"));
		String id = String.valueOf(System.currentTimeMillis());
		String type = "Album";
		List<JsonObjectDescriptor> descriptors = new ArrayList<>();
		descriptors.add(new JsonObjectDescriptor(type, Arrays.asList("id", "type"), "type"));
		JsonDocument jsonDocument = new JsonDocument(id, type, content, JsonMappingStrategy.DOMAIN_DRIVEN, descriptors);
		NodeJsonTransformer transformer = new NodeJsonTransformer();
		long start = System.currentTimeMillis();
		Set<String> transformedSet = transformer.transform(jsonDocument);
		long end = System.currentTimeMillis();
		System.out.println("---------RESULT---------");
		System.out.println(transformedSet);
		System.out.println("Time elapsed: " + DurationFormatUtils.formatDurationHMS(end - start));
	}
}
