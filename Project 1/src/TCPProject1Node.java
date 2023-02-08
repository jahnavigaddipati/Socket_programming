import java.io.*;
import java.net.*;
import java.util.*;

public class TCPProject1Node
 {
	// configuration file tokens
	private Map<Integer, String> nodeIDToName = new HashMap<>();
	private Map<Integer, Integer> nodeIDToPortNumber = new HashMap<>();
	private Map<Integer, Socket> nodeIDToSocket = new HashMap<>();
	private ServerSocket serverSocket;
	private boolean[][] nodeAdjacencyMatrix;
	private int numberOfNodes;
	private int hostNodeID;
	private int nodeCount;
	private int minPerActive;
	private int maxPerActive;
	private int minSendDelay;
    private int snapshotDelay;
	private int maxNumber;
	private boolean active;
	private int totalMessagesSent;
	private long startTime;
	private long endTime;
	
	private final Runnable applicationSendThread = () ->
	{
		// start by being either active or passive
		if (hostNodeID == 0 || (int)(Math.random() * 2) == 1)
		 {
		  	active = true;
		}
		while (totalMessagesSent < maxNumber) 
		{
			// if active
			//	use function to draw random int between minPerActive and maxPerActive (messagesToSend)
			//	while messages sent during active period < messagesToSend
			//		send message to such random neighbor ID
			//		add one to count to messagesToSend
			//		add one to count to maxNumber
			//		if messages sent during active period < messagesToSend
			//		use function to draw random number from minSendDelay up
			//			wait that random number
			// 	become passive
			while (!active);
			int messagesToSend = (int)(Math.random() * (maxPerActive - minPerActive + 1)) + minPerActive;
			for (int messagesSent = 0; messagesSent < messagesToSend;)
			 {
				int neighborsToCheck = (int)(Math.random() * (nodeIDToSocket.size())) + 1;
				int neighborsChecked = 0;
				Set<Integer> nodeIDToSocketKeys = nodeIDToSocket.keySet();
				for (Map.Entry<Integer, Socket> nodeIDToSocketEntry: nodeIDToSocket.entrySet())
				 {
					if (neighborsChecked + 1 == neighborsToCheck) {
						PrintWriter writer = null;
						try {
							writer = new PrintWriter(nodeIDToSocket.get(nodeIDToSocketEntry.getKey()).getOutputStream());
						} catch (IOException ioex) {
							
							ioex.printStackTrace();
						}
						endTime = System.currentTimeMillis();
						if (endTime - startTime > 0 && endTime - startTime < minSendDelay) {
							try {
								Thread.sleep(minSendDelay - endTime + startTime);
							} catch (InterruptedException interruptex) {
						
								interruptex.printStackTrace();
							}
						}
						startTime = System.currentTimeMillis();
						writer.println("Application: Message " + (++messagesSent) + " from " + hostNodeID);
						writer.close();
						break;
					}
					neighborsChecked++;
				}
				totalMessagesSent++;
				// instead of drawing random when approaching the sleep method so as to ensure
				// sleep for at least minSendDelay (which the limitation of requiring a max value),
				// start a clock right after a message is sent, and then as as soon as ready to
				// send the next application message, stop the clock and sleep if less than
				// minSendDelay
				if (messagesSent == messagesToSend) {
					active = false;
				}
			}
			// alternate between active and passive until termination
		}
	};

	
	private final Runnable controlSendThread = () -> {
		
	};
	
	private final Runnable receiveThread = () -> {
		try {
			// if passive
			//	wait for message from itself as server
			//	check if total number of messages sent < maxNumber
			//		become active
			BufferedReader reader = new BufferedReader(new InputStreamReader(nodeIDToSocket.get(nodeCount).getInputStream()));
			while (true) {
				String confirmedMessage = reader.readLine();
				if (!active && totalMessagesSent < maxNumber) {
					active = true;
				}
				reader.close();
			}
			
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
	};

	public void go(String[] args) {
		try {
			hostNodeID = Integer.parseInt(args[1]);
			boolean parseGlobalParameters = true;
			boolean timeToParseNodeInformation = false;
			boolean parseNodeInformation = false;
			boolean timeToParseNodeNeighbors = false;
			boolean parseNodeNeighbors = false;
			boolean parseNoMore = false;
			//Create a client socket and connect to the dcXX servers at their ports
			File configFile = new File("config.txt");
			Scanner input = new Scanner(configFile);
			while (input.hasNext()) {
				String fileLine = input.nextLine();
				if (!fileLine.trim().isEmpty() && fileLine.trim().charAt(0) != '#') {
					if (parseNoMore) {
						throw new Exception("Invalid line in the configuration file after parsing neighbors. Try again.");
					}
					if (timeToParseNodeInformation) {
						timeToParseNodeInformation = false;
						parseNodeInformation = true;
					}
					else if (timeToParseNodeNeighbors) {
						timeToParseNodeNeighbors = false;
						parseNodeNeighbors = true;
					}
					String[] fileLineTokens = fileLine.trim().split(" ");
					if (parseGlobalParameters) {
						numberOfNodes= Integer.parseInt(fileLineTokens[0]);
						minPerActive = Integer.parseInt(fileLineTokens[1]);
						maxPerActive = Integer.parseInt(fileLineTokens[2]);
						minSendDelay = Integer.parseInt(fileLineTokens[3]);
						snapshotDelay = Integer.parseInt(fileLineTokens[4]);
						maxNumber = Integer.parseInt(fileLineTokens[5]);
						if (maxPerActive < minPerActive) {
							throw new Exception("Cannot have a smaller maximum than minimum of messages to send per active period. Try again.");
						}
						nodeAdjacencyMatrix = new boolean[numberOfNodes][numberOfNodes];
						parseGlobalParameters = false;
						timeToParseNodeInformation = true;
					}
					else if (parseNodeInformation) {
						if (nodeCount >= numberOfNodes) {
							throw new Exception("Too many nodes to parse in the configuration file. Try again.");
						}
						if (Integer.parseInt(fileLineTokens[0]) != nodeCount) {
							throw new Exception("Wrong node ID for what should be " + fileLineTokens[0] + " in the configuration file. Try again.");
						}
						if (Integer.parseInt(fileLineTokens[0]) == hostNodeID){
							serverSocket = new ServerSocket(Integer.parseInt(fileLineTokens[2]));
						}
						nodeIDToName.put(Integer.parseInt(fileLineTokens[0]), fileLineTokens[1]);
						nodeIDToPortNumber.put(Integer.parseInt(fileLineTokens[0]), Integer.parseInt(fileLineTokens[2]));
						nodeCount++;
					}
					else if (parseNodeNeighbors) {
						if (nodeCount >= numberOfNodes) {
							throw new Exception("Too many nodes to parse in the configuration file. Try again.");
						}
						for (String fileLineToken: fileLineTokens) {
							if (fileLineToken.charAt(0) == '#') {
								break;
							}
							int fileLineTokenInt = Integer.parseInt(fileLineToken);
							if (fileLineTokenInt == nodeCount) {
								throw new Exception("A node cannot connect to itself. Try again.");
							}
							if (fileLineTokenInt < 0 || fileLineTokenInt >= numberOfNodes) {
								throw new Exception("Neighbor node ID does not exist. Try again.");
							}
							if (nodeAdjacencyMatrix[nodeCount][fileLineTokenInt]) {
								throw new Exception("Neighbor node ID can only be connected once. Try again.");
							}
							nodeAdjacencyMatrix[nodeCount][fileLineTokenInt] = true;
						}
						nodeCount++;
					}
				}
				else if (parseNodeInformation) {
					if (nodeCount < numberOfNodes) {
						throw new Exception("Too few nodes to parse in the configuration file. Try again.");
					}
					nodeCount = 0;
					parseNodeInformation = false;
					timeToParseNodeNeighbors = true;
				}
				else if (parseNodeNeighbors) {
					if (nodeCount < numberOfNodes) {
						throw new Exception("Too few nodes to parse in the configuration file. Try again.");
					}
					parseNodeNeighbors = false;
					parseNoMore = true;
				}
			}
			for (int row = 0; row < numberOfNodes; row++) {
				for (int column = row + 1; column < numberOfNodes; column++) {
					if (nodeAdjacencyMatrix[row][column] != nodeAdjacencyMatrix[column][row]) {
						throw new Exception("Nodes cannot be neighbors in only one direction. Try again.");
					}
				}
			}
			for (int column = 0; column < numberOfNodes; column++) {
				if (nodeAdjacencyMatrix[hostNodeID][column]) {
					Socket neighborSocket = null;
					if (hostNodeID < column) {
						neighborSocket = serverSocket.accept();
					}
					else {
						while (true) {
							try {
								neighborSocket = new Socket(nodeIDToName.get(column), nodeIDToPortNumber.get(column));
								break;
							}
							catch (ConnectException cex) {
								
							}
						}
					}
					nodeIDToSocket.put(column, neighborSocket);
				}
			}
			applicationSendThread.run();
			controlSendThread.run();
		}
		catch (ArrayIndexOutOfBoundsException aex) {
			System.err.println("Need a command line argument for the node ID. Try again.");
			System.exit(1);
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
		catch (Exception e) {
			// TOD Auto-generated catch block
			System.err.println(e.toString());
			System.exit(1);
		}
	}
	
	
	public static void main(String[] args) {
		new TCPProject1Client().go(args);
	}

}
