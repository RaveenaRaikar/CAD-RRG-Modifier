package pack.netlist;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//import pack.architecture.Architecture;
import pack.cluster.LogicBlock;
import pack.cluster.VPRThread;
import pack.main.Simulation;
import pack.partition.Partition;
import pack.util.ErrorLog;
import pack.util.Output;
import pack.util.ThreadPool;
//import pack.util.Util;

public class DieLevelNetlist {
		private Netlist root;
		private Partition partition;
		//private Architecture architecture;
		private Simulation simulation;

		private String vpr_folder;

		private Set<String> netlistInputs;
		private Set<String> netlistOutputs;

		private List<Netlist> subcircuits;
		private List<LogicBlock> logicBlocks;
		private List<Netlist> leafNodes;
		private ThreadPool threadPool;
		private List<VPRThread> packPool;


		public DieLevelNetlist(Netlist root, Partition partition, Simulation simulation){
			this.root = root;
			this.partition = partition;
			//this.architecture = architecture;
			this.simulation = simulation;

			this.vpr_folder = simulation.getStringValue("vpr_folder");

			this.logicBlocks = new ArrayList<>();

			this.findNetlistInputsAndOutputTerminalNames();
			//this.deleteExistingFiles();


		}
		
		private void findNetlistInputsAndOutputTerminalNames(){
			this.netlistInputs = new HashSet<>();
	 		for(N inputNet:this.root.get_input_nets()){
	 			boolean inputTerminalFound = false;
				for(P terminalPin:inputNet.get_terminal_pins()){
					T t = terminalPin.get_terminal();
					if(t.is_input_type()){
						this.netlistInputs.add(t.get_name());
						inputTerminalFound = true;
					}
				}
				if(!inputTerminalFound){
					ErrorLog.print("Input net " + inputNet.toString() + " has no input terminal");
				}
	 		}
	 		this.netlistOutputs = new HashSet<>();
			ArrayList<N> outputNets = new ArrayList<>();
	 		outputNets.addAll(this.root.get_output_nets());
			if(this.root.has_floating_blocks()){
				for(B floatingBlock:this.root.get_floating_blocks()){
					for(N n:floatingBlock.get_output_nets()){
						if(n.has_terminals()){
							if(!outputNets.contains(n)){
								outputNets.add(n);
							}
						}
					}
				}
			}
	 		for(N outputNet:outputNets){
	 			boolean outputTerminalFound = false;
				for(P terminalPin:outputNet.get_terminal_pins()){
					T t = terminalPin.get_terminal();
					if(t.is_output_type()){
						this.netlistOutputs.add("out:" + t.get_name());
						outputTerminalFound = true;
					}
				}
				if(!outputTerminalFound){
					ErrorLog.print("Output net " + outputNet.toString() + " has no output terminal");
				}
			}
		}
		
		public void GenerateDieNetlist(){
			//Output.println("PHASE 2: SEED BASED PACKING");
			//Output.newLine();
			boolean dielevel = true;
			this.logicBlocks = new ArrayList<>();
			this.leafNodes = new ArrayList<>();
			this.subcircuits = this.root.get_leaf_nodes();
			
			//Analyze the leaf nodes
			for(Netlist leafNode:this.subcircuits){
				if(leafNode.has_children()){
					ErrorLog.print("A leaf node should not have children!");
				}
			}

			//Analyze the hierarchy recursively and give each netlist a hierarchy identifier
			this.root.setDieRecursiveHierarchyIdentifier("");
			//for(Netlist subcircuit:this.subcircuits){
			//      System.out.println(subcircuit.getHierarchyIdentifier());
			//}
			
			Output.println("\tLeaf nodes: " + this.subcircuits.size());
			Output.print("\t\t");
			int no = 0;
			for(Netlist nl:this.subcircuits){
				if(no == 10){
					Output.newLine();
					Output.print("\t\t");
					no = 0;
				}
				System.out.print(nl.atom_count() + " ");
				no += 1;
			}
			Output.newLine();
			Output.newLine();
			int poolSize = this.simulation.getIntValue("num_threads");
			this.threadPool = new ThreadPool(poolSize);
			this.packPool = new ArrayList<>();
			while(!this.subcircuits.isEmpty() && !this.threadPool.isEmpty()){
				Netlist leafNode = this.subcircuits.remove(0);
				int thread = this.threadPool.getThread();
				//Output.println("The threads are " + thread );
				leafNode.writeSDC(this.vpr_folder + "vpr/files/", thread, this.partition, this.simulation.getSimulationID());
				leafNode.writeBlif(this.vpr_folder + "vpr/files/", thread, this.partition, this.simulation.getSimulationID(),dielevel);
				
			}
		}
		
}
