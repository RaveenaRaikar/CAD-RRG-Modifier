package pack.main;

import java.util.ArrayList;

import pack.architecture.Architecture;
import pack.cluster.Cluster;
import pack.netlist.DieLevelNetlist;
import pack.netlist.Netlist;
//import pack.netlist.PartitionNetlist;
import pack.netlist.PathWeight;
import pack.partition.Partition;
import pack.util.Info;
import pack.util.Output;
import pack.util.Timing;

public class Main {
	public static void main(String[] args){
		//ArrayList<Netlist> PartNet = new ArrayList<Netlist>();
	
//MULTI-DIE MODIFICATIONS////
		
		Simulation simulation = new Simulation();
		simulation.parseArgs(args);

		Info.enabled(simulation.getBooleanValue("print_stats_to_file"));

		Output.path(simulation);//Set path of output
		Output.println(simulation.toValueString());
		Output.newLine();

		//NETLIST
		Netlist netlist = new Netlist(simulation);

		//////// PACKING TIMER ////////
		Timing multiPartTimer = new Timing();
		multiPartTimer.start();
		///////////////////////////////

		//ARCHITECTURE
		Timing architecturetimer = new Timing();
		architecturetimer.start();
/*
		Architecture arch1 = new Architecture(simulation);
		arch1.generate_light_architecture(netlist.get_models().keySet());
		
		Architecture arch2 = new Architecture(simulation);
		arch2.generate_pack_architecture(netlist.get_models().keySet());
*/

	    Architecture archLight = new Architecture(simulation);
		archLight.initialize();
		  
		architecturetimer.stop(); 
		Output.println("Architecture functionality took " + architecturetimer.toString()); 
		Output.newLine();
  
		netlist.floating_blocks();
		  
		//Timing edges 
		PathWeight path = new PathWeight(netlist, archLight,simulation); 
		path.assign_net_weight();
		  
		//Pre-packing 
		/*
		netlist.pre_pack_dsp();
		  
		netlist.pre_pack_carry(); 
		//netlist.pre_pack_share();
		  
		netlist.pre_pack_lut_ff();
		  
		netlist.pre_pack_ram(archLight);
		  
		netlist.netlist_checker(); 
		*/
		//PATITIONING USING HMETIS
/*		  
	    //////Die level partitioning//////// 
	    Partition partition = new Partition(netlist, archLight, simulation, path.get_max_arr_time(),true);
	    Timing partitioningTimer = new Timing();
	    partitioningTimer.start();
	    Output.println("\tStarting Die level Partitioning ");
	    partition.diepartition(); 
	    partitioningTimer.stop();
	    Output.println("\tDie level Partitioning took " + partitioningTimer.toString());
	    Output.newLine();
	  
	    Info.finish(simulation);
	    //Change the netlist file
	    DieLevelNetlist net = new DieLevelNetlist(netlist,partition,simulation);
	    net.GenerateDieNetlist();
       //create a new object for netlist // pass the name of the file to the netlist.

	    
	    Netlist [] PartNet = new Netlist [simulation.getIntValue("Number_of_die")];
	    PathWeight [] PathPart = new PathWeight [simulation.getIntValue("Number_of_die")];
	    Partition [] MultiPart = new Partition [simulation.getIntValue("Number_of_die")];
	    Cluster [] Pack = new Cluster [simulation.getIntValue("Number_of_die")];
	    int partnum;
	    Timing DiepartitioningTimer = new Timing();
	    Timing seedBasedPackingTimer = new Timing();
	    for (partnum = 0; partnum < (simulation.getIntValue("Number_of_die")); partnum++)
	    {
	    	//Output.println("How many times am I executed?");
	    	//get input variables;
	    	PartNet[partnum] = new Netlist(simulation,partnum);
	    	PathPart[partnum] = new PathWeight(PartNet[partnum], archLight,simulation);
	    	PathPart[partnum].assign_net_weight();
			//Pre-packing 
	    	PartNet[partnum].pre_pack_dsp();
			  
	    	PartNet[partnum].pre_pack_carry(); 
			//netlist.pre_pack_share();
			  
	    	PartNet[partnum].pre_pack_lut_ff();
			  
	    	PartNet[partnum].pre_pack_ram(archLight);
			  
	    	PartNet[partnum].netlist_checker();
	    	MultiPart[partnum] = new Partition(PartNet[partnum], archLight, simulation, PathPart[partnum].get_max_arr_time(),false);
	    	// Partition multiPart= new Partition(net1, archLight, simulation, path_part.get_max_arr_time());
		    //Timing MultipartTimer = new Timing();
	    	DiepartitioningTimer.start();
		    MultiPart[partnum].partitionate(); 
		    DiepartitioningTimer.stop();
		    Output.println("\tPartitioning took " + DiepartitioningTimer.toString());
		    Output.newLine();
//		    Pack[partnum] = new Cluster(PartNet[partnum], archLight, MultiPart[partnum], simulation, partnum);
//		    
//		    seedBasedPackingTimer.start();
//		    Pack[partnum].packing();
//		    seedBasedPackingTimer.stop();
//		    Output.println("\tSeed based packing took " + seedBasedPackingTimer.toString());
//		    Pack[partnum].writeNetlistFile(); 
//		    Pack[partnum].writeHierarchyFile();
//		    Info.finish(simulation);
		    
		    
	    }
	    ////////PACKING TIMER ////////
		multiPartTimer.stop();
		Output.println("\tMultiPart took " + multiPartTimer.toString());
		//Accumulate all the time
	    //Output.println("\t Total Partitioning took " + DiepartitioningTimer.toString());
	    //Output.println("\t Total Seed based packing took " + seedBasedPackingTimer.toString());
	   */ 
	}
}