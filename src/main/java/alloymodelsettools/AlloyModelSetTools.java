/*
 * Catalyst -- A framework for performance analysis/optimization of Alloy models
 * Copyright (C) 2018-2019 Amin Bandali
 * Modified 2021 Nancy Day to become AlloyModelSetTools
 *
 * This file is part of Catalyst.
 *
 * Catalyst is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Catalyst is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Catalyst.  If not, see <https://www.gnu.org/licenses/>.
 */

package alloymodelsettools;

import java.io.*;

import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import edu.mit.csail.sdg.ast.Module;
import edu.mit.csail.sdg.parser.CompUtil;

import static java.lang.Math.min;

public class AlloyModelSetTools {
    // Users set these options.
    // You can choose to gather from Github repositories or from existing model sets or both.
    static boolean gatherFromGithub = true;
    // The max number of repos to clone, -1 for downloading all github repos
    static int num_git_repos = 5;
    static boolean gatherFromExistingModelSets = true;
    // List of existing model sets directories to draw from, paths can either be relative or absolute,  relative path
    // are expected to be relative to "alloy-model-sets/". One example: "model-sets/2021-05-07-14-22-48".
    static String[] existing_model_sets = {};
    static boolean downloadPlatinumModelSet = false;
    // Whether to remove non-Alloy files, note that hidden files will also be removed.
    static boolean removeNonAlloyFiles = true;
    // Whether to remove Alloy utility models:
    // boolean.als, integer.als, ordering.als, seqrel.als, sequniv.als, time.als, graph.als, natural.als, relation
    // .als, sequence.als, ternary.als
    static boolean removeUtilModels = true;
    static boolean removeDuplicateFiles = true;
    static boolean removeMultipleVersion = true;
    static boolean removeDoNotParse = true;
    // Remove files with common file names to avoid extracting models with high similarity, like those in Jackson's
    // book.
    static boolean hitlistFilter = true;
    static String jackson_model_dir = "2021-05-25-13-24-28-jackson";
    static String[] jackson_model_names = {"abstractMemory", "addressBook", "barbers", "cacheMemory", "checkCache",
            "checkFixedSize", "closure", "distribution", "filesystem", "fixedSizeMemory", "grandpa", "hotel", "lights",
            "lists", "mediaAssets", "phones", "prison", "properties", "ring", "sets", "spanning", "tree", "tube", "undirected"};
    // Put additional file names (other than Jackson's) you also want to filter on here. For each name, only one
    // model containing it will be kept. For example, "ownGrandpa".
    static String[] additional_common_file_names = {};
    // You don't need to change anything after this line

    // static variables
    static Random randomGenerator = new Random();
    static FileWriter readmefile;
    static String dirname;
    static HashMap<String, List<File>> alsFileNames = new HashMap<>();
    static int numAlsFiles = 0;
    static int numFilesFromExisting = 0;
    static int numFilesRemoved = 0;
    static HashSet<String> files_encountered = new HashSet<>();
    static Logger logger;
    // stdio is used for error output


    static Integer CreateModelSetDir() {
        try {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
            LocalDateTime now = LocalDateTime.now();
            dirname = "model-sets/" + dtf.format(now);
            String readmefilename = dirname + "/" + "README.md";

            File f = new File(readmefilename);
            f.getParentFile().mkdirs();
            readmefile = new FileWriter(readmefilename);
            readmefile.write("Model set created: " + dirname + "\n");

            // Set up logger
            logger = Logger.getLogger("MyLog");
            FileHandler fh;
            // Configure the logger with handler and formatter
            fh = new FileHandler(dirname + "/log.txt", true);
            logger.addHandler(fh);
            System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tc %2$s%n%4$s: %5$s%6$s%n");
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
            // Print out $machine info
            logger.info(util.getSystemInfo());
            return 0;
        } catch (Exception e) {
            logger.warning("An error occurred creating the model set directory and/or setting up the logger.");
            return 1;
        }
    }


    public static void GetAllDuplicateFiles(File[] files) {
        for (File file : files) {
            if (file.isDirectory()) {
                GetAllDuplicateFiles(file.listFiles());
            } else {
                if (!alsFileNames.containsKey(file.getName())) {
                    alsFileNames.put(file.getName(), new ArrayList<>());
                }
                alsFileNames.get(file.getName()).add(file);
            }
        }
    }


    public static void RemoveDuplicateFiles(String dirname) {
        // Get all files with duplicate names
        for (File f : new File(dirname).listFiles()) {
            if (f.isDirectory()) {
                GetAllDuplicateFiles(f.listFiles());
            }
        }
        for (List<File> files : alsFileNames.values()) {
            if (files.size() > 1) {
                // Look at the file size
                HashMap<Long, List<File>> fileSizes = new HashMap<>();
                for (File f : files) {
                    long file_size = f.length();
                    if (!fileSizes.containsKey(file_size)) {
                        fileSizes.put(file_size, new ArrayList<>());
                    }
                    fileSizes.get(file_size).add(f);
                }
                for (List<File> fs : fileSizes.values()) {
                    if (fs.size() > 1) {
                        // Randomly select one to keep, and delete the other ones
                        int index = randomGenerator.nextInt(fs.size());
                        for (int i = 0; i < fs.size(); i++) {
                            if (i != index) {
                                numFilesRemoved++;
                                logger.info("Duplicate: " + fs.get(i).getPath());
                                if (!fs.get(i).delete()) {
                                    logger.warning("Abnormal Behaviour! Something bad happened when deleting duplicate files.");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Returns the prefix of a string until a number or a ".als";
    public static String prefixOf(String str) {
        int i = 0;
        while (i < str.length() && !Character.isDigit(str.charAt(i))) i++;
        // If it starts with a number, return the entire string as prefix so that it won't be able to match anything
        if (i == 0) return str;
        i = min(i, str.split(".als", 2)[0].length());
        return str.substring(0, i);
    }

    // Remove multiple version files in one directory
    public static void RemoveMultipleVersionInDirectory(File file) {
        // Sort all file names in alphabetical order
        List<String> f_names = new ArrayList<String>();
        for (File f : file.listFiles()) {
            if (!f.isDirectory()) {
                f_names.add(f.getName());
            }
        }
        Collections.sort(f_names);
        // Iterate through the sorted list.
        // If it has the same prefix as previous file, discard the previous one;
        // Otherwise, make this new prefix what we are comparing against.
        if (!f_names.isEmpty()) {
            String prefix = f_names.get(0) + "not";
            String prev = f_names.get(0);
            for (String f_name : f_names) {
                if (prefixOf(f_name).equals(prefix)) {
                    logger.info(file.getPath() + "/" + prev + " removed by the multiple version filter.");
                    if (!new File(file.getPath() + "/" + prev).delete()) {
                        logger.warning("Abnormal Behaviour! Something bad happened when deleting files do not parse.");
                    }
                } else {
                    prefix = prefixOf(f_name);
                }
                prev = f_name;
            }
        }
    }


    public static void RemoveMultipleVersion(File[] files) {
        // Get all files with duplicate names
        for (File f : files) {
            if (f.isDirectory()) {
                RemoveMultipleVersion(f.listFiles());
                RemoveMultipleVersionInDirectory(f);
            }
        }
    }

    public static void RemoveDoNotParse(File[] files) {
        for (File file : files) {
            if (file.isDirectory()) {
                RemoveDoNotParse(file.listFiles());
                // Calls  same method again.
            } else {
                // Parse+typecheck the model
                logger.info("=========== Parsing+Typechecking " + file.getPath() + " =============");
                try {
                    Module world = CompUtil.parseEverything_fromFile(null, null, file.getPath());

                } catch (Exception e) {
                    logger.log(Level.INFO, e.getMessage(), e);
                    logger.info(file.getPath() + " do not parse");
                    numFilesRemoved++;
                    if (!file.delete()) {
                        logger.warning("Abnormal Behaviour! Something bad happened when deleting files do not parse.");
                    }
                    alsFileNames.remove(file.getName());
                }
            }
        }
    }

    public static void HitlistFilter(File[] files) {
        for (File file : files) {
            if (file.isDirectory()) {
                HitlistFilter(file.listFiles());
            } else {
                String fname = file.getName();
                if (Arrays.stream(jackson_model_names).anyMatch(fname::contains)) {
                    // if it is a filename in Jackson's original repo we discard this file
                    if (!file.delete()) {
                        logger.warning("Abnormal Behaviour! Something bad happened when deleting files in hitlist.");
                    }
                    logger.info(file.getPath() + " removed by the hitlist filter");
                    numFilesRemoved++;
                } else {
                    // if it is not a filename in Jackson's original repo, keep the first one we encounter and then no more of that name on the hitlist
                    Optional<String> common_name = Arrays.stream(additional_common_file_names).filter(fname::contains).findFirst();
                    if (common_name.isPresent()) {
                        if (files_encountered.contains(common_name.get())) {
                            if (!file.delete()) {
                                logger.warning("Abnormal Behaviour! Something bad happened when deleting files in hitlist.");
                            }
                            logger.info(file.getPath() + " removed by the hitlist filter");
                            numFilesRemoved++;
                        } else {
                            files_encountered.add(common_name.get());
                        }
                    }
                }
            }
        }
    }

    static Integer CleanUpFiles() {
        String cleanUpCommands = "";
        if (removeNonAlloyFiles) {
            cleanUpCommands += "rm -rf .*\n"; // remove hidden files
            cleanUpCommands += "find . -mindepth 2 -depth -type f ! -name '*.als' -delete\n";
        }
        if (removeUtilModels)
            cleanUpCommands += "find . -depth -type f \\( -name 'boolean.als' -o -name 'graph.als' -o -name 'integer.als' -o -name 'natural.als' -o -name 'ordering.als' -o -name 'relation.als' -o -name 'seqrel.als' -o -name 'sequence.als' -o -name 'sequniv.als' -o -name 'ternary.als' -o -name 'time.als' \\) -print -delete | wc -l | tr -d ' ' | awk '{print \"Number of removed files: \"$1}'\n";
        cleanUpCommands += "find . -type d -empty -delete\n"; // remove empty folders

        int numUtilFiles = 0;
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", cleanUpCommands);
        pb.directory(new File(dirname));
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Number of removed files:")) {
                    numUtilFiles = Integer.parseInt(line.split(" ")[4]);
                }
                logger.info(line);
            }
            reader.close();
            int exitVal = process.waitFor();
            if (exitVal != 0) {
                logger.warning("Abnormal Behaviour! Something bad happened when cleaning up files.");
                return 1;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return 1;
        }

        if (removeUtilModels) {
            try {
                readmefile.write("Removed " + numUtilFiles + " util files" + "\n");
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                return 1;
            }
        }

        if (removeDuplicateFiles) {
            numFilesRemoved = 0;
            RemoveDuplicateFiles(dirname);
            try {
                readmefile.write("Removed " + numFilesRemoved + " duplicate files" + "\n");
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                return 1;
            }
        }

        if (removeMultipleVersion) {
            numFilesRemoved = 0;
            RemoveMultipleVersion(new File(dirname).listFiles());

            try {
                readmefile.write("Removed " + numFilesRemoved + " files that " +
                        "might be an earlier version of another file." + "\n");
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                return 1;
            }
        }

        if (removeDoNotParse) {
            numFilesRemoved = 0;
            for (File f : new File(dirname).listFiles()) {
                if (f.isDirectory()) {
                    RemoveDoNotParse(f.listFiles());
                }
            }

            try {
                readmefile.write("Removed " + numFilesRemoved + " files that " +
                        "do not parse." + "\n");
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                return 1;
            }
        }

        if (hitlistFilter) {
            numFilesRemoved = 0;
            for (File f : new File(dirname).listFiles()) {
                // Skip the directory containing jackson's original models
                if (f.isDirectory()) {
                    HitlistFilter(f.listFiles());
                }
            }

            try {
                readmefile.write("Removed " + numFilesRemoved + " files whose name is in hitlist." + "\n");
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                return 1;
            }
        }

        // remove empty folders
        pb = new ProcessBuilder("bash", "-c", "find . -type d -empty -delete\n");
        pb.directory(new File(dirname));
        pb.inheritIO();
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            int exitVal = process.waitFor();
            if (exitVal != 0) {
                logger.warning("Abnormal Behaviour! Something bad happened when cleaning up files.");
                return 1;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return 1;
        }

        return 0;
    }

    static void CountFiles(File[] files, boolean isFromExisting) {
        for (File file : files) {
            if (file.isDirectory()) {
                CountFiles(file.listFiles(), isFromExisting);
            } else {
                numAlsFiles++;
                if (isFromExisting) numFilesFromExisting++;
            }
        }
    }

    static void printNumOfFiles() {
        numAlsFiles = 0;
        HashSet<String> existing_model_sets_name = new HashSet<>();
        for (String path : existing_model_sets) {
            existing_model_sets_name.add(Paths.get(path).getFileName().toString());
        }
        for (File f : new File(dirname).listFiles()) {
            if (f.isDirectory()) {
                CountFiles(f.listFiles(), existing_model_sets_name.contains(f.getName()));
            }
        }

        try {
            String outputCount = "Total " + numAlsFiles + " .als " +
                    "files.\n";

            if (gatherFromGithub && gatherFromExistingModelSets) {
                outputCount += numAlsFiles - numFilesFromExisting + " " +
                        ".als files drawn from " + num_git_repos + " github " +
                        "repos and " + numFilesFromExisting + " .als files " +
                        "drawn from " + existing_model_sets.length + " " +
                        "existing model set directories.";
            } else if (gatherFromGithub) {
                outputCount += numAlsFiles - numFilesFromExisting + " " +
                        ".als files drawn from " + num_git_repos + " github " +
                        "repos.";
            } else if (gatherFromExistingModelSets) {
                outputCount += numFilesFromExisting + " .als files " +
                        "drawn from " + existing_model_sets.length + " " +
                        "existing model set directories.";
            }
            readmefile.write(outputCount + "\n");
        } catch (Exception e) {
            logger.warning("Failed to write file count to readme file");
        }
    }

    public static void main(String[] args) {
        // Start a new model set directory
        // expects to be run in root of alloy-models-sets directory
        if (CreateModelSetDir() == 1) {
            return;
        }

        if (gatherFromGithub) {
            // Gather from github
            if (util.GatherFromGithub(num_git_repos, logger, dirname, readmefile) == 1) {
                logger.warning("Failed to gather models from github");
                return;
            }
        }

        if (downloadPlatinumModelSet) {
            // Gather from github
            if (util.DownloadPlatinumFromGoogleDrive(logger, dirname, readmefile) == 1) {
                logger.warning("Failed to download Platinum model set from Google Drive");
                return;
            }
        }

        // Remove non-Alloy files, Alloy util/library models and duplicate models if options set
        if (CleanUpFiles() == 1) {
            logger.warning("Failed to remove not needed files");
            return;
        }

        // Gather from existing models-sets
        if (gatherFromExistingModelSets) {
            if (util.GatherFromExistingModelSets(existing_model_sets, logger, dirname, readmefile) == 1) {
                logger.warning("Failed to gather models from existing model sets");
                return;
            }
            // Remove non-Alloy files
            if (removeNonAlloyFiles) {
                if (util.RemoveNonAlloyFiles(logger, dirname) == 1) {
                    logger.warning("Failed to remove non-alloy files.");
                    return;
                }
            }
        }

        printNumOfFiles();

        try {
            readmefile.close();
        } catch (Exception e) {
            logger.warning("Failed to close readme file");
        }
    }
}
