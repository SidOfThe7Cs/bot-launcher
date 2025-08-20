package sidly.botlauncher;

import net.fabricmc.api.DedicatedServerModInitializer;

import java.io.File;
import java.io.IOException;

import java.util.Optional;


public class BotLauncher implements DedicatedServerModInitializer {

	@Override
	public void onInitializeServer() {
		// Path to your bot jar file relative to the MC server directory
		File botJar = getBotFile();

		if (!botJar.exists()) {
			System.err.println("[BotLauncher] Bot.jar not found! Expected at: " + botJar.getAbsolutePath());
			return;
		}

		try {

			String botJarPrefix = "wynn-discord-bot-";
			boolean isRunning = ProcessHandle.allProcesses()
					.map(ProcessHandle::info)
					.map(ProcessHandle.Info::commandLine)
					.filter(Optional::isPresent)
					.map(Optional::get)
					.anyMatch(cmd -> cmd.contains("java -jar") && cmd.contains(botJarPrefix));

			if (isRunning) {
				System.out.println("Bot is already running");
				return;
			}

			System.out.println("[BotLauncher] Launching bot from: " + botJar.getAbsolutePath());

			// Build command to run bot jar
			ProcessBuilder pb = new ProcessBuilder(
					"java",
					"-XX:+HeapDumpOnOutOfMemoryError",
					"-XX:HeapDumpPath=" + botJar.getParentFile().getAbsolutePath() + "/heapdump.hprof",
					"-jar",
					botJar.getAbsolutePath()
			);


			// Redirect bot's output to MC console
			pb.inheritIO();

			// Start the process (runs independently of MC main thread)
			Process process = pb.start();

			// Optionally: track if the bot process exits
			new Thread(() -> {
				try {
					int exitCode = process.waitFor();
					System.err.println("[BotLauncher] Bot process exited with code: " + exitCode);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}, "BotProcessWatcher").start();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static File getBotFile() {
		File resourcesDir = new File("resources");

		// Filter jar files that match the pattern
		File[] matchingJars = resourcesDir.listFiles(file ->
				file.isFile() &&
						file.getName().startsWith("wynn-discord-bot-") &&
						file.getName().endsWith("-withDependencies.jar")
		);

		if (matchingJars == null || matchingJars.length == 0) {
			throw new IllegalStateException("No bot jar files found in /resources");
		}

		File newestJar = null;
		String newestVersion = "0.0.0";
		for (File file : matchingJars){
			String version2 = extractVersion(file.getName());
			if (compareVersions(version2, newestVersion) > 0){
				newestJar = file;
				newestVersion = version2;
			}

		}

		System.out.println("Using bot jar: " + newestJar.getAbsolutePath());
		return newestJar;
	}

	// returns >0 if v1 is greater than v2
	private static int compareVersions(String v1, String v2) {
		String[] p1 = v1.split("\\.");
		String[] p2 = v2.split("\\.");
		for (int i = 0; i < Math.max(p1.length, p2.length); i++) {
			int num1 = i < p1.length ? Integer.parseInt(p1[i]) : 0;
			int num2 = i < p2.length ? Integer.parseInt(p2[i]) : 0;
			if (num1 != num2) {
				return Integer.compare(num1, num2);
			}
		}
		return 0;
	}

	private static String extractVersion(String fileName) {
		// Expected format: wynn-discord-bot-X.Y.Z-withDependencies.jar
		String withoutPrefix = fileName.substring("wynn-discord-bot-".length());
		String versionPart = withoutPrefix.replace("-withDependencies.jar", "");
		return versionPart;
	}
}
