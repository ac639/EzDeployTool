package org.build;

import java.io.*;
import java.util.Properties;

public class WorkingDirectoryManager {
    private static final String CONFIG_FILE = "config.properties";
    private static final String WORKING_DIR_KEY = "workingDir";

    public static String getWorkingDirectory() throws IOException {
        Properties properties = new Properties();
        File configFile = new File(CONFIG_FILE);

        if (!configFile.exists()) {
            throw new FileNotFoundException("Working directory is not set. Please use -d to set it.");
        }

        try (InputStream input = new FileInputStream(configFile)) {
            properties.load(input);
            String workingDir = properties.getProperty(WORKING_DIR_KEY);

            if (workingDir == null || workingDir.isEmpty()) {
                throw new FileNotFoundException("Working directory is not set in the config file.");
            }

            return workingDir;
        }
    }

    // Save the working directory to the config file
    public static void setWorkingDirectory(String directory) throws IOException {
        Properties properties = new Properties();
        File configFile = new File(CONFIG_FILE);

        // Load existing properties if the file exists
        if (configFile.exists()) {
            try (InputStream input = new FileInputStream(configFile)) {
                properties.load(input);
            }
        }

        // Update or set the working directory property
        properties.setProperty(WORKING_DIR_KEY, directory);

        // Save properties to the config file
        try (OutputStream output = new FileOutputStream(configFile)) {
            properties.store(output, null);
        }

        System.out.println("Working directory set to: " + directory);
    }
}
