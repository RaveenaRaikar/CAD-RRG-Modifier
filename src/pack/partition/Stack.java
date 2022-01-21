package pack.partition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import pack.netlist.Netlist;
//import pack.netlist.PartitionNetlist;
//import pack.util.Output;
import pack.util.Output;

public class Stack {
	private HashMap<Integer,ArrayList<Netlist>> work;
//	private HashMap<Integer,ArrayList<PartitionNetlist>> partwork;
	private int numElements = 0;
	
	public Stack(){
		this.work = new HashMap<Integer,ArrayList<Netlist>>();
//		this.partwork = new HashMap<Integer,ArrayList<PartitionNetlist>>();
		this.numElements = 0;
		}
			
	public void pushNetlist(Netlist netlist){
		//Output.println("this.numElements in push Netlist" + this.numElements);
		this.numElements += 1;
		int numBlocks = netlist.atom_count();
		//Output.println("The size in pushnetlist is " + numBlocks);
		if(!this.work.containsKey(numBlocks)){
		//	Output.println("Is this true?)");
			this.work.put(numBlocks, new ArrayList<Netlist>());
		}
		this.work.get(numBlocks).add(netlist); //Adding the last block of the netlist as the previous condition is satisfied then 
											   //last block will not be added
		///Output.println("The size of partwork is " + this.work.size());	
	}
/*	public void pushNetlist(PartitionNetlist partnetlist){
		//Output.println("this.numElements in push Netlist" + this.numElements);
		this.numElements += 1;
		int numBlocks = partnetlist.atom_count();
		Output.println("The size is " + numBlocks);
		if(!this.partwork.containsKey(numBlocks)){
			Output.println("Is this true?)");
			this.partwork.put(numBlocks, new ArrayList<PartitionNetlist>());
		}
		this.partwork.get(numBlocks).add(partnetlist); //Adding the last block of the netlist as the previous condition is satisfied then 
		Output.println("The size of partwork is " + this.partwork.size());								   //last block will not be added
	}
	*/
	public Netlist pullNetlist(){
		//Output.println("this.numElements in pull Netlist" + this.numElements);
		this.numElements -= 1;
		//Output.println("The max size is" + work.size());
		//Output.println("The max size is" + this.getMaxSize());
		//TODO
		Netlist netlist = this.work.get(this.getMaxSize()).remove(0);
		this.removeEmptyRows();
		return netlist;
	}
/*	
	public PartitionNetlist partpullNetlist(){
		//	Output.println("this.partwork.get(this.getMaxSize()).remove(0) in pull Netlist" + this.partwork.get(this.getMaxSize()).remove(0));
		this.numElements -= 1;
		Output.println("The max size is" + partwork.size());
		//TODO
		PartitionNetlist partnetlist = this.partwork.get(this.getMaxSize()).remove(0);
		this.removeEmptyRows();
		return partnetlist;
	}
	*/
	public int size(){
		return this.numElements;
		
	}
	public boolean isEmpty(){
	//	Output.println("this.numElements in empty" + this.numElements);
		return (this.numElements == 0);
	}
	private int getMaxSize(){
		int maxSize = 0;
		for(int row:this.work.keySet()){
			if(row > maxSize){
				maxSize = row;
			}
		}
		return maxSize;
	}
	private void removeEmptyRows(){
		Set<Integer> emptyRows = new HashSet<Integer>();
		for(Integer row:this.work.keySet()){
			if(this.work.get(row).isEmpty()){
				emptyRows.add(row);
			}
		}
		for(Integer emptyRow:emptyRows){
			this.work.remove(emptyRow);
		}
	}
}
