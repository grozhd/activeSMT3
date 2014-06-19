package custom;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ResultsProcessing {
	
	private static String processFolder(File resultFolder) throws IOException {
		String result = null;
		for (File f : resultFolder.listFiles()) {
			if (f.getName().contains("bleu")) {
				String sentenceNum = f.getName().split("\\.")[1];
				List<String> lines = Files.readAllLines(Paths.get(f.getPath()), Charset.defaultCharset());
				
				String bleuScore = lines.get(1).split(" ")[2];
				bleuScore = bleuScore.substring(0, bleuScore.length() - 1);
				
				result = sentenceNum + "\t" + bleuScore;
			}
		}
		return result;
	}
	
	/*
	 * Gets the BLEU score from separate files in results folder and
	 * writes all of them to one file.
	 */
	public static void main(String args[]) throws Exception {
		File resultsMainFolder = new File(args[0]);
		File outputFile = new File(args[1]);
		
		outputFile.createNewFile();
		PrintWriter out = new PrintWriter(outputFile.getPath());
		
		
		for (File resultFolder : resultsMainFolder.listFiles()) {
			String results = processFolder(resultFolder);
			out.write(results + "\n");
		}
		
		out.close();
	}
	
}
