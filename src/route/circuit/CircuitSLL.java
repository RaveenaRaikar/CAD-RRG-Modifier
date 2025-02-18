package route.circuit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import route.circuit.block.AbstractBlock;
import route.circuit.io.SllNetData;
import route.circuit.pin.AbstractPin;
import route.circuit.pin.GlobalPin;
import route.route.Connection;
import route.route.Net;

public class CircuitSLL {
	private HashMap<String, SllNetData> sllNetInfo;
	private List<Connection> sllConnections;
	private List<Net> sllNets;
	private List<String> globalNets;
	
	public CircuitSLL(HashMap<String, SllNetData> sllInfo, List<String> globalNetList) {
		this.sllNetInfo = sllInfo;
		this.globalNets = globalNetList;
	}
	
	
    public void loadSllNetsAndConnections() {
    	short boundingBoxRange = 3; 
    	
    	this.sllConnections = new ArrayList<>();
    	this.sllNets = new ArrayList<>();
        
        int id = 0;
        
        for(String sllNet: this.sllNetInfo.keySet()) {

        	if(!this.globalNets.contains(sllNet)) {
            	SllNetData netInfo = this.sllNetInfo.get(sllNet);
            	GlobalPin sourcePin = netInfo.getSLLsourceGlobalPin();
                     	            	
            	List<Map<AbstractBlock, AbstractPin>> sinkInfo = netInfo.getSinkBlocks();
            	List<Connection> net = new ArrayList<>();
            	for(int i = 0; i< sinkInfo.size(); i++) {

    				for (Map.Entry<AbstractBlock, AbstractPin> sinkMap : sinkInfo.get(i).entrySet()) {
    					GlobalPin sinkPin = (GlobalPin) sinkMap.getValue();
    					Connection c = new Connection(id, sourcePin, sinkPin);
    					this.sllConnections.add(c);
    					net.add(c);
    					id++;
    				}
            	}

            	this.sllNets.add(new Net(net, boundingBoxRange));
        	}
        }
        
    }
    
    public List<Connection> getSLLConnections(){
    	return this.sllConnections;
    }
    public List<Net> getSLLNets(){
    	return this.sllNets;
    }
}
