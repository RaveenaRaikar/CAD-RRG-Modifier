package pack.partition;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import pack.architecture.Architecture;
import pack.main.Simulation;
import pack.netlist.B;
import pack.netlist.Netlist;
//import pack.netlist.PartitionNetlist;
import pack.util.ErrorLog;
import pack.util.Info;
import pack.util.Output;
import pack.util.ThreadPool;
import pack.util.Timing;
import pack.util.Util;

public class Partition{
	private Netlist root;
//	private PartitionNetlist rootpart;
	private Architecture architecture;
	private Simulation simulation;
	
	private int maxNetlistSize;
	private Param param;
	private ThreadPool threadPool;
	
	private long startTime;
	private ArrayList<String> timeSteps;
	
	private Stack stack;
	//private Stack stack;
	private ArrayList<HMetis> hMetisPool;
	private HashMap<Thread, NetGen> netGenThreadPool;
	
	private int metisIt;
	private CutEdges cutEdges;
	
	private String vpr_folder;
	
	private List<Netlist> subcircuits;
	
	private static final boolean debug = false;
	public static boolean diePart = true;
	private int NoOfdie;
	//public static boolean diepartition = false;
	private int numberOfCutEdges = 0;
/*	
	public Partition(Netlist netlist, Architecture architecture, Simulation simulation, int maxDelay){
		this.root = netlist;
		this.architecture = architecture;
		this.simulation = simulation;
		//Output.println("The netlist is " + this.root);
		this.vpr_folder = simulation.getStringValue("vpr_folder");

		Output.println("PHASE 1: PARTITIONING");
		Output.newLine();
		Output.println("\tSettings: ");

		//Stop criterium
		this.maxNetlistSize = this.simulation.getIntValue("max_pack_size");
		Output.println("\t\tMaximum netlist size: " + Util.parseDigit(this.maxNetlistSize));
		Output.newLine();
		
		Output.println("\t\tTiming edge weight update: " + this.simulation.getBooleanValue("timing_edge_weight_update"));
		Output.newLine();
		
		this.param = new Param(this.simulation, Partition.diePart);
		Output.print(this.param.getHMetisParameters("\t\t"));
		Output.newLine();
		
		this.metisIt = 0;
		this.cutEdges = new CutEdges(maxDelay);
		
		//Thread pool
		int poolSize = this.simulation.getIntValue("num_threads");
		Output.println("\t\tPartition pool size: " + poolSize);
		this.threadPool = new ThreadPool(poolSize);
				
		this.hMetisPool = new ArrayList<HMetis>();
	//	Output.println("\tthis.hMetisPool" + this.hMetisPool);
		this.netGenThreadPool = new HashMap<Thread, NetGen>();
	//	Output.println("\tthis.netGenThreadPool" + this.netGenThreadPool);
		Output.newLine();
		
		this.numberOfCutEdges = 0;
		
		//this.deleteExistingFiles();
	}
*/
	//For die level partitioning only
	
	public Partition(Netlist netlist, Architecture architecture, Simulation simulation, int maxDelay, boolean diepartition){
		this.root = netlist;
		this.architecture = architecture;
		this.simulation = simulation;
		this.NoOfdie = this.simulation.getIntValue("Number_of_die");
		
		Output.println("The number of dies  is " + this.root);
		
		this.vpr_folder = simulation.getStringValue("vpr_folder");
		
		if(diepartition)
		{
			Output.println("PHASE 1: DIE PARTITIONING");
			Output.newLine();
			Output.println("\tSettings: ");
		}
		else {
			Output.println("PHASE 2: MULTIPART PARTITIONING");
			Output.newLine();
			Output.println("\tSettings: ");
			//Stop criterium
			this.maxNetlistSize = this.simulation.getIntValue("max_pack_size");
			Output.println("\t\tMaximum netlist size: " + Util.parseDigit(this.maxNetlistSize));
			Output.newLine();
		}
		//Stop criterium
		//this.maxNetlistSize = this.simulation.getIntValue("max_pack_size");
		//Output.println("\t\tMaximum netlist size: " + Util.parseDigit(this.maxNetlistSize));
		//Output.newLine();
		
		Output.println("\t\tTiming edge weight update: " + this.simulation.getBooleanValue("timing_edge_weight_update"));
		Output.newLine();
		
		
		this.param = new Param(this.simulation, Partition.diePart);
		//Output.println("The param value is " + this.param);
		Output.print(this.param.getHMetisParameters("\t\t"));
		Output.newLine();
		
		this.metisIt = 0;
		this.cutEdges = new CutEdges(maxDelay);
		
		//Thread pool
		int poolSize = this.simulation.getIntValue("num_threads");
		Output.println("\t\tPartition pool size: " + poolSize);
		this.threadPool = new ThreadPool(poolSize);
				
		this.hMetisPool = new ArrayList<HMetis>();
		//Output.println("\tthis.hMetisPool" + this.hMetisPool.size());
		this.netGenThreadPool = new HashMap<Thread, NetGen>();
		//Output.println("\tthis.netGenThreadPool" + this.netGenThreadPool.size());
		Output.newLine();
		
		this.numberOfCutEdges = 0;
		
		//this.deleteExistingFiles();
	}
	
	public void diepartition() {
		Partition.diePart = true;
		this.startTime = System.nanoTime();
		this.timeSteps = new ArrayList<>();
		
		//Stack
		this.stack = new Stack();
		
		//Partition
		//Output.println("\tDie level partitioning :");
		//this.processChildNetlist(this.root);
		//Not required to check any condition as it is the first level
		//Partition
		this.processChildNetlist(this.root);
		
		//while(!this.stack.isEmpty() || !this.hMetisPool.isEmpty() || !this.netGenThreadPool.isEmpty()){
			///Output.println("this.stack.isEmpty() is " + this.stack.isEmpty());
			//Output.println("this.hMetisPool.isEmpty() is " + this.hMetisPool.isEmpty());
			//Output.println("this.netGenThreadPool.isEmpty() is " + this.netGenThreadPool.isEmpty());
			//Output.println("hMetis is working?");
		//while(!this.stack.isEmpty() || !this.netGenThreadPool.isEmpty()){
			//Output.println("hMetis is working?");
			this.startdieHMetis();
			//finish hmetis
			this.finishHMetis();
			this.finishNetGen();
			//generate the blif files for further partitioning
		//	Netlist leafNode = this.subcircuits.remove(0);
		//	leafNode.writeBlif(this.vpr_folder + "vpr/files/", thread, this.partition, this.simulation.getSimulationID());
		//	if(this.metisIt ==1)
		//	break;
			
			
		//}
		Output.newLine();
		
		//Testers
		this.eachParentHasTwoChildren();
		
		for(int i=1; i<this.timeSteps.size();i++){
			Info.add("partitioning", "subcircuit" + "\t" + i + "\t=>\t" + this.timeSteps.get(i).replace(".", ","));
		}
		
		int i = 1;
		int j = 1;
		while(i<this.timeSteps.size()){
			Info.add("hierarchylevel", "subcircuit" + "\t" + j + "\t" + this.timeSteps.get(i).replace(".", ","));
			i *= 2;
			j += 1;
		}
		
		Output.println("\tThere are " + this.numberOfCutEdges + " edges cut during partitioning");
		Output.newLine();
		
		Output.println("\t" + "A maximum of " + this.threadPool.maxUsage() + " threads is used during partitioning");
		Output.newLine();
		Partition.diePart = false;
	}
	
	public void partitionate(){
		Partition.diePart = false;
		this.startTime = System.nanoTime();
		this.timeSteps = new ArrayList<>();
		
		//Stack
		this.stack = new Stack();
		//Output.println("\t the netlist is " + this.rootpart);
		//Partition
		Output.println("\tPartitionate netlist:");
		//Provide cluster size smaller than the circuit size
		this.processChildNetlist(this.root);


		while(!this.stack.isEmpty() || !this.hMetisPool.isEmpty() || !this.netGenThreadPool.isEmpty()){
			this.startHMetis();
			this.finishHMetis();
			this.finishNetGen();
			///detect+=1;
		}
		Output.newLine();
		
		//Testers
		this.eachParentHasTwoChildren();
		
		for(int i=1; i<this.timeSteps.size();i++){
			Info.add("partitioning", "subcircuit" + "\t" + i + "\t=>\t" + this.timeSteps.get(i).replace(".", ","));
		}
		
		int i = 1;
		int j = 1;
		while(i<this.timeSteps.size()){
			Info.add("hierarchylevel", "subcircuit" + "\t" + j + "\t" + this.timeSteps.get(i).replace(".", ","));
			i *= 2;
			j += 1;
		}
		
		Output.println("\tThere are " + this.numberOfCutEdges + " edges cut during partitioning");
		Output.newLine();
		
		Output.println("\t" + "A maximum of " + this.threadPool.maxUsage() + " threads is used during partitioning");
		Output.newLine();
		
	}
	
	public void startdieHMetis(){
		Partition.diePart = true;
		//Output.println("Die partitioning is " + Partition.diePart);
		while(!this.threadPool.isEmpty() && !this.stack.isEmpty()){
			//Output.println("this.threadPool.isEmpty() is " + this.threadPool.isEmpty());
		//	Output.println("this.stack.isEmpty() is " + this.stack.isEmpty());
			Netlist parent = this.stack.pullNetlist();
			int thread = this.threadPool.getThread();
			HMetis hMetis = new HMetis(parent, thread, this.metisIt, this.param, Partition.diePart);
			this.metisIt += 1;
			hMetis.startRun();
			this.hMetisPool.add(hMetis);
		}
	}

	
	//PARTITION
	public void startHMetis(){
		//Output.println("I am excuted ");
		Partition.diePart = false;
		while(!this.threadPool.isEmpty() && !this.stack.isEmpty()){
		//	Output.println("this.threadPool.isEmpty() is " + this.threadPool.isEmpty());
		//	Output.println("this.stack.isEmpty() is " + this.stack.isEmpty());
		//	Output.println("I am excuted ");
			Netlist parent = this.stack.pullNetlist();
			int thread = this.threadPool.getThread();
			//Output.println("The value of die part is " +  this.diePart);
			HMetis hMetis = new HMetis(parent, thread, this.metisIt, this.param, Partition.diePart);
			this.metisIt += 1;
			hMetis.startRun();
			this.hMetisPool.add(hMetis);
		}
	}
	
	public void finishdiehMetis() {
		//t
	}
	public void finishHMetis(){
		HMetis hMetisMax = null;
		for(HMetis hMetis:this.hMetisPool){
			//Output.println("Is hMETIS running?? " + hMetis.isRunning());
			if(!hMetis.isRunning()){
				//Output.println("Am I true");
				if(!hMetis.isFinished()){
					//Output.println("hMetis.is finished is true");
					this.threadPool.addThread(hMetis.getThreadNumber());
					hMetis.finishRun();
				}		
				if(hMetisMax == null){
					hMetisMax = hMetis;
				}else if(hMetis.size() > hMetisMax.size()){
					hMetisMax = hMetis;
				}
				//Output.println("hMetisMax is " + hMetisMax);
			}
			//Output.println("hMETIS is running?? ");
		}
		if(hMetisMax != null){
			//Output.println("hMETISmax is true??");
			this.hMetisPool.remove(hMetisMax);
			
			Netlist parent = hMetisMax.getNetlist();
			Part[] result = hMetisMax.getResult();
			
			this.numberOfCutEdges += hMetisMax.numberOfCutEdges();
		
			if(debug)this.analyzeParts(result);
			this.hardBlockSwap(result);
			
			//FINISH BIPARTITION
			if(this.diePart)
			{
				//Output.println("Die level partitioning is executed" );
				this.finishdiePartition(parent, result, hMetisMax);
			}else this.finishPartition(parent, result, hMetisMax);
			
			if(parent.get_children().size() == 2){
				if(parent.get_level() != 0){
					//Output.println("IS this even true??");
					parent.clean_up();
				}
			}
		}
	}
	public void finishNetGen(){
		//Boolean exit = true;
		for(Thread thread:new HashSet<Thread>(this.netGenThreadPool.keySet())){
		//	Output.println("The thread state is " + thread.getState());
		//	Output.println("The thread is alive?" + thread.isAlive());
			//Need to put in a loop
		/*	while(!thread.isInterrupted())
			{
				Output.println("The thread is interrupted??" + thread.isInterrupted());
				if(!thread.isAlive()){
					
					//	Output.println("The thread is alive in the loop?" + thread.isAlive());
						Output.println("Is this executed in finish net gen??");
						NetGen ng = this.netGenThreadPool.remove(thread);
						
						Netlist result = ng.result();
						Netlist parent = ng.parent();
						result.updateFamily(parent);
						
						this.threadPool.addThread(ng.thread());
						
						this.processChildNetlist(result);
						//Output.println(" The parent is " + parent.get_children());
						
						if(parent.get_children().size() == 2){
							if(parent.get_level() != 0){
						//		Output.println("This is true in Net generation");
								parent.clean_up();
							}
						}
					//	exit = false;
					}
			}
		*/	
		
			
			if(diePart== true)
			{
				//Output.println("I am here");
				while(thread.isAlive()){
					//Output.println("Printing when thread is alive");
				}
			}				
			if(!thread.isAlive()){
			//	Output.println("The thread is alive in the loop?" + thread.isAlive());
				//Output.println("Is this executed in finish net gen??");
				NetGen ng = this.netGenThreadPool.remove(thread);
				
				Netlist result = ng.result();
				Netlist parent = ng.parent();
				result.updateFamily(parent);
				
				this.threadPool.addThread(ng.thread());
				
				this.processChildNetlist(result);
				//Output.println(" The parent is " + parent.get_children());
				
				if(parent.get_children().size() == 2){
					if(parent.get_level() != 0){
						//Output.println("This is true in Net generation");
						parent.clean_up();
					}
				}
			}
		
		}
	}
	//For the die level partitioning
	public void finishdiePartition(Netlist parent, Part[] result, HMetis hMetis){
		this.testBipartition(parent, result);
		
		Part X = null,Y = null,Z = null;
		//Part [this.NoOfdie] X = null;
		
		if(this.NoOfdie == 2)
		{
			if(result[0].size() > result[1].size()){//Process smallest benchmark first
				//Output.println("1st case");
				X = result[1];
				Y = result[0];
			}else{
				//Output.println("2nd case");
				X = result[0];
				Y = result[1];
			}
		}else if(this.NoOfdie == 3)
		{
			//Output.println("The execssive code is implemented");
			if (result[0].size() > result[1].size() && result[0].size() > result[2].size())
			{
				//Output.println("Condition 1 is true " );
				Z = result[0];
				if(result[1].size() > result[2].size()){//Process smallest benchmark first
					//Output.println("1st case");
					X = result[2];
					Y = result[1];
				}else{
					//Output.println("2nd case");
					X = result[1];
					Y = result[2];
				}
			}else if(result[1].size() > result[0].size() && result[1].size() > result[2].size()) {
				//Output.println("Condition 2 is true " );
				Z = result[1];
				if(result[0].size() > result[2].size()){//Process smallest benchmark first
					//Output.println("1st case");
					X = result[2];
					Y = result[0];
				}else{
					//Output.println("2nd case");
					X = result[0];
					Y = result[2];
				}
			}else {
				//Output.println("Condition 3 is true " );
				Z = result[2];
				if(result[0].size() > result[1].size()){//Process smallest benchmark first
					//Output.println("1st case");
					X = result[1];
					Y = result[0];
				}else{
					//Output.println("2nd case");
					X = result[0];
					Y = result[1];
				}
			}
		}

		//CUT CRITICAL EDGES
		for(Edge critEdge:hMetis.cutCriticalEdges(this.architecture)){
			this.cutEdges.addCriticalEdge(critEdge);//THESE CRITICAL EDGES ARE ADDED TO SDC FILE
		}
		
		if(this.simulation.getBooleanValue("timing_edge_weight_update")){
			//Output.println("Does this work?");
			hMetis.increasePinWeightOnPadWithCutEdge(this.architecture);
		}
		

			if(!this.threadPool.isEmpty() && this.threadPool.size() > 1){
				//Output.println("Is this true??");
				NetGen ngx = new NetGen(X, parent, this.threadPool.getThread());
				Thread tx = new Thread(ngx);
				tx.start();
				if(this.netGenThreadPool.containsKey(tx)){
					ErrorLog.print("Duplicate thread!");
				}
				this.netGenThreadPool.put(tx, ngx);
			}else{
				Netlist childX = new Netlist(X, parent);
				childX.updateFamily(parent);
				this.processChildNetlist(childX);
			}
			if(!this.threadPool.isEmpty() && this.threadPool.size() > 1){
				//Output.println("Is this true for y??");
				NetGen ngy = new NetGen(Y, parent, this.threadPool.getThread());
				Thread ty = new Thread(ngy);
				ty.start();
				if(this.netGenThreadPool.containsKey(ty)){
					ErrorLog.print("Duplicate thread!");
				}
				this.netGenThreadPool.put(ty, ngy);
			}else{
				Netlist childY = new Netlist(Y, parent);
				childY.updateFamily(parent);
				this.processChildNetlist(childY);
			}
			if(this.NoOfdie == 3)
			{
				if(!this.threadPool.isEmpty() && this.threadPool.size() > 1){
					//Output.println("Is this true for y??");
					NetGen ngz = new NetGen(Z, parent, this.threadPool.getThread());
					Thread tz = new Thread(ngz);
					tz.start();
					if(this.netGenThreadPool.containsKey(tz)){
						ErrorLog.print("Duplicate thread!");
					}
					this.netGenThreadPool.put(tz, ngz);
				}else{
					Netlist childZ = new Netlist(Z, parent);
					childZ.updateFamily(parent);
					this.processChildNetlist(childZ);
				}
		}
	}
	public void finishPartition(Netlist parent, Part[] result, HMetis hMetis){
		this.testBipartition(parent, result);
		
		Part X,Y = null;
		
		if(result[0].size() > result[1].size()){//Process smallest benchmark first
			//Output.println("1st case");
			X = result[1];
			Y = result[0];
		}else{
			//Output.println("2nd case");
			X = result[0];
			Y = result[1];
		}

		//CUT CRITICAL EDGES
		for(Edge critEdge:hMetis.cutCriticalEdges(this.architecture)){
			this.cutEdges.addCriticalEdge(critEdge);//THESE CRITICAL EDGES ARE ADDED TO SDC FILE
		}
		
		if(this.simulation.getBooleanValue("timing_edge_weight_update")){
			//Output.println("Does this work?");
			hMetis.increasePinWeightOnPadWithCutEdge(this.architecture);
		}
		
		if(!this.threadPool.isEmpty() && this.threadPool.size() > 1){
			//Output.println("Is this true??");
			NetGen ngx = new NetGen(X, parent, this.threadPool.getThread());
			Thread tx = new Thread(ngx);
			tx.start();
			if(this.netGenThreadPool.containsKey(tx)){
				ErrorLog.print("Duplicate thread!");
			}
			this.netGenThreadPool.put(tx, ngx);
		}else{
			Netlist childX = new Netlist(X, parent);
			childX.updateFamily(parent);
			this.processChildNetlist(childX);
		}
		if(!this.threadPool.isEmpty() && this.threadPool.size() > 1){
			//Output.println("Is this true for y??");
			NetGen ngy = new NetGen(Y, parent, this.threadPool.getThread());
			Thread ty = new Thread(ngy);
			ty.start();
			if(this.netGenThreadPool.containsKey(ty)){
				ErrorLog.print("Duplicate thread!");
			}
			this.netGenThreadPool.put(ty, ngy);
		}else{
			Netlist childY = new Netlist(Y, parent);
			childY.updateFamily(parent);
			this.processChildNetlist(childY);
		}
	}
	private void analyzeParts(Part[] result){
		for(Part part:result){
			int partNum = part.getPartNumber();
			for(B b:part.getBlocks()){
				if(b.get_part() != partNum){
					ErrorLog.print("PartNum is not equal to b.get_part()\n\tPartNum: " + partNum + "\n\tb.get_part(): " + b.get_part());
				}
			}
		}
	}
	public void hardBlockSwap(Part[] result){
		if(result[0].numDSPPrimitives() + result[1].numDSPPrimitives() > 0){
			SwapDSP swapDSP = new SwapDSP(result);
			swapDSP.run();
		}
		if(result[0].numRAMPrimitives() + result[1].numRAMPrimitives() > 0){
			SwapRAM swapRAM = new SwapRAM(result);
			swapRAM.run();
		}
	}
	public void processChildNetlist(Netlist child){
//If it is die level partitioning:
//Do not check for the cluster size, else do as usual.
		//Output.println("The value of diePart in process child netlist is " + this.diePart);
		if(this.diePart)
		{
			//Output.println("process child netlist is true ");
			this.stack.pushNetlist(child);
		}
		else if(child.atom_count() > this.maxNetlistSize){
			//Output.println("The atom count is " + child.atom_count());
			this.stack.pushNetlist(child);
		}
		this.timeSteps.add(Timing.currentTime(this.startTime) + "\t" + this.threadPool.usedThreads());
		if(this.diePart)
		{
			//Output.println("The die level partitioning has started");	
		this.startdieHMetis();
		}else
		{
		this.startHMetis();
		}
	
	}
/*	public void processChildNetlist(PartitionNetlist child){
	//If it is die level partitioning:
	//Do not check for the cluster size, else do as usual.
			if(this.diePart)
			{
				Output.println("process child netlist is true ");
				this.stack.pushNetlist(child);
			}
			else if(child.atom_count() > this.maxNetlistSize){
				Output.println("process child netlist is true in 2nd loop");
				//Output.println("The atom count is " + child.atom_count());
				this.stack.pushNetlist(child);
			}
			this.timeSteps.add(Timing.currentTime(this.startTime) + "\t" + this.threadPool.usedThreads());
			//if(!this.diePart)
			{
			this.startHMetis();
			}
		
		}
		*/
	private void testBipartition(Netlist root, Part[] result){
		if(Partition.diePart)
		{
			//Output.println("The output is");
			//Output.println("The number of parts is " + result.length);
			//Part[num of dies] result.
			Output.println("Netlist " + root.toString() + " has " + result.length + " parts each of size" );
			for(int i=0; i<this.NoOfdie;i++)
			{
				Output.println("\n Part " + i + " of " + result[i].size());
			}
			//Info.add("partstat", "Netlist " + root.toString() + " has two parts of size " + result[0].size() + " and size " + result[1].size());
			int blockCount = 0;
			for(Part part:result){
				blockCount += part.size();
			}
			if(blockCount != root.block_count()){
				Output.println("Blocks lost during partitioning:\n\tPartition block count:\t" + blockCount + "\n\tParent block count:\t" + root.block_count());
			}
			for(int i=0; i<this.NoOfdie;i++)
			{
				if(result[i].isEmpty()){
					ErrorLog.print("Error in bipartitioning" + "\n\tSize part[0] = " + result[0].size() + "\n\tSize part[1] = " + result[1].size());
				}
			}
		}else {
			
			Info.add("partstat", "Netlist " + root.toString() + " has two parts of size " + result[0].size() + " and size " + result[1].size());
			int blockCount = 0;
			for(Part part:result){
				blockCount += part.size();
			}
			if(blockCount != root.block_count()){
				Output.println("Blocks lost during partitioning:\n\tPartition block count:\t" + blockCount + "\n\tParent block count:\t" + root.block_count());
			}
			if(result[0].isEmpty() || result[1].isEmpty()){
				ErrorLog.print("Error in bipartitioning" + "\n\tSize part[0] = " + result[0].size() + "\n\tSize part[1] = " + result[1].size());
			}
		}
	}
	public CutEdges getCutEdges(){
		return this.cutEdges;
	}
	private void eachParentHasTwoChildren(){
		Netlist parent = this.root;
		ArrayList<Netlist> currentWork = new ArrayList<Netlist>();
		ArrayList<Netlist> nextWork = new ArrayList<Netlist>();
		nextWork.add(parent);
		
		while(nextWork.size()>0){
			currentWork = new ArrayList<Netlist>(nextWork);
			nextWork = new ArrayList<Netlist>();
			while(!currentWork.isEmpty()){
				parent = currentWork.remove(0);
				if(parent.has_children()){
					if(parent.get_children().size()==2){
						for(Netlist child:parent.get_children()){
							nextWork.add(child);
						}
					}else{
						Output.println("Netlist " + parent.toString() + " does not have 2 children: " + parent.get_children().size());
						for(Netlist child:parent.get_children()){
							nextWork.add(child);
						}
					}
				}
			}
		}
	}
	private void deleteExistingFiles(){
		File folder = new File(this.simulation.getStringValue("hmetis_folder") + "files/");
		File[] listOfFiles = folder.listFiles();
		for(int i = 0; i < listOfFiles.length; i++){
			File file = listOfFiles[i];
			if(file.isFile()){
				if(file.getName().contains(this.root.get_blif() + "_" + this.simulation.getSimulationID())){
					file.delete();
				}
			}
		}
	}
}