package io.pivotal.cdm.aws;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.log4j.Logger;
import org.cloudfoundry.community.servicebroker.exception.ServiceBrokerException;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;

public class AWSHelper {
	private Logger log = Logger.getLogger(AWSHelper.class);

	public AmazonEC2Client ec2Client;

	private String subnetId;

	/**
	 * Create a new pattern interface to AWS.
	 * 
	 * @param ec2Client
	 * @param subnetId
	 */
	public AWSHelper(AmazonEC2Client ec2Client, String subnetId) {
		this.ec2Client = ec2Client;
		this.subnetId = subnetId;
	}

	public void deregisterAMI(String ami) {
		log.info("Deregistering AMI " + ami);
		ec2Client
				.deregisterImage(new DeregisterImageRequest().withImageId(ami));
	}

	public void terminateEc2Instance(String ec2Instance) {
		log.info("Terminating instance " + ec2Instance);
		ec2Client.terminateInstances(new TerminateInstancesRequest()
				.withInstanceIds(Arrays.asList(ec2Instance)));
	}

	/**
	 * Given an AMI start an EC2 Instance.
	 * 
	 * @param amiId
	 * @return the id of the running instance.
	 */
	public String startEC2Instance(String amiId) {
		RunInstancesResult instance = ec2Client
				.runInstances(new RunInstancesRequest().withImageId(amiId)
						.withInstanceType("m1.small").withMinCount(1)
						.withMaxCount(1).withSubnetId(subnetId)
						.withInstanceType(InstanceType.T2Micro));

		String instanceId = getInstanceId(instance);
		log.info("Started instance:" + instanceId);
		return instanceId;
	}

	/**
	 * Build an AMI of a running EC2Instance. Creates a snap which is
	 * disassociated and tracked via a the description.
	 * 
	 * @param sourceInstance
	 *            the EC2 instance to create an AMI from
	 * @param description
	 *            to shove in the console so you know what your looking at
	 * @return id of the ami
	 * @throws ServiceBrokerException
	 *             if the ami isn't available in time.
	 * 
	 * @see #deleteSnapshotsForImage(String)
	 */
	public String createAMI(String sourceInstance, String description)
			throws ServiceBrokerException {
		CreateImageResult imageResult = ec2Client
				.createImage(new CreateImageRequest()
						.withInstanceId(sourceInstance)
						.withDescription(description)
						.withName(
								sourceInstance + "-"
										+ System.currentTimeMillis()));

		String amiId = imageResult.getImageId();
		if (!waitForImage(amiId)) {
			throw new ServiceBrokerException(
					"Timed out waiting for amazon to create AMI " + amiId);
		}
		log.info("Created new AMI with ID: " + amiId);
		return amiId;
	}

	/**
	 * Find the snap associated with the AMI we used and delete it. AWS doesn't
	 * help us out much and the only relationship (as of 2/14/2015) we can
	 * leverage is the description field.
	 * 
	 * TODO we could make the snap ourselves and track it that way.
	 * 
	 * TODO As it stands this method is not very generalizable. Address if
	 * packaging separately.
	 * 
	 * @param ami
	 *            to find associated snaps for
	 * @throws ServiceBrokerExceptions
	 */
	public void deleteSnapshotsForImage(String ami)
			throws ServiceBrokerException {

		DescribeSnapshotsResult desc = ec2Client.describeSnapshots();
		if (null == desc.getSnapshots()) {
			return;
		}
		List<Snapshot> snapshots = desc.getSnapshots();

		// The only way I can find to track the snaps that get created (but not
		// cleaned up) as part of the ami creation is by the description. This
		// code is brittle and will probably fail in unexpected and glamorous
		// ways.
		String amiDesc = "Created by CreateImage(i-bf72d345) for " + ami
				+ " from vol";
		List<Snapshot> matching = snapshots.stream()
				.filter(s -> safeContain(s::getDescription, amiDesc))
				.collect(Collectors.toList());
		if (matching.size() > 1) {
			throw new ServiceBrokerException(
					"Found too many snapshots for AMI " + ami);
		}
		if (1 == matching.size()) {
			String snap = matching.get(0).getSnapshotId();
			log.info("Deleting snapshot " + snap);
			ec2Client.deleteSnapshot(new DeleteSnapshotRequest()
					.withSnapshotId(snap));
		}
	}

	boolean safeContain(Callable<String> s, String c) {
		try {
			return (null == s.call()) ? false : s.call().contains(c);
		} catch (Exception e) {
			log.error(e);
		}
		return false;
	}

	private boolean waitForImage(String imageId) {

		return IntStream.range(0, 5).anyMatch(new IntPredicate() {
			@Override
			public boolean test(int i) {
				String imageState = getImageState(imageId);
				log.info("Image state is " + imageState);
				switch (imageState) {
				case "available":
					return true;
				case "failed":
					return false;
				default:
					log.info("Waiting 30s more for AMI " + imageId);
					sleep();
				}
				return false;
			}
		});
	}

	private void sleep() {
		try {
			Thread.sleep(30000);
		} catch (InterruptedException e) {
			log.error(e);
		}
	}

	private String getInstanceId(RunInstancesResult instance) {
		return instance.getReservation().getInstances().get(0).getInstanceId();
	}

	private String getImageState(String imageId) {
		String state = "failed";
		DescribeImagesResult result = ec2Client
				.describeImages(new DescribeImagesRequest()
						.withImageIds(imageId));
		if (null != result && null != result.getImages()
				&& !result.getImages().isEmpty()) {
			state = result.getImages().get(0).getState();
		}
		return state;
	}
}