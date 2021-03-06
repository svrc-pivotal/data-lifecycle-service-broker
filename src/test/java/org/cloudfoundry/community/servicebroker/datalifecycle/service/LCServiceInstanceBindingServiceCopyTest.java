package org.cloudfoundry.community.servicebroker.datalifecycle.service;

import static org.cloudfoundry.community.servicebroker.datalifecycle.config.LCCatalogConfig.COPY;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import org.cloudfoundry.community.servicebroker.datalifecycle.dto.InstancePair;
import org.cloudfoundry.community.servicebroker.datalifecycle.model.BrokerAction;
import org.cloudfoundry.community.servicebroker.datalifecycle.provider.CopyProvider;
import org.cloudfoundry.community.servicebroker.datalifecycle.repo.BrokerActionRepository;
import org.cloudfoundry.community.servicebroker.datalifecycle.service.LCServiceInstanceBindingManager;
import org.cloudfoundry.community.servicebroker.datalifecycle.service.LCServiceInstanceBindingService;
import org.cloudfoundry.community.servicebroker.datalifecycle.service.LCServiceInstanceService;
import org.cloudfoundry.community.servicebroker.exception.ServiceBrokerException;
import org.cloudfoundry.community.servicebroker.exception.ServiceInstanceBindingExistsException;
import org.cloudfoundry.community.servicebroker.model.CreateServiceInstanceBindingRequest;
import org.cloudfoundry.community.servicebroker.model.CreateServiceInstanceRequest;
import org.cloudfoundry.community.servicebroker.model.DeleteServiceInstanceBindingRequest;
import org.cloudfoundry.community.servicebroker.model.ServiceInstance;
import org.cloudfoundry.community.servicebroker.model.ServiceInstanceBinding;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.amazonaws.services.ec2.AmazonEC2Client;

public class LCServiceInstanceBindingServiceCopyTest {

	@Mock
	AmazonEC2Client ec2Client;

	private LCServiceInstanceBindingService bindingService;

	private ServiceInstance serviceInstance = new ServiceInstance(
			new CreateServiceInstanceRequest("test_service_def_id", COPY,
					"org", "space", false)
					.withServiceInstanceId("test_service_instance_id"));

	private ServiceInstanceBinding bindResult;

	private static String bindingId = "test_binding_copy";

	@Mock
	private CopyProvider provider;

	@Mock
	private LCServiceInstanceService instanceService;

	@Mock
	private BrokerActionRepository actionRepo;

	@Mock
	LCServiceInstanceBindingManager bindingManager;

	private CreateServiceInstanceBindingRequest createServiceInstanceBindingRequest;

	@Before
	public void setUp() throws ServiceInstanceBindingExistsException,
			ServiceBrokerException {
		MockitoAnnotations.initMocks(this);

		bindingService = new LCServiceInstanceBindingService(provider,
				instanceService, actionRepo, bindingManager);

		createServiceInstanceBindingRequest = new CreateServiceInstanceBindingRequest(
				"postgrescdm", COPY, "test_app").withBindingId(bindingId).and()
				.withServiceInstanceId(serviceInstance.getServiceInstanceId());
	}

	@Test
	public void itShouldProvideTheCopyCreds() throws Exception {

		Map<String, Object> testCreds = new HashMap<String, Object>();
		testCreds.put("uri", "test_uri");

		when(provider.getCreds("test_instance")).thenReturn(testCreds);

		when(
				instanceService.getInstanceIdForServiceInstance(serviceInstance
						.getServiceInstanceId())).thenReturn("test_instance");

		bindResult = bindingService
				.createServiceInstanceBinding(createServiceInstanceBindingRequest);

		assertThat(bindResult.getId(), is(equalTo(bindingId)));
		assertThat(bindResult.getCredentials().get("uri"),
				is(equalTo("test_uri")));
	}

	@Test
	public void itPlaysItCoolIfItDoesNotHaveAnInstance()
			throws ServiceBrokerException {

		bindingService
				.deleteServiceInstanceBinding(new DeleteServiceInstanceBindingRequest(
						"foo", serviceInstance, "postgrescdm", COPY));
	}

	@Test(expected = ServiceInstanceBindingExistsException.class)
	public void duplicateServiceShouldThrow()
			throws ServiceInstanceBindingExistsException,
			ServiceBrokerException {

		when(bindingManager.getBinding(any())).thenReturn(
				new ServiceInstanceBinding(null, null, null, null, null));

		bindResult = bindingService
				.createServiceInstanceBinding(createServiceInstanceBindingRequest);
	}

	@Test(expected = ServiceInstanceBindingExistsException.class)
	public void itShouldNotBindToTheSameAppTwice()
			throws ServiceInstanceBindingExistsException,
			ServiceBrokerException {

		ArrayList<ServiceInstanceBinding> list = new ArrayList<ServiceInstanceBinding>();
		list.add(new ServiceInstanceBinding("foo", serviceInstance
				.getServiceInstanceId(), null, null, "test_app"));
		when(bindingManager.getBindings()).thenReturn(list);

		bindResult = bindingService
				.createServiceInstanceBinding(createServiceInstanceBindingRequest);
		createServiceInstanceBindingRequest = new CreateServiceInstanceBindingRequest(
				"postgrescdm", COPY, "test_app")
				.withBindingId(bindingId + "foo").and()
				.withServiceInstanceId(serviceInstance.getServiceInstanceId());
		bindResult = bindingService
				.createServiceInstanceBinding(createServiceInstanceBindingRequest);
	}

	public void duplicatePlansWithDifferentServiceInstancesAreGood()
			throws ServiceInstanceBindingExistsException,
			ServiceBrokerException {
		assertNotNull(bindingService
				.createServiceInstanceBinding(createServiceInstanceBindingRequest));
		createServiceInstanceBindingRequest = new CreateServiceInstanceBindingRequest(
				"postgrescdm", COPY, "test_app")
				.withBindingId(bindingId + "foo").and()
				.withServiceInstanceId("Another service instance");
		assertNotNull(bindingService
				.createServiceInstanceBinding(createServiceInstanceBindingRequest));

	}

	@Test
	public void itShouldReturnAppToInstancePairsAndBindToMutipleApps()
			throws ServiceInstanceBindingExistsException,
			ServiceBrokerException {

		when(
				instanceService.getInstanceIdForServiceInstance(serviceInstance
						.getServiceInstanceId())).thenReturn("test_copy");

		List<ServiceInstanceBinding> bindings = buildServiceInstanceBindings();
		when(bindingManager.getBindings()).thenReturn(bindings);

		List<InstancePair> appBindings = bindingService.getAppToCopyBinding();
		assertThat(appBindings, hasSize(3));
		assertTrue(appBindings.contains(new InstancePair("test_app2",
				"test_copy")));
	}

	private List<ServiceInstanceBinding> buildServiceInstanceBindings() {
		List<ServiceInstanceBinding> list = new ArrayList<ServiceInstanceBinding>();

		IntConsumer consumer = new IntConsumer() {
			@Override
			public void accept(int i) {
				createServiceInstanceBindingRequest.withBindingId("bind" + i)
						.setAppGuid("test_app" + i);
				try {
					list.add(bindingService
							.createServiceInstanceBinding(createServiceInstanceBindingRequest));
				} catch (ServiceInstanceBindingExistsException
						| ServiceBrokerException e) {
					fail("Failed to create service instance bindings");
				}
			}
		};
		IntStream.range(1, 4).forEach(consumer);
		return list;
	}

	@Test
	public void itShouldUpdateItsStatusDuringTheBind()
			throws ServiceInstanceBindingExistsException,
			ServiceBrokerException {
		when(bindingManager.getBindings()).thenReturn(
				new ArrayList<ServiceInstanceBinding>());
		bindingService
				.createServiceInstanceBinding(createServiceInstanceBindingRequest);
		verify(actionRepo, times(2)).save(any(BrokerAction.class));
	}
}
