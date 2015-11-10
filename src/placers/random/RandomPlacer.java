package placers.random;

import interfaces.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import circuit.Circuit;
import circuit.architecture.BlockType;
import circuit.architecture.BlockCategory;
import circuit.block.AbstractBlock;
import circuit.block.AbstractSite;
import circuit.block.GlobalBlock;
import circuit.exceptions.FullSiteException;
import circuit.exceptions.PlacedBlockException;

import placers.Placer;
import visual.PlacementVisualizer;

public class RandomPlacer extends Placer {

    private static final String name = "Random Placer";

    static {
        defaultOptions.put("categories", "");
    }

    private final Set<BlockCategory> categories = new HashSet<>();

    public RandomPlacer(Logger logger, PlacementVisualizer visualizer, Circuit circuit, Map<String, String> options) {
        super(logger, visualizer, circuit, options);

        String categoriesString = this.options.get("categories");
        Set<String> categoriesStrings = new HashSet<>(Arrays.asList(categoriesString.split(",")));
        if(categoriesString.length() != 0) {
            for(BlockCategory category : BlockCategory.values()) {
                if(categoriesStrings.contains(category.toString())) {
                    this.categories.add(category);
                }
            }
        }
    }

    @Override
    public void initializeData() {
        // Do nothing
    }

    @Override
    public void place() {
        Random random = new Random(100);

        List<BlockType> blockTypes = this.circuit.getGlobalBlockTypes();
        for(BlockType blockType : blockTypes) {

            if(this.categories.size() > 0 && !this.categories.contains(blockType.getCategory())) {
                continue;
            }

            // Get all possible blocks and sites for this type
            List<AbstractBlock> blocks = this.circuit.getBlocks(blockType);
            List<AbstractSite> sites = this.circuit.getSites(blockType);

            // Permutate sites
            Collections.shuffle(sites, random);

            // Assign each block to a site
            int siteIndex = 0;
            for(AbstractBlock abstractBlock : blocks) {
                AbstractSite site = sites.get(siteIndex);
                GlobalBlock block = (GlobalBlock) abstractBlock;

                try {
                    block.setSite(site);
                } catch(PlacedBlockException | FullSiteException error) {
                    this.logger.raise(error);
                }

                siteIndex++;
            }
        }
    }

    @Override
    public String getName() {
        return RandomPlacer.name;
    }
}
