package me.kr1s_d.ultimateantibot.common.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class RuntimeUtil {
    public static Process execute(String... command) {
        try {
            String[] commands = new String[2 + command.length];
            commands[0] = "/bin/bash";
            commands[1] = "-c";
            System.arraycopy(command, 0, commands, 2, command.length);
            return new ProcessBuilder(commands).start();
        } catch (IOException e) {
            ServerUtil.getInstance().getLogHelper().error("An error occurred while dispatching: " + Arrays.toString(command) + ", message -> " + e.getMessage());
        }

        return null;
    }

    public static String executeAndGetOutput(String command) {
        try {
            String[] args = new String[] {"/bin/bash", "-c", command};
            StringBuilder stringBuilder = new StringBuilder(256);
            Process process = new ProcessBuilder(args).start();
            BufferedReader bufferedReader1 = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader bufferedReader2 = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String str;
            while ((str = bufferedReader1.readLine()) != null)
                stringBuilder.append(str).append('\n');
            while ((str = bufferedReader2.readLine()) != null)
                stringBuilder.append(str).append('\n');
            return stringBuilder.toString();
        } catch (IOException e) {
            ServerUtil.getInstance().getLogHelper().error("An error occurred while dispatching: " + command + ", message -> " + e.getMessage());
        }
        return "";
    }
}
