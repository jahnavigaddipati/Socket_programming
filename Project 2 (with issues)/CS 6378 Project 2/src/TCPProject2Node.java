
/**********************************************************************
 * 
 * TCPProject2Node.java
 * 
 * Created for CS6378.001, Fall 2022
 * Created by Jahnavi Gaddipati, Rashmitha Kalliot Yadav, and Benjamin Rubarts
 * 
 * This file contains the source code for executing the distributed system
 * that participates in a mutual exclusion service using either Lamport's
 * protocol or Ricart and Agrawala's protocol. The number of critical section
 * requests per process is decided by configuration file. After all processes
 * have each executed the number of critical sections that matches the number
 * specified in the configuration file, node 0 either reports the performance
 * metrics (response time and system throughput) or compares vector timestamps
 * in files to which each process writes for unwanted concurrency of critical
 * sections, which violates mutual exclusion.
 * 
 ***********************************************************************/

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class TestScriptProject2 {
	private ConfigurationFileParameters cfp;
	private File csTestFile;

	public TestScriptProject2(ConfigurationFileParameters cfp, File csTestFile) {
		this.cfp = cfp;
		this.csTestFile = csTestFile;
	}

	public long inspectForErrors() {
		Map<Integer, Integer> numberOfCSEntries = new HashMap<>();
		boolean state = false; // True- has entered the critical section, False- has left the critical section
		int currentNode = 0;
		long lineCount = 0;
		long errorCount = 0;
		try (BufferedReader input = new BufferedReader(new FileReader(csTestFile))) {
			for (String fileLine = input.readLine(); fileLine != null; fileLine = input.readLine()) {
				lineCount++;
				String[] fileLineTokens = fileLine.split("[ \t\n\f\r]+");
				if (state) {
					if (currentNode != Integer.parseInt(fileLineTokens[1])) {
						System.out.println("Critical section activity by another node before node " + currentNode
								+ " could leave the critical section. Error found at line number " + lineCount);
						errorCount++;
					} else if (fileLineTokens[3].equals("entered")) {
						System.out.println("Node " + currentNode
								+ " in multiple critical sections at once. Error found at line number " + lineCount);
						errorCount++;
					} else {
						state = false;
						numberOfCSEntries.put(currentNode, numberOfCSEntries.get(currentNode) + 1);
					}
				} else {
					if (fileLineTokens[3].equals("left")) {
						System.out.println("Node " + fileLineTokens[1]
								+ " has left the critical section before entering at line " + lineCount);
						errorCount++;
					} else {
						currentNode = Integer.parseInt(fileLineTokens[1]);
						state = true;
						if (!numberOfCSEntries.containsKey(currentNode)) {
							numberOfCSEntries.put(currentNode, 0);
						}
					}
				}
			}
		} catch (FileNotFoundException fex) {
			System.err.println("The test file could not be found. Testing fails.");
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {

			}
			System.exit(1);
		} catch (IOException iex) {
			System.err.println("Error reading a line in the test file. Testing fails.");
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {

			}
			System.exit(1);
		} catch (NumberFormatException nex) {
			System.err.println("The test file has malformed content (an integer could not be parsed). Testing fails.");
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {

			}
			System.exit(1);
		}
		System.out.println("After " + lineCount + " lines of the test file...");
		long linesMissingOrInExcess = Math.abs(2 * cfp.getNumberOfNodes() * cfp.getNumberOfRequests() - lineCount);
		if (linesMissingOrInExcess > 0) {
			System.out.println(linesMissingOrInExcess + " lines of the test file missing or in excess.");
			errorCount += linesMissingOrInExcess;
		}
		// check if state is true to see which node has not left the critical section
		if (state) {
			System.out.println("Node " + currentNode + " has not left its last critical section.");
			errorCount++;
		}
		for (int i = 0; i < cfp.getNumberOfNodes(); i++) {
			if (!numberOfCSEntries.containsKey(i)) {
				System.out.println("Node " + i + " never entered an uninterrupting critical section.");
			} else if (numberOfCSEntries.get(i) != cfp.getNumberOfRequests()) {
				System.out.println("Node " + i + " executed " + numberOfCSEntries.get(i)
						+ " uninterrupting critical sections against what should be " + cfp.getNumberOfRequests()
						+ ".");
			}
		}
		System.out.println("In total, there were " + errorCount + " errors detected.");
		return errorCount;
	}
}

class MessageAndString {
	public static String createMessageString(String messageClass, long logicalClock, long[] vectorClock,
			int sourceNodeId, int times, long totalResponseTime, long nodeThroughputStartTime,
			long nodeThroughputEndTime, int totalSatisfiedRequests) {
		String messageString = "MessageClass=" + messageClass + "\tLogicalClock=" + logicalClock + "\tVectorClock=[";
		for (int i = 0; i < vectorClock.length; i++) {
			messageString += vectorClock[i] + ((vectorClock.length - i == 1) ? "]" : " ");
		}
		return messageString + "\tSourceNodeId=" + sourceNodeId + "\tTimes=" + times + "\tTotalResponseTime="
				+ totalResponseTime + "\tNodeThroughputStartTime=" + nodeThroughputStartTime
				+ "\tNodeThroughputEndTime=" + nodeThroughputEndTime + "\tTotalSatisfiedRequests="
				+ totalSatisfiedRequests;
	}
}

class Request implements Comparable<Request> {
	private long timestamp;
	private int nodeId;
	private int number;

	public Request(long timestamp, int nodeId, int number) {
		this.timestamp = timestamp;
		this.nodeId = nodeId;
		this.number = number;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public int getNodeId() {
		return nodeId;
	}

	public int getNumber() {
		return number;
	}

	@Override
	public int compareTo(Request request) {
		int timestampCompare = Long.compare(timestamp, request.timestamp);
		return (timestampCompare == 0) ? Integer.compare(nodeId, request.nodeId) : timestampCompare;
	}
}

class Node {
	private int id;
	private String hostName;
	private int portNumber;
	private Socket clientSocket;
	private long logicalClock;
	private long[] vectorClock;
	private long[] lastCSEntryVectorClock;
	private long[] lastCSExitVectorClock;
	private PriorityBlockingQueue<Request> requestPriorityQueue;
	private AtomicReference<Request> ownRequest;
//private ConcurrentLinkedDeque<Request> ownRequest;
	private CopyOnWriteArrayList<Node> neighboringNodes;
	private ConcurrentSkipListSet<Request> deferredReplySet;
	private AtomicLong totalResponseTime;
	private AtomicLong nodeThroughputStartTime;
	private AtomicLong nodeThroughputEndTime;
	private AtomicInteger totalSatisfiedRequests;
// private CopyOnWriteArrayList<Channel> connectingChannels;
// private Node parent;
// private CopyOnWriteArrayList<Node> children;
// private boolean maxNumberSent;
// private boolean red;
// private boolean allConnectingChannelsEmpty;
// private boolean terminated;

	public Node(int id, String hostName, int portNumber, int numberOfNodes) {
		this.id = id;
		this.hostName = hostName;
		this.portNumber = portNumber;
		vectorClock = new long[numberOfNodes];
		lastCSEntryVectorClock = new long[numberOfNodes];
		lastCSExitVectorClock = new long[numberOfNodes];
		requestPriorityQueue = new PriorityBlockingQueue<>();
		ownRequest = new AtomicReference<>();
		neighboringNodes = new CopyOnWriteArrayList<>();
		deferredReplySet = new ConcurrentSkipListSet<>();
		totalResponseTime = new AtomicLong();
		nodeThroughputStartTime = new AtomicLong();
		nodeThroughputEndTime = new AtomicLong();
		totalSatisfiedRequests = new AtomicInteger();
		// connectingChannels = new CopyOnWriteArrayList<>();
		// children = new CopyOnWriteArrayList<>();
	}

	public int getId() {
		return id;
	}

	public String getHostName() {
		return hostName;
	}

	public int getPortNumber() {
		return portNumber;
	}

	public void setClientSocket(Socket clientSocket) {
		this.clientSocket = clientSocket;
	}

	public Socket getClientSocket() {
		return clientSocket;
	}

	public void setLogicalClock(long logicalClock) {
		this.logicalClock = logicalClock;
	}

	public long getLogicalClock() {
		return logicalClock;
	}

	public long[] getVectorClock() {
		return vectorClock;
	}

	public long[] getLastCSEntryVectorClock() {
		return lastCSEntryVectorClock;
	}

	public long[] getLastCSExitVectorClock() {
		return lastCSExitVectorClock;
	}

	public PriorityBlockingQueue<Request> getRequestPriorityQueue() {
		return requestPriorityQueue;
	}

	public void setOwnRequest(Request ownRequest) {
		this.ownRequest.set(ownRequest);
	}

	public Request getOwnRequest() {
		return ownRequest.get();
	}

	public CopyOnWriteArrayList<Node> getNeighboringNodes() {
		return neighboringNodes;
	}

	public ConcurrentSkipListSet<Request> getDeferredReplySet() {
		return deferredReplySet;
	}

	public void addResponseTime(long responseTime) {
		totalResponseTime.addAndGet(responseTime);
	}

	public long getTotalResponseTime() {
		return totalResponseTime.get();
	}

	public void setNodeThroughputStartTime(long nodeThroughputStartTime) {
		this.nodeThroughputStartTime.set(nodeThroughputStartTime);
	}

	public long getNodeThroughputStartTime() {
		return nodeThroughputStartTime.get();
	}

	public void setNodeThroughputEndTime(long nodeThroughputEndTime) {
		this.nodeThroughputEndTime.set(nodeThroughputEndTime);
	}

	public long getNodeThroughputEndTime() {
		return nodeThroughputEndTime.get();
	}

	public void addTotalSatisfiedRequests(int satisfiedRequestCount) {
		totalSatisfiedRequests.addAndGet(satisfiedRequestCount);
	}

	public int getTotalSatisfiedRequests() {
		return totalSatisfiedRequests.get();
	}

	/*
	 * public void setConnectingChannels(CopyOnWriteArrayList<Channel>
	 * connectingChannels) { this.connectingChannels = connectingChannels; }
	 * 
	 * public CopyOnWriteArrayList<Channel> getConnectingChannels() { return
	 * connectingChannels; }
	 * 
	 * public void setParent(Node parent) { this.parent = parent; }
	 * 
	 * public Node getParent() { return parent; }
	 * 
	 * public CopyOnWriteArrayList<Node> getChildren() { return children; } public
	 * void setActive(boolean active) { this.active = active; }
	 * 
	 * public boolean isActive() { return active; }
	 * 
	 * public int[] getVectorClock() { return vectorClock; }
	 * 
	 * public void setRecordedVectorClock(int[] recordedVectorClock) {
	 * this.recordedVectorClock = recordedVectorClock; }
	 * 
	 * public int[] getRecordedVectorClock() { return recordedVectorClock; }
	 * 
	 * public void setMaxNumberSent(boolean maxNumberSent) { this.maxNumberSent =
	 * maxNumberSent; }
	 * 
	 * public boolean isMaxNumberSent() { return maxNumberSent; }
	 * 
	 * public void setRed(boolean red) { this.red = red; }
	 * 
	 * public boolean isRed() { return red; }
	 * 
	 * public void setAllConnectingChannelsEmpty(boolean allConnectingChannelsEmpty)
	 * { this.allConnectingChannelsEmpty = allConnectingChannelsEmpty; }
	 * 
	 * public boolean areAllConnectingChannelsEmpty() { return
	 * allConnectingChannelsEmpty; }
	 * 
	 * public void setTerminated(boolean terminated) { this.terminated = terminated;
	 * }
	 * 
	 * public boolean isTerminated() { return terminated; }
	 * 
	 * @Override public int compareTo(Node node) { stub return
	 * this.hostName.compareTo(node.hostName); }
	 */
}

class Channel {
	private ConcurrentSkipListSet<Integer> nodeEnds;
	private ConcurrentSkipListSet<String> channelState;
	private ConcurrentSkipListSet<String> recordedChannelState;

	public Channel(ConcurrentSkipListSet<Integer> nodeEnds) {
		this.nodeEnds = nodeEnds;
		channelState = new ConcurrentSkipListSet<>();
		recordedChannelState = new ConcurrentSkipListSet<>();
	}

	public ConcurrentSkipListSet<Integer> getNodeEnds() {
		return nodeEnds;
	}

	public ConcurrentSkipListSet<String> getChannelState() {
		return channelState;
	}

	public void setRecordedChannelState(ConcurrentSkipListSet<String> recordedChannelState) {
		this.recordedChannelState = recordedChannelState;
	}

	public ConcurrentSkipListSet<String> getRecordedChannelState() {
		return recordedChannelState;
	}
}

class SpanningTree {
	private CopyOnWriteArrayList<Node> nodesInTree;
	private CopyOnWriteArrayList<Channel> channelsInTree;

	public SpanningTree(Node zeroNode) {
		nodesInTree = new CopyOnWriteArrayList<>();
		nodesInTree.add(zeroNode);
		channelsInTree = new CopyOnWriteArrayList<>();
	}

	public CopyOnWriteArrayList<Node> getNodesInTree() {
		return nodesInTree;
	}

	public CopyOnWriteArrayList<Channel> getChannelsInTree() {
		return channelsInTree;
	}
}

class ConfigurationFileParameters {
	private int numberOfNodes;
	private double meanInterRequestDelay;
	private double meanCSExecutionTime;
	private int numberOfRequests;

	public ConfigurationFileParameters(int numberOfNodes, double meanInterRequestDelay, double meanCSExecutionTime,
			int numberOfRequests) {
		this.numberOfNodes = numberOfNodes;
		this.meanInterRequestDelay = meanInterRequestDelay;
		this.meanCSExecutionTime = meanCSExecutionTime;
		this.numberOfRequests = numberOfRequests;
	}

	public int getNumberOfNodes() {
		return numberOfNodes;
	}

	public double getMeanInterRequestDelay() {
		return meanInterRequestDelay;
	}

	public double getMeanCSExecutionTime() {
		return meanCSExecutionTime;
	}

	public int getNumberOfRequests() {
		return numberOfRequests;
	}
}

public class TCPProject2Node extends Thread {
	private int hostNodeId;
	private boolean modeRicartAndAgrawala;
	private boolean modeTest;
	private Node[] nodes;
	private ConfigurationFileParameters cfp;
	private ServerSocket serverSocket;
// private SpanningTree spanningTree;
	private PrintWriter[] messageWriters;
	private FileWriter csTestFileWriter;
	private ConcurrentSkipListSet<Integer> permittingNodeIdsThisRequest;
	private AtomicInteger doneNodeIdTo0Count;
	// private ConcurrentSkipListSet<Integer> doneNodeIdsTo0;
	private int criticalSectionIteration;
	private File csTestFile;

	public double getRandom(double meanValue) {
		return meanValue == 0.0 ? 0.0 : -(Math.log(ThreadLocalRandom.current().nextDouble()) / meanValue);
	}

	public void csEnter() {
		synchronized (nodes[hostNodeId]) {
			nodes[hostNodeId].setLogicalClock(nodes[hostNodeId].getLogicalClock() + 1);
			nodes[hostNodeId].getVectorClock()[hostNodeId]++;
			nodes[hostNodeId].setOwnRequest(
					new Request(nodes[hostNodeId].getLogicalClock(), hostNodeId, criticalSectionIteration));
			/*
			 * synchronized (nodes[hostNodeId].getOwnRequest()) {
			 * nodes[hostNodeId].getOwnRequest().push(ownRequest); }
			 */
			if (!modeRicartAndAgrawala) {
				synchronized (nodes[hostNodeId].getRequestPriorityQueue()) {
					nodes[hostNodeId].getRequestPriorityQueue().offer(nodes[hostNodeId].getOwnRequest());
				}
			}
			for (Node neighbor : nodes[hostNodeId].getNeighboringNodes()) {
				messageWriters[neighbor.getId()].println(MessageAndString.createMessageString("Request",
						nodes[hostNodeId].getLogicalClock(), nodes[hostNodeId].getVectorClock(), hostNodeId,
						criticalSectionIteration, nodes[hostNodeId].getTotalResponseTime(),
						nodes[hostNodeId].getNodeThroughputStartTime(), nodes[hostNodeId].getNodeThroughputEndTime(),
						nodes[hostNodeId].getTotalSatisfiedRequests()));
				messageWriters[neighbor.getId()].flush();
				System.out.println("Node " + hostNodeId + " sent REQUEST message " + criticalSectionIteration
						+ " with timestamp " + nodes[hostNodeId].getLogicalClock() + " to node " + neighbor.getId());
			}
		}
		synchronized (this) {
			while (permittingNodeIdsThisRequest.size() < cfp.getNumberOfNodes() - 1) {
				try {
					wait();
				} catch (InterruptedException e) {

				}
			}
		}
		synchronized (permittingNodeIdsThisRequest) {
			permittingNodeIdsThisRequest.clear();
		}
		if (!modeRicartAndAgrawala) {
			synchronized (this) {
				while (nodes[hostNodeId].getRequestPriorityQueue().peek().getNodeId() != hostNodeId) {
					try {
						wait();
					} catch (InterruptedException e) {

					}
				}
			}
		}
	}

	public void csLeave() {
		synchronized (nodes[hostNodeId]) {
			nodes[hostNodeId].setOwnRequest(null);
			/*
			 * synchronized (nodes[hostNodeId].getOwnRequest()) {
			 * nodes[hostNodeId].getOwnRequest().pop(); }
			 */
			if (!modeRicartAndAgrawala) {
				synchronized (nodes[hostNodeId].getRequestPriorityQueue()) {
					nodes[hostNodeId].getRequestPriorityQueue().poll();
				}
				nodes[hostNodeId].setLogicalClock(nodes[hostNodeId].getLogicalClock() + 1);
				nodes[hostNodeId].getVectorClock()[hostNodeId]++;
				for (Node neighbor : nodes[hostNodeId].getNeighboringNodes()) {
					messageWriters[neighbor.getId()].println(MessageAndString.createMessageString("Release",
							nodes[hostNodeId].getLogicalClock(), nodes[hostNodeId].getVectorClock(), hostNodeId,
							criticalSectionIteration, nodes[hostNodeId].getTotalResponseTime(),
							nodes[hostNodeId].getNodeThroughputStartTime(),
							nodes[hostNodeId].getNodeThroughputEndTime(),
							nodes[hostNodeId].getTotalSatisfiedRequests()));
					messageWriters[neighbor.getId()].flush();
					System.out.println("Node " + hostNodeId + " sent RELEASE message " + criticalSectionIteration
							+ " with timestamp " + nodes[hostNodeId].getLogicalClock() + " to node "
							+ neighbor.getId());
				}
			} else {
				synchronized (nodes[hostNodeId].getDeferredReplySet()) {
					if (!nodes[hostNodeId].getDeferredReplySet().isEmpty()) {
						nodes[hostNodeId].setLogicalClock(nodes[hostNodeId].getLogicalClock() + 1);
						nodes[hostNodeId].getVectorClock()[hostNodeId]++;
						for (Request deferredReply = nodes[hostNodeId].getDeferredReplySet()
								.pollFirst(); deferredReply != null; deferredReply = nodes[hostNodeId]
										.getDeferredReplySet().pollFirst()) {
							messageWriters[deferredReply.getNodeId()].println(MessageAndString.createMessageString(
									"Reply", nodes[hostNodeId].getLogicalClock(), nodes[hostNodeId].getVectorClock(),
									hostNodeId, deferredReply.getNumber(), nodes[hostNodeId].getTotalResponseTime(),
									nodes[hostNodeId].getNodeThroughputStartTime(),
									nodes[hostNodeId].getNodeThroughputEndTime(),
									nodes[hostNodeId].getTotalSatisfiedRequests()));
							messageWriters[deferredReply.getNodeId()].flush();
							System.out.println("Node " + hostNodeId + " sent REPLY message " + deferredReply.getNumber()
									+ " with timestamp " + nodes[hostNodeId].getLogicalClock() + " to node "
									+ deferredReply.getNodeId());
						}
					}
				}
			}
		}
	}

	public void criticalSectionSend() {
		long interRequestDelayStartTime = 0;
		long responseTimeStartTime = 0;
		long responseTimeEndTime = 0;
		for (criticalSectionIteration = 1; criticalSectionIteration <= cfp
				.getNumberOfRequests(); criticalSectionIteration++) {
			double d = getRandom(cfp.getMeanInterRequestDelay());
			long interRequestDelayEndTime = System.currentTimeMillis();
			if (interRequestDelayEndTime - interRequestDelayStartTime < d) {
				try {
					Thread.sleep((long) (d - interRequestDelayEndTime + interRequestDelayStartTime));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (criticalSectionIteration == 1) {
				nodes[hostNodeId].setNodeThroughputStartTime(System.currentTimeMillis());
			}
			responseTimeStartTime = System.currentTimeMillis();
			csEnter();
			long csExecutionStartTime = System.currentTimeMillis();
			if (modeTest) {
				synchronized (nodes[hostNodeId]) {
					System.arraycopy(nodes[hostNodeId].getVectorClock(), 0,
							nodes[hostNodeId].getLastCSEntryVectorClock(), 0, cfp.getNumberOfNodes());
				}
			}
			double c = getRandom(cfp.getMeanCSExecutionTime());
			long csExecutionEndTime = System.currentTimeMillis();
			if (csExecutionEndTime - csExecutionStartTime < c) {
				try {
					Thread.sleep((long) (c - csExecutionEndTime + csExecutionStartTime));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (modeTest) {
				synchronized (nodes[hostNodeId]) {
					System.arraycopy(nodes[hostNodeId].getVectorClock(), 0,
							nodes[hostNodeId].getLastCSExitVectorClock(), 0, cfp.getNumberOfNodes());
					String startVectorTimestamp = "";
					for (int i = 0; i < cfp.getNumberOfNodes(); i++) {
						startVectorTimestamp += nodes[hostNodeId].getLastCSEntryVectorClock()[i]
								+ ((cfp.getNumberOfNodes() - i == 1) ? "\t" : " ");
					}
					String endVectorTimestamp = "";
					for (int i = 0; i < cfp.getNumberOfNodes(); i++) {
						endVectorTimestamp += nodes[hostNodeId].getLastCSExitVectorClock()[i]
								+ ((cfp.getNumberOfNodes() - i == 1) ? "\n" : " ");
					}
					try {
						csTestFileWriter = new FileWriter(csTestFile, true);
						csTestFileWriter.write(startVectorTimestamp + endVectorTimestamp);
						csTestFileWriter.close();
					} catch (IOException e) {

					}
				}
			}
			csLeave();
			nodes[hostNodeId].addTotalSatisfiedRequests(1);
			responseTimeEndTime = System.currentTimeMillis();
			nodes[hostNodeId].addResponseTime(responseTimeEndTime - responseTimeStartTime);
			if (criticalSectionIteration == cfp.getNumberOfRequests()) {
				nodes[hostNodeId].setNodeThroughputEndTime(System.currentTimeMillis());
			}
			interRequestDelayStartTime = System.currentTimeMillis();
		}

		if (hostNodeId == 0) {
			synchronized (this) {
				while (doneNodeIdTo0Count.get() < cfp.getNumberOfNodes() - 1) {
					try {
						wait();
					} catch (InterruptedException e) {

					}
				}
			}
			synchronized (nodes[hostNodeId]) {
				nodes[hostNodeId].setLogicalClock(nodes[hostNodeId].getLogicalClock() + 1);
				nodes[hostNodeId].getVectorClock()[hostNodeId]++;
				for (Node neighbor : nodes[hostNodeId].getNeighboringNodes()) {
					messageWriters[neighbor.getId()].println(MessageAndString.createMessageString("Termination",
							nodes[hostNodeId].getLogicalClock(), nodes[hostNodeId].getVectorClock(), hostNodeId, 1,
							nodes[hostNodeId].getTotalResponseTime(), nodes[hostNodeId].getNodeThroughputStartTime(),
							nodes[hostNodeId].getNodeThroughputEndTime(),
							nodes[hostNodeId].getTotalSatisfiedRequests()));
					messageWriters[neighbor.getId()].flush();
					System.out.println("Node " + hostNodeId + " sent TERMINATION message 1 with timestamp "
							+ nodes[hostNodeId].getLogicalClock() + " to node " + neighbor.getId());
					try {
						synchronized (neighbor) {
							neighbor.getClientSocket().close();
						}
					} catch (IOException ignored) {

					}
				}
			}
		} else {
			synchronized (nodes[hostNodeId]) {
				nodes[hostNodeId].setLogicalClock(nodes[hostNodeId].getLogicalClock() + 1);
				nodes[hostNodeId].getVectorClock()[hostNodeId]++;
				messageWriters[0].println(MessageAndString.createMessageString("Done",
						nodes[hostNodeId].getLogicalClock(), nodes[hostNodeId].getVectorClock(), hostNodeId, 1,
						nodes[hostNodeId].getTotalResponseTime(), nodes[hostNodeId].getNodeThroughputStartTime(),
						nodes[hostNodeId].getNodeThroughputEndTime(), nodes[hostNodeId].getTotalSatisfiedRequests()));
				messageWriters[0].flush();
				System.out.println("Node " + hostNodeId + " sent DONE message 1 with timestamp "
						+ nodes[hostNodeId].getLogicalClock() + " to node 0");
			}
		}
	}

	public void receive(int sendingNodeId) {
		try {
			BufferedReader messageReader = new BufferedReader(
					new InputStreamReader(nodes[sendingNodeId].getClientSocket().getInputStream()));
			String[] receivedMessageTokens = null;
			String receivedMessageClass = null;
			long receivedMessageTimestamp = 0;
			long[] receivedMessageVectorClock = new long[cfp.getNumberOfNodes()];
			int receivedMessageSourceNodeId = 0;
			int receivedMessageTimes = 0;
			long receivedMessageTotalResponseTime = 0;
			long receivedMessageNodeThroughputStartTime = 0;
			long receivedMessageNodeThroughputEndTime = 0;
			int receivedMessageTotalSatisfiedRequests = 0;
			while (true) {
				String receivedMessage = messageReader.readLine();
				if (receivedMessage == null) {
					throw new SocketException();
				}
				receivedMessageTokens = receivedMessage.trim().split("\t");
				receivedMessageClass = receivedMessageTokens[0]
						.substring(receivedMessageTokens[0].indexOf('=') + 1);
				receivedMessageTimestamp = Long
						.parseLong(receivedMessageTokens[1].substring(receivedMessageTokens[1].indexOf('=') + 1));
				String receivedMessageVectorClockString = receivedMessageTokens[2].substring(
						receivedMessageTokens[2].indexOf("[") + 1, receivedMessageTokens[2].length() - 1);
				String[] receivedMessageVectorClockTokens = receivedMessageVectorClockString.trim().split(" ");
				for (int i = 0; i < cfp.getNumberOfNodes(); i++) {
					receivedMessageVectorClock[i] = Long.parseLong(receivedMessageVectorClockTokens[i]);
				}
				receivedMessageSourceNodeId = Integer
						.parseInt(receivedMessageTokens[3].substring(receivedMessageTokens[3].indexOf('=') + 1));
				receivedMessageTimes = Integer
						.parseInt(receivedMessageTokens[4].substring(receivedMessageTokens[4].indexOf('=') + 1));
				receivedMessageTotalResponseTime = Long
						.parseLong(receivedMessageTokens[5].substring(receivedMessageTokens[5].indexOf('=') + 1));
				receivedMessageNodeThroughputStartTime = Long
						.parseLong(receivedMessageTokens[6].substring(receivedMessageTokens[6].indexOf('=') + 1));
				receivedMessageNodeThroughputEndTime = Long
						.parseLong(receivedMessageTokens[7].substring(receivedMessageTokens[7].indexOf('=') + 1));
				receivedMessageTotalSatisfiedRequests = Integer
						.parseInt(receivedMessageTokens[8].substring(receivedMessageTokens[8].indexOf('=') + 1));
				System.out.println("Node " + hostNodeId + " received " + receivedMessageClass.toUpperCase()
						+ " message " + receivedMessageTimes + " with timestamp " + receivedMessageTimestamp
						+ " from node " + sendingNodeId);
				synchronized (nodes[hostNodeId]) {
					nodes[hostNodeId].setLogicalClock(
							Math.max(nodes[hostNodeId].getLogicalClock(), receivedMessageTimestamp) + 1);
					for (int i = 0; i < cfp.getNumberOfNodes(); i++) {
						nodes[hostNodeId].getVectorClock()[i] = Math.max(nodes[hostNodeId].getVectorClock()[i],
								receivedMessageVectorClock[i]);
					}
					nodes[hostNodeId].getVectorClock()[hostNodeId]++;
				}

				/*
				 * if (!modeRicartAndAgrawala && !nodes[hostNodeId].getOwnRequest().isEmpty() &&
				 * (receivedMessageTimestamp >
				 * nodes[hostNodeId].getOwnRequest().peek().getTimestamp() ||
				 * receivedMessageTimestamp ==
				 * nodes[hostNodeId].getOwnRequest().peek().getTimestamp() &&
				 * receivedMessageSourceNodeId > nodes[hostNodeId].getOwnRequest().peek()
				 * .getNodeId())) {
				 */

				if (!modeRicartAndAgrawala && nodes[hostNodeId].getOwnRequest() != null
						&& nodes[hostNodeId].getOwnRequest().compareTo(new Request(receivedMessageTimestamp,
								receivedMessageSourceNodeId, receivedMessageTimes)) < 0) {
					synchronized (this) {
						synchronized (permittingNodeIdsThisRequest) {
							permittingNodeIdsThisRequest.add(receivedMessageSourceNodeId);
						}
						notifyAll();
					}
				}
				if (receivedMessageClass.equals("Request")) {
					if (!modeRicartAndAgrawala) {
						synchronized (nodes[hostNodeId]) {
							synchronized (nodes[hostNodeId].getRequestPriorityQueue()) {
								nodes[hostNodeId].getRequestPriorityQueue().offer(new Request(receivedMessageTimestamp,
										receivedMessageSourceNodeId, receivedMessageTimes));
							}
							nodes[hostNodeId].setLogicalClock(nodes[hostNodeId].getLogicalClock() + 1);
							nodes[hostNodeId].getVectorClock()[hostNodeId]++;
							messageWriters[sendingNodeId].println(MessageAndString.createMessageString("Reply",
									nodes[hostNodeId].getLogicalClock(), nodes[hostNodeId].getVectorClock(), hostNodeId,
									receivedMessageTimes, nodes[hostNodeId].getTotalResponseTime(),
									nodes[hostNodeId].getNodeThroughputStartTime(),
									nodes[hostNodeId].getNodeThroughputEndTime(),
									nodes[hostNodeId].getTotalSatisfiedRequests()));
							messageWriters[sendingNodeId].flush();
							System.out.println("Node " + hostNodeId + " sent REPLY message " + receivedMessageTimes
									+ " with timestamp " + nodes[hostNodeId].getLogicalClock() + " to node "
									+ sendingNodeId);
						}
					} else if (nodes[hostNodeId].getOwnRequest() == null
							|| nodes[hostNodeId].getOwnRequest().compareTo(new Request(receivedMessageTimestamp,
									receivedMessageSourceNodeId, receivedMessageTimes)) > 0) {
						synchronized (nodes[hostNodeId]) {
							nodes[hostNodeId].setLogicalClock(nodes[hostNodeId].getLogicalClock() + 1);
							nodes[hostNodeId].getVectorClock()[hostNodeId]++;
							messageWriters[sendingNodeId].println(MessageAndString.createMessageString("Reply",
									nodes[hostNodeId].getLogicalClock(), nodes[hostNodeId].getVectorClock(), hostNodeId,
									receivedMessageTimes, nodes[hostNodeId].getTotalResponseTime(),
									nodes[hostNodeId].getNodeThroughputStartTime(),
									nodes[hostNodeId].getNodeThroughputEndTime(),
									nodes[hostNodeId].getTotalSatisfiedRequests()));
							messageWriters[sendingNodeId].flush();
							System.out.println("Node " + hostNodeId + " sent REPLY message " + receivedMessageTimes
									+ " with timestamp " + nodes[hostNodeId].getLogicalClock() + " to node "
									+ sendingNodeId);
						}
					} else {
						synchronized (nodes[hostNodeId].getDeferredReplySet()) {
							nodes[hostNodeId].getDeferredReplySet().add(new Request(receivedMessageTimestamp,
									receivedMessageSourceNodeId, receivedMessageTimes));
						}
					}
				} else if (receivedMessageClass.contains("Reply") && modeRicartAndAgrawala) {
					synchronized (this) {
						synchronized (permittingNodeIdsThisRequest) {
							permittingNodeIdsThisRequest.add(receivedMessageSourceNodeId);
						}
						notifyAll();
					}
				} else if (receivedMessageClass.contains("Release")) {
					synchronized (this) {
						synchronized (nodes[hostNodeId].getRequestPriorityQueue()) {
							nodes[hostNodeId].getRequestPriorityQueue().poll();
						}
						notifyAll();
					}
				} else if (receivedMessageClass.contains("Done")) {
					nodes[receivedMessageSourceNodeId].addResponseTime(receivedMessageTotalResponseTime);
					nodes[receivedMessageSourceNodeId]
							.setNodeThroughputStartTime(receivedMessageNodeThroughputStartTime);
					nodes[receivedMessageSourceNodeId].setNodeThroughputEndTime(receivedMessageNodeThroughputEndTime);
					nodes[receivedMessageSourceNodeId].addTotalSatisfiedRequests(receivedMessageTotalSatisfiedRequests);
					synchronized (this) {
						/*
						 * synchronized (doneNodeIdsTo0) { doneNodeIdsTo0.add(sendingNodeId); }
						 */
						doneNodeIdTo0Count.incrementAndGet();
						notifyAll();
					}
				} else {
					for (Node neighbor : nodes[hostNodeId].getNeighboringNodes()) {
						try {
							synchronized (neighbor) {
								neighbor.getClientSocket().close();
							}
						} catch (IOException ignored) {

						}
					}
					System.out.println("Without exception: Finished connecting with node " + sendingNodeId + ".");
					break;
				}
			}
		} catch (SocketException ignored) {
			System.out.println("Within exception: Finished connecting with node " + sendingNodeId + ".");
		} catch (IOException e) {

			System.err.println(e.toString());
			e.printStackTrace();
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
			System.exit(1);
		}
	}

	@Override
	public void run() {
		if (Thread.currentThread().getName().equals("CriticalSectionSend")) {
			criticalSectionSend();
		} else {
			receive(Integer.parseInt(
					Thread.currentThread().getName().substring(Thread.currentThread().getName().indexOf("-") + 1)));
		}
	}

	public void go(String[] args) {
		try {
			hostNodeId = Integer.parseInt(args[0]);
			if (args.length > 1) {
				modeRicartAndAgrawala = args[1].equalsIgnoreCase("Ricart_and_Agrawala");
				if (modeRicartAndAgrawala) {
					if (args.length > 2) {
						modeTest = args[2].equalsIgnoreCase("test");
					}
				} else {
					modeTest = args[1].equalsIgnoreCase("test");
				}
			}
			String ancestors = "";
			if (System.getProperty("user.dir").endsWith("bin")) { // during manual runs
				ancestors += "../";
			} else { // during launcher script runs
				ancestors += System.getProperty("user.dir") + "/aosproj2/";
			}
			csTestFile = new File(ancestors + "cs_test_file-" + hostNodeId + ".out");
			csTestFile.delete();
			csTestFile.createNewFile();
			File configFile = new File(ancestors + "config.txt");
			int validLineCount = 0;
			try (Scanner input = new Scanner(configFile)) {
				while (input.hasNext()) {
					String fileLine = input.nextLine();
					if (fileLine.isEmpty()) {
						continue;
					}
					String[] fileLineTokens = fileLine.split("[ \t\n\f\r]+");
					try {
						Integer.parseUnsignedInt(fileLine.substring(0, 1));
						Integer.parseUnsignedInt(fileLineTokens[0]);
					} catch (NumberFormatException ignored) {
						continue;
					}
					validLineCount++;
					if (cfp != null && validLineCount > cfp.getNumberOfNodes() + 1) {
						throw new Exception("Too many valid lines in the configuration file. Try again.");
					}
					if (validLineCount == 1) {
						if (fileLineTokens.length > 4 && fileLineTokens[4].charAt(0) != '#') {
							throw new Exception(
									"Too many tokens in the first valid line of the configuration file. Try again.");
						}
						cfp = new ConfigurationFileParameters(Integer.parseInt(fileLineTokens[0]),
								Double.parseDouble(fileLineTokens[1]), Double.parseDouble(fileLineTokens[2]),
								Integer.parseInt(fileLineTokens[3]));
						if (cfp.getNumberOfNodes() < 1) {
							throw new Exception(
									"There must be at least one node in the distributed system. Try again.");
						}
						if (cfp.getMeanInterRequestDelay() <= 0) {
							throw new Exception("Mean Inter-Request Delay should be greater than zero. Try again.");
						}
						if (cfp.getMeanCSExecutionTime() <= 0) {
							throw new Exception("Mean CS Execution Delay should be greater than zero. Try again.");
						}
						if (cfp.getNumberOfRequests() < 1) {
							throw new Exception("A node must send at least one request. Try again.");
						}
						nodes = new Node[cfp.getNumberOfNodes()];
					} else {
						if (fileLineTokens.length > 3 && fileLineTokens[3].charAt(0) != '#') {
							throw new Exception("Too many tokens in valid line " + validLineCount
									+ " of the configuration file. Try again.");
						}
						if (Integer.parseInt(fileLineTokens[0]) != validLineCount - 2) {
							throw new Exception("Wrong node ID for what should be " + (validLineCount - 2)
									+ " in the configuration file. Try again.");
						}
						if (fileLineTokens[1].length() != 4 || !fileLineTokens[1].startsWith("dc")
								|| Integer.parseUnsignedInt(fileLineTokens[1].substring(2)) < 1
								|| Integer.parseUnsignedInt(fileLineTokens[1].substring(2)) > 45) {
							throw new Exception("Invalid hostname for node " + (validLineCount - 2) + ". Try again.");
						}
						if (validLineCount - 2 == hostNodeId) {
							if (!InetAddress.getLocalHost().getHostName().contains(fileLineTokens[1])) {
								throw new Exception(
										"The local hostname does not match that specified in the configuration file. Try again.");
							}
							serverSocket = new ServerSocket(Integer.parseInt(fileLineTokens[2]));
						}
						nodes[validLineCount - 2] = new Node(validLineCount - 2, fileLineTokens[1],
								Integer.parseInt(fileLineTokens[2]), cfp.getNumberOfNodes());
					}

					/*
					 * if (timeToParseNodeInformation) { timeToParseNodeInformation = false;
					 * parseNodeInformation = true; } if (parseGlobalParameters) {
					 * 
					 * parseGlobalParameters = false; timeToParseNodeInformation = true; } else if
					 * (parseNodeInformation) {
					 * 
					 * } else if (parseNodeNeighbors) { if (nodeCount >= cfp.getNumberOfNodes()) {
					 * throw new
					 * Exception("Too many nodes to parse in the configuration file. Try again."); }
					 * for (String fileLineToken : fileLineTokens) { if (fileLineToken.charAt(0) ==
					 * '#') { break; } int fileLineTokenInt = Integer.parseInt(fileLineToken); if
					 * (fileLineTokenInt == nodeCount) { throw new
					 * Exception("A node cannot connect to itself. Try again."); } if
					 * (fileLineTokenInt < 0 || fileLineTokenInt >= cfp.getNumberOfNodes()) { throw
					 * new Exception("Neighbor node ID does not exist. Try again."); } if
					 * (nodeAdjacencyMatrix[nodeCount][fileLineTokenInt]) { throw new
					 * Exception("Neighbor node ID can only be connected once. Try again."); }
					 * nodeAdjacencyMatrix[nodeCount][fileLineTokenInt] = true;
					 * nodes[nodeCount].getNeighboringNodes().add(nodes[fileLineTokenInt]);
					 * ConcurrentSkipListSet<Integer> nodeEnds = new ConcurrentSkipListSet<>();
					 * nodeEnds.add(nodeCount); nodeEnds.add(fileLineTokenInt); if
					 * (nodes[fileLineTokenInt].getConnectingChannels().isEmpty()) {
					 * nodes[nodeCount].getConnectingChannels().add(new Channel(nodeEnds)); } else {
					 * for (int connectingChannelsIndex = 0; connectingChannelsIndex <
					 * nodes[fileLineTokenInt] .getConnectingChannels().size();
					 * connectingChannelsIndex++) { if
					 * (nodes[fileLineTokenInt].getConnectingChannels().get(connectingChannelsIndex)
					 * .getNodeEnds().equals(nodeEnds)) {
					 * nodes[nodeCount].getConnectingChannels().add(nodes[fileLineTokenInt]
					 * .getConnectingChannels().get(connectingChannelsIndex)); break; } else if
					 * (nodes[fileLineTokenInt].getConnectingChannels().size() -
					 * connectingChannelsIndex == 1) {
					 * nodes[nodeCount].getConnectingChannels().add(new Channel(nodeEnds)); } } } }
					 * nodeCount++; }
					 */

				}
			}
			if (validLineCount < cfp.getNumberOfNodes() + 1) {
				throw new Exception("Too few valid lines in the configuration file. Try again.");
			}
			for (int i = 0; i < cfp.getNumberOfNodes(); i++) {
				for (int j = 0; j < cfp.getNumberOfNodes(); j++) {
					if (i != j) {
						nodes[i].getNeighboringNodes().add(nodes[j]);
					}
				}
			}

			for (Node neighborToConnect : nodes[hostNodeId].getNeighboringNodes()) {
				Socket neighborSocket = null;
				if (hostNodeId < neighborToConnect.getId()) {
					neighborSocket = serverSocket.accept();
					for (Node neighborToCheck : nodes[hostNodeId].getNeighboringNodes()) {
						if (neighborSocket.getInetAddress().getHostName().contains(neighborToCheck.getHostName())) {
							neighborToCheck.setClientSocket(neighborSocket);
							break;
						}
					}
				} else {
					while (true) {
						try {
							neighborSocket = new Socket(neighborToConnect.getHostName(),
									neighborToConnect.getPortNumber());
							neighborToConnect.setClientSocket(neighborSocket);
							break;
						} catch (ConnectException cex) {

						}
					}
				}
			}
			// buildSpanningTreeByBreadthFirstSearch();
			/*
			 * if (spanningTree.getNodesInTree().size() != cfp.getNumberOfNodes()) { throw
			 * new
			 * Exception("The distributed system is not strongly connected. Try again."); }
			 */
			permittingNodeIdsThisRequest = new ConcurrentSkipListSet<>();
			doneNodeIdTo0Count = new AtomicInteger();
			messageWriters = new PrintWriter[cfp.getNumberOfNodes()];
			for (Node neighbor : nodes[hostNodeId].getNeighboringNodes()) {
				messageWriters[neighbor.getId()] = new PrintWriter(neighbor.getClientSocket().getOutputStream());
			}
			ConcurrentLinkedDeque<Thread> receiveThreads = new ConcurrentLinkedDeque<>();
			for (Node neighbor : nodes[hostNodeId].getNeighboringNodes()) {
				receiveThreads.offer(new Thread(this, "Receive-" + neighbor.getId()));
				receiveThreads.peekLast().start();
			}
			Thread criticalSectionSendThread = new Thread(this, "CriticalSectionSend");
			criticalSectionSendThread.start();
			criticalSectionSendThread.join();
			for (Thread receiveThread : receiveThreads) {
				receiveThread.join();
			}
			for (int i = 0; i < messageWriters.length; i++) {
				if (messageWriters[i] != null) {
					messageWriters[i].close();
				}
			}
			System.out.println("Mutual exclusion service successful.");
			Thread.sleep(5000);
			if (hostNodeId == 0) {
				if (modeTest) {
					long errorCount = 0;
					System.out.println("Testing for implementation correctness.");
					File[] filesToRead = new File[cfp.getNumberOfNodes()];
					for (int i = 0; i < cfp.getNumberOfNodes(); i++) {
						filesToRead[i] = new File(ancestors + "cs_test_file-" + i + ".out");
					}
					for (int iFile = 0; iFile < cfp.getNumberOfNodes() - 1; iFile++) {
						BufferedReader iFileToRead = new BufferedReader(new FileReader(filesToRead[iFile]));
						for (String iVectorTimestamps = iFileToRead
								.readLine(); iVectorTimestamps != null; iVectorTimestamps = iFileToRead.readLine()) {
							String[] iVectorTimestampTokens = iVectorTimestamps.trim().split("\t");
							String[] iStartVectorTimestamp = iVectorTimestampTokens[0].trim().split(" ");
							long[] iStartVectorClock = new long[cfp.getNumberOfNodes()];
							for (int i = 0; i < cfp.getNumberOfNodes(); i++) {
								iStartVectorClock[i] = Long.parseLong(iStartVectorTimestamp[i]);
							}
							String[] iEndVectorTimestamp = iVectorTimestampTokens[1].trim().split(" ");
							long[] iEndVectorClock = new long[cfp.getNumberOfNodes()];
							for (int i = 0; i < cfp.getNumberOfNodes(); i++) {
								iEndVectorClock[i] = Long.parseLong(iEndVectorTimestamp[i]);
							}
							for (int jFile = iFile + 1; jFile < cfp.getNumberOfNodes(); jFile++) {
								BufferedReader jFileToRead = new BufferedReader(new FileReader(filesToRead[jFile]));
								for (String jVectorTimestamps = jFileToRead
										.readLine(); jVectorTimestamps != null; jVectorTimestamps = jFileToRead
												.readLine()) {
									String[] jVectorTimestampTokens = jVectorTimestamps.trim().split("\t");
									String[] jStartVectorTimestamp = jVectorTimestampTokens[0].trim().split(" ");
									long[] jStartVectorClock = new long[cfp.getNumberOfNodes()];
									for (int j = 0; j < cfp.getNumberOfNodes(); j++) {
										jStartVectorClock[j] = Long.parseLong(jStartVectorTimestamp[j]);
									}
									String[] jEndVectorTimestamp = jVectorTimestampTokens[1].trim().split(" ");
									long[] jEndVectorClock = new long[cfp.getNumberOfNodes()];
									for (int j = 0; j < cfp.getNumberOfNodes(); j++) {
										jEndVectorClock[j] = Long.parseLong(jEndVectorTimestamp[j]);
									}
									if (iEndVectorClock[iFile] > jStartVectorClock[iFile]
											&& jEndVectorClock[jFile] > iStartVectorClock[jFile]) {
										errorCount++;
									}
								}
								jFileToRead.close();
							}
						}
						iFileToRead.close();
					}
					System.out.println(errorCount + " pair" + ((errorCount == 1) ? "" : "s")
							+ " of concurrent critical sections found. The implementation is "
							+ ((errorCount > 0) ? "in" : "") + "correct. Program terminating.");
				} else {
					double systemTotalResponseTime = 0.0;
					long minSystemThroughputStartTime = Long.MAX_VALUE;
					long maxSystemThroughputEndTime = 0;
					double systemTotalSatisfiedRequests = 0;
					for (int i = 0; i < cfp.getNumberOfNodes(); i++) {
						systemTotalResponseTime += nodes[i].getTotalResponseTime();
						minSystemThroughputStartTime = Math.min(minSystemThroughputStartTime,
								nodes[i].getNodeThroughputStartTime());
						maxSystemThroughputEndTime = Math.max(maxSystemThroughputEndTime,
								nodes[i].getNodeThroughputEndTime());
						systemTotalSatisfiedRequests += nodes[i].getTotalSatisfiedRequests();
					}
					double systemResponseTime = systemTotalResponseTime / systemTotalSatisfiedRequests;
					double systemThroughput = systemTotalSatisfiedRequests
							/ (maxSystemThroughputEndTime - minSystemThroughputStartTime);
					File performanceFile = new File(ancestors + "performance.out");
					performanceFile.delete();
					performanceFile.createNewFile();
					FileWriter performanceFileWriter = new FileWriter(performanceFile);
					performanceFileWriter.write("Response time: " + systemResponseTime + " ms\nSystem throughput: "
							+ systemThroughput + " requests per ms\n");
					performanceFileWriter.close();
					System.out.println(
							"Refer to file performance.out for response time and system throughput. Program terminating.");
				}
				/*
				 * Thread.sleep(1000); System.out.println("Testing begins!!!");
				 * Thread.sleep(3000); TestScriptProject2 test = new TestScriptProject2(cfp,
				 * csTestFile); if (test.inspectForErrors() == 0) {
				 * System.out.println("The mutual exclusion implementation is correct."); } else
				 * { System.out.println("The mutual exclusion implementation is not correct.");
				 * } System.out.println("Testing complete.");
				 */
				Thread.sleep(10000);
			}
		} catch (Exception e) {
			System.err.println(e.toString());
			e.printStackTrace();
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
			System.exit(1);
		}
	}

	public static void main(String[] args) {
		new TCPProject2Node().go(args);
	}

}