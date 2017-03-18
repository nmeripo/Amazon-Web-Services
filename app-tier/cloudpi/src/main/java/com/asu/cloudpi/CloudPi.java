package com.asu.cloudpi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.mturk.model.transform.SendBonusRequestMarshaller;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;

public class CloudPi implements AWSCredentialsProvider {

	// AWS Credentials
	private static final String ACCESS_KEY_PROPERTY = "aws.accessKeyId";

	// System property name for the AWS secret key
	private static final String SECRET_KEY_PROPERTY = "aws.secretKey";

	// Amazon EC2 Client
	static AmazonEC2 amazonEC2client;
	static String keyName = "aws";
	static String sgName = "default";

	// Amazon S3 Client
	static AmazonS3 s3Client;

	// Amazon SQS
	static AmazonSQS sqs;

	public static void main(String[] args) {
		System.setProperty("aws.accessKeyId", "**************");
		System.setProperty("aws.secretKey", "**********************************");
		AWSCredentials credentials = new CloudPi().getCredentials();

		amazonEC2client = AmazonEC2ClientBuilder.standard().withRegion("us-west-2").build();
		s3Client = new AmazonS3Client(credentials);

		sqs = new AmazonSQSClient(credentials);
		Region usWest2 = Region.getRegion(Regions.US_WEST_2);
		sqs.setRegion(usWest2);

		String inputQueueUrl = "https://sqs.us-west-2.amazonaws.com/564849021211/input.fifo";

		System.out.println("Message: " + args[0]);
		sendMessageToQueue(inputQueueUrl, args[0]);
		System.out.println("Message sent to Queue");

		while (numActiveInstances() >= 11)
			;
		if (numActiveInstances() < 12) {
			while (getNumberOfMsgs(inputQueueUrl) < 1)
				;
			String input = receiveAndDeleteMessageFromQueue(inputQueueUrl);
			createInstance(input);

		}

	}

	public AWSCredentials getCredentials() {
		if (System.getProperty(ACCESS_KEY_PROPERTY) != null && System.getProperty(SECRET_KEY_PROPERTY) != null) {
			return new BasicAWSCredentials(System.getProperty(ACCESS_KEY_PROPERTY),
					System.getProperty(SECRET_KEY_PROPERTY));
		}

		throw new AmazonClientException("Unable to load AWS credentials from Java system properties " + "("
				+ ACCESS_KEY_PROPERTY + " and " + SECRET_KEY_PROPERTY + ")");
	}

	public void refresh() {
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	static int numActiveInstances() {
		int count = 0;
		List<String> instanceIDs = listInstances();
		for (String i : instanceIDs) {
			if (getInstanceStatus(i) == 16 || getInstanceStatus(i) == 0) {
				count++;
			}
		}
		return count;
	}

	public static Integer getInstanceStatus(String instanceId) {
		DescribeInstancesRequest describeInstanceRequest = new DescribeInstancesRequest().withInstanceIds(instanceId);
		DescribeInstancesResult describeInstanceResult = amazonEC2client.describeInstances(describeInstanceRequest);
		InstanceState state = describeInstanceResult.getReservations().get(0).getInstances().get(0).getState();
		return state.getCode();
	}

	static void createInstance(String input) {
		System.out.println("Creating Instance");
		String userData = "#!/bin/bash\ncd /root\n./pifft " + input + " >> " + input + ".txt\naws s3 cp " + input
				+ ".txt s3://cloudpi-requests/" + input + ".txt";
		String formattedString = Base64.encodeBase64String(userData.getBytes());
		RunInstancesRequest run = new RunInstancesRequest();

		run.withImageId("ami-81b23ce1").withInstanceType("t2.micro").withMinCount(1).withMaxCount(1)
				.withKeyName(keyName).withSecurityGroups(sgName).withUserData(formattedString);

		RunInstancesResult result = amazonEC2client.runInstances(run);

		String instanceId = result.toString();
		instanceId = instanceId.substring(instanceId.lastIndexOf(" i-") + 1);
		instanceId = instanceId.substring(0, 19);
		System.out.println(instanceId);

		String bucketname = "cloudpi-requests";
		String mys3object = input + ".txt";

		boolean exists = s3Client.doesObjectExist(bucketname, mys3object);

		while (!exists) {
			exists = s3Client.doesObjectExist(bucketname, mys3object);
		}

		System.out.println("Terminating Instance");
		terminateInstance(instanceId);

	}

	static void terminateInstance(String instanceId) {
		TerminateInstancesRequest terminate = new TerminateInstancesRequest();

		terminate.withInstanceIds(instanceId);

		TerminateInstancesResult result = amazonEC2client.terminateInstances(terminate);

		System.out.println("Instance Terminated");
	}

	static List<String> listInstances() {
		List<String> instances = new ArrayList<String>();
		DescribeInstancesResult result = amazonEC2client.describeInstances();
		List<Reservation> listReservations = result.getReservations();
		for (Reservation res : listReservations) {
			List<Instance> listInstances = res.getInstances();
			for (Instance i : listInstances) {
				String instanceId = i.toString();
				instanceId = instanceId.substring(instanceId.lastIndexOf(" i-") + 1);
				instanceId = instanceId.substring(0, 19);
				instances.add(instanceId);
			}
		}
		return instances;
	}

	static void sendMessageToQueue(String myQueueUrl, String input) {
		SendMessageRequest sendMessageRequest = new SendMessageRequest(myQueueUrl, input);
		sendMessageRequest.setMessageGroupId("007");
		SendMessageResult sendMessageResult = sqs.sendMessage(sendMessageRequest);
	}

	static String receiveAndDeleteMessageFromQueue(String myQueueUrl) {
		System.out.println("Receiving message from Input Queue");
		ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(myQueueUrl);
		List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
		String input = "";
		for (Message message : messages) {
			input = message.getBody();
			System.out.println("Message Body: " + input);
		}

		System.out.println("Deleting message from Input Queue");

		String messageReceiptHandle = messages.get(0).getReceiptHandle();
		sqs.deleteMessage(new DeleteMessageRequest(myQueueUrl, messageReceiptHandle));
		return input;
	}

	static int getNumberOfMsgs(String myQueueUrl) {
		GetQueueAttributesRequest request = new GetQueueAttributesRequest();
		request = request.withAttributeNames("ApproximateNumberOfMessages");
		request = request.withQueueUrl(myQueueUrl);

		Map<String, String> attrs = sqs.getQueueAttributes(request).getAttributes();
		int messages = Integer.parseInt(attrs.get("ApproximateNumberOfMessages"));
		return messages;
	}

}
