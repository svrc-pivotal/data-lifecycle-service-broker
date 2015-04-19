package io.pivotal.cdm.controller;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import io.pivotal.cdm.CdmServiceBrokerApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import com.jayway.restassured.RestAssured;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = CdmServiceBrokerApplication.class)
@WebAppConfiguration
@IntegrationTest("server.port:0")
@Category(IntegrationTest.class)
public class SanitizeIntegrationTest {

	@Value("${local.server.port}")
	private int port;

	@Value("#{environment.SECURITY_USER_NAME}")
	private String username;

	@Value("#{environment.SECURITY_USER_PASSWORD}")
	private String password;

	private String script = "drop * from table";

	private String location = "/api/sanitizescript";

	@Before
	public void setUp() {
		RestAssured.port = port;
	}

	@Test
	public void itSetsAndGetsTheScript() {

		given().auth().basic(username, password).and().content(script)
				.post(location).then().statusCode(HttpStatus.CREATED.value())
				.and().header("Location", containsString(location));

		given().auth().basic(username, password).and().get(location).then()
				.statusCode(HttpStatus.OK.value()).and()
				.body("script", equalTo(script));

	}

}
