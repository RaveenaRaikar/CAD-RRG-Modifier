package route.circuit.resource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import route.circuit.architecture.ParseException;
import route.circuit.exceptions.InvalidFileFormatException;
import route.circuit.Circuit;
import route.circuit.architecture.Architecture;
import route.circuit.architecture.BlockCategory;
import route.circuit.architecture.BlockType;
import route.circuit.resource.Site;
import route.circuit.resource.RouteNode;

public class ResourceGraph {
	private final Circuit[] circuit;
	private final Architecture architecture;
	
	private final int width, height;
	private File RRGFile;
    private Document xmlDocument;
    private XPath xPath = XPathFactory.newInstance().newXPath();
    
	private final List<Site> sites;
	private final Site[][] siteArray;
	
	private int sllRows;
	private float sllDelay;
	private int totDie;
	private int dieBoundary; 
	
	private int indexStart = 0;
	private final Map<String, BlockTypeRRG> blockTypeNodes;
	private final List<RouteNode> routeNodes;
	private final List<RouteNode> routeNodesDeleted;
	private final List<RouteNode> boundaryDeletedNodes;
	private final Map<String, SLLRouteNode> interposerWireNodes;
	
	private final List<RouteNode>[][] allReplaceNodes;
	private final Map<RouteNode, List<RouteNode>> childToParentMap;
	private Map<String, IndexedData> indexedDataList;
	private Map<Float, String> baseCostToLength;
	private List<RouteSwitch> switchTypesList;
	private Map<String , Integer> lengthToSwitchMap;
	private Map<Integer, String> segmentList;
	
	private final Map<RouteNodeType, List<RouteNode>> routeNodeMap;
	
	private static int SOURCE_COST_INDEX = 0;
	private static int SINK_COST_INDEX = 1;
	private static int OPIN_COST_INDEX = 2;
	private static int IPIN_COST_INDEX = 3;
	
    public ResourceGraph(Circuit[] circuit) {
    	this.circuit = circuit;
    	this.architecture = this.circuit[0].getArchitecture();
    	this.RRGFile = this.architecture.getRRGFile();
    	System.out.print("\nthe RRG is " + this.RRGFile);
    	
    	this.width = this.architecture.getWidth();
    	this.height = this.architecture.getHeight();
    	
    	this.sllRows = this.architecture.getSllRows();
    	this.sllDelay = (float) (this.architecture.getSllDelay() * 1e-12);
    	this.totDie = this.architecture.getTotDie();
    	this.dieBoundary = this.height/this.totDie;
		this.sites = new ArrayList<>();
		this.siteArray = new Site[this.width+2][this.height+2];
		
		this.blockTypeNodes = new HashMap<>();
		this.routeNodes = new ArrayList<>();
		this.routeNodesDeleted = new ArrayList<>();
		this.boundaryDeletedNodes = new ArrayList<>();
		
		this.interposerWireNodes = new HashMap<>();
		this.allReplaceNodes = new List[this.width][this.height];
		this.childToParentMap = new HashMap<RouteNode, List<RouteNode>>();
		this.routeNodeMap = new HashMap<>();
		for(RouteNodeType routeNodeType: RouteNodeType.values()){
			List<RouteNode> temp = new ArrayList<>();
			this.routeNodeMap.put(routeNodeType, temp);
		}
       	for(int i = 0; i < this.width - 1; i++) {
        	for(int j = 0; j < this.height - 1 ; j++) {
	        		this.allReplaceNodes[i][j] = new ArrayList<RouteNode>();
        	}
        }
		
    }
    
    public void build(){
        this.createSites();
        
		try {
			this.parseRRG();
		} catch (ParseException | IOException | InvalidFileFormatException | InterruptedException | ParserConfigurationException | SAXException error)  {
			System.err.println("Problem in generating RRG: " + error.getMessage());
			error.printStackTrace();
		}
		
		this.assignNamesToSourceAndSink();
		this.connectSourceAndSinkToSite();
    }
    
    public IndexedData get_ipin_indexed_data() {
    	return this.indexedDataList.get("IPIN");
    }
    public IndexedData get_opin_indexed_data() {
    	return this.indexedDataList.get("OPIN");
    }
    public IndexedData get_source_indexed_data() {
    	return this.indexedDataList.get("SOURCE");
    }
    public IndexedData get_sink_indexed_data() {
    	return this.indexedDataList.get("SINK");
    }
    public Map<String, IndexedData> getIndexedDataList() {
    	return this.indexedDataList;
    }
    
    private void createSites() {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        int ioCapacity = this.architecture.getIoCapacity();

        int ioHeight = ioType.getHeight();
        
        //IO Sites
        for(int i = 1; i < this.height - 1; i++) {
        	this.addSite(new Site(1, i, ioHeight, ioType, ioCapacity));
            this.addSite(new Site(this.width - 2, i, ioHeight, ioType, ioCapacity));
        }
        for(int i = 1; i < this.width - 1; i++) {
        	this.addSite(new Site(i, 1, ioHeight, ioType, ioCapacity));
            this.addSite(new Site(i, this.height - 2, ioHeight, ioType, ioCapacity));
        }
        
        for(int column = 2; column < this.width - 2; column++) {
            BlockType blockType = this.circuit[0].getColumnType(column);
            
            int blockHeight = blockType.getHeight();
            for(int row = 2; row < this.height - 1 - blockHeight; row += blockHeight) {
            	this.addSite(new Site(column, row, blockHeight, blockType, 1));
            }

        }
    }
    public void addSite(Site site) {
    	this.siteArray[site.getColumn()][site.getRow()] = site;
    	this.sites.add(site);
    }
    
    /**
     * Return the site at coordinate (x, y). If allowNull is false,
     * return the site that overlaps coordinate (x, y) but possibly
     * doesn't start at that position.
     */
    public Site getSite(int column, int row) {
        return this.getSite(column, row, false);
    }
    public Site getSite(int column, int row, boolean allowNull) {
        if(allowNull) {
            return this.siteArray[column][row];
        } else {
            Site site = null;
            int topY = row;
            while(site == null) {
                site = this.siteArray[column][topY];
                topY--;
            }
            
            return site;
        }
    }
    
    public String getBlocktypeAtSite(int column, int row) {
    	Site blockSite = this.getSite(column, row);
    	String blockName = blockSite.getInstance(0).getBlockType().getName();
    	return blockName;
    }
    public List<Site> getSites(BlockType blockType) {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        List<Site> sites;
        
        if(blockType.equals(ioType)) {
            int ioCapacity = this.architecture.getIoCapacity();
            sites = new ArrayList<Site>((this.width + this.height) * 2 * ioCapacity);
            
            for(int n = 0; n < ioCapacity; n++) {
            	for(int i = 1; i < this.height - 1; i++) {
                    sites.add(this.siteArray[1][i]);
                    sites.add(this.siteArray[this.width - 2][i]);
                }
                
            	for(int i = 1; i < this.width - 1; i++) {
                    sites.add(this.siteArray[i][1]);
                    sites.add(this.siteArray[i][this.height - 2]);
                }
            }
        } else {
        	
        	//assumption: circuits are identical
            List<Integer> columns = this.circuit[0].getColumnsPerBlockType(blockType);
            int blockHeight = blockType.getHeight();
            sites = new ArrayList<Site>(columns.size() * this.height);
            
            for(Integer column : columns) {
            	for(int row = 2; row < this.height - 2 - blockHeight; row += blockHeight) {
                    sites.add(this.siteArray[column][row]);
                }
            }
        }
    
        return sites;
    }
    public List<Site> getSites(){
    	return this.sites;
    }
    
    /******************************
     * GENERATE THE RRG READ FROM * 
     * RRG FILE DUMPED BY VPR     *
     ******************************/
    //Changing the RRG format to now read as an XML file instead of VPR7 format.
    
    private void parseRRG() throws ParseException, IOException, InvalidFileFormatException, InterruptedException, ParserConfigurationException, SAXException{
		System.out.println("---------------");
		System.out.println("| Process RRG |");
		System.out.println("---------------");
        
        //Process index list
        this.processIndexList();
        //process channels
        
        //process switches
        this.processSwitchList();
        
        //process segments
        this.processSegmentList();
        //process blocktypes
        this.processBlockTypes();
        //process RRG nodes
        this.processRRGNodes();
        

        //Add the interposer nodes
        long startTime = System.nanoTime();
        this.addInterposerNodes();
        //Build a map for replacement node and interposer node;
        long endTime   = System.nanoTime();
        long totalTime = endTime - startTime;
        System.out.println("\nAdding Interposer nodes took " + (totalTime*1e-9) +"s");
        
        //Identify the replace nodes
        this.allReplaceNodes();
        startTime = System.nanoTime();
        //Process RRG edges
        this.processRRGEdges();
        //Add SLL edges
        endTime   = System.nanoTime();
        totalTime = endTime - startTime;
        System.out.println("\nProcessing edges took " + (totalTime*1e-9) +"s");
        
        startTime = System.nanoTime();
        this.processInterposerEdges();
        //Process the SLL information
        endTime   = System.nanoTime();
        totalTime = endTime - startTime;
        System.out.println("\nProcessing interposer edges took " + (totalTime*1e-9) +"s");
        
        this.postProcess();

        
        this.writeRRNodeToFile();
        this.writeModifiedEdges();
    }
    
    private void postProcess() {
    	System.out.print("\nThe postProcess is on going");
		for(RouteNode node : this.routeNodes) {
			for(int i = 0; i < node.getNumChildren(); i++) {
				RouteNode child = node.children[i];
				if(child != null) {
					RouteSwitch routeSwitch = node.switches[i];
					child.setDelay(routeSwitch);
				}
				
			}
		}
		for(RouteNode node : this.routeNodeMap.get(RouteNodeType.SOURCE)) {
			Source source = (Source) node;
			source.setDelay(null);
		}
		
		System.out.println();
    }
 
    private void allReplaceNodes() {
    	int xlow, xhigh, ylow, yhigh;
    	String direction;
    	for(SLLRouteNode sllWire : this.interposerWireNodes.values()) {
    		direction = sllWire.direction;
			xlow = sllWire.xlow;
			ylow = sllWire.ylow;
			yhigh = sllWire.yhigh;
    		if(direction.equals("INC_DIR")) {
    			List<RouteNode> allNodes; 			
    			if(!sllWire.hasSourceNode()) {
    				allNodes = this.allReplaceNodes[xlow][ylow];
    				for(RouteNode node: allNodes) {
    					if(node.direction.equals(direction) && !node.isReplacedNode && (node.xlow == xlow) && (node.ylow == ylow)) {
    						sllWire.setSourceReplaceNode(node);
    						node.isReplacedNode();
    						break;
    					}
    				}
    			}
    			if(!sllWire.hasSinkNode()) {
    				allNodes = this.allReplaceNodes[xlow][yhigh];
    				for(RouteNode node: allNodes) {
    					if(node.direction.equals(direction) && !node.isReplacedNode && (node.xlow == xlow) && (node.yhigh == yhigh)) {
    						sllWire.setSinkReplaceNode(node);
    						node.isReplacedNode();
    						break;
    					}
    				}
    			}
    		}else if(direction.equals("DEC_DIR")) {
    			List<RouteNode> allNodes; 			
    			if(!sllWire.hasSourceNode()) {
    				allNodes = this.allReplaceNodes[xlow][yhigh];
    				for(RouteNode node: allNodes) {
    					if(node.direction.equals(direction) && !node.isReplacedNode && (node.xlow == xlow) && (node.yhigh == yhigh)) {
    						sllWire.setSourceReplaceNode(node);
    						node.isReplacedNode();
    						break;
    					}
    				}
    			}
    			if(!sllWire.hasSinkNode()) {
    				allNodes = this.allReplaceNodes[xlow][ylow];
    				for(RouteNode node: allNodes) {
    					if(node.direction.equals(direction) && !node.isReplacedNode && (node.xlow == xlow) && (node.ylow == ylow)) {
    						sllWire.setSinkReplaceNode(node);
    						node.isReplacedNode();
    						break;
    					}
    				}
    			}
    		}   		
    	}

    }
    
    private void processSegmentList() throws IOException {
    	System.out.print("\n Processing the segmentList");
		this.segmentList = new HashMap<>();
		String rrgIndexFileName = this.RRGFile.getAbsolutePath();
		System.out.print("RR file path is " + rrgIndexFileName);
        int lastSlashIndex = rrgIndexFileName.lastIndexOf("/");
        String newFileName = null;
        if (lastSlashIndex >= 0) {
            String directoryPath = rrgIndexFileName.substring(0, lastSlashIndex + 1); // Include the last slash
            newFileName = directoryPath + "segment_info.echo";
        }
            //return newFileName;
		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(newFileName));
		StringBuilder contentBuilder = new StringBuilder();
		System.out.println("\n   Read " + newFileName);
        String line;
        int id = 0;
        String sllInfo = "";
 
		while ((line = reader.readLine()) != null) {
			contentBuilder.append(line).append(System.lineSeparator());
			line = line.trim();

			if (line.length() > 0) {
				while(line.contains("  ")) line = line.replace("  ", " ");
				
				String[] words = line.split(";");
		        id = Integer.parseInt(words[0]);
		        String name = words[1];
		        this.segmentList.put(id, name);

			}
		}
		//Add the segment information for the SLL wire
		String sllName = "L" + this.sllRows + "SN";
		id++;
		this.segmentList.put(id, sllName);
        reader.close();
    	
        float rWire, Cwire = 0;
        
        rWire = this.architecture.getSLLSegmentInfo().get("Rmetal");
        Cwire = this.architecture.getSLLSegmentInfo().get("Cmetal");
		
		
        sllInfo += id + ";";
        sllInfo += sllName + ";";
        sllInfo += Cwire + ";";
        sllInfo += rWire + ";";
        
        for(Integer idN: this.segmentList.keySet()) {
        	System.out.print("\nThe id is " + idN + " and the value is " + this.segmentList.get(idN));
        }
        
		File indexFile = new File(this.RRGFile.getParentFile(), "segment_info"+this.sllRows+"L.echo");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(indexFile))) {
            writer.write(contentBuilder.toString());
            writer.write(sllInfo);
        }
    }

	private void processSwitchList() throws IOException{
		System.out.print("\n Processing the switchList");
		this.switchTypesList = new ArrayList<>();
		this.lengthToSwitchMap = new HashMap<String, Integer>();
		String rrgIndexFileName = this.RRGFile.getAbsolutePath();
		System.out.print("RR file path is " + rrgIndexFileName);
        int lastSlashIndex = rrgIndexFileName.lastIndexOf("/");
        String newFileName = null;
        String sllInfo = "";
        
        if (lastSlashIndex >= 0) {
            String directoryPath = rrgIndexFileName.substring(0, lastSlashIndex + 1); // Include the last slash
            newFileName = directoryPath + "switch_info.echo";
        }

		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(newFileName));
		StringBuilder contentBuilder = new StringBuilder();
		System.out.println("\n   Read " + newFileName);
        String line;

        int indexCounter = 0;
		while ((line = reader.readLine()) != null) {
			contentBuilder.append(line).append(System.lineSeparator());
			line = line.trim();
			if (line.length() > 0) {
				
				this.switchTypesList.add(new RouteSwitch(line));
			}
			indexCounter++;
		}
		
		//Add the switch info based on the input.
		String switchName = "seg" + this.sllRows +"_driverSN";
		float rsw,csw,tdel, cin = 0;
		cin = 0.1e-15f;
		rsw = this.architecture.getSLLSegmentInfo().get("RSwitch");
		tdel = this.architecture.getSLLSegmentInfo().get("tdel");
		csw = this.architecture.getSLLSegmentInfo().get("CSwitch");
		RouteSwitch tempInfo = this.switchTypesList.get(indexCounter - 1);
		this.switchTypesList.add(new RouteSwitch(indexCounter, switchName, rsw, cin, csw, tdel, tempInfo.mux_trans_size, tempInfo.buf_size));
        reader.close();
        
        sllInfo += indexCounter + ";";
        sllInfo += switchName + ";";
        sllInfo += "mux;";
        sllInfo += cin + ";0;";
        sllInfo += csw + ";";
        sllInfo += rsw + ";";
        sllInfo += tdel + ";";
        sllInfo += tempInfo.buf_size + ";";
        sllInfo += tempInfo.mux_trans_size + ";";
        
        
		for(RouteSwitch switchInfor : this.switchTypesList) {
			this.lengthToSwitchMap.put( switchInfor.name, switchInfor.index);
			System.out.println("\nThe switch id is " + switchInfor.index + " and the name is " + switchInfor.name);
		}
		File indexFile = new File(this.RRGFile.getParentFile(), "switch_info"+this.sllRows+"L.echo");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(indexFile))) {
            writer.write(contentBuilder.toString());
            // Append additional content
            writer.write(sllInfo);
        }
	}

	private void processBlockTypes() throws IOException {
		System.out.print("\n Processing the Blocktypes");
		String rrgIndexFileName = this.RRGFile.getAbsolutePath();
		System.out.print("RR file path is " + rrgIndexFileName);
        int lastSlashIndex = rrgIndexFileName.lastIndexOf("/");
        String newFileName = null;
        if (lastSlashIndex >= 0) {
            String directoryPath = rrgIndexFileName.substring(0, lastSlashIndex + 1); // Include the last slash
            newFileName = directoryPath + "block_type_info.echo";
        }
            //return newFileName;
		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(newFileName));
		System.out.println("\n   Read " + newFileName);
        String line;

        BlockTypeRRG newBlock = null;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.length() > 0) {
				while(line.contains("  ")) line = line.replace("  ", " ");				
				String[] words = line.split(";");

		        int id = Integer.parseInt(words[1]);
		        String name = words[2];
	            int width = Integer.parseInt(words[3]);
	            int height = Integer.parseInt(words[0]);  
	            if(this.blockTypeNodes.get(name)!= null) {
	            	newBlock = this.blockTypeNodes.get(name);
	            }else {
	            	newBlock = new BlockTypeRRG(id, height, width, name);
	            }
	            String pinClassType = words[4];
	            String pinValue	= words[6];
	            int ptc = Integer.parseInt(words[5]);
	            newBlock.addPinClass(pinValue, ptc, pinClassType);
	            this.blockTypeNodes.put(name, newBlock);
			}
		}
		
        reader.close();
        
	}

	private void processRRGNodes() throws IOException {
    	System.out.print("\n Processing the RRGNodes");
		String rrgIndexFileName = this.RRGFile.getAbsolutePath();
		System.out.print("RR file path is " + rrgIndexFileName);
        int lastSlashIndex = rrgIndexFileName.lastIndexOf("/");
        String newFileName = null;
        if (lastSlashIndex >= 0) {
            String directoryPath = rrgIndexFileName.substring(0, lastSlashIndex + 1); // Include the last slash
            newFileName = directoryPath + "rrNode_info.echo";
        }
            //return newFileName;
		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(newFileName));
		System.out.println("\n   Read " + newFileName);
		RouteNode routeNode = null;
		BlockTypeRRG blockNode = null;
		String currentBlockTypeName = null;
		String currentPort = null;
		int portIndex = -1;
		IndexedData data = null;
		String fullName = null;
		String name = null;
		String names[] = null;
		String line;
        String[] words = null;
        int replaceCounter = 0; 
        int removeCounter = 0;
        int Counter = 0;
        //Starting with INC_DIR, all nodes start at ylow and end at yhigh
    	for(int xCord = 2; xCord < this.width - 2; xCord++) {
        	for(int yCord = (this.dieBoundary - this.sllRows + 1); yCord < this.dieBoundary; yCord++) {
        		int xlow = xCord, xhigh = xCord;
        		int ylow = yCord;
        		int yhigh = yCord + this.sllRows - 1;
        		String index = "INC_" + String.valueOf(xlow) + "_" + String.valueOf(ylow);       		
        		this.interposerWireNodes.put(index, new SLLRouteNode("INC_DIR", xlow, xhigh, ylow, yhigh));
        		index = "DEC_" + String.valueOf(xlow) + "_" + String.valueOf(yhigh);
        		this.interposerWireNodes.put(index,new SLLRouteNode("DEC_DIR", xlow, xhigh, ylow, yhigh));
    		}
		}
        
        
        //reader.readLine();
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.length() > 0) {
				while(line.contains("  ")) line = line.replace("  ", " ");
        		words = line.split(";");
        		int capacity = Integer.parseInt(words[3]);
        		int index = Integer.parseInt(words[0]);
        		this.indexStart = Math.max(this.indexStart, index);
        		String direction = words[2];
        		String type = words[1];
        		//String side = words[8];
        		int ptc = Integer.parseInt(words[8]);;
        		int xhigh = Integer.parseInt(words[6]);
        		int xlow  = Integer.parseInt(words[4]);
        		int yhigh = Integer.parseInt(words[7]);
        		int ylow  = Integer.parseInt(words[5]);
      
        		int segID = 0;
        		String chanType;
        		float Reg = Float.parseFloat(words[9]);
        		float Cap = Float.parseFloat(words[10]);
        		int numChildren = Integer.parseInt(words[12]);
        		switch (type) {
    				case "SOURCE":        				
    					assert Reg == 0;
    					assert Cap == 0;
    					data = this.indexedDataList.get(type);
    					routeNode = new Source(index, xlow, xhigh, ylow, yhigh, ptc, capacity, data, numChildren);
    					
    					break;
    				case "SINK":        				
    					assert Reg == 0;
    					assert Cap == 0;
    					data = this.indexedDataList.get(type);
    					routeNode = new Sink(index, xlow, xhigh, ylow, yhigh, ptc, capacity, data, numChildren);
    					
    					break;
	    			case "IPIN":
	    				//Assertions
	    				assert capacity == 1;
	    				assert Reg == 0;
	    				assert Cap == 0;
	    				currentBlockTypeName = this.getBlocktypeAtSite(xlow, ylow);

	    				blockNode = this.blockTypeNodes.get(currentBlockTypeName);
	    				fullName = blockNode.getPinClassNameByPinId(ptc); // currentBlockTypeName);
	    				
	    				names = fullName.split("\\.");
	    				names = names[1].split("\\[");
	    				name = names[0];
	    				if(currentPort == null){
	    					currentPort = name;
	    					portIndex = 0;
	    				}else if(!currentPort.equals(name)){
	    					currentPort = name;
	    					portIndex = 0;
	    				}
	    				data = this.indexedDataList.get(type);
	    				routeNode = new Ipin(index, xlow, xhigh, ylow, yhigh, ptc, currentPort, portIndex, data, direction, numChildren);
	    				
	    				portIndex += 1;
	    				
	    				break;
	    			case "OPIN":        				
	    				//Assertions
	    				assert capacity == 1;
	    				assert Reg == 0;
	    				assert Cap == 0;
	    				
	    				currentBlockTypeName = this.getBlocktypeAtSite(xlow, ylow);
	    				blockNode = this.blockTypeNodes.get(currentBlockTypeName);
	    				fullName = blockNode.getPinClassNameByPinId(ptc); //, currentBlockTypeName);
	    				
	    				names = fullName.split("\\.");
	    				names = names[1].split("\\[");
	    				name = names[0];
	    				
	    				if(currentPort == null){
	    					currentPort = name;
	    					portIndex = 0;
	    				}else if(!currentPort.equals(name)){
	    					currentPort = name;
	    					portIndex = 0;
	    				}
	    				data = this.indexedDataList.get(type);
	    				routeNode = new Opin(index, xlow, xhigh, ylow, yhigh, ptc, currentPort, portIndex, data, direction, numChildren);
	    				
	    				portIndex += 1;
	    				
	    				break;
	    			case "CHANX":        				
	    				assert capacity == 1;
	    				segID = Integer.parseInt(words[11]);
	    				chanType = type + "_" + this.segmentList.get(segID);
	    				data = this.indexedDataList.get(chanType);
	    				routeNode = new Chanx(index, xlow, xhigh, ylow, yhigh, ptc, Reg, Cap, data, direction, numChildren, segID);
	    				
	    				break;
	    			case "CHANY":        				

	    				assert capacity == 1;
	    				segID = Integer.parseInt(words[11]);
	    				chanType = type + "_" + this.segmentList.get(segID);
	    				data = this.indexedDataList.get(chanType);
	    				routeNode = new Chany(index, xlow, xhigh, ylow, yhigh, ptc, Reg, Cap, data, direction, numChildren, segID);
	    				
	    				break;
	    			default:
	    				System.out.println("Unknown type: " + type);
	    				break;
	    		}
        		

        		if(this.isInInterposerRegion(xlow, ylow, yhigh)) {
        			if(type.equals("CHANY")) {
        				if((yhigh - ylow == 5) && (xlow == xhigh)) {
        					if((ylow < this.dieBoundary && yhigh >= this.dieBoundary)) {
        						routeNode.isDeletedNode();
                				this.routeNodesDeleted.add(routeNode);
            					this.allReplaceNodes[xlow][ylow].add(routeNode);
            					this.allReplaceNodes[xlow][yhigh].add(routeNode);
        					}else{
            					this.allReplaceNodes[xlow][ylow].add(routeNode);
            					this.allReplaceNodes[xlow][yhigh].add(routeNode);
        					}
        					
        					if(yhigh == (this.dieBoundary -1) || ylow == this.dieBoundary) {
            					routeNode.setBorderStatus();
            					this.boundaryDeletedNodes.add(routeNode);
            				}
        				}else if((ylow < this.dieBoundary && yhigh >= this.dieBoundary)) {
        					routeNode.isDeletedNode();
        					this.routeNodesDeleted.add(routeNode);
        					removeCounter++;
        				}else if(yhigh == (this.dieBoundary -1) || ylow == this.dieBoundary) {
        					routeNode.setBorderStatus();
        					Counter++;
        				}
        			}else if(type.equals("CHANX")) {
        				if(yhigh == (this.dieBoundary -1) || ylow == this.dieBoundary) {
        					routeNode.setBorderStatus();
        					Counter++;
        				}
        			}
        		}
	
        		this.addRouteNode(routeNode);
        		
        		
        			
				
			}
		}
		System.out.print("\nThe size of routing node deleted is " + this.routeNodesDeleted.size());
		System.out.print("available sll nodes " + replaceCounter + " removed " + removeCounter);
		System.out.print("\n New route node is " + Counter);
        reader.close();
        System.out.print("\n RR nodes: " + this.routeNodes.size());
	}
   
	
	public boolean checkEdgeCondition(int ylow) {
		Boolean isValid = false;
		
		if((ylow >= this.dieBoundary - 5)) {
			isValid = true;
		}
		
		return isValid;
	}
	
    private void processRRGEdges() throws IOException {
    	System.out.print("\n Processing the RRGEdges");
		String rrgIndexFileName = this.RRGFile.getAbsolutePath();
		System.out.print("\nRR file path is " + rrgIndexFileName);
        int lastSlashIndex = rrgIndexFileName.lastIndexOf("/");
        String newFileName = null;
        if (lastSlashIndex >= 0) {
            String directoryPath = rrgIndexFileName.substring(0, lastSlashIndex + 1); // Include the last slash
            newFileName = directoryPath + "rrEdge_info.echo";
        }
		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(newFileName));
		System.out.println("\n   Read " + newFileName);
		int counterRemovd = 0;
		String line;
        String[] words = null;
        int counter = 0;
        while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.length() > 0) {
				while(line.contains("  ")) line = line.replace("  ", " ");
        		words = line.split(";");
        		int sinkNode = Integer.parseInt(words[0]);
        		int sourceNode = Integer.parseInt(words[1]);
        		int switchID = Integer.parseInt(words[2]);
        		
        		
                RouteNode parent = this.routeNodes.get(sourceNode);
                RouteNode child = this.routeNodes.get(sinkNode);
        		//Check if node has to be removed
                int index = parent.currentIndex;
                if(child != null && parent != null) {
                    
                    parent.setChild(index, child);
                    RouteSwitch routeSwitch = this.switchTypesList.get(switchID);
                    parent.setSwitchType(index, routeSwitch);
                    
                }

                if(child.replaced()) {
                	if(this.childToParentMap.containsKey(child)) {
                		this.childToParentMap.get(child).add(parent);
                	}else {
                		this.childToParentMap.put(child, new ArrayList<RouteNode>());
                		this.childToParentMap.get(child).add(parent);
                	}
                }
			}
		}
		
        reader.close();
        
        System.out.print("\n RR Edges: " + counter);
        System.out.print("\nRemoved Edges " + counterRemovd);
    }
    
    private Boolean checkForL1Boundary(int ylowP, int yhighC) {
    	boolean valid = false;
    	if(ylowP == this.dieBoundary && yhighC < this.dieBoundary) {
    		valid = true;
    	}else if( ylowP == (this.dieBoundary -1) && yhighC > this.dieBoundary) {
    		valid = true;
    	}
    	return valid;
    }
    
    private void processInterposerEdges() {
    	System.out.print("\nAdding interposer Edges");
    	this.processInterposerParentChildEdges();
    	this.removeEdgesFromDeletedNodes();
    	
    }
    
    private void removeEdgesFromDeletedNodes() {
    	long startTime = System.nanoTime();
   	
    	

    	long endTime   = System.nanoTime();
    	long totalTime = endTime - startTime;
    	System.out.println("\nremove edges from deleted first nodes took " + (totalTime*1e-9) +"s");
    	startTime = System.nanoTime();
    	for(RouteNode deletedNode: this.routeNodesDeleted) {
    		this.removeRouteNode(deletedNode);
    	}
    	endTime   = System.nanoTime();
    	totalTime = endTime - startTime;
  	  System.out.println("\nremove edges from deleted nodes took " + (totalTime*1e-9) +"s");
    }
    
    
    private void writeModifiedEdges() throws IOException {
    	File rrEdgeFile = new File(this.RRGFile.getParentFile(), "rrEdge_info_"+this.sllRows+"L.echo");
    	BufferedWriter writer = new BufferedWriter(new FileWriter(rrEdgeFile));
    	
    	for(RouteNode allNode:this.routeNodes) {
    		RouteNode[] children = allNode.children;
    		for(int i=0; i< children.length;i++) {
    			RouteNode child = children[i];
    			if(child!=null) {
					writer.write(child.index+";"+ allNode.index +";"+ allNode.getSwitchType(i).index +"\n");
    				
    			}
    			
    		}
    	}
    	 writer.close();
    }
    
    private void processInterposerParentChildEdges() {
    	//Since I am restricting the number of children from the SLLs, I need to make sure that only I wire type is added from each side
    	/***********************
    	 * index    			 Y = 		 0     1 		2       X=
    	 * TOP	(CHANY,DEC_DIR)				 1L	   2L		6L		0
    	 * BOTTOM (CHANY,INC_DIR)			 1L	   2L		6L		1
    	 * LEFT (CHANX,DEC_DIR)				 1L	   2L		6L		2
    	 * RIGHT (CHANX,INC_DIR)			 1L	   2L		6L		3
    	 */
    	
    	
    	// ---------------::Source -------------------> Sink ::---------------->
    	//--------------------for INC DIR --------------------------------------->
    	// -------------::Child point ---------------> Parent point ::---------------->
    	// ---------------::(xlow, ylow)  -------------------> (xhigh, yhigh) ::---------------->
    	
    	//--------------------for DEC DIR --------------------------------------->
    	// ---------------::(xlow, ylow)  <------------------- (xhigh, yhigh) ::---------------->
    	// -------------::Parent point <--------------- child point ::---------------->
    	
    	long startTime = System.nanoTime();
    	
    	
    	for(int xCord = 2; xCord < this.width - 2; xCord++) {
        	for(int yCord = (this.dieBoundary - this.sllRows + 1); yCord < this.dieBoundary; yCord++) {
        		int xlow = xCord;
        		int ylow = yCord;
        		int yhigh = yCord + this.sllRows - 1;
        		
        		//Starting with all INC nodes; 
        		//The x coordinate will remain same, but we will identify based on ylow
        		String wireIndex = "INC_" + String.valueOf(xlow) + "_" + String.valueOf(ylow);
        		SLLRouteNode sllWireData = this.interposerWireNodes.get(wireIndex);
        		RouteNode startPoint = sllWireData.getSourceReplaceNode();
        		RouteNode endPoint = sllWireData.getSinkReplaceNode();
        		RouteNode interposerNode = sllWireData.getInterposerNode();
        		
        		this.addInterposerNodeAsChild(interposerNode, startPoint);
        		this.addInterposerNodeAsParent(interposerNode, endPoint);
        		
        		//Now doing the same with DEC nodes, the start of these nodes is at yhigh
        		wireIndex = "DEC_" + String.valueOf(xlow) + "_" + String.valueOf(yhigh);
        		sllWireData = this.interposerWireNodes.get(wireIndex);
        		startPoint = sllWireData.getSourceReplaceNode();
        		endPoint = sllWireData.getSinkReplaceNode();
        		interposerNode = sllWireData.getInterposerNode();

        		this.addInterposerNodeAsChild(interposerNode, startPoint);
        		this.addInterposerNodeAsParent(interposerNode, endPoint);
        	}	
        }
    	

    	  long endTime   = System.nanoTime();
    	  long totalTime = endTime - startTime;

  	  System.out.println("\nrouteNodeInterposerDEC took " + (totalTime*1e-9) +"s");
    	
    }
    
    
    private void addInterposerNodeAsParent(RouteNode interposerNode, RouteNode EndNode) {
    	interposerNode.setNumChildren(1);
    	
    	interposerNode.setChild(0, EndNode);
    	
    	String wireLeng = this.baseCostToLength.get(EndNode.base_cost);
		String segType = "seg" + (wireLeng) +"_driverSN";
		RouteSwitch routeSwitch = this.switchTypesList.get(this.lengthToSwitchMap.get(segType)); // this has to be the switch of the child
		interposerNode.setSwitchType(0, routeSwitch);

    	
    }
    
    private void addInterposerNodeAsChild(RouteNode interposerNode, RouteNode startNode) {
    	List<RouteNode> allParents = this.childToParentMap.get(startNode);
    	int numChildren = startNode.numChildren + 2;
    	startNode.setNumChildren(numChildren);
    	
    	int index = 0;
//
    	for(int i = 0; i < startNode.children.length; i ++) {
    		if(startNode.children[i] == null) {
    			index = i;
    		}
    	}
    	if(index == numChildren) {
    		System.out.print("\nError!!");
    	}
    	startNode.setChild(index, interposerNode);
    	
		RouteSwitch routeSwitch = this.switchTypesList.get(5);
		startNode.setSwitchType(index, routeSwitch); 
		
    	
		
    	
    }
    private void processEdgesToInterposer(RouteNode interposerNode, RouteNode startNode) {
    	//Here the interposer will be the child. We need to get all the parent edges to the source node and replace the source node with interposer node for these edges.
    	List<RouteNode> allParents = this.childToParentMap.get(startNode);
    	for(RouteNode parentNode: allParents) {
    		if(parentNode.isWire) {
    			RouteNode[] childofParent = parentNode.children;
    			for(int i = 0; i < childofParent.length; i++) {
    				RouteNode origChild = childofParent[i];
    				if(origChild != null) {
    					if(origChild == startNode) {
    						parentNode.removeChild(i);
    						parentNode.setNewChild(i, interposerNode);
    						RouteSwitch routeSwitch = this.switchTypesList.get(5);
    						parentNode.setSwitchType(i, routeSwitch); 
    					}
    				}
    			}
    		}else {
    			RouteNode[] childofParent = parentNode.children;
    			for(int i = 0; i < childofParent.length; i++) {
    				RouteNode origChild = childofParent[i];
    				if(origChild != null) {
    					parentNode.removeChild(i);
						parentNode.setNewChild(i, interposerNode);
						RouteSwitch routeSwitch = this.switchTypesList.get(5);
						parentNode.setSwitchType(i, routeSwitch); 
    				}
    			}
    		}
    	}
		//Need to remove the children of this node.
		
		for(int i = 0; i < startNode.numChildren; i++) {
			if(startNode.children[i] != null) {
				startNode.removeChild(i);
			}
			
		}
		
		this.routeNodesDeleted.add(startNode);

    	
    }
    
    private void processEdgesFromInterposer(RouteNode interposerNode, RouteNode endNode) {
    	//Here the interposer behaves as the parent, we need to get the children of sink node and replace them as the children of the interposer.
    	interposerNode.setNumChildren(endNode.numChildren);
		
		RouteNode [] children = endNode.getChildren();
		for(int i = 0; i < endNode.numChildren; i++) {
				int index = interposerNode.currentIndex;
				RouteNode childNode = children[i];
    			if(childNode != null && childNode.isWire && !childNode.isSLLWire) {
    				interposerNode.setChild(index, childNode);}
        					String wireLeng = this.baseCostToLength.get(childNode.base_cost);
        					String segType = "seg" + (wireLeng) +"_driverSN";
        					RouteSwitch routeSwitch = this.switchTypesList.get(this.lengthToSwitchMap.get(segType)); // this has to be the switch of the child
        					interposerNode.setSwitchType(index, routeSwitch);
		}

		this.routeNodesDeleted.add(endNode);
    }
 					

    private void processIndexList() throws IOException {
    	BufferedReader reader = null;
    	this.indexedDataList = new HashMap<String, IndexedData>();
		String rrgIndexFileName = this.RRGFile.getAbsolutePath();
		System.out.print("RR file path is " + rrgIndexFileName);
        int lastSlashIndex = rrgIndexFileName.lastIndexOf("/");
        String newFileName = null;
        if (lastSlashIndex >= 0) {
            String directoryPath = rrgIndexFileName.substring(0, lastSlashIndex + 1); // Include the last slash
            newFileName = directoryPath + "rr_indexed_data.echo";

        } 
		reader = new BufferedReader(new FileReader(newFileName));
		StringBuilder contentBuilder = new StringBuilder();
		System.out.println("\n   Read " + newFileName);
        String line;
        Float baseCostDefault = (float) 0.0;
        int index = 0;
        line = reader.readLine();
        String sllInfo ="";
        int orthoCostIndex = -1, segIndex = 0;
        if(line.contains("Delay normalization factor:")) {

    		String[] header = line.split(":");
    		baseCostDefault = Float.parseFloat(header[1]);

    	}
        this.baseCostToLength = new HashMap<Float, String>();
        // Process each line of the file
        while ((line = reader.readLine()) != null) {
        	contentBuilder.append(line).append(System.lineSeparator());
        	while(line.contains("  ")) line = line.replace("  ", " ");
            String[] tokens = line.split("\\s");
            float baseCost, invLength, tLinear,tQuadratic, cLoad = 0;
            String segLength;
            if(!tokens[0].equals("Cost")) {
                if (tokens.length >= 8) {
                    index = Integer.parseInt(tokens[0]);
                    String type = tokens[1];
                    if(type.contains("CHAN")) {
                    	type = tokens[1] +"_"+ tokens[2];
                    	segLength = tokens[2].replaceAll("\\D+", "");
                    	System.out.print("\nThe length is " + segLength);
                    	baseCost = Float.valueOf(tokens[3]);
                    	this.baseCostToLength.put(baseCost, segLength);
                        orthoCostIndex = Integer.parseInt(tokens[4]);
                        segIndex = Integer.parseInt(tokens[5]);
                        invLength = 0;
                          if(tokens[6].equals("nan")) {
                        	invLength = 0;
                        }else {
                        	invLength = Float.valueOf(tokens[6]);
                        }
                        
                        tLinear = Float.valueOf(tokens[7]);
                        tQuadratic = Float.valueOf(tokens[8]);
                        cLoad = Float.valueOf(tokens[9]);
                    }else {
                        baseCost = Float.valueOf(tokens[2]);
                        orthoCostIndex = Integer.parseInt(tokens[3]);
                        segIndex = Integer.parseInt(tokens[4]);
                        invLength = 0;
                        if(tokens[5].equals("nan")) {
                        	invLength = 0;
                        }else {
                        	invLength = Float.valueOf(tokens[5]);
                        }
                        
                        tLinear = Float.valueOf(tokens[6]);
                        tQuadratic = Float.valueOf(tokens[7]);
                        cLoad = Float.valueOf(tokens[8]);
                    }
                    
                    this.indexedDataList.put(type ,new IndexedData(index, baseCost, orthoCostIndex, invLength, tLinear, tQuadratic, cLoad));
                    this.baseCostToLength.put(baseCost, "36");
                    
                } else {
                    System.err.println("Invalid line: " + line);
                }
            }

        }
        String sllName = "CHANY_L" + this.sllRows + "SN"; //The assumption is that the SLL will only go in the vertical direction
        baseCostDefault = (float) ((1.708e-11 * this.sllRows) + 1000e-12);
        float sllInvLength = 1.0f/(float)this.sllRows;

        float tLinear = calculateTlinear();
        float tQuadratic = calculateTquadratic();
        float CLoad = getCload();
        this.indexedDataList.put(sllName ,new IndexedData(index+1, (baseCostDefault), orthoCostIndex + 1, sllInvLength, tLinear, tQuadratic, CLoad));     
        
        

		
        sllInfo = String.format("%-5d", (index+1));
        sllInfo += String.format("%-25s", "CHANY L" + this.sllRows +"SN");
        sllInfo += String.format("%-20.5e", baseCostDefault);
        sllInfo += String.format("%-20d", orthoCostIndex);
        sllInfo += String.format("%-20d", segIndex);
        sllInfo += String.format("%-20.6f", sllInvLength);
        sllInfo += String.format("%-20.5e", tLinear);
        sllInfo += String.format("%-20.6f", tQuadratic);
        sllInfo += String.format("%-20.6f", CLoad);
		
        reader.close();
        for(String dataTypes: this.indexedDataList.keySet()) {
        	System.out.print("\nThe content of index list is " + dataTypes);
        	IndexedData data = this.indexedDataList.get(dataTypes);
        	if (data.orthoCostIndex != -1) {
            	
            	String[] chann = dataTypes.split("_");
            	String orthdata = null;
            	if(chann[0].equals("CHANX")) {
            		orthdata = "CHANY_" +  chann[1];
            		
            	}else {
            		orthdata = "CHANX_" +  chann[1];
            	}

        		data.setOrthoData(this.indexedDataList.get(orthdata));
        	}
        }

        //Create a new file to accomodate the sll information
        File indexFile = new File(this.RRGFile.getParentFile(), "rr_indexed_data_"+this.sllRows+"L.echo");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(indexFile))) {
            writer.write(contentBuilder.toString());

            writer.write(sllInfo);
        }
        
    }

    
    private float calculateTlinear() {
    	float tLinear, rsw, cnode, tsw = 0;
    	tsw = this.architecture.getSLLSegmentInfo().get("tdel");
    	rsw = this.architecture.getSLLSegmentInfo().get("RSwitch");

    	cnode = this.architecture.getSLLSegmentInfo().get("Cmetal") + this.architecture.getSLLSegmentInfo().get("CSwitch");

    	tLinear = tsw + 0.5f * rsw * cnode; 
    	return tLinear;
    }
    
    private float calculateTquadratic() {
    	float tquadratic,rsw, rwire, cnode, tsw = 0;
    	rwire = this.architecture.getSLLSegmentInfo().get("Rmetal");
    	rsw = this.architecture.getSLLSegmentInfo().get("RSwitch");
    	
    	cnode = this.architecture.getSLLSegmentInfo().get("Cmetal") + this.architecture.getSLLSegmentInfo().get("CSwitch");

    	tquadratic = 0.5f * (rsw + rwire) * cnode; 
    	return tquadratic;
    }
    
    private float getCload() {
    	return (this.architecture.getSLLSegmentInfo().get("Cmetal") + this.architecture.getSLLSegmentInfo().get("CSwitch"));
    }
	private void assignNamesToSourceAndSink() {
		for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.SOURCE)){
			Source source = (Source) routeNode;
			source.setName();
		}
		
		for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.IPIN)){
			Ipin ipin = (Ipin) routeNode;
			ipin.setSinkName();
		}
	}
    private void connectSourceAndSinkToSite() {
    	for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.SOURCE)){
			Source source = (Source) routeNode;
			Site site = this.getSite(source.xlow, source.ylow);
			

			if(site.addSource((Source)routeNode) == false) {
				System.out.print("\nThe site is " + site.getblockType());
				System.err.println("\nUnable to add " + routeNode + " as source to " + site);
			}
		}
    	for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.SINK)){
			Sink sink = (Sink) routeNode;
			Site site = this.getSite(sink.xlow, sink.ylow);

			if(site.addSink((Sink)routeNode) == false) {
				System.err.println("\nUnable to add " + routeNode + " as sink to " + site);
			}
		}
    }
	
    private boolean isInInterposerRegion(int xCord, int yCordLow, int yCordHigh) {
    	boolean isValid = false;
    	if(xCord >=2 && xCord < this.width -2) {
    		if((yCordLow >= (this.dieBoundary - this.sllRows + 1)) && (yCordHigh < this.dieBoundary + this.sllRows)) {
    			isValid = true;
    		}
    	}
		return isValid;
    }
    private void addInterposerNodes() {
    	//Create nodes for the SLL interposer
    	/*******************VPR delay calculation *****************************
    	 * Tdel = (Length X res X Length X Cap)/2
    	 * res = Length 1 x total length
    	 * cap = length 1 x total length
    	 */
    	
    	RouteNode routeNodeInc, routeNodeDec = null;
    	String sllName = "CHANY_L" + this.sllRows + "SN";
    	IndexedData data = this.indexedDataList.get(sllName);
    	System.out.print("\nThe indexed data is " + data.getBaseCost());
    	float Reg = this.architecture.getSLLSegmentInfo().get("Rmetal") * this.sllRows;
    	float Cap  = this.architecture.getSLLSegmentInfo().get("Cmetal") * this.sllRows;
    	
    	int sllIndex = this.indexStart + 1;
    	
    	for(int xCord = 2; xCord < this.width - 2; xCord++) {
        	for(int yCord = (this.dieBoundary - this.sllRows + 1); yCord < this.dieBoundary; yCord++) {
        		
        		int xlow = xCord, xhigh = xCord;
        		int ylow = yCord;
        		int yhigh = yCord + this.sllRows - 1;
        		
        		String wireIndex = "INC_" + String.valueOf(xlow) + "_" + String.valueOf(ylow);
				SLLRouteNode sllWire = this.interposerWireNodes.get(wireIndex);
        		
        		routeNodeInc = new Chany(sllIndex, xlow, xhigh, ylow, yhigh, 0, Reg, Cap, data, "INC_DIR");
        		sllWire.setInterposerNode(routeNodeInc);

        		
        		sllIndex++;
        		routeNodeInc.setSLLWireNode();
        		this.addRouteNode(routeNodeInc);
        		
        		
        		wireIndex = "DEC_" + String.valueOf(xlow) + "_" + String.valueOf(yhigh);
        		sllWire = this.interposerWireNodes.get(wireIndex);
        		
        		routeNodeDec = new Chany(sllIndex, xlow, xhigh, ylow, yhigh, 0, Reg, Cap, data, "DEC_DIR");
        		sllWire.setInterposerNode(routeNodeDec);
        		routeNodeDec.setSLLWireNode();
        		sllIndex++;
        		this.addRouteNode(routeNodeDec);
        		
        	}
    	}
    	
    	
    }
    
    
    private void writeRRNodeToFile() throws IOException {
    	File rrNodeFile = new File(this.RRGFile.getParentFile(), "rrNode_info_"+this.sllRows+"L.echo");
    	BufferedWriter writer = new BufferedWriter(new FileWriter(rrNodeFile));
    	
    	for(RouteNode allNode:this.routeNodes) {
    		writer.write(allNode.getDetails() +"\n");
    	}
    	 writer.close();
    }

    private void removeRouteNode(RouteNode routeNode) {

    	this.routeNodes.remove(routeNode);

    }
    
    
	private void addRouteNode(RouteNode routeNode) {
		assert routeNode.index == this.routeNodes.size();
		
		this.routeNodes.add(routeNode);
		if(!routeNode.isDeleted) {
			this.routeNodeMap.get(routeNode.type).add(routeNode);
		}
		
	}
	public List<RouteNode> getRouteNodes() {
		return this.routeNodes;
	}
	public int numRouteNodes() {
		return this.routeNodes.size();
	}
	public int numRouteNodes(RouteNodeType type) {
		if(this.routeNodeMap.containsKey(type)) {
			return this.routeNodeMap.get(type).size();
		} else {
			return 0;
		}
	}
	
	@Override
	public String toString() {
		String s = new String();
		
		s+= "The system has " + this.numRouteNodes() + " rr nodes:\n";
		
		for(RouteNodeType type : RouteNodeType.values()) {
			s += "\t" + type + "\t" + this.numRouteNodes(type) + "\n";
		}
		return s;
	}
	
	/********************
	 * Routing statistics
	 ********************/
	public int totalWireLength() {
		int totalWireLength = 0;
		int counter = 0;
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					totalWireLength += routeNode.wireLength();
					counter++;
				}
			}
		}
		System.out.print("\nThe overall counter is " + counter);
		return totalWireLength;
	}
	public int congestedTotalWireLengt() {
		int totalWireLength = 0;
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					totalWireLength += routeNode.wireLength();
				}
			}
		}
		return totalWireLength;
	}
	public int wireSegmentsUsed() {
		int wireSegmentsUsed = 0;
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					wireSegmentsUsed++;
				}
			}
		}
		return wireSegmentsUsed;
	}
	public void sanityCheck() {
		for(Site site:this.getSites()) {
			site.sanityCheck();
		}
	}
	public void printRoutingGraph() {
	
		for(RouteNode node : this.getRouteNodes()) {
			if(node.used()) {
				for (RouteNode child : node.children) {
					System.out.println("\t" + child);
				}
				System.out.println();
			}
			

		}
	}

	public void printWireUsage() {
		System.out.println("-------------------------------------------------------------------------------");
		System.out.println("|                              WIRELENGTH STATS                               |");
		System.out.println("-------------------------------------------------------------------------------");
		System.out.println("Total wirelength: " + this.totalWireLength());
		System.out.println("Total congested wirelength: " + this.congestedTotalWireLengt());
		System.out.println("Wire segments: " + this.wireSegmentsUsed());
		for(int i = 0; i < this.totDie; i++) {
			System.out.println("Maximum net length: " + this.circuit[i].maximumNetLength() + " for die " + i);
		}
		
		System.out.println();
		//Length of wire, Count of available wire
		Map<Integer, Integer> numWiresMap = new HashMap<Integer, Integer>();
		//Length of wire, Count of used wires.
		Map<Integer, Integer> numUsedWiresMap = new HashMap<Integer, Integer>();
		//Length of wire, Count of available wirelength
		Map<Integer, Integer> wirelengthMap = new HashMap<Integer, Integer>();
		Map<Integer, Integer> UsedWirelengthMap = new HashMap<Integer, Integer>();
		int wireType = 0;
		int wireLength = 0;
		for(RouteNode node : this.routeNodes) {
			if((node.type == RouteNodeType.CHANX) || (node.type == RouteNodeType.CHANY)) {
				wireType = node.indexedData.length;
				numWiresMap.put(wireType, numWiresMap.getOrDefault(wireType, 0) + 1);
				wireLength = node.wireLength();
				wirelengthMap.put(wireType, wirelengthMap.getOrDefault(wireType, 0) + wireLength);
				if(node.used()) {
					numUsedWiresMap.put(wireType, numUsedWiresMap.getOrDefault(wireType, 0) + 1);
					UsedWirelengthMap.put(wireType, UsedWirelengthMap.getOrDefault(wireType, 0) + wireLength);
				}
			}
			
		}

		for(Integer wireTypes : numWiresMap.keySet()) {
			if(numUsedWiresMap.get(wireTypes) != null) {
				double averageLength = (double) wirelengthMap.get(wireTypes) / numWiresMap.get(wireTypes) ;
				System.out.printf("\nLength %8d (%5.2f) wires: %8d of %8d | %5.2f%% => Wire-length: %8d\n",
						wireTypes,
						averageLength, 
						numUsedWiresMap.get(wireTypes), 
						numWiresMap.get(wireTypes), 
						100.0 * numUsedWiresMap.get(wireTypes)/numWiresMap.get(wireTypes), 
						UsedWirelengthMap.get(wireTypes));
				System.out.printf("L%8d Wirelength: %8d\n",wireTypes, UsedWirelengthMap.get(wireTypes));
				System.out.printf("L%8d Usage: %5.2f\n",wireTypes, 100.0 * numUsedWiresMap.get(wireTypes)/numWiresMap.get(wireTypes));
			}

			
		}
	}
}
