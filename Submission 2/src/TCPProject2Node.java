
/**********************************************************************
 * 
 * TCPProject2Node.java
 * 
 * Created for CS6378.001, Fall 2022
 * Created by Jahnavi Gaddipati, Rashmitha Kalliot Yadav, and Benjamin Rubarts
 * 
 * This file contains the source code for executing the distributed system
 * that ...
 * 
 ***********************************************************************/

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

class TestScriptProject2 {
	private String TestFileAncestors;
	private File csTestFile;
	private boolean state; // True- has entered the critical section, False- has left the critical section
	private int curNode;
	private int lineCount = 0;
	private boolean errorDetected = false;
	private ConfigurationFileParameters cfp;
	private Map<Integer, Integer> numberOfCSEntries;

	public TestScriptProject2(ConfigurationFileParameters cfp) {
		this.cfp = cfp;
	}

	public boolean getErrorDetected() {
		return errorDetected;
	}

	public boolean getState() {
		return state;
	}

	public int getLastNodeEnteringCs() {
		return curNode;
	}

	public void inspect() {

		TestFileAncestors = "";
		if (System.getProperty("user.dir").endsWith("bin")) { // during manual runs
			TestFileAncestors += "../";
		} else { // during launcher script runs
			TestFileAncestors += System.getProperty("user.dir") + "/aosproj2/";
		}
		csTestFile = new File(TestFileAncestors + "cs_test_file.out");
		if (!csTestFile.exists()) {
			System.out.println("Could not locate csTest file. Try again!");
			System.exit(1);

		}
		numberOfCSEntries = new HashMap<Integer, Integer>();
		state = false;
		try (Scanner input = new Scanner(csTestFile)) {
			while (input.hasNext()) {
				lineCount++;
				String fileLine = input.nextLine();
				String[] fileLineTokens = fileLine.split("[ \t\n\f\r]+");
				if (state) {
					if (curNode != Integer.parseInt(fileLineTokens[1])) {
						System.out.println("Node " + curNode
								+ " did not leave critical section. Error found at line number " + lineCount);
						errorDetected = true;
						continue;
					}
					if (fileLineTokens[3].equals("entered")) {
						System.out.println("Node " + curNode
								+ " did not leave critical section. Error found at line number " + lineCount);
						errorDetected = true;
						continue;
					} else {
						state = false;
						if (numberOfCSEntries.containsKey(curNode)) {
							numberOfCSEntries.replace(curNode, numberOfCSEntries.get(curNode) + 1);
						} else {
							numberOfCSEntries.put(curNode, 1);
						}
					}
				} else {
					if (fileLineTokens[3].equals("left")) {
						System.out.println("Node has left the critical section before entering at line " + lineCount);
						errorDetected = true;
						continue;
					} else {
						curNode = Integer.parseInt(fileLineTokens[1]);
						state = true;
					}
				}

			}
			input.close();
			for (int i = 0; i < cfp.getNumberOfNodes(); i++) {
				if (!numberOfCSEntries.containsKey(i)) {
					System.out.println("Node " + i + "never entered critical section");
					continue;
				}
				if (numberOfCSEntries.get(i) != cfp.getNumberOfRequests()) {
					System.out.println("For node " + i + " , only " + numberOfCSEntries.get(i)
							+ "critical section request was completed");
				}
			}

		} catch (Exception E) {

		}
	}
}

class MessageAndString {
	public static String createMessageString(String messageClass, long logicalClock, int sourceNodeId, int times) {
		// String messageString = ;
		/*
		 * if (vectorClock == null) { messageString += "null\t"; } else { messageString
		 * += "["; for (int i = 0; i < vectorClock.length; i++) { messageString +=
		 * vectorClock[i] + ((vectorClock.length - i == 1) ? "]\t" : " "); } }
		 */
		return "MessageClass=" + messageClass + "\tLogicalClock=" + logicalClock + "\tSourceNodeId=" + sourceNodeId
				+ "\tTimes=" + times;
	}
}

class Request implements Comparable<Request> {
	private long timestamp;
	private int nodeId;

	public Request(long timestamp, int nodeId) {
		this.timestamp = timestamp;
		this.nodeId = nodeId;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public int getNodeId() {
		return nodeId;
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
// private boolean active;
	private long logicalClock;
// private int[] recordedVectorClock;
	private PriorityBlockingQueue<Request> requestPriorityQueue;
	private ConcurrentLinkedDeque<Request> ownRequest; // or atomic reference?
//private ConcurrentLinkedDeque<Request> ownRequest;
// private boolean inCriticalSection;
	private CopyOnWriteArrayList<Node> neighboringNodes;
// private CopyOnWriteArrayList<Channel> connectingChannels;
// private Node parent;
// private CopyOnWriteArrayList<Node> children;
// private boolean maxNumberSent;
// private boolean red;
// private boolean allConnectingChannelsEmpty;
// private boolean terminated;

	public Node(int id, String hostName, int portNumber) {
		this.id = id;
		this.hostName = hostName;
		this.portNumber = portNumber;
		requestPriorityQueue = new PriorityBlockingQueue<>();
		ownRequest = new ConcurrentLinkedDeque<>();
		neighboringNodes = new CopyOnWriteArrayList<>();
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

	public PriorityBlockingQueue<Request> getRequestPriorityQueue() {
		return requestPriorityQueue;
	}

	public ConcurrentLinkedDeque<Request> getOwnRequest() {
		return ownRequest;
	}

	public CopyOnWriteArrayList<Node> getNeighboringNodes() {
		return neighboringNodes;
	}

	public void setLogicalClock(long logicalClock) {
		this.logicalClock = logicalClock;
	}

	public long getLogicalClock() {
		return logicalClock;
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
	private Node[] nodes;
	private ConfigurationFileParameters cfp;
	private ServerSocket serverSocket;
// private SpanningTree spanningTree;
	private PrintWriter[] messageWriters;
	private FileWriter fileWriter;
	private ConcurrentSkipListSet<Integer> permittingNodeIdsThisRequest;
	private ConcurrentSkipListSet<Integer> doneNodeIdsTo0;
	private boolean modeRicartAndAgrawala;
	private int criticalSectionIteration;
	private String ancestors;
	private File csTestFile;

	public double getRandom(double meanValue) {
		return -(Math.log(ThreadLocalRandom.current().nextDouble()) / meanValue);
	}

	public void csEnter() {
		synchronized (nodes[hostNodeId]) {
			nodes[hostNodeId].setLogicalClock(nodes[hostNodeId].getLogicalClock() + 1);
			Request ownRequest = new Request(nodes[hostNodeId].getLogicalClock(), hostNodeId);
			synchronized (nodes[hostNodeId].getOwnRequest()) {
				nodes[hostNodeId].getOwnRequest().push(ownRequest);
			}
			if (!modeRicartAndAgrawala) {
				synchronized (nodes[hostNodeId].getRequestPriorityQueue()) {
					nodes[hostNodeId].getRequestPriorityQueue().offer(ownRequest);
				}
			}
			for (Node neighbor : nodes[hostNodeId].getNeighboringNodes()) {
				synchronized (messageWriters[neighbor.getId()]) {
					messageWriters[neighbor.getId()].println(MessageAndString.createMessageString("Request",
							nodes[hostNodeId].getLogicalClock(), hostNodeId, criticalSectionIteration));
					messageWriters[neighbor.getId()].flush();
					System.out.println("Node " + hostNodeId + " sent REQUEST message " + criticalSectionIteration
							+ " with timestamp " + nodes[hostNodeId].getLogicalClock() + " to node "
							+ neighbor.getId());
				}
			}
		}
		while (permittingNodeIdsThisRequest.size() < cfp.getNumberOfNodes() - 1)
			;
		synchronized (permittingNodeIdsThisRequest) {
			permittingNodeIdsThisRequest.clear();
		}
		if (!modeRicartAndAgrawala) {
			while (nodes[hostNodeId].getRequestPriorityQueue().peek().getNodeId() != hostNodeId)
				;
		}
	}

	public void csLeave() {
		synchronized (nodes[hostNodeId]) {
			synchronized (nodes[hostNodeId].getOwnRequest()) {
				nodes[hostNodeId].getOwnRequest().pop();
			}
			if (!modeRicartAndAgrawala) {
				synchronized (nodes[hostNodeId].getRequestPriorityQueue()) {
					nodes[hostNodeId].getRequestPriorityQueue().poll();
				}
			}
			nodes[hostNodeId].setLogicalClock(nodes[hostNodeId].getLogicalClock() + 1);
			for (Node neighbor : nodes[hostNodeId].getNeighboringNodes()) {
				synchronized (messageWriters[neighbor.getId()]) {
					messageWriters[neighbor.getId()].println(MessageAndString.createMessageString("Release",
							nodes[hostNodeId].getLogicalClock(), hostNodeId, criticalSectionIteration));
					messageWriters[neighbor.getId()].flush();
					System.out.println("Node " + hostNodeId + " sent RELEASE message " + criticalSectionIteration
							+ " with timestamp " + nodes[hostNodeId].getLogicalClock() + " to node "
							+ neighbor.getId());
				}
			}
		}
	}

	public void criticalSectionSend() {
		long interRequestDelayStartTime = 0;
		for (criticalSectionIteration = 1; criticalSectionIteration <= cfp
				.getNumberOfRequests(); criticalSectionIteration++) {
			double d = getRandom(cfp.getMeanInterRequestDelay());
			long interRequestDelayEndTime = System.currentTimeMillis();
			if (interRequestDelayEndTime - interRequestDelayStartTime < d) {
				try {
					Thread.sleep((long) (d - interRequestDelayEndTime + interRequestDelayStartTime));
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			csEnter();
			long csExecutionStartTime = System.currentTimeMillis();
			try {
				fileWriter = new FileWriter(csTestFile, true);
				synchronized (fileWriter) {
					fileWriter.write("Node " + hostNodeId + " has entered the critical section.\n");
					fileWriter.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			double c = getRandom(cfp.getMeanCSExecutionTime());
			long csExecutionEndTime = System.currentTimeMillis();
			if (csExecutionEndTime - csExecutionStartTime < c) {
				try {
					Thread.sleep((long) (c - csExecutionEndTime + csExecutionStartTime));
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			try {
				fileWriter = new FileWriter(csTestFile, true);
				synchronized (fileWriter) {
					fileWriter.write("Node " + hostNodeId + " has left the critical section.\n");
					fileWriter.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			csLeave();
			interRequestDelayStartTime = System.currentTimeMillis();
		}
		if (hostNodeId == 0) {
			while (doneNodeIdsTo0.size() < cfp.getNumberOfNodes() - 1)
				;
			synchronized (nodes[hostNodeId]) {
				nodes[hostNodeId].setLogicalClock(nodes[hostNodeId].getLogicalClock() + 1);
				for (Node neighbor : nodes[hostNodeId].getNeighboringNodes()) {
					synchronized (messageWriters[neighbor.getId()]) {
						messageWriters[neighbor.getId()].println(MessageAndString.createMessageString("Termination",
								nodes[hostNodeId].getLogicalClock(), hostNodeId, 1));
						messageWriters[neighbor.getId()].flush();
						System.out.println("Node " + hostNodeId + " sent TERMINATION message 1 with timestamp "
								+ nodes[hostNodeId].getLogicalClock() + " to node " + neighbor.getId());
					}
					try {
						synchronized (neighbor) {
							neighbor.getClientSocket().close();
						}
					} catch (IOException ignored) {
						// TODO Auto-generated catch block
					}
				}
			}
		} else {
			synchronized (nodes[hostNodeId]) {
				nodes[hostNodeId].setLogicalClock(nodes[hostNodeId].getLogicalClock() + 1);
				synchronized (messageWriters[0]) {
					messageWriters[0].println(MessageAndString.createMessageString("Done",
							nodes[hostNodeId].getLogicalClock(), hostNodeId, 1));
					messageWriters[0].flush();
					System.out.println("Node " + hostNodeId + " sent DONE message 1 with timestamp "
							+ nodes[hostNodeId].getLogicalClock() + " to node 0");
				}
			}
		}
	}

	public void receive(int sendingNodeId) {
		try {
			BufferedReader messageReader = new BufferedReader(
					new InputStreamReader(nodes[sendingNodeId].getClientSocket().getInputStream()));
			int replyMessageCount = 0;
			while (true) {
				String receivedMessage = messageReader.readLine();
				if (receivedMessage == null) {
					throw new SocketException();
				}
				String[] receivedMessageTokens = receivedMessage.trim().split("\t");
				String receivedMessageClass = receivedMessageTokens[0]
						.substring(receivedMessageTokens[0].indexOf('=') + 1);
				long receivedMessageTimestamp = Long
						.parseLong(receivedMessageTokens[1].substring(receivedMessageTokens[1].indexOf('=') + 1));
				int receivedMessageSourceNodeId = Integer
						.parseInt(receivedMessageTokens[2].substring(receivedMessageTokens[2].indexOf('=') + 1));
				int receivedMessageTimes = Integer
						.parseInt(receivedMessageTokens[3].substring(receivedMessageTokens[3].indexOf('=') + 1));
				System.out.println("Node " + hostNodeId + " received " + receivedMessageClass.toUpperCase()
						+ " message " + receivedMessageTimes + " with timestamp " + receivedMessageTimestamp
						+ " from node " + sendingNodeId);
				synchronized (nodes[hostNodeId]) {
					nodes[hostNodeId].setLogicalClock(
							Math.max(nodes[hostNodeId].getLogicalClock(), receivedMessageTimestamp) + 1);
				}
				if (!nodes[hostNodeId].getOwnRequest().isEmpty()
						&& (receivedMessageTimestamp > nodes[hostNodeId].getOwnRequest().peek().getTimestamp()
								|| receivedMessageTimestamp == nodes[hostNodeId].getOwnRequest().peek().getTimestamp()
										&& receivedMessageSourceNodeId > nodes[hostNodeId].getOwnRequest().peek()
												.getNodeId())) {
					synchronized (permittingNodeIdsThisRequest) {
						permittingNodeIdsThisRequest.add(receivedMessageSourceNodeId);
					}
				}
				if (receivedMessageClass.equals("Request")) {
					synchronized (nodes[hostNodeId]) {
						synchronized (nodes[hostNodeId].getRequestPriorityQueue()) {
							nodes[hostNodeId].getRequestPriorityQueue()
									.offer(new Request(receivedMessageTimestamp, receivedMessageSourceNodeId));
						}
						nodes[hostNodeId].setLogicalClock(nodes[hostNodeId].getLogicalClock() + 1);
						synchronized (messageWriters[sendingNodeId]) {
							messageWriters[sendingNodeId].println(MessageAndString.createMessageString("Reply",
									nodes[hostNodeId].getLogicalClock(), hostNodeId, receivedMessageTimes));
							messageWriters[sendingNodeId].flush();
							System.out.println("Node " + hostNodeId + " sent REPLY message " + receivedMessageTimes
									+ " with timestamp " + nodes[hostNodeId].getLogicalClock() + " to node "
									+ sendingNodeId);
						}
					}
				} else if (receivedMessageClass.contains("Reply")) {

				} else if (receivedMessageClass.contains("Release")) {
					synchronized (nodes[hostNodeId].getRequestPriorityQueue()) {
						nodes[hostNodeId].getRequestPriorityQueue().poll();
					}
				} else if (receivedMessageClass.contains("Done")) {
					synchronized (doneNodeIdsTo0) {
						doneNodeIdsTo0.add(sendingNodeId);
					}
				} else {
					for (Node neighbor : nodes[hostNodeId].getNeighboringNodes()) {
						try {
							synchronized (neighbor) {
								neighbor.getClientSocket().close();
							}
						} catch (IOException ignored) {
							// TODO Auto-generated catch block
						}
					}
					System.out.println("Finished connecting with node " + sendingNodeId + ".");
					break;
				}
			}
		} catch (SocketException ignored) {
			System.out.println("Finished connecting with node " + sendingNodeId + ".");
		} catch (IOException e) {
			// TODO Auto-generated catch block
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
			try {
				modeRicartAndAgrawala = args[1].equalsIgnoreCase("Ricart_and_Agrawala");
			} catch (ArrayIndexOutOfBoundsException ignored) {

			}
			ancestors = "";
			if (System.getProperty("user.dir").endsWith("bin")) { // during manual runs
				ancestors += "../";
			} else { // during launcher script runs
				ancestors += System.getProperty("user.dir") + "/aosproj2/";
			}
			csTestFile = new File(ancestors + "cs_test_file.out");
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
								Integer.parseInt(fileLineTokens[2]));
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
			doneNodeIdsTo0 = new ConcurrentSkipListSet<>();
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
			System.out.println("Program successfully terminated.");
			Thread.sleep(5000);
			for (int i = 0; i < messageWriters.length; i++) {
				if (messageWriters[i] != null) {
					messageWriters[i].close();
				}
			}
			if (hostNodeId == 0) {
				System.out.println("Testing begins!!!");
				TestScriptProject2 test = new TestScriptProject2(cfp);
				test.inspect();
				if (test.getErrorDetected() == false) {
					if (test.getState() == true) {
						System.out.println("Node " + test.getLastNodeEnteringCs() + " did not exit critical section.");
					} else {
						System.out.println("Testing completed. No error was detected.");
					}
				}
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
