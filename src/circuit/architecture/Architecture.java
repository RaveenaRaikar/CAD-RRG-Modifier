package circuit.architecture;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import circuit.exceptions.InvalidFileFormatException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Architecture implements Serializable {

    private static final long serialVersionUID = -5436935126902935000L;

    private static final double FILL_GRADE = 1;

    private File architectureFile, architectureFileVPR, blifFile, netFile;
    private String circuitName;

    private DelayTables delayTables;
    private transient JSONObject blockDefinitions;

    private int ioCapacity;


    public Architecture(String circuitName, File architectureFile, File architectureFileVPR, File blifFile, File netFile) {
        this.architectureFile = architectureFile;
        this.architectureFileVPR = architectureFileVPR;

        this.blifFile = blifFile;
        this.netFile = netFile;

        this.circuitName = circuitName;
    }

    @SuppressWarnings("unchecked")
    public void parse() throws IOException, InvalidFileFormatException, InterruptedException {

        // Read the entire file
        BufferedReader reader = new BufferedReader(new FileReader(this.architectureFile));

        String content = "";
        String line;
        while((line = reader.readLine()) != null) {
            content += line;
        }

        reader.close();


        // Parse the JSONObject
        JSONObject jsonContent = (JSONObject) JSONValue.parse(content);
        this.blockDefinitions = (JSONObject) jsonContent.get("blocks");


        // Set the IO capacity
        this.ioCapacity = (int) (long) jsonContent.get("io_capacity");


        // Add all the block types
        this.addBlockTypes();

        // Get all the delays
        this.processDelays((Map<String, Double>) jsonContent.get("delays"));

        // Build the delay matrixes
        this.buildDelayMatrixes();
    }


    private void addBlockTypes() {

        @SuppressWarnings("unchecked")
        Set<String> blockTypes = this.blockDefinitions.keySet();

        for(String typeName : blockTypes) {
            JSONObject definition = this.getDefinition(typeName);

            // Get some general info
            boolean isGlobal = definition.containsKey("globalCategory");
            boolean isLeaf = (boolean) definition.get("leaf");

            String category;
            if(isGlobal) {
                category = (String) definition.get("globalCategory");
            } else if(isLeaf) {
                category = "leaf";
            } else {
                category = "local";
            }

            boolean isClocked = false;
            if(isLeaf) {
                isClocked = (boolean) definition.get("clocked");
            }

            int height = 1, start = 1, repeat = 1;
            if(category.equals("hardblock")) {
                height = (int) (long) definition.get("height");
                start = (int) (long) definition.get("start");
                repeat = (int) (long) definition.get("repeat");
            }



            // Get the port counts
            @SuppressWarnings("unchecked")
            Map<String, JSONObject> ports = (Map<String, JSONObject>) definition.get("ports");

            Map<String, Integer> inputs = castIntegers(ports.get("input"));
            Map<String, Integer> outputs = castIntegers(ports.get("output"));



            // Get the modes and children
            List<String> modes = new ArrayList<String>();
            List<Map<String, Integer>> children = new ArrayList<Map<String, Integer>>();

            // If the block is a leaf: there are no modes, the only mode is unnamed
            if(isLeaf) {
                modes.add("");
                children.add(this.getChildren(definition));


            // There is only one mode, but we have to name it like the block for some reason
            } else if(!definition.containsKey("modes")) {
                modes.add(typeName);
                children.add(this.getChildren(definition));


            // There are multiple modes
            } else {
                JSONObject modeDefinitions = (JSONObject) definition.get("modes");

                @SuppressWarnings("unchecked")
                Set<String> modeNames = modeDefinitions.keySet();

                for(String mode : modeNames) {
                    modes.add(mode);
                    children.add(this.getChildren((JSONObject) modeDefinitions.get(mode)));
                }
            }

            BlockTypeData.getInstance().addType(typeName, category, height, start, repeat, isClocked, inputs, outputs);

            for(int i = 0; i < modes.size(); i++) {
                BlockTypeData.getInstance().addMode(typeName, modes.get(i), children.get(i));
            }
        }

        BlockTypeData.getInstance().postProcess();
    }

    private JSONObject getDefinition(String blockType) {
        return (JSONObject) this.blockDefinitions.get(blockType);
    }

    private Map<String, Integer> getChildren(JSONObject subDefinition) {
        return castIntegers((JSONObject) subDefinition.get("children"));
    }

    private Map<String, Integer> castIntegers(JSONObject subDefinition) {
        @SuppressWarnings("unchecked")
        Set<String> keys = (Set<String>) subDefinition.keySet();

        Map<String, Integer> newSubDefinition = new HashMap<String, Integer>();

        for(String key : keys) {
            int value = (int) (long) subDefinition.get(key);
            newSubDefinition.put(key, value);
        }

        return newSubDefinition;
    }

    private void processDelays(Map<String, Double> delays) {
        Pattern keyPattern = Pattern.compile("(?<sourceBlock>[^.-]+)(\\.(?<sourcePort>[^-]+))?-(?<sinkBlock>[^.-]+)(\\.(?<sinkPort>.+))?");

        for(Map.Entry<String, Double> delayEntry : delays.entrySet()) {
            String key = delayEntry.getKey();
            Double delay = delayEntry.getValue();

            if(key.equals("clock_setup_time")) {
                PortTypeData.getInstance().setClockSetupTime(delay);
                continue;
            }


            Matcher matcher = keyPattern.matcher(key);
            matcher.matches();




            String sourceBlockName = matcher.group("sourceBlock");
            String sourcePortName = matcher.group("sourcePort");
            String sinkBlockName = matcher.group("sinkBlock");
            String sinkPortName = matcher.group("sinkPort");

            if(sourcePortName == null) {
                PortType portType = new PortType(sinkBlockName, sinkPortName);
                portType.setSetupTime(delay);

            } else if(sinkPortName == null) {
                PortType portType = new PortType(sourceBlockName, sourcePortName);
                portType.setSetupTime(delay);

            } else {
                PortType sourcePortType = new PortType(sourceBlockName, sourcePortName);
                PortType sinkPortType = new PortType(sinkBlockName, sinkPortName);
                sourcePortType.setDelay(sinkPortType, delay);
            }
        }
    }


    private void buildDelayMatrixes() throws IOException, InvalidFileFormatException, InterruptedException {
        // For this method to work, the macro PRINT_ARRAYS should be defined
        // in vpr: place/timing_place_lookup.c


        // Run vpr
        String command = String.format(
                "./vpr %s %s --blif_file %s --net_file %s --place_file vpr_tmp --place --init_t 1 --exit_t 1",
                this.architectureFileVPR, this.circuitName, this.blifFile, this.netFile);

        Process process = null;
        process = Runtime.getRuntime().exec(command);


        // Read output to avoid buffer overflow and deadlock
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        while ((reader.readLine()) != null) {}
        process.waitFor();


        // Parse the delay tables
        File delaysFile = new File("lookup_dump.echo");
        this.delayTables = new DelayTables(delaysFile);
        this.delayTables.parse();

        // Clean up
        this.deleteFile("vpr_tmp");
        this.deleteFile("vpr_stdout.log");
        this.deleteFile("lookup_dump.echo");
    }

    private void deleteFile(String path) throws IOException {
        Files.deleteIfExists(new File(path).toPath());
    }


    public DelayTables getDelayTables() {
        return this.delayTables;
    }



    public int getIoCapacity() {
        return this.ioCapacity;
    }

    public double getFillGrade() {
        return Architecture.FILL_GRADE;
    }


    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(BlockTypeData.getInstance());
        out.writeObject(PortTypeData.getInstance());
    }

    private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
        in.defaultReadObject();
        BlockTypeData.setInstance((BlockTypeData) in.readObject());
        PortTypeData.setInstance((PortTypeData) in.readObject());
    }
}
