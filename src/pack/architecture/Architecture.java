package pack.architecture;

import java.util.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import pack.main.Simulation;
import pack.netlist.P;
import pack.util.ErrorLog;
import pack.util.Output;
import pack.util.Timing;
import pack.util.Util;

public class Architecture {
	private String name;
	private Simulation simulation;
	
	private ArrayList<String> lines;
	
	private Set<String> modelSet;
	private Set<String> removedModels;
	private final ArrayList<Pin> pins;
	private HashMap<String,ArrayList<Block>> blifBlocks;
	private HashMap<String,ArrayList<Pin>> blifPins;
	private ArrayList<Block> complexBlocks;
	private int numConn;
	private HashMap<Integer, HashMap<Integer, Conn>> connections;
	private HashMap<String, HashMap<String, Boolean>> globalConnections;
	private HashMap<String, HashMap<String,Integer>> delayMap;
	private Map<String, Integer> alldimensions;
	private int sizeX;
	private int sizeY;
	//DSP, RAM block size
	private int DSPht;
	private int RAMht;
	private int DSPwt;
	private int RAMwt;
	//DSP, RAM start and repeat locations
	private int DSPx;
	private int RAMx;
	private int DSPstartx;
	private int RAMstartx;
	private int RAMpriority;
	private int DSPpriority;
	
	public Architecture(Simulation simulation){
		this.name = simulation.getStringValue("architecture");
		this.simulation = simulation;
		this.modelSet = new HashSet<String>();
		this.removedModels = new HashSet<String>();
		this.pins = new ArrayList<Pin>();
		this.complexBlocks = new ArrayList<Block>();
		this.blifBlocks = new HashMap<String,ArrayList<Block>>();
		this.blifPins = new HashMap<String,ArrayList<Pin>>();
		this.numConn = 0;
		this.connections = new HashMap<Integer,HashMap<Integer,Conn>>();
		this.globalConnections = new HashMap<String,HashMap<String, Boolean>>();
		this.alldimensions = new HashMap<>();
		
		this.delayMap = new HashMap<String, HashMap<String,Integer>>();
	}
	
	//READ AND PARSE FILE
	private ArrayList<String> read_file(String fileName){
		if(!Util.fileExists(fileName)){
			Output.println("Architecture file " + fileName + " does not exist");
		}
		Output.println("\tFilename: " + fileName);
		
		ArrayList<String> lines = new ArrayList<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(fileName));
		    String line = br.readLine();
		    while (line != null) {
		    	if(line.contains("<!--") && !line.contains("-->")){
		    		StringBuilder sb = new StringBuilder();
		    		while(!line.contains("-->")){
		    			sb.append(line);
		    			line = br.readLine();
		    		}
		    		sb.append(line);
		    		lines.add(sb.toString());
		    	}else{
		    		lines.add(line);
		    		//System.out.println(lines);
		    	}
		        line = br.readLine();
		    }
		    br.close();
		}catch (IOException e) {
			e.printStackTrace();
		}
		//System.out.println(lines);
		return parse_file(lines);
		
	}
	private ArrayList<String> parse_file(ArrayList<String> lines){//Trim lines | Remove comment and empty lines
		ArrayList<String> res = new ArrayList<String>();
		for(String line:lines){
			if(line.contains("<!--") && line.contains("-->")){
				int startPos = line.indexOf("<!--");
				int endPos = line.indexOf("-->", startPos);
				String comment = line.substring(startPos, endPos+3);
				line = line.replace(comment, "");
			}
			line = line.trim();
			while(line.contains("\t")) line = line.replace("\t", "");
			while(line.contains("  ")) line = line.replace("  ", " ");
			
			if(line.length() > 0){
				res.add(line);
			}
		}
		return res;
	}
	
	//INITIALIZE ARCHITECTURE
	public void initialize(){
		Output.println("Initialize architecture:");
	//	this.lines = this.read_file(this.simulation.getStringValue("result_folder") + "arch.light.xml");
		this.lines = this.read_file(this.simulation.getStringValue("result_folder") + "Huawei_arch_multi_die_V2.xml");
	//	this.lines = this.read_file(this.simulation.getStringValue("result_folder") + "k6FracN10LB_mem20K_complexDSP_customSB_22nm.xml");
	//	this.lines = this.read_file(this.simulation.getStringValue("result_folder") + "stratixiv_arch.timing.xml");
		
		this.get_models();
		this.get_complex_blocks(true);
		this.initializeDimensions();
		this.HardBlock_dimensions();
		Output.newLine();
	}
	private void get_models(){
		for(int i=0;i<this.lines.size();i++){
			String line = this.lines.get(i);
			if(line.contains("<model ")){
				int name = line.indexOf("name=");
				int start = line.indexOf("\"", name+1);
				int stop = line.indexOf("\"", start+1);
				String model = line.substring(start+1, stop);
				this.modelSet.add(model);
			}
		}
	}
	private void get_complex_blocks(boolean loadConnections){
		Timing t = new Timing();
		t.start();
		Element current = null;
		boolean complexBlockLines = false;
		boolean interconnectLines = false;
		boolean complete = false;
		boolean mux = false;
		boolean direct = false;
	//	int pbchecker = 0;
		
		
		for(int i=0;i<this.lines.size();i++){
			String line = this.lines.get(i);
			if(line.contains("<complexblocklist>")){
				complexBlockLines = true;
			}else if(line.contains("</complexblocklist>")){
				complexBlockLines = false;
			}else if(complexBlockLines){
				//Blocks and modes
				if(line.contains("<pb_type")){
					//pbchecker ++;
					//Output.println("pbchecker in pbytype is " + pbchecker);
					Element parent = current;
					current = new Block(new Line(line));
					
					//System.out.println(current.has_blif_model() +" " );
					if(current.has_blif_model()){
						String blifModel = current.get_blif_model();
					//	System.out.println(current.has_blif_model() +" " +  current.get_blif_model() + " " + current.get_name());
					//	System.out.println(this.blifBlocks.containsKey(blifModel) + " " );
					//	System.out.println("the parent is " + parent.get_name() + " " + "And the child is " + current.get_name());
						if(!this.blifBlocks.containsKey(blifModel)){
							this.blifBlocks.put(blifModel, new ArrayList<Block>());
						}
						this.blifBlocks.get(blifModel).add((Block)current);
					}
					if(parent != null){
						//Output.println("parent is not null");
						//Output.println("The parent in pbtype is " + parent.get_name());
						//Output.println("The child in pbtype is " + current.get_name());
						current.set_parent(parent);
						parent.add_child(current);
					}
				}else if(line.contains("<mode")){
					//pbchecker=0;
					//Output.println("pbchecker in mode is " + pbchecker);
					Element parent = current;
					current = new Mode(new Line(line));
					if(parent != null){
						//System.out.println("the parent in mode  is " + parent.get_name() + " " + "And the child in mode is " + current.get_name());
						current.set_parent(parent);
						parent.add_child(current);
						//Output.println("current.set_parent(parent) " + line);
						//Output.println("current.add_child(parent) " + this.current);
					}
					
				}else if(line.contains("</pb_type>")){
					if(current.has_parent()){
						//Output.println("The parent is " + parent.get_name());
						//Output.println("The child before is " + current.get_name());
						current = current.get_parent();
						//Output.println("The child after is " + current.get_name());
					}else{
						this.complexBlocks.add((Block)current);
						current = null;
					}
				}else if(line.contains("</mode>")){
					//pbchecker=0;
					//Output.println("pbchecker at the end of mode is " + pbchecker);
					if(current.has_parent()){
						current = current.get_parent();
					}else{
						ErrorLog.print("Mode has no parent block");
					}
				//Block pins
				}else if(line.contains("<input")){
					Line l = new Line(line);
					if(l.get_type().equals("input")){
						Port inputPort = new Port(l, (Block)current);
						this.pins.addAll(inputPort.get_pins());
						current.add_input(inputPort);
					}else{
						ErrorLog.print("This line should have type input instead of " + l.get_type() + " | " + line);
					}
				}else if(line.contains("<output")){
					Line l = new Line(line);
					if(l.get_type().equals("output")){
						Port outputPort = new Port(l, (Block)current);
						this.pins.addAll(outputPort.get_pins());
						current.add_output(outputPort);
					}else{
						ErrorLog.print("This line should have type output instead of " + l.get_type() + " | " + line);
					}
				}else if(line.contains("<clock")){
					Line l = new Line(line);
					if(l.get_type().equals("clock")){
						Port clockPort = new Port(l, (Block)current);
						this.pins.addAll(clockPort.get_pins());
						current.set_clock(clockPort);
					}else{
						ErrorLog.print("This line should have type clock instead of " + l.get_type() + " | " + line);
					}	
				//Delay information
				}else if(line.contains("<delay_constant") && !interconnectLines){
					Line l = new Line(line);
					if(l.get_type().equals("delay_constant")){
						String[] inputs = l.get_value("in_port").split(" ");
						String[] outputs = l.get_value("out_port").split(" ");
						double secondDelay = Double.parseDouble(l.get_value("max"));
						int picoSecondDelay = (int)Math.round(secondDelay*Math.pow(10, 12));
						for(String input:inputs){
							if(input.contains("[") || input.contains("]")) ErrorLog.print("Wrong input format " + input);
							for(String output:outputs){
								if(output.contains("[") || output.contains("]")) ErrorLog.print("Wrong output format " + output);
								current.add_delay(input, output, picoSecondDelay);
							}
						}
					}else{
						ErrorLog.print("This line should have type delay_constant instead of " + l.get_type() + " | " + line);
					}
				//}else if(line.contains("<T_clock_to_Q")){
				//	Line l = new Line(line);
				//	if(l.get_type().equals("T_clock_to_Q")){
				//		String clock = l.get_value("clock");
				//		if(clock.contains("[") || clock.contains("]")) ErrorLog.print("Wrong clock format " + clock);
						//Add input 
				//		String[] outputs = l.get_value("port").split(" ");
				//		double secondDelay = Double.parseDouble(l.get_value("max"));
				//		int picoSecondDelay = (int)Math.round(secondDelay*Math.pow(10, 12));
						
				//		for(String output:outputs){
				//			if(output.contains("[") || output.contains("]")) ErrorLog.print("Wrong output format " + output);
				//			current.add_clock_to_output(clock, output, picoSecondDelay);
				//		}
				//	}else{
				//		ErrorLog.print("This line should have type T_clock_to_Q instead of " + l.get_type() + " | " + line);
				//	}
				}else if(line.contains("<T_clock_to_Q")){
					Line l = new Line(line);
					if(l.get_type().equals("T_clock_to_Q")){
						String clock = l.get_value("clock");
						if(clock.contains("[") || clock.contains("]")) ErrorLog.print("Wrong clock format " + clock);
						//Add inputget_complex_blocks 
						String[] inputs = l.get_value("port").split(" ");
						String[] outputs = l.get_value("port").split(" ");
						double secondDelay = Double.parseDouble(l.get_value("max"));
						int picoSecondDelay = (int)Math.round(secondDelay*Math.pow(10, 12));
						
						for(String input:inputs){
							if(input.contains("[") || input.contains("]")) ErrorLog.print("Wrong input format " + input);
							for(String output:outputs){
								if(output.contains("[") || output.contains("]")) ErrorLog.print("Wrong output format " + output);
								current.add_clock_to_input_output(clock, input, output, picoSecondDelay);
							}
						}
					}else{
						ErrorLog.print("This line should have type T_clock_to_Q instead of " + l.get_type() + " | " + line);
					}
				}else if(line.contains("<T_setup")){
					Line l = new Line(line);
					if(l.get_type().equals("T_setup")){
						String[] inputs = l.get_value("port").split(" ");
						String[] outputs = l.get_value("port").split(" ");
						String clock = l.get_value("clock");
						if(clock.contains("[") || clock.contains("]")) ErrorLog.print("Wrong clock format " + clock);
						double secondDelay = Double.parseDouble(l.get_value("value"));
						int picoSecondDelay = (int)Math.round(secondDelay*Math.pow(10, 12));
						for(String input:inputs){
							if(input.contains("[") || input.contains("]")) ErrorLog.print("Wrong input format " + input);
							for(String output:outputs){
								if(output.contains("[") || output.contains("]")) ErrorLog.print("Wrong output format " + output);
								current.add_setup(input, output, clock, picoSecondDelay);
							}
						}

					}else{
						ErrorLog.print("This line should have type T_setup instead of " + l.get_type() + " | " + line);
					}
					//else if(line.contains("<T_setup")){
					//	Line l = new Line(line);
					//	if(l.get_type().equals("T_setup")){
					//		String[] inputs = l.get_value("port").split(" ");
					//		String clock = l.get_value("clock");
					//		if(clock.contains("[") || clock.contains("]")) ErrorLog.print("Wrong clock format " + clock);
					//		double secondDelay = Double.parseDouble(l.get_value("value"));
					//		int picoSecondDelay = (int)Math.round(secondDelay*Math.pow(10, 12));
					//		for(String input:inputs){
					//			if(input.contains("[") || input.contains("]")) ErrorLog.print("Wrong input format " + input);
					//			current.add_setup(input, clock, picoSecondDelay);
					//		}
					//	}else{
					//		ErrorLog.print("This line should have type T_setup instead of " + l.get_type() + " | " + line);
					//	}
				}else if(line.contains("<T_hold")){
					Line l = new Line(line);
					if(l.get_type().equals("T_hold")){
						String[] inputs = l.get_value("port").split(" ");
						String[] outputs = l.get_value("port").split(" ");
						String clock = l.get_value("clock");
						if(clock.contains("[") || clock.contains("]")) ErrorLog.print("Wrong clock format " + clock);
						double secondDelay = Double.parseDouble(l.get_value("value"));
						int picoSecondDelay = (int)Math.round(secondDelay*Math.pow(10, 12));
						
						for(String input:inputs){
							if(input.contains("[") || input.contains("]")) ErrorLog.print("Wrong input format " + input);
							for(String output:outputs){
								if(output.contains("[") || output.contains("]")) ErrorLog.print("Wrong output format " + output);
								current.add_hold(input, output, clock, picoSecondDelay);
							}
						}
					}else{
						ErrorLog.print("This line should have type T_hold instead of " + l.get_type() + " | " + line);
					}
				//Delay matrix
				}else if(line.contains("<delay_matrix")){
					Line l = new Line(line);
					if(l.get_type().equals("delay_matrix")){
						String[] inputs = l.get_value("in_port").split(" ");
						String[] outputs = l.get_value("out_port").split(" ");
						double secondDelay = 0.0;
						line = this.lines.get(++i);
						while(!line.contains("</delay_matrix>")){
							double localDelay = Double.parseDouble(line.replace("\t", "").replace(" ", ""));
							if(secondDelay == 0.0){
								secondDelay = localDelay;
							}else if(secondDelay != localDelay){
								ErrorLog.print("Problem in delay matrix | Delay  = " + secondDelay + " | Local delay = " + localDelay);
							}
							line = lines.get(++i);
						}
						int picoSecondDelay = (int)Math.round(secondDelay*Math.pow(10, 12));
						for(String input:inputs){
							if(input.contains("[") || input.contains("]")) ErrorLog.print("Wrong input format " + input);
							for(String output:outputs){
								if(output.contains("[") || output.contains("]")) ErrorLog.print("Wrong output format " + output);
								current.add_delay(input, output, picoSecondDelay);
							}
						}
						//To add the shorted LUT interconnect
						if(current.get_blif_model().equals(".names"))
						{
							//Output.println("I am printed for this condition");
							current.add_interconnect_line(line);
							//Line l = new Line(line);
							boolean valid = true;
							if(l.get_value("in_port").length() == 0) valid = false; //No input found
							if(l.get_value("out_port").length() == 0) valid = false; //No output found
							if(valid){
								HashMap<String, Element> interconnectedBlocks = this.get_interconnected_blocks((Element)current);
								
						//		String name = l.get_value("name");
								String[] inputPorts = l.get_value("in_port").split(" ");
								String[] outputPorts = l.get_value("out_port").split(" ");
									
								for(String inputPort:inputPorts){
									for(String outputPort:outputPorts){
										Element sourceBlock = interconnectedBlocks.get(process_block_name(inputPort.split("\\.")[0], interconnectedBlocks));
										Element sinkBlock = interconnectedBlocks.get(process_block_name(outputPort.split("\\.")[0], interconnectedBlocks));
										
										ArrayList<Pin> inputPins = this.get_pins(inputPort.split("\\.")[1], sourceBlock);
										ArrayList<Pin> outputPins = this.get_pins(outputPort.split("\\.")[1], sinkBlock);
									
										for(Pin inputPin:inputPins){
											//Output.println("The input pin for the delay matrix is " + inputPin.get_name());
											for(Pin outputPin:outputPins){
											//	Output.println("The Output pin for the delay matrix is " + outputPin.get_name());
												boolean add = true;
												//Output.println("The input pin is " + inputPin.get_name() + " The output pin is " + outputPin.get_name()  );
												if(this.connections.containsKey(inputPin.get_number())){
													//Output.println("The second time it is added");
													if(this.connections.get(inputPin.get_number()).containsKey(outputPin.get_number())){
														add = false;
													}
												}
												if(add){
													Conn conn = new Conn(name, inputPin, outputPin, false);
													inputPin.add_output_connection(conn);
													outputPin.add_input_connection(conn);
													//Output.println("The first time it is added");
													if(!this.connections.containsKey(inputPin.get_number())){
														this.connections.put(inputPin.get_number(), new HashMap<Integer,Conn>());
													}
													this.connections.get(inputPin.get_number()).put(outputPin.get_number(), conn);
													this.numConn += 1;
													conn.set_delay(picoSecondDelay);
													//Output.println("The connection and the delays are " + conn.get_source().get_name() + " " + conn.get_sink().get_name() + " " + conn.get_delay());
												}
											}
										}							
									}
								}
							}else{
								//Output.println("Non valid complete interconnect line: " + line);
							}
							complete = false;
							mux = false;
							direct = true;
						}
					}else{
						ErrorLog.print("This line should have type delay_matrix instead of " + l.get_type() + " | " + line);
					}
					
					///Check if it is a blif model of the type .names and add it as an interconnect line.
					
				//INTERCONNECT LINES
				}else if(line.contains("<interconnect>")){
					interconnectLines = true;
				//	Output.println("The current interconnect line is " + current.get_name());
					current.add_interconnect_line(line);
				}else if(line.contains("</interconnect>")){
					interconnectLines = false;
					current.add_interconnect_line(line);
				//Complete crossbar
				}else if(line.contains("<complete") && loadConnections){
					current.add_interconnect_line(line);
					Line l = new Line(line);
					boolean valid = true;
					if(l.get_value("input").length() == 0) valid = false; //No input found
					if(l.get_value("output").length() == 0) valid = false; //No output found
					if(valid){
					//	HashMap<String, Block> interconnectedBlocks = this.get_interconnected_blocks((Mode)current);
						//CHANGE
						HashMap<String, Element> interconnectedBlocks = this.get_interconnected_blocks((Element)current);
						String name = l.get_value("name");
						String[] inputPorts = l.get_value("input").split(" ");
						String[] outputPorts = l.get_value("output").split(" ");
							
						for(String inputPort:inputPorts){
							for(String outputPort:outputPorts){
								Element sourceBlock = interconnectedBlocks.get(process_block_name(inputPort.split("\\.")[0], interconnectedBlocks));
								Element sinkBlock = interconnectedBlocks.get(process_block_name(outputPort.split("\\.")[0], interconnectedBlocks));
								
							//	Block sourceBlock = interconnectedBlocks.get(process_block_name(inputPort.split("\\.")[0], interconnectedBlocks));
							//	Block sinkBlock = interconnectedBlocks.get(process_block_name(outputPort.split("\\.")[0], interconnectedBlocks));
								
								ArrayList<Pin> inputPins = this.get_pins(inputPort.split("\\.")[1], sourceBlock);
								ArrayList<Pin> outputPins = this.get_pins(outputPort.split("\\.")[1], sinkBlock);
							
								for(Pin inputPin:inputPins){
									for(Pin outputPin:outputPins){
										boolean add = true;
										if(this.connections.containsKey(inputPin.get_number())){
											if(this.connections.get(inputPin.get_number()).containsKey(outputPin.get_number())){
												add = false;
											}
										}
										if(add){
											Conn conn = new Conn(name, inputPin, outputPin, false);
											inputPin.add_output_connection(conn);
											outputPin.add_input_connection(conn);
											if(!this.connections.containsKey(inputPin.get_number())){
												this.connections.put(inputPin.get_number(), new HashMap<Integer,Conn>());
											}
											this.connections.get(inputPin.get_number()).put(outputPin.get_number(), conn);
											this.numConn += 1;
										}
									}
								}							
							}
						}
					}else{
						//Output.println("Non valid complete interconnect line: " + line);
					}
					complete = true;
					mux = false;
					direct = false;
				//Direct connection
				}else if(line.contains("<direct") && loadConnections){
					current.add_interconnect_line(line);
					Line l = new Line(line);
					boolean valid = true;
					if(l.get_value("input").length() == 0) valid = false; //No input found
					if(l.get_value("output").length() == 0) valid = false; //No output found
					if(valid){
					//	Output.println("The current type is "+ current.get_type());
					//	HashMap<String, Block> interconnectedBlocks = this.get_interconnected_blocks((Mode)current);
					//	HashMap<String, Block> interconnectedB = new HashMap<String, Block>();
					//	HashMap<String, Block> interconnectedM = new HashMap<String, Block>();
						//CHANGE THIS
						HashMap<String, Element> interconnectedBlocks = this.get_interconnected_blocks((Element)current);
					//	if(current.get_type().equals("pb_type"))
					//	{
					//		interconnectedB = this.get_interconnected_blocks((Block)current);
					//	}else
					//	{
					//		interconnectedM = this.get_interconnected_blocks((Mode)current);
					//	}
						
					//	HashMap<String, Block> interconnectedBlocks = new HashMap<String,Block>();
					//	interconnectedBlocks.putAll(interconnectedB);
					//	interconnectedBlocks.putAll(interconnectedM);
					//	Output.println("The value for one_mult is " + interconnectedBlocks.get("one_mult_27x27"));
						String name = l.get_value("name");
						String[] inputPorts = l.get_value("input").split(" ");
						String[] outputPorts = l.get_value("output").split(" ");
						
						String Outputpin = l.get_value("output");
					//	Output.println("The value is " + Outputpin);
						for(String inputPort:inputPorts){
						//	Output.println("The input ports are " + inputPort);
							for(String outputPort:outputPorts){

								Element sourceBlock = interconnectedBlocks.get(process_block_name(inputPort.split("\\.")[0], interconnectedBlocks));
								Element sinkBlock =interconnectedBlocks.get(process_block_name(outputPort.split("\\.")[0], interconnectedBlocks));

								ArrayList<Pin> inputPins = this.get_pins(inputPort.split("\\.")[1], sourceBlock);
								ArrayList<Pin> outputPins = this.get_pins(outputPort.split("\\.")[1], sinkBlock);
								

								for(int p=0;p<inputPins.size();p++){
									Pin inputPin = inputPins.get(p);
									Pin outputPin = outputPins.get(p);
									boolean add = true;
									if(this.connections.containsKey(inputPin.get_number())){
										if(this.connections.get(inputPin.get_number()).containsKey(outputPin.get_number())){
											add = false;
										}
									}
									if(add){
										Conn conn = new Conn(name, inputPin, outputPin, false);
										inputPin.add_output_connection(conn);
										outputPin.add_input_connection(conn);
										if(!this.connections.containsKey(inputPin.get_number())){
											this.connections.put(inputPin.get_number(), new HashMap<Integer,Conn>());
										}
										this.connections.get(inputPin.get_number()).put(outputPin.get_number(), conn);
										this.numConn += 1;
									}
								}					
							}
						}
					}else{
						Output.println("Non valid direct interconnect line: " + line);
					}
					complete = false;
					mux = false;
					direct = true;
				//Multiplexer
				}else if(line.contains("<mux") && loadConnections){
					current.add_interconnect_line(line);
					Line l = new Line(line);
					boolean valid = true;
					if(l.get_value("input").length() == 0) valid = false; //No input found
					if(l.get_value("output").length() == 0) valid = false; //No output found
					if(valid){
						//HashMap<String, Block> interconnectedBlocks = this.get_interconnected_blocks((Mode)current);
						//CHANGE
						HashMap<String, Element> interconnectedBlocks = this.get_interconnected_blocks((Element)current);
						
						String name = l.get_value("name");
						String[] inputPorts = l.get_value("input").split(" ");
						String[] outputPorts = l.get_value("output").split(" ");
							
						for(String inputPort:inputPorts){
							for(String outputPort:outputPorts){
								Element sourceBlock = interconnectedBlocks.get(process_block_name(inputPort.split("\\.")[0], interconnectedBlocks));
								Element sinkBlock =interconnectedBlocks.get(process_block_name(outputPort.split("\\.")[0], interconnectedBlocks));
								
								//Block sourceBlock = interconnectedBlocks.get(process_block_name(inputPort.split("\\.")[0], interconnectedBlocks));
								//Block sinkBlock =interconnectedBlocks.get(process_block_name(outputPort.split("\\.")[0], interconnectedBlocks));
								
								ArrayList<Pin> inputPins = this.get_pins(inputPort.split("\\.")[1], sourceBlock);
								ArrayList<Pin> outputPins = this.get_pins(outputPort.split("\\.")[1], sinkBlock);
								if(outputPins.size()!= 1){
									ErrorLog.print("To many output pins in mux connection | output pins: " + outputPins.size() + " | " + line);
								}
								Pin outputPin = outputPins.get(0);
								for(Pin inputPin:inputPins){
									boolean add = true;
									if(this.connections.containsKey(inputPin.get_number())){
										if(this.connections.get(inputPin.get_number()).containsKey(outputPin.get_number())){
											add = false;
										}
									}
									if(add){
										Conn conn = new Conn(name, inputPin, outputPin, false);
										inputPin.add_output_connection(conn);
										outputPin.add_input_connection(conn);
										if(!this.connections.containsKey(inputPin.get_number())){
											this.connections.put(inputPin.get_number(), new HashMap<Integer,Conn>());
										}
										this.connections.get(inputPin.get_number()).put(outputPin.get_number(), conn);
										this.numConn += 1;
									}
								}
							}
						}
					}else{
						Output.println("Non valid mux interconnect line: " + line);
					}
					complete = false;
					mux = true;
					direct = false;
				}else if(line.contains("<delay_constant") && interconnectLines && loadConnections){
					current.add_interconnect_line(line);
					Line l = new Line(line);
					boolean valid = true;
					if(l.get_value("in_port").length() == 0) valid = false; //No input found
					if(l.get_value("out_port").length() == 0) valid = false; //No output found
					if(valid){
						
						//HashMap<String, Block> interconnectedBlocks = this.get_interconnected_blocks((Mode)current);
						HashMap<String, Element> interconnectedBlocks = this.get_interconnected_blocks((Element)current);
						double connectionSecondDelay = Double.parseDouble(l.get_value("max"));
						int connectionPicoSecondDelay = (int)Math.round(connectionSecondDelay*Math.pow(10, 12));
						
						String[] inputPorts = l.get_value("in_port").split(" ");
						String[] outputPorts = l.get_value("out_port").split(" ");
							
						for(String inputPort:inputPorts){
							for(String outputPort:outputPorts){
								Element sourceBlock = interconnectedBlocks.get(process_block_name(inputPort.split("\\.")[0], interconnectedBlocks));
								Element sinkBlock = interconnectedBlocks.get(process_block_name(outputPort.split("\\.")[0], interconnectedBlocks));
							//	Block sourceBlock = interconnectedBlocks.get(process_block_name(inputPort.split("\\.")[0], interconnectedBlocks));
							//	Block sinkBlock = interconnectedBlocks.get(process_block_name(outputPort.split("\\.")[0], interconnectedBlocks));
								//Raav : Done these changes to support nested pb_type structures in VTR arch file
								ArrayList<Pin> inputPins = this.get_pins(inputPort.split("\\.")[1], sourceBlock);
								ArrayList<Pin> outputPins = this.get_pins(outputPort.split("\\.")[1], sinkBlock);
								
								if(complete){
									for(Pin inputPin:inputPins){
										//Output.println("The input pins are " + inputPin.get_name());
										for(Pin outputPin:outputPins){
											//Output.println("The output pins are " + outputPin.get_name());
											Conn conn = this.connections.get(inputPin.get_number()).get(outputPin.get_number());
											int localDelay = conn.get_delay();
											//Output.println("The local delay is " + localDelay);
											if(localDelay < 0){
												conn.set_delay(connectionPicoSecondDelay);
											//	Output.println("The connection delay is " + conn.get_delay());
											}else if(localDelay != connectionPicoSecondDelay){
												Output.println(localDelay + "!=" + connectionPicoSecondDelay);
											}
										}
									}
								}else if(mux){
									if(outputPins.size() != 1){
										ErrorLog.print("Mux should have only one output");
									}
									Pin outputPin = outputPins.get(0);
									for(Pin inputPin:inputPins){
										Conn conn = this.connections.get(inputPin.get_number()).get(outputPin.get_number());
										int localDelay = conn.get_delay();
										if(localDelay < 0){
											conn.set_delay(connectionPicoSecondDelay);
										}else if(localDelay != connectionPicoSecondDelay){
											Output.println(localDelay + "!=" + connectionPicoSecondDelay);
										}
									}
								}else if(direct){
									if(inputPins.size() != outputPins.size()){
										ErrorLog.print("The number of input pins is not equal to the number of output pins in direct connection | input pins: " + inputPins.size() + " | output pins: " + outputPins.size());
									}
									for(int p=0;p<inputPins.size();p++){
										Pin inputPin = inputPins.get(p);
										Pin outputPin = outputPins.get(p);
										Conn conn = this.connections.get(inputPin.get_number()).get(outputPin.get_number());
										int localDelay = conn.get_delay();
										if(localDelay < 0){
											conn.set_delay(connectionPicoSecondDelay);
										}else if(localDelay != connectionPicoSecondDelay){
											Output.println(localDelay + "!=" + connectionPicoSecondDelay);
										}
									}
								}
							}
						}
					}else{
						//Output.println("Non valid delay constant line: " + line);
					}
				}else{
					current.add_interconnect_line(line);
				}
			}
		}
		if(loadConnections){
			//Delay of short connection in global routing architecture 
			//      => this value is architecture specific! 
			//TODO Determine this value based on architecture file
			//Change this to average of L1+L2+L6 delays
			int interconnectDelay = 42;

			for(Block sourceBlock:this.complexBlocks){
				for(Port sourcePort:sourceBlock.get_output_ports()){
					for(Pin sourcePin:sourcePort.get_pins()){
						for(Block sinkBlock:this.complexBlocks){
							for(Port sinkPort:sinkBlock.get_input_and_clock_ports()){
								for(Pin sinkPin:sinkPort.get_pins()){
									Conn conn = new Conn("global_interconnect", sourcePin, sinkPin, true);
									conn.set_delay(interconnectDelay);
									//Output.println("The connections between source" + sourcePin.get_name() + " and sink pink " + sinkPin.get_name() + " is a " + conn.get_name() + " and its delay is " + conn.get_delay());
									sourcePin.add_output_connection(conn);
									sinkPin.add_input_connection(conn);
									if(!this.connections.containsKey(sourcePin.get_number())){
										this.connections.put(sourcePin.get_number(), new HashMap<Integer,Conn>());
									}
									if(!this.connections.get(sourcePin.get_number()).containsKey(sinkPin.get_number())){
										this.connections.get(sourcePin.get_number()).put(sinkPin.get_number(), conn);
										this.numConn += 1;
									}else{
										Output.println("There is already a connection between " + sourcePin.get_name() + " and " + sinkPin.get_name());
									}
								}
							}
						}
					}
				}
			}
			//Set delay of all connection without a delay to O
			for(int source:this.connections.keySet()){
				//Output.println("The source is " + source);
				for(int sink:this.connections.get(source).keySet()){
					Conn conn = this.connections.get(source).get(sink);
					if(conn.get_delay()<0){
						conn.set_delay(0);
					}
				}
			}
			//Assign blif pins
			for(String blifName:this.blifBlocks.keySet()){
				for(Block blifBlock:this.blifBlocks.get(blifName)){
					for(Port inputPort:blifBlock.get_input_and_clock_ports()){
						for(Pin inputPin:inputPort.get_pins()){
							if(!this.blifPins.containsKey(inputPin.get_name())){
								this.blifPins.put(inputPin.get_name(), new ArrayList<Pin>());
							}
							this.blifPins.get(inputPin.get_name()).add(inputPin);
						}
					}
					for(Port outputPort:blifBlock.get_output_ports()){
						for(Pin outputPin:outputPort.get_pins()){
							if(!this.blifPins.containsKey(outputPin.get_name())){
								this.blifPins.put(outputPin.get_name(), new ArrayList<Pin>());
							}
							this.blifPins.get(outputPin.get_name()).add(outputPin);
						}
					}
				}
			}
			//Assign neighbours of each pin
			for(Pin pin:this.pins){
				pin.assign_neighbours();
			}
			Output.println("\t" + this.numConn + " connections");
		}
		t.stop();
		Output.println("\tArchitecture generation took " + t.toString());
		this.test();
	}

	private String process_block_name(String blockName, HashMap<String,Element> interconnectedBlocks){
		if(blockName.contains("[") && blockName.contains("]") && blockName.contains(":")){
			//Output.println("Am i true?");
			Element block = interconnectedBlocks.get(blockName.substring(0,blockName.indexOf("[")));
			int startNum = Integer.parseInt(blockName.substring(blockName.indexOf("[")+1,blockName.indexOf(":")));
			int endNum = Integer.parseInt(blockName.substring(blockName.indexOf(":")+1,blockName.indexOf("]")));
			if(endNum < startNum){
				int temp = endNum;
				endNum = startNum;
				startNum = temp;
			}
			//if(Integer.parseInt(block.get_value("num_pb")) != (endNum - startNum + 1)){
			//	ErrorLog.print("Non symmetric connections for block " + blockName);
			//}
			return blockName.substring(0,blockName.indexOf("["));
		}else if(blockName.contains("[") && blockName.contains("]") && !blockName.contains(":")){
			//Output.println("Or am i true??" );
			Element block = interconnectedBlocks.get(blockName.substring(0,blockName.indexOf("[")));
			if(Integer.parseInt(block.get_value("num_pb")) == 1){
				return blockName.substring(0,blockName.indexOf("["));
			}else{
				return blockName.substring(0,blockName.indexOf("["));
			}
		}else{
			//Output.println("Why is it so tough?" );
			return blockName;
		}
	}
	
	/*
	private String process_block_name(String blockName, HashMap<String,Block> interconnectedBlocks){
		if(blockName.contains("[") && blockName.contains("]") && blockName.contains(":")){
			Output.println("Am i true?");
			Element block = interconnectedBlocks.get(blockName.substring(0,blockName.indexOf("[")));
			int startNum = Integer.parseInt(blockName.substring(blockName.indexOf("[")+1,blockName.indexOf(":")));
			int endNum = Integer.parseInt(blockName.substring(blockName.indexOf(":")+1,blockName.indexOf("]")));
			if(endNum < startNum){
				int temp = endNum;
				endNum = startNum;
				startNum = temp;
			}
			//if(Integer.parseInt(block.get_value("num_pb")) != (endNum - startNum + 1)){
			//	ErrorLog.print("Non symmetric connections for block " + blockName);
			//}
			return blockName.substring(0,blockName.indexOf("["));
		}else if(blockName.contains("[") && blockName.contains("]") && !blockName.contains(":")){
			Output.println("Or am i true??" );
			Element block = interconnectedBlocks.get(blockName.substring(0,blockName.indexOf("[")));
			if(Integer.parseInt(block.get_value("num_pb")) == 1){
				return blockName.substring(0,blockName.indexOf("["));
			}else{
				return blockName.substring(0,blockName.indexOf("["));
			}
		}else{
			Output.println("Why is it so tough?" );
			return blockName;
		}
	}
	*/
	/*
	private ArrayList<Pin> get_pins(String portName, Block block){
		if(portName.contains("[") && portName.contains("]") && portName.contains(":")){
			Port port = block.get_port(portName.substring(0,portName.indexOf("[")));
			int startNum = Integer.parseInt(portName.substring(portName.indexOf("[")+1,portName.indexOf(":")));
			int endNum = Integer.parseInt(portName.substring(portName.indexOf(":")+1,portName.indexOf("]")));
			if(endNum < startNum){
				int temp = endNum;
				endNum = startNum;
				startNum = temp;
			}
			ArrayList<Pin> pins = new ArrayList<Pin>();
			for(int pinNum=startNum;pinNum<=endNum;pinNum++){
				pins.add(port.get_pin(pinNum));
			}
			return pins;
		}else if(portName.contains("[") && portName.contains("]") && !portName.contains(":")){
			Port port = block.get_port(portName.substring(0,portName.indexOf("[")));
			int pinNum = Integer.parseInt(portName.substring(portName.indexOf("[")+1,portName.indexOf("]")));
			ArrayList<Pin> pins = new ArrayList<Pin>();
			pins.add(port.get_pin(pinNum));
			return pins;
		}else{
			Port port = block.get_port(portName);
			return port.get_pins();
		}
	}
	*/
	
	private ArrayList<Pin> get_pins(String portName, Element block){
		if(portName.contains("[") && portName.contains("]") && portName.contains(":")){
			Port port = block.get_port(portName.substring(0,portName.indexOf("[")));
			int startNum = Integer.parseInt(portName.substring(portName.indexOf("[")+1,portName.indexOf(":")));
			int endNum = Integer.parseInt(portName.substring(portName.indexOf(":")+1,portName.indexOf("]")));
			if(endNum < startNum){
				int temp = endNum;
				endNum = startNum;
				startNum = temp;
			}
			ArrayList<Pin> pins = new ArrayList<Pin>();
			for(int pinNum=startNum;pinNum<=endNum;pinNum++){
				pins.add(port.get_pin(pinNum));
			}
			return pins;
		}else if(portName.contains("[") && portName.contains("]") && !portName.contains(":")){
			Port port = block.get_port(portName.substring(0,portName.indexOf("[")));
			int pinNum = Integer.parseInt(portName.substring(portName.indexOf("[")+1,portName.indexOf("]")));
			ArrayList<Pin> pins = new ArrayList<Pin>();
			pins.add(port.get_pin(pinNum));
			return pins;
		}else{
			Port port = block.get_port(portName);
			return port.get_pins();
		}
	}
	
	private HashMap<String, Block> get_interconnected_blocks(Mode current){
		HashMap<String, Block> interconnectedBlocks = new HashMap<String, Block>();
		Element parent = current.get_parent();
		interconnectedBlocks.put(parent.get_name(), (Block)parent);
		for(Element child:current.get_children()){
			if(!interconnectedBlocks.containsKey(child.get_name())){
				interconnectedBlocks.put(child.get_name(), (Block)child);
			}else{
				ErrorLog.print("Duplicate child block names");
			}
		}
		return interconnectedBlocks;
	}
	private HashMap<String, Element> get_interconnected_blocks(Element current){
		HashMap<String, Element> interconnectedBlocks = new HashMap<String, Element>();
		//Output.println("The current is " + current.get_name() + " And its type is " + current.get_type());
		//Element parent = null;
		Element parent = current.get_parent();
		if(current.get_type().equals("pb_type"))
		{
			parent = current;
		}
		else {
			parent = current.get_parent();
		}
		
		//Why will there be a condition of mode followed by mode??
		/*if(parent.get_type().equals("mode"))
		{
			Output.println("The parent before is " + parent.get_name());
			parent = parent.get_parent();
			Output.println("The parent after is " + parent.get_name());
		}*/
		//Blocks interconnection : multiple pbtypes in the same mode
		interconnectedBlocks.put(parent.get_name(), (Element)parent);
		for(Element child:current.get_children()){
			if(!interconnectedBlocks.containsKey(child.get_name())){
				interconnectedBlocks.put(child.get_name(), (Element)child);
			}else{
				ErrorLog.print("Duplicate child block names");
			}
		}
		return interconnectedBlocks;
	}	
	/*
	private HashMap<String, Block> get_interconnected_blocks(Block current){
		HashMap<String, Block> interconnectedBlocks = new HashMap<String, Block>();
		Element parent = current.get_parent();
		//Output.println("The parent type is " + parent.get_type());
		if(parent.get_type().equals("mode"))
		{
			parent = parent.get_parent();
		}
		interconnectedBlocks.put(parent.get_name(), (Block)parent);
		for(Element child:current.get_children()){
			if(!interconnectedBlocks.containsKey(child.get_name())){
				interconnectedBlocks.put(child.get_name(), (Element)child);
			}else{
				ErrorLog.print("Duplicate child block names");
			}
		}
		return interconnectedBlocks;
	}
*/
	private void test(){
		for(String blifModel:this.blifBlocks.keySet()){
			//Output.println("The blif model is defined as "+ blifModel );
			if(!blifModel.equals(".names"))
			{
			for(Block blifBlock:this.blifBlocks.get(blifModel)){
				for(Port inputPort:blifBlock.get_input_and_clock_ports()){
					for(Pin inputPin:inputPort.get_pins()){
						if(inputPin.has_output_connections()){
							ErrorLog.print("This inputPin should not have output connections");
						}
					}
				}
				for(Port outputPort:blifBlock.get_output_ports()){
					for(Pin outputPin:outputPort.get_pins()){
						if(outputPin.has_intput_connections()){
							ErrorLog.print("This outputPin should not have input connections");
						}
					}
				}
			}
		}
		}
	}
	
	//Architecture dimensions
	public void initializeDimensions(){
		//TODO: Add support for auto + Fixed layout
		for(int i=0; i<this.lines.size();i++){
			String line = this.lines.get(i);
			if(line.contains("layout") && line.contains("width") && line.contains("height")){
				String[]words = line.split(" ");
				for(String word:words){
					if(word.contains("width")){
						word = word.replace("\"", "");
						word = word.replace("width", "");
						word = word.replace("=", "");
						word = word.replace("/", "");
						word = word.replace(">", "");
						this.sizeX = Integer.parseInt(word);
					}else if(word.contains("height")){
						word = word.replace("\"", "");
						word = word.replace("height", "");
						word = word.replace("=", "");
						word = word.replace("/", "");
						word = word.replace(">", "");
						this.sizeY = Integer.parseInt(word);
					}
				}
			}/*
			 * //DSP, RAM block size private int DSPht; private int RAMht; private int
			 * DSPwt; private int RAMwt; //DSP, RAM repeat locations private int DSPx;
			 * private int RAMx;
			 */
		}
	}
	
	public void HardBlock_dimensions(){
	//	Map<String, Integer> alldimensions = new HashMap<>();
		for(int i=0; i<this.lines.size();i++){
			String line = this.lines.get(i);
			if(line.contains("tile") && line.contains("dsp")) {
				String[]words = line.split(" ");
				for(String word:words){
					if(word.contains("width")){
						word = word.replace("\"", "");
						word = word.replace("width", "");
						word = word.replace("=", "");
						word = word.replace("/", "");
						word = word.replace(">", "");
						this.DSPwt = Integer.parseInt(word);
						//Output.println("The width of DSP is " + this.DSPwt);
						this.alldimensions.put("DSPwt", this.DSPwt);
					}else if(word.contains("height")){
						word = word.replace("\"", "");
						word = word.replace("height", "");
						word = word.replace("=", "");
						word = word.replace("/", "");
						word = word.replace(">", "");
						this.DSPht = Integer.parseInt(word);
						//Output.println("The height of DSP is " + this.DSPht);
						this.alldimensions.put("DSPht", this.DSPht);
					}
				}
			}else if(line.contains("tile") && line.contains("memory")) {
				String[]words = line.split(" ");
				for(String word:words){
					if(word.contains("width")){
						word = word.replace("\"", "");
						word = word.replace("width", "");
						word = word.replace("=", "");
						word = word.replace("/", "");
						word = word.replace(">", "");
						this.RAMwt = Integer.parseInt(word);
						//Output.println("The width of RAM is " + this.RAMwt);
						this.alldimensions.put("RAMwt", this.RAMwt);
					}else if(word.contains("height")){
						word = word.replace("\"", "");
						word = word.replace("height", "");
						word = word.replace("=", "");
						word = word.replace("/", "");
						word = word.replace(">", "");
						this.RAMht = Integer.parseInt(word);
						//Output.println("The height of RAM is " + this.RAMht);
						this.alldimensions.put("RAMht", this.RAMht);
					}
				}
			}else if(line.contains("col") && line.contains("dsp")) {
				/*
				 * <col type="dsp_top" startx="6" starty="1" repeatx="16" priority="20"/> <col
				 * type="memory" startx="2" starty="1" repeatx="16" priority="20"/>
				 */
				String[]words = line.split(" ");
				for(String word:words){
					if(word.contains("repeatx")){
						word = word.replace("\"", "");
						word = word.replace("repeatx", "");
						word = word.replace("=", "");
						word = word.replace("/", "");
						word = word.replace(">", "");
						this.DSPx = Integer.parseInt(word);
						//Output.println("The repeat frequency of DSP is " + this.DSPx);
						this.alldimensions.put("DSPx", this.DSPx);
					}else if(word.contains("startx")){
						word = word.replace("\"", "");
						word = word.replace("startx", "");
						word = word.replace("=", "");
						word = word.replace("/", "");
						word = word.replace(">", "");
						this.DSPstartx = Integer.parseInt(word);
						//Output.println("The start location of DSP is " + this.DSPstartx);
						this.alldimensions.put("DSPstartx", this.DSPstartx);
					}else if(word.contains("priority")) {
						word = word.replace("\"", "");
						word = word.replace("priority", "");
						word = word.replace("=", "");
						word = word.replace("/", "");
						word = word.replace(">", "");
						this.DSPpriority = Integer.parseInt(word);
						//Output.println("The priority of DSP is " + this.DSPpriority);
						this.alldimensions.put("DSPpriority", this.DSPpriority);
					}
				}
			}else if(line.contains("col") && line.contains("memory")) {
				String[]words = line.split(" ");
				for(String word:words){
					if(word.contains("repeatx")){
						word = word.replace("\"", "");
						word = word.replace("repeatx", "");
						word = word.replace("=", "");
						word = word.replace("/", "");
						word = word.replace(">", "");
						this.RAMx = Integer.parseInt(word);
						//Output.println("The repeat frequency of DSP is " + this.RAMx);
						this.alldimensions.put("RAMx", this.RAMx);
					}else if(word.contains("startx")){
						word = word.replace("\"", "");
						word = word.replace("startx", "");
						word = word.replace("=", "");
						word = word.replace("/", "");
						word = word.replace(">", "");
						this.RAMstartx = Integer.parseInt(word);
						//Output.println("The start location of DSP is " + this.RAMstartx);
						this.alldimensions.put("RAMstartx", this.RAMstartx);
					}else if(word.contains("priority")) {
						word = word.replace("\"", "");
						word = word.replace("priority", "");
						word = word.replace("=", "");
						word = word.replace("/", "");
						word = word.replace(">", "");
						this.RAMpriority = Integer.parseInt(word);
						//Output.println("The priority of DSP is " + this.RAMpriority);
						this.alldimensions.put("RAMpriority", this.RAMpriority);
					}
				}
			}
			/*
			 * //DSP, RAM block size private int DSPht; private int RAMht; private int
			 * DSPwt; private int RAMwt; //DSP, RAM repeat locations private int DSPx;
			 * private int RAMx;
			 */
		}
	}
	public void removeDimensions(){
		for(int i=0; i<this.lines.size();i++){
			String line = this.lines.get(i);
			if(line.contains("layout") && line.contains("width") && line.contains("height")){
				this.lines.set(i, "<layout auto=\"1.35\"/>"); //Changed
			}
		}
	}
	
	//GENERATE NETLIST SPECIFIC ARCHITECTURES
	
	public void generate_light_architecture(Set<String> usedModelsInNetlist){
		Output.println("Generate light architecture:");
		
		this.lines = this.read_file(this.simulation.getStringValue("result_folder") + this.name);
		this.initializeDimensions();
		Output.println("\tSizeX: " + this.sizeX + " | SizeY: " + this.sizeY);
		
		this.get_models();
		this.get_complex_blocks(false);
				
		//This function removes unused blocks and modes from the architecture
		HashSet<String> unremovableBlocks = new HashSet<String>();
		unremovableBlocks.add("io");
		unremovableBlocks.add("io_cell");
		unremovableBlocks.add("dell");
		unremovableBlocks.add("pad");
		unremovableBlocks.add("inpad");
		
		this.analyze_blocks(usedModelsInNetlist, unremovableBlocks);
		HashMap<String, ArrayList<String>> models = new HashMap<String, ArrayList<String>>();
		ArrayList<String> device = new ArrayList<String>();
		boolean modelLines = false;
		boolean deviceLines = false;
		for(int i=0;i<this.lines.size();i++){
			String line = this.lines.get(i);
			if(line.contains("<models>")){
				modelLines = true;
			}else if(line.contains("</models>")){
				modelLines = false;
				deviceLines = true; ///OTHER PARTS OF THE ARCHITECTURE
			}else if(line.contains("<complexblocklist>")){
				deviceLines = false;
			}else if(modelLines){
				if(line.contains("<model ")){
					Line l = new Line(line);
					if(!this.removedModels.contains(l.get_value("name"))){
						ArrayList<String> temp = new ArrayList<String>();
						temp.add(line);
						do{
							line = this.lines.get(++i);
							temp.add(line);
						}while(!line.contains("</model>"));
						models.put(l.get_value("name"), temp);
					}else{
						do{
							line = this.lines.get(++i);
						}while(!line.contains("</model>"));
					}
				}
			}else if(deviceLines){
				device.add(line);
			}
		}
		
		//LIGHT ARCH
		ArrayList<String> lightArch = new ArrayList<String>();
		lightArch.add("<?xml version=\"1.0\"?>");
		lightArch.add("<architecture>");
		lightArch.add("<models>");
		for(String m:models.keySet()){
			ArrayList<String> temp = models.get(m);
			for(int t=0;t<temp.size();t++){
				lightArch.add(temp.get(t));
			}
		}
		
		//Models for the dummy blocks
		for(Block block:this.complexBlocks){
			lightArch.addAll(block.getDummyModels());
		}
		
		lightArch.add("</models>");
		for(String d:device){
			lightArch.add(d);
		}

		//IF THIS.COMPLEX BLOCKS DOESNT CONTAIN ICELL COMB --ADD THE LINES DIRECTLY
		lightArch.add("<complexblocklist>");
		for(Block block:this.complexBlocks){
			lightArch.addAll(block.toStringList());
		//	Output.println("blocks in arch are  " + block.get_name());
			}
		
		lightArch.add("</complexblocklist>");
		lightArch.add("</architecture>");
		this.write_arch_file(lightArch,"arch.light.xml");
		
		Output.newLine();
	}
	public void generate_pack_architecture(Set<String> usedModelsInNetlist){
		Output.println("Generate pack architecture:");
		
		this.lines = this.read_file(this.simulation.getStringValue("result_folder") + this.name);
		this.initializeDimensions();
		this.removeDimensions();
		Output.println("\tSizeX: " + this.sizeX + " | SizeY: " + this.sizeY);
		
		this.get_models();
		this.get_complex_blocks(false);
		
		//This function removes unused blocks and modes from the architecture
		HashSet<String> unremovableBlocks = new HashSet<String>();
		unremovableBlocks.add("io");
		unremovableBlocks.add("io_cell");
		unremovableBlocks.add("dell");
		unremovableBlocks.add("pad");
		unremovableBlocks.add("inpad");
		
		unremovableBlocks.add("PLL");
		unremovableBlocks.add("normal");
		unremovableBlocks.add("pll_normal");
		
		unremovableBlocks.add("LAB");

		unremovableBlocks.add("DSP");
		unremovableBlocks.add("full_DSP");
		unremovableBlocks.add("half_DSP");
		unremovableBlocks.add("half_DSP_normal");
		unremovableBlocks.add("mac_mult");
		unremovableBlocks.add("dult");
		unremovableBlocks.add("mac_out");
		unremovableBlocks.add("dout");
		
		unremovableBlocks.add("M9K");
		unremovableBlocks.add("ram");
		unremovableBlocks.add("ram_block_M9K");
		unremovableBlocks.add("B9K");
		
		unremovableBlocks.add("M144K");
		unremovableBlocks.add("ram");
		unremovableBlocks.add("ram_block_M144K");
		unremovableBlocks.add("B144K");
		//Output.println("usedModelsInNetlist " + usedModelsInNetlist + " unremovableBlocks " + unremovableBlocks);
		this.analyze_blocks(usedModelsInNetlist, unremovableBlocks);
		
		//PACK PATTERNS
		boolean dspFound = false;
		for(Element element:this.complexBlocks){
			if(element.get_name().equals("DSP")){
				if(!dspFound){
					this.set_pack_pattern(element);
					dspFound = true;
				}else{
					ErrorLog.print("Two DSP complex blocks found");
				}
			}
		}
		if(!dspFound){
			Output.println("\tWarning: No DSP complex block found");
		}
		
		//MEMORY BLOCKS
		Block M9K = null;
		HashSet<String> M9KModels = new HashSet<String>();
		Block M144K = null;
		HashSet<String> M144KModels = new HashSet<String>();
		for(Block block:this.complexBlocks){
			if(block.get_name().equals("M9K")){
				if(M9K == null){
					M9K = block;
				}else{
					ErrorLog.print("Already an M9K block found");
				}
			}
			if(block.get_name().equals("M144K")){
				if(M144K == null){
					M144K = block;
				}else{
					ErrorLog.print("Already an M144K block found");
				}
			}
		}
		if(M9K != null){
			M9KModels = new HashSet<String>(M9K.get_blif_models());
		}else{
			M9KModels = new HashSet<String>();
			Output.println("\tWarning: No M9K complex block found");
		}
		if(M144K != null){
			M144KModels = new HashSet<String>(M144K.get_blif_models());
		}else{
			M144KModels = new HashSet<String>();
			Output.println("\tWarning: No M144K complex block found");
		}
		
		HashMap<String, ArrayList<String>> models = new HashMap<String, ArrayList<String>>();
		ArrayList<String> device = new ArrayList<String>();
		boolean modelLines = false;
		boolean deviceLines = false;
		for(int i=0;i<this.lines.size();i++){
			String line = this.lines.get(i);
			if(line.contains("<models>")){
				modelLines = true;
			}else if(line.contains("</models>")){
				modelLines = false;
				deviceLines = true;
			}else if(line.contains("<complexblocklist>")){
				deviceLines = false;
			}else if(modelLines){
				if(line.contains("<model ")){
					Line l = new Line(line);
					if(!this.removedModels.contains(l.get_value("name"))){
						ArrayList<String> temp = new ArrayList<String>();
						temp.add(line);
						do{
							line = this.lines.get(++i);
							temp.add(line);
						}while(!line.contains("</model>"));
						models.put(l.get_value("name"), temp);
					}else{
						
						do{
							line = this.lines.get(++i);
						}while(!line.contains("</model>"));
					}
				}
			}else if(deviceLines){
				device.add(line);
			}
		}
		
		//PACK ARCH
		ArrayList<String> packArch = new ArrayList<String>();
		packArch.add("<?xml version=\"1.0\"?>");
		packArch.add("<architecture>");
		packArch.add("<models>");
		for(String m:models.keySet()){
			ArrayList<String> temp = models.get(m);
			if(m.contains("stratixiv_ram_block") && m.contains("width")){
				if(M9KModels.contains(m)){
					packArch.add(temp.get(0).replace("\">", "") + "_M9K" + "\">");
					for(int t=1;t<temp.size();t++) packArch.add(temp.get(t));
				}
				if(M144KModels.contains(m)){
					packArch.add(temp.get(0).replace("\">", "") + "_M144K" + "\">");
					for(int t=1;t<temp.size();t++) packArch.add(temp.get(t));
				}
			}else{
				for(int t=0;t<temp.size();t++) packArch.add(temp.get(t));
			}
		}
		
		//Models for the dummy blocks
		for(Block block:this.complexBlocks){
			packArch.addAll(block.getDummyModels());
		}
		
		packArch.add("</models>");
		for(String d:device){
			packArch.add(d);
		}
		packArch.add("<complexblocklist>");
		
		if(M9K != null) M9K.modify_blif_model_names("_M9K");
		if(M144K != null) M144K.modify_blif_model_names("_M144K");
		
	
		for(Block block:this.complexBlocks){
			packArch.addAll(block.toStringList());
			//System.out.println(block.toStringList());
		}
		packArch.add("</complexblocklist>");
		packArch.add("</architecture>");
		this.write_arch_file(packArch, "arch.pack.xml");
		
		Output.newLine();
	}
	
	private void analyze_blocks(Set<String> usedModels, HashSet<String> unremovableBlocks){
		//TOP LEVEL
		ArrayList<Element> currentLevel = new ArrayList<Element>();
		ArrayList<Element> nextLevel = new ArrayList<Element>();
		
		HashSet<Element> removedBlocks = new HashSet<Element>();
		for(Element block:this.complexBlocks){
		//	Output.println("Complex block" + block.get_name());
			if(unremovableBlocks.contains(block.get_name())){
				nextLevel.add(block);
			  }else if(block.remove_block(usedModels, this.modelSet)){
				removedBlocks.add(block);
			}else{
				nextLevel.add(block);
			}
		}
		for(Element removedBlock:removedBlocks){
			this.complexBlocks.remove(removedBlock);
			this.removedModels.addAll(removedBlock.get_blif_models());
		}

		while(!nextLevel.isEmpty()){
			currentLevel = nextLevel;
			nextLevel = new ArrayList<Element>();
			for(Element block:currentLevel){
				//block.add_child("lcell_comb");
				Set<Element> children = new HashSet<Element>(block.get_children());
				
				//Output.println(" unremovableBlocks child is " + unremovableBlocks );
				for(Element child:children){
					if(unremovableBlocks.contains(child.get_name())){
						nextLevel.add(child);
						//Output.println(" child in 1st level is " + child.get_name() );
						//Output.println(" Unremovable child is " + child.get_name() );
					}else if(child.remove_block(usedModels, this.modelSet)){
						if(unremovableBlocks.contains(child.get_name())){
						nextLevel.add(child);
						}
						else {
						block.remove_child(child);
						this.removedModels.addAll(child.get_blif_models());
						}
						//Output.println("blif model" + child.get_blif_models()); REMOVES THE MODELS ONLY
					}else{
						nextLevel.add(child);
					}
				}
			}
		}
	}
	private void write_arch_file(ArrayList<String> arch, String name){
		int tabs = 0;
		try{
			BufferedWriter bw = new BufferedWriter(new FileWriter(this.simulation.getStringValue("result_folder") + name));
			for(String line:arch){
				if(line.contains("</")){
					tabs -= 1;
					
					if(line.replace("</", "").contains("<")){
						tabs += 1;
					}
				}
				bw.write(Util.tabs(tabs) + line);
				bw.newLine();
				
				if(line.contains("<") && !line.contains("</")){
					tabs += 1;
				}
				if(line.contains("/>")){
					tabs -= 1;
				}
			}
			bw.close();
		}catch(IOException ex){
			Output.println (ex.toString());
		}
	}
	
	//GET DELAY VALUES
	public boolean valid_connection(String blifModel, String sourcePortName, String sinkPortName){
		boolean validConnection = true;
		if(!this.blifBlocks.containsKey(blifModel)){
			ErrorLog.print("Blif model " + blifModel + " not found");
		}else{
			for(Block block:this.blifBlocks.get(blifModel)){
				if(!block.valid_connection(sourcePortName, sinkPortName)){
					validConnection = false;
				}
			}
		}
		return validConnection;
	}
	public int get_block_delay(String blifModel, String sourcePortName, String sinkPortName){
		int minDelay = Integer.MAX_VALUE;
		
		if(!this.blifBlocks.containsKey(blifModel)){
			ErrorLog.print("Blif model " + blifModel + " not found");
		}else{
			for(Block block:this.blifBlocks.get(blifModel)){
				if(!block.has_input_port(sourcePortName) && !block.has_clock_port(sourcePortName)){
					ErrorLog.print("Block " + blifModel + " does not have source port " + sourcePortName);
				}
				if(!block.has_output_port(sinkPortName)){
					ErrorLog.print("Block " + blifModel + " does not have sink port " + sinkPortName);
				}
				int localMinDelay = block.get_delay(sourcePortName, sinkPortName);
				if(localMinDelay < minDelay){
					minDelay = localMinDelay;
				}
			}
		}
		if(minDelay > 100000){
			Output.println("Large block delay for block " + blifModel + " between " + sourcePortName + " and " + sinkPortName + " => " + minDelay);
		}
		return minDelay;
	}
	public int get_connection_delay(P sourcePin, P sinkPin){
		//Setup time and Tcq delay are included in connection delay
		
		String source = null, sourceLight = null;
		String sink = null, sinkLight = null;
		if(sourcePin.has_block() && sinkPin.has_block()){
			//Output.println("The first condition is true ");
			sourceLight = sourcePin.get_light_architecture_name();
			sinkLight = sinkPin.get_light_architecture_name();
		}else if(sourcePin.has_block() && !sinkPin.has_block()){
			sourceLight = sourcePin.get_light_architecture_name();
			sinkLight = ".output" + "." + sinkPin.get_port_name();
		}else if(!sourcePin.has_block() && sinkPin.has_block()){
			sourceLight = ".input" + "." + sourcePin.get_port_name();
			sinkLight = sinkPin.get_light_architecture_name();
			
		}else if(!sourcePin.has_block() && !sinkPin.has_block()){
			sourceLight =".input" + "." + sourcePin.get_port_name();
			sinkLight = ".output" + "." + sinkPin.get_port_name();
			
		}
		
		//Output.println("Source light and sink light is " + sourceLight + sinkLight);
		Integer connectionDelay = null;
		//Output.println("the size of the delay map before initialisation is " + this.delayMap.size());
		HashMap<String,Integer> tempMap = this.delayMap.get(sourceLight);
		

		if(tempMap != null){
			connectionDelay = tempMap.get(sinkLight);
			if(connectionDelay == null){
				if(sourcePin.has_block() && sinkPin.has_block()){
					source = sourcePin.get_detailed_architecture_name();
					sink = sinkPin.get_detailed_architecture_name();
				}else if(sourcePin.has_block() && !sinkPin.has_block()){
					source = sourcePin.get_detailed_architecture_name();
					sink = ".output" + "." + sinkPin.get_port_name() + "[" +  sinkPin.get_pin_num() + "]";
				}else if(!sourcePin.has_block() && sinkPin.has_block()){
					source = ".input" + "." + sourcePin.get_port_name() + "[" +  sourcePin.get_pin_num() + "]";
					sink = sinkPin.get_detailed_architecture_name();
				}else if(!sourcePin.has_block() && !sinkPin.has_block()){
					source =".input" + "." + sourcePin.get_port_name() + "[" +  sourcePin.get_pin_num() + "]";
					sink = ".output" + "." + sinkPin.get_port_name() + "[" +  sinkPin.get_pin_num() + "]";
				}
				connectionDelay = this.get_connection_delay(source, sink, sourceLight, sinkLight);
				
				tempMap.put(sinkLight, connectionDelay);
			}
		}else{
			//Output.println("The temp is null");
			tempMap = new HashMap<String,Integer>();
			if(sourcePin.has_block() && sinkPin.has_block()){
				source = sourcePin.get_detailed_architecture_name();
				sink = sinkPin.get_detailed_architecture_name();
			}else if(sourcePin.has_block() && !sinkPin.has_block()){
				source = sourcePin.get_detailed_architecture_name();
				sink = ".output" + "." + sinkPin.get_port_name() + "[" +  sinkPin.get_pin_num() + "]";
			}else if(!sourcePin.has_block() && sinkPin.has_block()){
				source = ".input" + "." + sourcePin.get_port_name() + "[" +  sourcePin.get_pin_num() + "]";
				sink = sinkPin.get_detailed_architecture_name();
			}else if(!sourcePin.has_block() && !sinkPin.has_block()){
				source =".input" + "." + sourcePin.get_port_name() + "[" +  sourcePin.get_pin_num() + "]";
				sink = ".output" + "." + sinkPin.get_port_name() + "[" +  sinkPin.get_pin_num() + "]";
			}
			//Output.println("Second one is true");
			connectionDelay = this.get_connection_delay(source, sink, sourceLight, sinkLight);
			tempMap.put(sinkLight, connectionDelay);
			this.delayMap.put(sourceLight, tempMap);
		}
		return connectionDelay;
	}
	private int get_connection_delay(String sourcePinName, String sinkPinName, String sourceLight, String sinkLight){
		//Setup time and Tcq delay are included in connection delay
		if(!this.blifPins.containsKey(sourcePinName)){
			ErrorLog.print("Architecture does not contain source pin " + sourcePinName);
			return Integer.MAX_VALUE;
		}
		if(!this.blifPins.containsKey(sinkPinName)){
			ErrorLog.print("Architecture does not contain sink pin " + sinkPinName);
			return Integer.MAX_VALUE;
		}
		
		PriorityQueue<Pin> q = new PriorityQueue<Pin>();
		for(Pin p:this.pins){
			if(p.get_name().equals(sourcePinName)){
				p.set_previous(p);
				Block block = p.get_parent();
				if(block.has_clock_to_output_delay(p.get_port_name())){
					p.set_delay(block.get_clock_to_output(p.get_port_name()));
				}else{
					p.set_delay(0);
				}
			}else{
				p.set_previous(null);
				p.set_delay(Integer.MAX_VALUE);
			}
			//Output.println("The pin is " + p.get_name() + " The delay is " + p.get_delay());
			q.add(p);
		}
		//Output.println("The source pin is " + sourcePinName + "The sink pin is " + sinkPinName);
		Pin endPin = this.dijkstra(q, sinkPinName);
		int minDelay = endPin.get_delay();
		
		//GLOBAL CONNECTIONS
		boolean global = false;
		Pin sink = endPin;
		while(!sink.get_name().equals(sourcePinName)){
			Pin source = sink.get_previous();
			//Output.println("The source is " + source.get_name());
			if(this.connections.get(source.get_number()).get(sink.get_number()).is_global()){
				if(!global){
					global = true;
				}else{
					ErrorLog.print("2 global connections between " + sourcePinName + " and " + sinkPinName);
				}
			}
			sink = source;
		}
		if(!this.globalConnections.containsKey(sourceLight)){
			this.globalConnections.put(sourceLight, new HashMap<String, Boolean>());
		}
		this.globalConnections.get(sourceLight).put(sinkLight, global);
		
		return minDelay;
	}
	private Pin dijkstra(PriorityQueue<Pin> q, String stopCondition){//, String sourceType, String sinkType){
		Pin u,v;
		
		//printing the priority queue
		
        
		while(!q.isEmpty()){
			
			u = q.poll();
			//Output.println("The value of u is " + u.get_name());

			if(u.get_name().equals(stopCondition))
				{
				//Output.println("The path is " + u.printPath());
				return u;
				}
			if(u.get_delay() == Integer.MAX_VALUE)break;

			for(Map.Entry<Pin, Integer> a:u.get_neighbours().entrySet()){
				v = a.getKey();
				final int alternateDelay = u.get_delay() + a.getValue();
				if(alternateDelay < v.get_delay()){
					q.remove(v);
					v.set_delay(alternateDelay);
					v.set_previous(u);
					q.add(v);
				}
				
			}
			
		}
		
		ErrorLog.print("Pin " + stopCondition + " not found in Dijkstra algorithm");
		return null;
	}
	public int slack(P sourcePin, P sinkPin){
		int arr = sourcePin.get_arrival_time();
		int req = sinkPin.get_required_time();
		int connDelay = this.get_connection_delay(sourcePin, sinkPin);
		int slack = req - arr - connDelay;
		Output.println("The source pin is " + sourcePin.get_id() + " the sink pin is " + sinkPin.get_id() + " the arrival time is " + arr + " the required time is " + req + " the conn delay is " + connDelay + " the slack is " + slack);
		if(slack < 0){
			Output.println("Slack should be larger than zero but is equal to " + slack);
		}
		return slack;
	}
	
	public int slackSLL(P sourcePin, P sinkPin){
		int arr = sourcePin.get_arrival_time();
		int req = sinkPin.get_required_time();
		int connDelay = this.get_connection_delay(sourcePin, sinkPin);
		int slackSLL = req - arr - connDelay - 360;
		Output.println("The source pin is " + sourcePin.get_id() + " the sink pin is " + sinkPin.get_id() + " the arrival time is " + arr + " the required time is " + req + " the conn delay is " + connDelay + " the slack is " + slackSLL + " with SLL");
		if(slackSLL < 0){
			//Output.println("Slack should be larger than zero but is equal to " + slackSLL);
		}
		return slackSLL;
	}
	public boolean is_connected_via_global_connection(String sourcePinName, String sinkPinName){
		//REMOVE M9K AND M144K APPENDIX
		sourcePinName = sourcePinName.replace("_M9K","");
		sinkPinName = sinkPinName.replace("_M9K","");
		sourcePinName = sourcePinName.replace("_M144K","");
		sinkPinName = sinkPinName.replace("_M144K","");
		
		if(this.globalConnections.containsKey(sourcePinName)){
			if(this.globalConnections.get(sourcePinName).containsKey(sinkPinName)){
				return this.globalConnections.get(sourcePinName).get(sinkPinName);
			}else{
				ErrorLog.print(sinkPinName + " not found as sink of a connection");
				return false;
			}
		}else{
			Output.println("SourcePins:");
			for(String sourcePin:this.globalConnections.keySet()){
				Output.println("\t" + sourcePin);
			}
			ErrorLog.print(sourcePinName + " not found as source of a connection");
			return false;
		}
	}
	
	//PACK PATTERN
	private void set_pack_pattern(Element element){
		Timing t = new Timing();
		t.start();
		this.remove_pack_pattern(element);
		this.add_pack_pattern(element);
		t.stop();
		if(t.time() > 0.5){
			ErrorLog.print("Set pack pattern took " + t.toString());
		}
	}
	private void remove_pack_pattern(Element element){
		ArrayList<Element> work = new ArrayList<Element>();
		work.add(element);
		while(!work.isEmpty()){
			Element current = work.remove(0);
			current.remove_pack_patterns();
			for(Element child:current.get_children()){
				work.add(child);
			}
		}
	}
	private void add_pack_pattern(Element element){
		Element halfDSP = null;
		HashSet<Element> macMults = new HashSet<Element>();
		HashSet<Element> macOuts = new HashSet<Element>();
		ArrayList<Element> work = new ArrayList<Element>();
		work.add(element);
		while(!work.isEmpty()){
			Element current = work.remove(0);
			if(current.get_type().equals("mode") && current.get_name().equals("half_DSP_normal")){
				halfDSP = current;
			}else if(current.get_type().equals("pb_type")){
				if(current.get_name().equals("mac_mult")){
					for(Element child:current.get_children()){
						if(!child.get_name().equals("dult")){
							macMults.add((Mode)child);
						}else{
							Output.println("\tNo pack patterns for " + child.get_name() + " added");
						}
					}
				}else if(current.get_name().equals("mac_out")){
					for(Element child:current.get_children()){
						if(!child.get_name().equals("dout")){
							macOuts.add((Mode)child);
						}else{
							Output.println("\tNo pack patterns for " + child.get_name() + " added");
						}
					}
				}
			}
			for(Element child:current.get_children()){
				work.add(child);
			}
		}
		
		ArrayList<String[]> packPatterns = new ArrayList<String[]>();
		for(Element macMult:macMults){
			String macMultType = macMult.get_name().replace("mac_mult_", "");
			for(Element macOut:macOuts){
				String macOutType = macOut.get_name().split("\\.")[0];
				String macOutInput = macOut.get_name().split("\\.")[1].replace("input_type{", "").replace("}", "");
				String macOutOutput = macOut.get_name().split("\\.")[2].replace("output_type{", "").replace("}", "");
				String[] packPattern = {macMultType,macOutType,macOutInput,macOutOutput,""};
				packPatterns.add(packPattern);
			}
		}
		for(Element macMult:macMults){
			macMult.set_pack_pattern(packPatterns);
		}
		for(Element macOut:macOuts){
			macOut.set_pack_pattern(packPatterns);
		}
		halfDSP.set_pack_pattern(packPatterns);
	}
	
	//SIZE
	public int getSizeX(){
		return this.sizeX;
	}
	public int getSizeY(){
		return this.sizeY;
	}
	public Map<String, Integer> getFPGAdimensionsMap()
	{
	    return this.alldimensions;
	}
	
}
