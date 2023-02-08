import java.io.*;
import java.net.*;
import java.util.*;

public class TCPProject1Server {
	// configuration file tokens
	Map<Integer, String> nodeIDToName = new HashMap<>();
	Map<Integer, Integer> nodeIDToPortNumber = new HashMap<>();
	Map<String, Integer> nodeNameToID = new HashMap<>();
	int numberOfNodes = 0;
	int minPerActive = 0;
	int maxPerActive = 0;
	int minSendDelay = 0;
	int snapshotDelay = 0;
	int maxNumber = 0;
	int nodeCount = 0;
	Socket otherClientSock = null;
	Socket selfClientSock = null;
	
	public void mapProtocol() throws IOException {
		while (true) {
			//	wait for message from some client
			BufferedReader reader = new BufferedReader(new InputStreamReader(otherClientSock.getInputStream()));
			String messageFromClient = reader.readLine();
			System.out.println("Received " + messageFromClient);
			reader.close();
			//	Once it receives a message it sends a confirmation message to client
			PrintWriter writer = new PrintWriter(selfClientSock.getOutputStream());
			writer.println("Received " + messageFromClient);
			writer.close();
		}
	}
	
	public void go() {
		try {
			boolean parseGlobalParameters = true;
			boolean timeToParseNodeInformation = false;
			boolean parseNodeInformation = false;
			boolean parseNodeNeighbors = false;
			File configFile = new File("config.txt");
			Scanner input = new Scanner(configFile);
			ServerSocket serverSock = null;
			while (input.hasNext()) {
				String fileLine = input.nextLine();
				if (!fileLine.isEmpty() && fileLine.charAt(0) != '#') {
					if (timeToParseNodeInformation) {
						timeToParseNodeInformation = false;
						parseNodeInformation = true;
					}
					String[] fileLineTokens = fileLine.trim().split(" ");
					if (parseGlobalParameters) {
						numberOfNodes= Integer.parseInt(fileLineTokens[0]);
						minPerActive = Integer.parseInt(fileLineTokens[1]);
						maxPerActive = Integer.parseInt(fileLineTokens[2]);
						minSendDelay = Integer.parseInt(fileLineTokens[3]);
						snapshotDelay = Integer.parseInt(fileLineTokens[4]);
						maxNumber = Integer.parseInt(fileLineTokens[5]);
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
						if (InetAddress.getByName(null).getHostName().equals(fileLineTokens[1] + ".utdallas.edu")) {
							serverSock = new ServerSocket(Integer.parseInt(fileLineTokens[2]));
						}
						nodeIDToName.put(Integer.parseInt(fileLineTokens[0]), fileLineTokens[1]);
						nodeIDToPortNumber.put(Integer.parseInt(fileLineTokens[0]), Integer.parseInt(fileLineTokens[2]));
						nodeNameToID.put(fileLineTokens[1] + ".utdallas.edu", Integer.parseInt(fileLineTokens[0]));
						nodeCount++;
					}
					else if (parseNodeNeighbors) {
						if (serverSock == null) {
							throw new Exception("Error in resolving hostname. Try again.");
						}
						if (nodeCount >= numberOfNodes) {
							throw new Exception("Too many nodes to parse in the configuration file. Try again.");
						}
						for (int neighborCount = 0; neighborCount < fileLineTokens.length; neighborCount++) {
							if (fileLineTokens[neighborCount].equals("#")) {
								break;
							}
							if (Integer.parseInt(fileLineTokens[neighborCount]) <= 0 || Integer.parseInt(fileLineTokens[neighborCount]) >= numberOfNodes) {
								throw new Exception("Node ID does not exist. Try again.");
							}
							if (nodeNameToID.get(InetAddress.getByName(null).getHostName()).equals(Integer.parseInt(fileLineTokens[neighborCount]))) {
								otherClientSock = serverSock.accept();
								break;
							}
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
					parseNodeNeighbors = true;
				}
				else if (parseNodeNeighbors) {
					if (nodeCount < numberOfNodes) {
						throw new Exception("Too few nodes to parse in the configuration file. Try again.");
					}
					parseNodeNeighbors = false;
				}
			}
			selfClientSock = serverSock.accept();
			mapProtocol();
		}
		catch (IOException ex) {
			ex.printStackTrace();
		}
		catch (Exception e) {
			System.err.println(e.toString());
			System.exit(1);
		}
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		new TCPProject1Server().go();
	}		

}
