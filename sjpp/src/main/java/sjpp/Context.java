package sjpp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Context {

	public static final String GENERATED = "// THIS FILE HAS BEEN GENERATED BY A PREPROCESSOR.";
	private Define define;
	private final ContextMode mode;
	private final List<JavaFile> files = new ArrayList<JavaFile>();

	private final Collection<String> removedCurrentFolder = new HashSet<String>();
	private final Collection<String> removedfolderAndSubfolders = new HashSet<String>();
	private final Collection<JavaFile> removedFiles = new HashSet<JavaFile>();

	private final Path source;

	public Context(ContextMode mode, Path source) {
		this.source = source;
		this.mode = mode;
	}

	public void addDefine(String id) {
		this.define = new Define(id);
	}

	public boolean doesApplyOn(String s) {
		return define.doesApplyOn(s);
	}

	private void addPath(Path path) {
		try {
			files.add(new JavaFile(this, path));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void process(Path destination) throws IOException, InterruptedException {

		final long start = System.currentTimeMillis();
		System.err.println("Starting...");

		Files.walk(source).filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java"))
				.forEach(this::addPath);

		System.err.println("(" + (System.currentTimeMillis() - start) + " ms) load start");

		for (JavaFile file : files)
			if (isRemoved(file) && removedFiles.contains(file) == false)
				removedFiles.add(file);

		final int availableProcessors = Runtime.getRuntime().availableProcessors();
		System.err.println("(Using " + availableProcessors + " runners)");
		final ExecutorService executorService = Executors.newFixedThreadPool(availableProcessors);
		for (JavaFile file : files) {
			if (removedFiles.contains(file))
				continue;
			executorService.submit(new Runnable() {
				public void run() {
					file.process();
				}
			});
		}
		System.err.println("(" + (System.currentTimeMillis() - start) + " ms) load done");
		executorService.shutdown();
		executorService.awaitTermination(1, TimeUnit.HOURS);

		System.err.println("(" + (System.currentTimeMillis() - start) + " ms) process done");
		deleteJavaFilesSafe(destination);

		for (JavaFile file : files) {
			if (removedFiles.contains(file))
				continue;
			final Path newPath = file.getNewPath(destination);
			file.save(newPath);

		}

		System.err.println("(" + (System.currentTimeMillis() - start) + " ms) saving done");

	}

	private void deleteJavaFilesSafe(Path out) throws IOException {
		Files.walk(out).filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).forEach(path -> {
			try {
				final String first = Files.readAllLines(path).get(0);
				if (first.startsWith(GENERATED))
					Files.delete(path);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

	}

	public void removeCurrentFolder(String packageName) {
		this.removedCurrentFolder.add(packageName);
	}

	public void removeFolderAndSubfolders(String packageName) {
		this.removedfolderAndSubfolders.add(packageName);
	}

	public void removeFile(JavaFile javaFile) {
		this.removedFiles.add(javaFile);
	}

	private boolean isRemoved(JavaFile javaFile) {
		for (String p : this.removedfolderAndSubfolders)
			if (javaFile.getPackageName().startsWith(p))
				return true;

		for (String p : this.removedCurrentFolder)
			if (javaFile.getPackageName().equals(p))
				return true;

		return removedFiles.contains(javaFile);
	}

	public boolean removeImportLine(String importName) {
		for (JavaFile file : removedFiles)
			if (file.isItMe(importName))
				return true;

		return false;
	}

//	public void save(Path out) throws IOException {
//		System.err.println("root=" + root);
//		System.err.println("out =" + out);
////		for (JavaFile file : files) {
////			final Path newPath = file.getNewPath(out);
////			// System.err.println("Writing " + newPath);
////			file.save(newPath);
////		}
//	}

	public final Path getRoot() {
		return source;
	}

	public ContextMode getMode() {
		return mode;
	}

}
