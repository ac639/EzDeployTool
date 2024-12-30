package org.build;

import java.io.IOException;

public class DirectoryPackager {
    public static String packageDirectory(String sourceDir, int versionNumber) throws IOException, InterruptedException {
        String targetDir = sourceDir + "-" + versionNumber + ".tar.gz";

        String command = "tar -czf " + targetDir + " -C " + sourceDir + " .";

        Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", command});
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            System.out.println("Bundle created: " + targetDir);
            return targetDir;
        } else {
            throw new IOException("Failed to bundle directory. Exit code: " + exitCode);
        }
    }
}
