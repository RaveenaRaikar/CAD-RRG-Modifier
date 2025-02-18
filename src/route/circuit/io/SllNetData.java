package route.circuit.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import route.circuit.block.AbstractBlock;
import route.circuit.pin.AbstractPin;
import route.circuit.pin.GlobalPin;

public class SllNetData {
	String netname;
	AbstractBlock sourceBlock;
	AbstractBlock sinkBlock;
	AbstractPin sourceLeafSLL;
	GlobalPin sourceGlobalSLL;
	List<AbstractPin> sinkSLL;
	private List<Map<AbstractBlock, AbstractPin>> sinkBlocks;
	SllNetData(String netname){
		this.netname = netname;
		this.sinkBlocks = new ArrayList<>();
	
	}
	public AbstractPin getSLLsourceLeafPin() {
		return this.sourceLeafSLL;
	}
	public AbstractBlock getSLLsourceBlock() {
		return this.sourceBlock;
	}
	public GlobalPin getSLLsourceGlobalPin() {
		return this.sourceGlobalSLL;
	}
	
    public void addSinkBlock(AbstractBlock sinkBlock, AbstractPin inputPin) {
        Map<AbstractBlock, AbstractPin> sinkInfo = new HashMap<>();
        sinkInfo.put(sinkBlock, inputPin);

        this.sinkBlocks.add(sinkInfo);
    }

    
    public List<Map<AbstractBlock, AbstractPin>> getSinkBlocks() {
        return this.sinkBlocks;
    }
    
    void setSLLsourceBlock(AbstractBlock sourceBlock) {
    	this.sourceBlock = sourceBlock;
    }
    
    void setSLLsourceLeafPin(AbstractPin sourcePin) {
    	this.sourceLeafSLL = sourcePin;
    }
    
    void setSLLsourceGlobalPin(GlobalPin sourcePin) {
    	this.sourceGlobalSLL = sourcePin;
    }

	void addSLLsink(AbstractPin sourceSLL) {
		this.sinkSLL.add(sourceSLL);
	}
}
