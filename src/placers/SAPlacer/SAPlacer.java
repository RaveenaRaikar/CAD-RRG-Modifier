package placers.SAPlacer;

import interfaces.Logger;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import circuit.Circuit;
import circuit.architecture.BlockCategory;
import circuit.block.AbstractSite;
import circuit.block.GlobalBlock;
import circuit.exceptions.FullSiteException;
import circuit.exceptions.InvalidBlockException;
import circuit.exceptions.PlacedBlockException;
import circuit.exceptions.UnplacedBlockException;


import placers.Placer;
import visual.PlacementVisualizer;

public abstract class SAPlacer extends Placer
{

    static {
        defaultOptions.put("effort_level", "1");
        defaultOptions.put("t_multiplier", "1");

        defaultOptions.put("detailed", "0");
        defaultOptions.put("greedy", "0");
        defaultOptions.put("rlim", "-1");
        defaultOptions.put("max_rlim", "-1");

        defaultOptions.put("fix_pins", "1");
    }

    private double Rlimd;
    private int Rlim, maxRlim;
    private double temperature;

    private final double temperatureMultiplier;
    private final boolean fixPins;
    private final boolean greedy, detailed;
    private final double effortLevel;
    private final int movesPerTemperature;

    protected boolean circuitChanged = true;
    private double[] deltaCosts;

    private final double TMultiplierGlobal = 5;


    protected Random random;

    public SAPlacer(Logger logger, PlacementVisualizer visualizer, Circuit circuit, Map<String, String> options) {
        super(logger, visualizer, circuit, options);

        // Get greedy option
        this.greedy = this.parseBooleanOption("greedy");

        // Get detailed option
        this.detailed = this.parseBooleanOption("detailed");

        this.fixPins = this.parseBooleanOption("fix_pins");

        // Get inner_num option
        this.effortLevel = Double.parseDouble(this.options.get("effort_level"));
        this.movesPerTemperature = (int) (this.effortLevel * Math.pow(this.circuit.getNumGlobalBlocks(), 4.0/3.0));

        // Get T multiplier option
        this.temperatureMultiplier = Double.parseDouble(this.options.get("t_multiplier"));
    }

    protected abstract void initializePlace();
    protected abstract void initializeSwapIteration();
    protected abstract String getStatistics();
    protected abstract double getCost();
    protected abstract double getDeltaCost(Swap swap);
    protected abstract void pushThrough(int iteration);
    protected abstract void revert(int iteration);


    @Override
    public void initializeData() {
        // Get Rlim and maxRlim option
        int size = Math.max(this.circuit.getWidth(), this.circuit.getHeight());

        int optionMaxRlim = this.parseIntegerOptionWithDefault("max_rlim", size);
        int optionRlim = this.parseIntegerOptionWithDefault("rlim", size);

        // Set maxRlim first, because Rlim depends on it
        this.setMaxRlim(optionMaxRlim);
        this.setRlimd(optionRlim);
    }


    @Override
    public void place() {
        this.initializePlace();

        this.random = new Random(10);

        //Print parameters
        this.logger.logln("Effort level: " + this.effortLevel);
        this.logger.logln("Moves per temperature: " + this.movesPerTemperature);


        if(this.greedy) {
            this.doSwapIteration();

        } else {
            this.calculateInitialTemperature();

            this.logger.logln("Initial temperature: " + this.temperature);


            int iteration = 0;

            // Do placement
            while(this.temperature > 0.005 * this.getCost() / this.circuit.getNumGlobalBlocks()) {
                int numSwaps = this.doSwapIteration();
                double alpha = ((double) numSwaps) / this.movesPerTemperature;

                this.updateRlim(alpha);
                this.updateTemperature(alpha);

                this.logger.logf("Temperature %d = %.9f, Rlim = %d, %s\n",
                        iteration, this.temperature, this.Rlim, this.getStatistics());

                iteration++;
            }

            this.logger.logln("Last temp: " + this.temperature);
        }
    }


    private void calculateInitialTemperature() {
        if(this.detailed) {
            this.temperature = this.calculateInitialTemperatureDetailed();
        } else {
            this.temperature = this.calculateInitialTemperatureGlobal();
        }
    }

    private double calculateInitialTemperatureGlobal() {
        int numSamples = this.circuit.getNumGlobalBlocks();
        double stdDev = this.doSwapIteration(numSamples, false);

        return this.temperatureMultiplier * this.TMultiplierGlobal * stdDev;
    }

    private double calculateInitialTemperatureDetailed() {
        // Use the method described in "Temperature Measurement and
        // Equilibrium Dynamics of Simulated Annealing Placements"

        int numSamples = Math.max(this.circuit.getNumGlobalBlocks() / 5, 500);
        this.doSwapIteration(numSamples, false);

        Arrays.sort(this.deltaCosts);

        int zeroIndex = Arrays.binarySearch(this.deltaCosts, 0);
        if(zeroIndex < 0) {
            zeroIndex = -zeroIndex - 1;
        }

        double Emin = integral(this.deltaCosts, 0, zeroIndex, 0);
        double maxEplus = integral(this.deltaCosts, zeroIndex, numSamples, 0);

        if(maxEplus < Emin) {
            this.logger.raise("SA failed to get a temperature estimate");
        }

        double minT = 0;
        double maxT = Double.MAX_VALUE;

        // very coarse estimate
        double temperature = this.deltaCosts[this.deltaCosts.length - 1] / 1000;

        while(minT == 0 || maxT / minT > 1.1) {
            double Eplus = integral(this.deltaCosts, zeroIndex, numSamples, temperature);

            if(Emin < Eplus) {
                if(temperature < maxT) {
                    maxT = temperature;
                }

                if(minT == 0) {
                    temperature /= 8;
                } else {
                    temperature = (maxT + minT) / 2;
                }

            } else {
                if(temperature > minT) {
                    minT = temperature;
                }

                if(maxT == Double.MAX_VALUE) {
                    temperature *= 8;
                } else {
                    temperature = (maxT + minT) / 2;
                }
            }
        }

        return temperature * this.temperatureMultiplier;
    }

    private double integral(double[] values, int start, int stop, double temperature) {
        double sum = 0;
        for(int i = start; i < stop; i++) {
            if(temperature == 0) {
                sum += values[i];
            } else {
                sum += values[i] * Math.exp(-values[i] / temperature);
            }
        }

        return Math.abs(sum / values.length);
    }



    private int doSwapIteration() {
        return (int) this.doSwapIteration(this.movesPerTemperature, true);
    }

    private double doSwapIteration(int moves, boolean pushThrough) {

        this.initializeSwapIteration();

        int numSwaps = 0;


        double sumDeltaCost = 0;
        double quadSumDeltaCost = 0;
        if(!pushThrough) {
            this.deltaCosts = new double[moves];
        }


        for (int i = 0; i < moves; i++) {
            Swap swap = this.findSwap(this.Rlim);

            if((swap.getBlock1() == null || !swap.getBlock1().isFixed())
                    && (swap.getBlock2() == null || !swap.getBlock2().isFixed())) {

                double deltaCost = this.getDeltaCost(swap);


                if(pushThrough) {
                    if(deltaCost <= 0 || (this.greedy == false && this.random.nextDouble() < Math.exp(-deltaCost / this.temperature))) {

                        try {
                            swap.apply();
                        } catch(UnplacedBlockException | InvalidBlockException | PlacedBlockException | FullSiteException error) {
                            this.logger.raise(error);
                        }
                        numSwaps++;

                        this.pushThrough(i);
                        this.circuitChanged = true;

                    } else {
                        this.revert(i);
                    }

                } else {
                    this.revert(i);
                    this.deltaCosts[i] = deltaCost;
                    sumDeltaCost += deltaCost;
                    quadSumDeltaCost += deltaCost * deltaCost;
                }
            }
        }

        if(pushThrough) {
            return numSwaps;

        } else {
            double sumQuads = quadSumDeltaCost;
            double quadSum = sumDeltaCost * sumDeltaCost;

            double numBlocks = this.circuit.getNumGlobalBlocks();
            double quadNumBlocks = numBlocks * numBlocks;

            return Math.sqrt(Math.abs(sumQuads / numBlocks - quadSum / quadNumBlocks));
        }
    }



    protected Swap findSwap(int Rlim) {
        GlobalBlock fromBlock = null;
        AbstractSite toSite = null;
        do {
            // Find a suitable block
            do {
                fromBlock = this.circuit.getRandomBlock(this.random);
            } while(this.isFixed(fromBlock));

            // Find a suitable site near this block
            do {
                toSite = this.circuit.getRandomSite(fromBlock, Rlim, this.random);
            } while(toSite != null && fromBlock.getSite() == toSite);

            // If toSite == null, this means there are no suitable blocks near the block
            // Try another block
        } while(toSite == null);

        Swap swap = new Swap(fromBlock, toSite, this.random);
        return swap;
    }

    private boolean isFixed(GlobalBlock block) {
        // Only IO blocks are fixed, if fixPins option is true
        return this.fixPins && block.getCategory() == BlockCategory.IO;
    }



    protected final void updateTemperature(double alpha) {
        double gamma;

        if (alpha > 0.96) {
            gamma = 0.5;
        } else if (alpha > 0.8) {
            gamma = 0.9;
        } else if (alpha > 0.15) {
            gamma = 0.95;
        } else {
            gamma = 0.8;
        }

        this.temperature *= gamma;
    }


    protected final int getRlim() {
        return this.Rlim;
    }
    protected final double getRlimd() {
        return this.Rlimd;
    }

    protected final void setMaxRlim(int maxRlim) {
        this.maxRlim = maxRlim;
    }
    protected final void setRlimd(double Rlimd) {
        this.Rlimd = Rlimd;
        this.Rlim = (int) Math.round(this.Rlimd);
    }


    protected final void updateRlim(double alpha) {
        this.updateRlim(alpha, this.maxRlim);
    }

    protected final void updateRlim(double alpha, int maxValue) {
        double newRlimd = this.Rlimd * (1 - 0.44 + alpha);

        if(newRlimd > maxValue) {
            newRlimd = maxValue;
        }

        if(newRlimd < 1) {
            newRlimd = 1;
        }

        this.setRlimd(newRlimd);
    }
}