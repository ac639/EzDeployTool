package org.build;

import java.io.FileNotFoundException;

public class EzDeployTool {

    private static String sourceDir;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Error: Missing arguments.");
            System.err.println("Usage: ezDep [dev|qa|prod] -m \"Your comment\" [versionNumber OPTIONAL]");
            System.err.println("Please Update Working Directory if not done so: ezDep -d \"PATHTODIRECTORY\"");
            return;
        }

        try {
            // Handle the -d command to set working directory
            if (args[0].equals("-d")) {
                if (args.length < 2 || args[1].isEmpty()) {
                    System.err.println("Error: Missing directory path.");
                    System.err.println("Usage: ezDep -d \"PATHTODIRECTORY\"");
                    return;
                }

                // Save the working directory
                sourceDir = args[1];
                WorkingDirectoryManager.setWorkingDirectory(sourceDir);
                System.out.println("Working directory set to: " + sourceDir);
                return;
            }

            sourceDir = WorkingDirectoryManager.getWorkingDirectory();

            String environment = args[0];
            switch (environment) {
                case "dev":
                    validateDevArgs(args);
                    handleDevDeployment(args);
                    break;

                case "qa":
                case "prod":
                    validateQaProdArgs(args);
                    handleQaProdDeployment(args, environment);
                    break;

                default:
                    System.err.println("Error: Invalid environment. Use 'dev', 'qa', or 'prod'.");
                    System.err.println("Usage: ezDep [dev|qa|prod] -m \"Your comment\" [versionNumber]");
            }
        } catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void validateDevArgs(String[] args) {
        if (args.length < 3 || !args[1].equals("-m")) {
            System.err.println("Usage: ezDep dev -m \"Your comment\"");
            System.exit(1);
        }
        if (args[2] == null || args[2].isEmpty()) {
            System.err.println("Error: Missing Comment after '-m'");
            System.err.println("Usage: ezDep dev -m \"Your comment\"");
            System.exit(1);
        }
    }

    private static void handleDevDeployment(String[] args) throws Exception {
        String message = args[2];
        int versionNumber = DatabaseManager.getNextVersionNumber();
        String filename = DirectoryPackager.packageDirectory(sourceDir, versionNumber);
        DatabaseManager.insertBundleData(filename, versionNumber, "dev", message);
        System.out.println("Dev deployment completed. Version: " + versionNumber);
    }

    private static void validateQaProdArgs(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: ezDep [qa|prod] [versionNumber]");
            System.exit(1);
        }

        try {
            if (args.length == 3) {
                Integer.parseInt(args[2]); // Ensure versionNumber is integer
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid version number.");
            System.exit(1);
        }
    }

    private static void handleQaProdDeployment(String[] args, String environment) throws Exception {
        int versionNumber;

        if (args.length == 3) { // Version number
            versionNumber = Integer.parseInt(args[2]);
            if (DatabaseManager.checkVersionExists(versionNumber)) {
                int highestVersion = DatabaseManager.getHighestVersionNumber();
                System.err.printf("Error: Version %d already exists. The highest version is %d.%n", versionNumber, highestVersion);
                return;
            }
        } else { // Use the highest version if no version is specified
            versionNumber = DatabaseManager.getHighestVersionNumber();
            if (versionNumber == 0) {
                System.err.println("Error: No versions available in the database.");
                return;
            }
        }

        System.out.printf("Deploying to %s environment, Version: %d%n", environment, versionNumber);

        // Use DatabaseManager to send the deployment message
        String message = String.format("{ \"environment\": \"%s\", \"version\": \"%d\" }", environment, versionNumber);
        DatabaseManager.sendDeploymentMessage("deployment", message);

        System.out.printf("Deployment request sent to %s for version %d.%n", environment, versionNumber);
    }
}
