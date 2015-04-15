package io.pivotal.cdm.config;

import java.util.*;

import org.cloudfoundry.community.servicebroker.model.*;
import org.springframework.context.annotation.*;

@Configuration
public class LCCatalogConfig {

	ServiceDefinition postgresServiceDefinition;

	public static String PRODUCTION = "prod";
	public static String COPY = "copy";

	public LCCatalogConfig() {
		List<Plan> plans = new ArrayList<Plan>();
		plans.add(new Plan(PRODUCTION, PRODUCTION, "The production datasource",
				getProdMetadata()));
		plans.add(new Plan(COPY, COPY, "Copy of production datasource",
				getCopyMetadata()));
		postgresServiceDefinition = new ServiceDefinition("lifecycle-sb",
				"lifecycle-sb", "Postgres Running in AWS", true, plans);
		postgresServiceDefinition.setPlanUpdatable(false);
	}

	private Map<String, Object> getProdMetadata() {
		return new HashMap<String, Object>();
	}

	private Map<String, Object> getCopyMetadata() {
		return new HashMap<String, Object>();
	}

	@Bean
	public Catalog catalog() {
		return new Catalog(Arrays.asList(postgresServiceDefinition));
	}
}
