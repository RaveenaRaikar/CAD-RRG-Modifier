package route.circuit.timing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import route.circuit.Circuit;
import route.circuit.architecture.BlockCategory;
import route.circuit.architecture.BlockType;
import route.circuit.architecture.PortType;
import route.circuit.block.AbstractBlock;
import route.circuit.block.GlobalBlock;
import route.circuit.block.LeafBlock;
import route.circuit.io.SllNetData;
import route.circuit.pin.AbstractPin;
import route.circuit.pin.GlobalPin;
import route.circuit.pin.LeafPin;
import route.circuit.resource.RouteNode;
import route.circuit.timing.TimingGraph;
import route.circuit.timing.TimingNode.Position;
import route.route.Connection;
import route.util.Pair;

public class TimingGraphSLL {
    private static String VIRTUAL_IO_CLOCK = "virtual-io-clock";

    private Circuit[] circuitdie;
    private TimingGraph[] timingGraphDie;
    private HashMap<String, SllNetData> sllNetInfo;
    // A map of clock domains, along with their unique id
    private Map<String, Integer> clockNamesToDomainsFin = new HashMap<>();
    
    private Map<Integer, Integer> clockDomainFanoutFinal = new HashMap<>();
    private int numClockDomainsTot = 0;
    private int virtualIoClockDomain = 0;

    private List<TimingNode> alltimingNodes = new ArrayList<>();
    private List<TimingNode> allSLLNodes = new ArrayList<>();;
    private List<TimingEdge> alltimingEdges  = new ArrayList<>();
    private List<List<TimingEdge>> allTimingNets = new ArrayList<>();
    
    private Map<Integer, List<TimingNode>> allRootNodes, allLeafNodes;
    //Multi-Clock Domain
    private String[] clockNamesFin;
    private boolean clockDomainsSet;
    private boolean[][] includeClockDomain;
    private float[][] maxDelay;
    private float globalMaxDelay;
    private int totaldie;
    //Tarjan's strongly connected components algorithm
    private int index;
    private Stack<TimingNode> stack;
    private List<Connection> allConnections = new ArrayList<>();
    private List<Connection> sllConnections = new ArrayList<>();

    public TimingGraphSLL(Circuit[] circuit, HashMap<String, SllNetData> sllInfo, List<Connection> sllConns, int totDie) {
        this.circuitdie = circuit;
        this.totaldie = totDie;
        this.sllNetInfo = sllInfo;
        this.sllConnections = sllConns;
        // Create the virtual io clock domain
        
    }    
    public TimingGraphSLL(Circuit[] circuit, HashMap<String, SllNetData> sllInfo, int totDie) {
        this.circuitdie = circuit;
        this.totaldie = totDie;
        this.sllNetInfo = sllInfo;
        
        this.clockDomainFanoutFinal.put(this.numClockDomainsTot, 0);
        this.clockNamesToDomainsFin.put(VIRTUAL_IO_CLOCK, this.virtualIoClockDomain);
        // Create the virtual io clock domain
        
    }

    /******************************************
     * These functions build the timing graph *
     ******************************************/    
    
    public void build() {
    	System.out.println("Building System level timing graph\n");
    	this.buildSystemGraph();
        
        this.setRootAndLeafNodes();
        
        this.setClockDomains();
        
        System.out.println("Timing Graph:");
        
        System.out.println("   Num clock domains " + this.numClockDomainsTot);
        System.out.println();
        
    }
    
    public void initializeTiming() {
    	this.calculatePlacementEstimatedWireDelay();
    	this.calculateArrivalRequiredAndCriticality(1, 1);
    	System.out.println("-------------------------------------------------------------------------------");
    	System.out.println("|        Timing information (based on placement estimated wire delay)         |");
    	System.out.println("-------------------------------------------------------------------------------");
    	this.printDelays();
        System.out.println();
        System.out.println();
    }

    private void buildSystemGraph(){
    	this.timingGraphDie = new TimingGraph[this.totaldie]; 	
    	for(int i = 0; i < this.totaldie; i ++) {
    		this.timingGraphDie[i] = this.circuitdie[i].getTimingGraph();
    		this.alltimingNodes.addAll(this.timingGraphDie[i].getTimingNodes());
    		this.alltimingEdges.addAll(this.timingGraphDie[i].getTimingEdges());
    		this.allTimingNets.addAll(this.timingGraphDie[i].getTimingNets());
    		this.allSLLNodes.addAll(this.timingGraphDie[i].getSlltimingNodes());
    		
    		this.numClockDomainsTot = Math.max(this.numClockDomainsTot, this.timingGraphDie[i].getNumClockDomains());
    	}
    	
    	System.out.print("\nThe number of clock domains is " + this.numClockDomainsTot);
    	System.out.print("\nThe number of sllNodes is " + this.allSLLNodes.size());
    	//Create the timing edges between the nodes.
    	for(TimingNode sourceNode: this.allSLLNodes) {
    		if(sourceNode.getPosition().equals(Position.C_SOURCE)) {
    			Map<GlobalBlock, List<TimingEdge>> sourceTimingNets = new HashMap<>();
    			GlobalPin sourcePin = (GlobalPin) sourceNode.getPin();
    			String netName = sourcePin.getNetName();
    			SllNetData sllinfo = this.sllNetInfo.get(netName);
    			List<Map<AbstractBlock, AbstractPin>> sinkInfo = sllinfo.getSinkBlocks();
    			for(int i = 0; i< sinkInfo.size(); i++) {
    				for (Map.Entry<AbstractBlock, AbstractPin> sinkMap : sinkInfo.get(i).entrySet()) {
    					int delay = 0; 
    		            AbstractPin sinkPin = sinkMap.getValue();
    		            TimingNode sinkNode = sinkPin.getTimingNode();
    		            TimingEdge edge = sourceNode.addSink(sinkNode, delay, this.circuitdie[0].getArchitecture().getDelayTables());
    		            this.alltimingEdges.add(edge);
    		            
                        if(sinkNode.getGlobalBlock() != sourceNode.getGlobalBlock()) {
                            if(!sourceTimingNets.containsKey(sinkNode.getGlobalBlock())) {
                                sourceTimingNets.put(sinkNode.getGlobalBlock(), new ArrayList<TimingEdge>());
                            }
                            sourceTimingNets.get(sinkNode.getGlobalBlock()).add(edge);
                        }
    		        }

    			}
    			
    	        for(List<TimingEdge> timingNet : sourceTimingNets.values()) {
    	            this.allTimingNets.add(timingNet);
    	        }
    			
    		}
    	}
    	//Need to traverse from the source to add the timing edges?  
    	
    	//add all the timing domain information
    	this.clockNamesToDomainsFin.put(VIRTUAL_IO_CLOCK, this.virtualIoClockDomain);
    	
    	
    	for(int clockDomain = 0; clockDomain < this.numClockDomainsTot; clockDomain++) {
    		if(!this.clockDomainFanoutFinal.containsKey(clockDomain)) {
    			this.clockDomainFanoutFinal.put(clockDomain, 0);
    		}
    		for(int dieCounter = 0; dieCounter < this.totaldie; dieCounter++) {
    			this.clockDomainFanoutFinal.put(clockDomain, (this.clockDomainFanoutFinal.get(clockDomain) + 1 + this.timingGraphDie[dieCounter].getClockDomainFanout(clockDomain)));
    			this.clockNamesToDomainsFin.putAll(this.timingGraphDie[dieCounter].getClockNamesToDomains());
    		
    		}
    		
    	}
    	
    }
    
    public void addSLLConnections(List<Connection> sllConnections) {
    	this.sllConnections = sllConnections;
    	for(int i = 0; i < this.totaldie; i ++) {
    		this.allConnections.addAll(this.circuitdie[i].getConnections());
    	}
    	
    }


    private void setRootAndLeafNodes(){
    	this.allRootNodes = new HashMap<>();
    	this.allLeafNodes = new HashMap<>();
    
    	for(int clockDomain = 0; clockDomain < this.numClockDomainsTot; clockDomain++) {
    		List<TimingNode> clockDomainRootNodes = new ArrayList<>();
    		List<TimingNode> clockDomainLeafNodes = new ArrayList<>();
    		
	        for (TimingNode timingNode:this.alltimingNodes) {
	        	if (timingNode.getClockDomain() == clockDomain) {
		        	if (timingNode.getPosition().equals(Position.ROOT)) {
		        		clockDomainRootNodes.add(timingNode);
		        	} else if (timingNode.getPosition().equals(Position.LEAF)) {
		        		clockDomainLeafNodes.add(timingNode);
		        	}
	        	}
	        }
	        
	        this.allRootNodes.put(clockDomain, clockDomainRootNodes);
	        this.allLeafNodes.put(clockDomain, clockDomainLeafNodes);
    	}
    }




    /****************************************************************
     * These functions calculate the criticality of all connections *
     ****************************************************************/
    public float getMaxDelay() {
        return 1e9f * this.globalMaxDelay;
    }

 
    public void calculatePlacementEstimatedWireDelay() {
    	for(TimingEdge edge : this.alltimingEdges) {
    		edge.calculatePlacementEstimatedWireDelay();
    	}
    }
    public void calculateActualWireDelay() {
    	//Set wire delay of the connections
    	for(Connection connection : this.allConnections) {
    		float wireDelay = 0;
    		for(RouteNode routeNode : connection.routeNodes) {
    			wireDelay += routeNode.getDelay();
    		}
    		connection.setWireDelay(wireDelay);
    	}
    }

    public void calculateArrivalRequiredAndCriticality(float maxCriticality, float criticalityExponent) {
    	//Initialization
        this.globalMaxDelay = 0;
        
        for(Connection connection : this.allConnections) {
        	connection.resetCriticality();
        }
        for(Connection sllConn : this.sllConnections) {
        	sllConn.resetCriticality();
        }

        
        for(int sourceClockDomain = 0; sourceClockDomain < this.numClockDomainsTot; sourceClockDomain++) {
        	for(int sinkClockDomain = 0; sinkClockDomain < this.numClockDomainsTot; sinkClockDomain++) {
        		if(this.includeClockDomain(sourceClockDomain, sinkClockDomain)) {
        			float maxDelay = 0;
        			
        			for(TimingNode node : this.alltimingNodes) {
        				node.resetArrivalAndRequiredTime();
        			}
                	
        			List<TimingNode> clockDomainRootNodes = this.allRootNodes.get(sourceClockDomain);
        			List<TimingNode> clockDomainLeafNodes = this.allLeafNodes.get(sinkClockDomain);
        			
        			//Arrival time
        			for(TimingNode rootNode: clockDomainRootNodes){
        				rootNode.setArrivalTime(0);
        			}
        			for(TimingNode leafNode: clockDomainLeafNodes){
        				leafNode.recursiveArrivalTime(sourceClockDomain);
        				
        				maxDelay = Math.max((leafNode.getArrivalTime() - leafNode.clockDelay), maxDelay);
        				System.out.print("\nThe leafNode is " + leafNode + " max delay is " + maxDelay);
        			}
                    
        			this.maxDelay[sourceClockDomain][sinkClockDomain] = maxDelay;
        			if(maxDelay > this.globalMaxDelay) {
        				this.globalMaxDelay = maxDelay;
        			}

        			//Required time
        			for(TimingNode leafNode: clockDomainLeafNodes) {
        				leafNode.setRequiredTime(maxDelay + leafNode.clockDelay);
        			}
        			for(TimingNode rootNode: clockDomainRootNodes) {
        				rootNode.recursiveRequiredTime(sinkClockDomain);
        			}
        			
        			//Criticality : TODO
        			for(Connection connection : this.allConnections) {
        				connection.calculateCriticality(maxDelay, maxCriticality, criticalityExponent);
        			}
        			for(Connection sllConn : this.sllConnections) {
        				sllConn.calculateCriticality(maxDelay, maxCriticality, criticalityExponent);
        			}
        		}
        	}
        }
    }
    
    public float calculateTotalCost() {
    	float totalCost = 0;


    	return totalCost;
    }
    
    private void setClockDomains() {
    	//Set clock names
    	this.clockNamesFin = new String[this.numClockDomainsTot];
    	for(String clockDomainName : this.clockNamesToDomainsFin.keySet()) {
    		this.clockNamesFin[this.clockNamesToDomainsFin.get(clockDomainName)] = clockDomainName;
    	}
    	
		for(TimingNode node : this.allSLLNodes) {
			node.setNumClockDomains(this.numClockDomainsTot);
		}
		
		for(TimingNode node : this.alltimingNodes) {		
    		if(node.getPosition() == Position.LEAF) {
    			if(node.getPin().getSLLSinkStatus()) {
    				node.setSourceClockDomains();
    			}
    		} else if(node.getPosition() == Position.ROOT) {
    			if(node.getPin().getSLLSourceStatus()) {
    				node.setSinkClockDomains();
    			}
        		
    		}
    	}
		
		this.includeClockDomain = new boolean[this.numClockDomainsTot][this.numClockDomainsTot];
		this.maxDelay = new float[this.numClockDomainsTot][this.numClockDomainsTot];
    	for(int sourceClockDomain = 0; sourceClockDomain < this.numClockDomainsTot; sourceClockDomain++) {
    		for(int sinkClockDomain = 0; sinkClockDomain < this.numClockDomainsTot; sinkClockDomain++) {
        		this.maxDelay[sourceClockDomain][sinkClockDomain] = -1;
        		this.includeClockDomain[sourceClockDomain][sinkClockDomain] = this.includeClockDomain(sourceClockDomain, sinkClockDomain);
    		}
    	}
    	
    	this.clockDomainsSet = true;
    }
    private boolean includeClockDomain(int sourceClockDomain, int sinkClockDomain) {
    	if(this.clockDomainsSet) {
    		return this.includeClockDomain[sourceClockDomain][sinkClockDomain];
    	} else {
    		if(!this.hasPaths(sourceClockDomain, sinkClockDomain)) {
        		return false;
        	} else {
        		return sourceClockDomain == 0 || sinkClockDomain == 0 || sourceClockDomain == sinkClockDomain;
        	}
    	}
    }
	private boolean hasPaths(int sourceClockDomain, int sinkClockDomain) {
		boolean hasPathsToSource = false;
		boolean hasPathsToSink = false;
		
		for (TimingNode leafNode : this.allLeafNodes.get(sinkClockDomain)) {
			if (leafNode.hasClockDomainAsSource(sourceClockDomain)) {
				hasPathsToSource = true;
			}
		}
		
		for (TimingNode rootNode : this.allRootNodes.get(sourceClockDomain)) {
			if (rootNode.hasClockDomainAsSink(sinkClockDomain)) {
				hasPathsToSink = true;
			}
		}
		
		if(hasPathsToSource != hasPathsToSink) System.err.println("Has paths from leaf to source is not equal to has paths form sink to source");
		return hasPathsToSource;
	}
	private boolean isNetlistClockDomain(int sourceClockDomain, int sinkClockDomain) {
		return sourceClockDomain != 0 && sinkClockDomain != 0;
	}
    
    public void printDelays() {
		String maxDelayString;
    	for(int sourceClockDomain = 0; sourceClockDomain < this.numClockDomainsTot; sourceClockDomain++) {
    		maxDelayString = this.maxDelay[sourceClockDomain][sourceClockDomain] > 0 ? String.format("%.3f", 1e9 * this.maxDelay[sourceClockDomain][sourceClockDomain]) : "---";
    		System.out.printf("%s to %s: %s\n", this.clockNamesFin[sourceClockDomain], this.clockNamesFin[sourceClockDomain], maxDelayString);
    		for(int sinkClockDomain = 0; sinkClockDomain < this.numClockDomainsTot; sinkClockDomain++) {
    			if(sourceClockDomain != sinkClockDomain) {
        			maxDelayString = this.maxDelay[sourceClockDomain][sinkClockDomain] > 0 ? String.format("%.3f", 1e9 * this.maxDelay[sourceClockDomain][sinkClockDomain]) : "---";
        			System.out.printf("\t%s to %s: %s\n", this.clockNamesFin[sourceClockDomain], this.clockNamesFin[sinkClockDomain], maxDelayString);
    			}
    		}
    	}
    	System.out.println();
    	
    	for(int sourceClockDomain = 0; sourceClockDomain < this.numClockDomainsTot; sourceClockDomain++) {
    		maxDelayString = this.maxDelay[sourceClockDomain][sourceClockDomain] > 0 ? String.format("%.3f", 1e9 * this.maxDelay[sourceClockDomain][sourceClockDomain]) : "---";
    		System.out.printf("%d to %d: %s\n", sourceClockDomain, sourceClockDomain, maxDelayString);
    		for(int sinkClockDomain = 0; sinkClockDomain < this.numClockDomainsTot; sinkClockDomain++) {
    			if(sourceClockDomain != sinkClockDomain) {
        			maxDelayString = this.maxDelay[sourceClockDomain][sinkClockDomain] > 0 ? String.format("%.3f", 1e9 * this.maxDelay[sourceClockDomain][sinkClockDomain]) : "---";
        			System.out.printf("  %d to %d: %s\n", sourceClockDomain, sinkClockDomain, maxDelayString);
    			}
    		}
    	}
    	System.out.println();
		
		float geomeanPeriod = 1;
		float fanoutWeightedGeomeanPeriod = 0;
		int totalFanout = 0;
		int numValidClockDomains = 0;
		for(int sourceClockDomain = 0; sourceClockDomain < this.numClockDomainsTot; sourceClockDomain++) {
    		for(int sinkClockDomain = 0; sinkClockDomain < this.numClockDomainsTot; sinkClockDomain++) {
    			if(this.includeClockDomain(sourceClockDomain, sinkClockDomain) && this.isNetlistClockDomain(sourceClockDomain, sinkClockDomain)) {
					float maxDelay = this.maxDelay[sourceClockDomain][sinkClockDomain];
    				int fanout = this.clockDomainFanoutFinal.get(sinkClockDomain);
					
    				geomeanPeriod *= maxDelay;
					totalFanout += fanout;
					fanoutWeightedGeomeanPeriod += Math.log(maxDelay) * fanout;    
					
					numValidClockDomains++;
    			}
			}
		}
		
		geomeanPeriod = (float) Math.pow(geomeanPeriod, 1.0/numValidClockDomains);
		fanoutWeightedGeomeanPeriod = (float) Math.exp(fanoutWeightedGeomeanPeriod/totalFanout);

		System.out.printf("Max delay %.3f ns\n", 1e9 * this.globalMaxDelay);
		System.out.printf("Geometric mean intra-domain period: %.3f ns\n", 1e9 * geomeanPeriod);
		System.out.printf("Fanout-weighted geomean intra-domain period: %.3f ns\n", 1e9 * fanoutWeightedGeomeanPeriod);
		System.out.printf("Timing cost %.3e\n", this.calculateTotalCost());
		System.out.println("-------------------------------------------------------------------------------");
		System.out.println();
    }

    public String criticalPathToString() {
    	int sourceClockDomain = -1;
    	int sinkClockDomain = -1;
    	float maxDelay = 0;
    	
    	for(int i = 0; i < this.numClockDomainsTot; i++) {
    		for(int j = 0; j < this.numClockDomainsTot; j++) {
        		if(this.maxDelay[i][j] > maxDelay) {
        			maxDelay = this.maxDelay[i][j];
        			sourceClockDomain = i;
        			sinkClockDomain = j;
        		}
        	}
    	}
    	List<TimingNode> criticalPath = new ArrayList<>();
		TimingNode node = this.getEndNodeOfCriticalPath(sinkClockDomain);
		criticalPath.add(node);
		System.out.print("\nThe node is "  + node);
		while(!node.getSourceEdges().isEmpty()){
    		node = this.getSourceNodeOnCriticalPath(node, sourceClockDomain);
    		criticalPath.add(node);
    	}
    	
    	int maxLen = 25;
    	for(TimingNode criticalNode:criticalPath){
    		if(criticalNode.toString().length() > maxLen){
    			maxLen = criticalNode.toString().length();
    		}
    	}
    	
    	String delay = String.format("Critical path: %.3f ns", this.globalMaxDelay * Math.pow(10, 9));
    	String result = String.format("%-" + maxLen + "s  %-3s %-3s  %-9s %-8s\n", delay, "x", "y", "Tarr (ns)", "LeafNode");
    	result += String.format("%-" + maxLen + "s..%-3s.%-3s..%-9s.%-8s\n","","","","","").replace(" ", "-").replace(".", " ");
    	for(TimingNode criticalNode:criticalPath){
    		result += this.printNode(criticalNode, maxLen);
    	}
    	return result;
    }
    private TimingNode getEndNodeOfCriticalPath(int sinkClockDomain){
    	for(TimingNode leafNode: this.allLeafNodes.get(sinkClockDomain)){
    		if(compareFloat(leafNode.getArrivalTime(), this.globalMaxDelay)){
    			return leafNode;
    		}
    	}
    	return null;
    }
    private TimingNode getSourceNodeOnCriticalPath(TimingNode sinkNode, int sourceClockDomain){
		for(TimingEdge edge: sinkNode.getSourceEdges()){
			if(this.compareFloat(edge.getSource().getArrivalTime(), sinkNode.getArrivalTime() - edge.getTotalDelay())){
				if(edge.getSource().hasClockDomainAsSource(sourceClockDomain)) {
					return edge.getSource();
				}
			}
		}
		return null;
    }
    private String printNode(TimingNode node, int maxLen){
    	String nodeInfo = node.toString();
    	int x = node.getGlobalBlock().getColumn();
    	int y = node.getGlobalBlock().getRow();
    	double delay = node.getArrivalTime() * Math.pow(10, 9);
    	
    	return String.format("%-" + maxLen + "s  %-3d %-3d  %-9s\n", nodeInfo, x, y, String.format("%.3f", delay));
    }
    private boolean compareFloat(float var1, float var2){
    	return Math.abs(var1 - var2) < Math.pow(10, -12);
    }

}
