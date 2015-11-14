package interfaces;

import interfaces.Logger.Stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import util.Pair;


class CLIOptions extends OptionsManager {

    private List<String> args;


    public CLIOptions(Logger logger) {
        super(logger);
    }

    void parseArguments(String[] argArray) {

        // First create a logger
        this.args = this.createLogger(Arrays.asList(argArray));

        // Check if a help argument is provided
        int helpArgIndex = Math.max(this.args.indexOf("-h"), this.args.indexOf("--help"));
        if(helpArgIndex > 0) {
            this.printHelp(Stream.OUT);
            System.exit(0);
        }


        // Get the start positions of the options for the different placers
        List<Pair<String, Integer>> placersAndArgIndexes = this.buildPlacerList();

        Options mainOptions = this.getMainOptions();
        this.parseArguments(0, placersAndArgIndexes.get(0).getValue(), mainOptions);

        int numPlacers = placersAndArgIndexes.size() - 1;
        for(int placerIndex = 0; placerIndex < numPlacers; placerIndex++) {
            String placerName = placersAndArgIndexes.get(placerIndex + 1).getKey();
            Options placerOptions = this.getDefaultOptions(placerName);

            int argIndexStart = placersAndArgIndexes.get(placerIndex).getValue() + 2;
            int argIndexEnd = placersAndArgIndexes.get(placerIndex + 1).getValue();

            this.parseArguments(
                    argIndexStart,
                    argIndexEnd,
                    placerOptions);

            this.addPlacer(placerName, placerOptions);
        }
    }

    private List<String> createLogger(List<String> argsWithLogger) {
        //TODO: provide an option to log to a file
        return argsWithLogger;
    }

    private List<Pair<String, Integer>> buildPlacerList() {
        List<Pair<String, Integer>> placers = new ArrayList<Pair<String, Integer>>();
        String previousPlacer = "main";

        int numArgs = this.args.size();
        for(int argIndex = 0; argIndex < numArgs; argIndex++) {
            if(this.args.get(argIndex).equals("--placer")) {

                String placer = this.getArgValue(argIndex);
                if(placer == null) {
                    this.printError(argIndex);
                }

                placers.add(new Pair<String, Integer>(previousPlacer, argIndex));
                previousPlacer = placer;
            }
        }

        placers.add(new Pair<String, Integer>(previousPlacer, numArgs));

        return placers;
    }

    private String getArgValue(int argIndex) {
        if(argIndex == this.args.size() - 1) {
            return null;
        }

        if(this.args.get(argIndex + 1).substring(0, 1).equals("-")) {
            return null;
        }

        return this.args.get(argIndex + 1);
    }

    private void parseArguments(int start, int end, Options options) {
        int argIndex = start;

        for(String optionName : options.keySet()) {
            if(options.isRequired(optionName)) {
                String optionValue = this.args.get(argIndex);
                options.set(optionName, optionValue);
                argIndex++;
            }
        }

        while(argIndex < end) {
            String argName = this.args.get(argIndex).substring(2).replace("_", " ");
            String argValue = this.getArgValue(argIndex);

            // The argument is boolean valued, and should be set to 1
            if(argValue == null) {
                options.set(argName, "1");
                argIndex += 1;

            // The argument is differently valued, let the OptionList parse it
            } else {
                options.set(argName, argValue);
                argIndex += 2;
            }
        }
    }



    private void printError(int argIndex) {

        Stream stream = Stream.ERR;

        String argValue = this.args.get(argIndex);
        this.logger.printf(stream, "Incorrect usage of the option \"%s\" at position %d\n\n", argValue, argIndex);

        this.printHelp(stream);

        System.exit(1);
    }
    private void printHelp(Stream stream) {
        Options mainOptions = this.getMainOptions();

        this.logger.print(stream, "usage: interfaces.CLI");
        this.printRequiredArguments(stream, mainOptions);
        this.logger.println(stream, " [options]\n");

        this.logger.println(stream, "General options:");
        this.printOptionalArguments(stream, mainOptions);


        for(String placerName : this.placerFactory.placers()) {
            Options placerOptions = this.placerFactory.initOptions(placerName);

            this.logger.println(stream, "--placer " + placerName);
            this.printRequiredArguments(stream, placerOptions);

            this.printOptionalArguments(stream, placerOptions);
        }
    }

    private void printRequiredArguments(Stream stream, Options options) {
        for(String optionName : options.keySet()) {
            if(options.isRequired(optionName)) {
                this.logger.print(stream, " " + optionName.replace(" ", "_"));
            }
        }
    }

    private void printOptionalArguments(Stream stream, Options options) {
        int maxLength = options.getMaxNameLength();

        String format = String.format("  --%%-%ds   %%s", maxLength);
        for(String optionName : options.keySet()) {
            if(!options.isRequired(optionName)) {
                String formattedName = optionName.replace(" ", "_");
                String optionDescription = options.getDescription(optionName);

                this.logger.printf(stream, format, formattedName, optionDescription);

                Object defaultValue = options.get(optionName);
                if(defaultValue != null) {
                    this.logger.print(" (default: " + defaultValue.toString() + ")");
                }
                this.logger.println();
            }
        }

        this.logger.println();
    }
}
