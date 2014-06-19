package custom;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import custom.frenchTokenizer.tddts.preprocessing.tokenizer.FrenchTokenizerAutomatonTest;
import edu.stanford.nlp.process.LexedTokenFactory;


public class Preprocessing {
	
	//private static String frenchSuffix;
	//private static String englishSuffix;
	private static String outputDirectory;
	private static boolean biText;
	private static int maxSentenceLength;

	// TODO change french tokenization to Stanford's
	// TODO change tokenizeEnglishFile (ugly implementation now)
	
	public static ArrayList<File> getFiles(File rootFolder) {
		// returns paths to all files in a folder (including files in subfolders)
		ArrayList<File> fileList = new ArrayList<File>();
		
		for (File file : rootFolder.listFiles()) {
			if (file.isFile()) {
				fileList.add(file);
			
			} else {
				fileList.addAll(getFiles(file));
			}
		}
		
		return fileList;
	}
	
	public static void preprocessFolder(String inputFolder) {
		String outputFolder = outputDirectory + "\\" + inputFolder;
		
		
		// lower casing
		
		ArrayList<File> fileList = getFiles(new File(inputFolder));
		try {
			for (int i = 0; i < fileList.size(); ++i) {
				File f = fileList.get(i);
				File out = new File(outputFolder + "\\" + f.getName() + ".lo");
				out.getParentFile().mkdirs();
				out.createNewFile();
				
				lowercase(f, out);
			}
		} catch (IOException e) {
			System.err.println("Error on lowercasing step!");
			e.printStackTrace();
		}
		
		
		// remove all files but *.lo files
		fileList = getFiles(new File(outputFolder));
		for (File f : fileList) {
			String name = f.getName();
			if (!name.contains(".lo")) f.delete();
		}
		
		// normalization
		fileList = getFiles(new File(outputFolder));
		try {
			if (biText) {
				File firstInFile = fileList.get(0);
				File firstOutFile = new File(firstInFile.getPath() + ".filt");
				firstOutFile.createNewFile();
				
				File secondInFile = fileList.get(1);
				File secondtOutFile = new File(secondInFile.getPath() + ".filt");
				secondtOutFile.createNewFile();
				
				normalizeBiText(firstInFile, secondInFile, firstOutFile, secondtOutFile);
				
				
			} else {
				File inFile = null;
				File outFile = null;
				
				for (int i = 0; i < fileList.size(); ++i) {
					inFile = fileList.get(i);
					outFile = new File(inFile.getPath() + ".filt");
					outFile.createNewFile();
					normalizeMonoText(inFile, outFile);
				}
			}
		} catch (Exception e) {
			System.err.println("Error on normalization step! Check \"bi/mono\" parameter");
			e.printStackTrace();
		}
		
		// tokenizing
		fileList = getFiles(new File(outputFolder));
		for (int i = 0; i < fileList.size(); ++i) {
			File in = fileList.get(i);
			File out = new File(in.getPath() + ".tok");
			
			
			String name = in.getName();
			if (name.contains("fr.lo.filt")) {
				tokenizeFrenchFile(in, out);
			} else if (name.contains("en.lo.filt")) {
				tokenizeEnglishFile(in, out);
			}
		}
		
		
		
		// remove all files but *.lo.filt.tok files
		fileList = getFiles(new File(outputFolder));
		for (File f : fileList) {
			String name = f.getName();
			if (!name.contains(".lo.filt.tok")) {
				System.err.println("Deleting:");
				System.err.println(f.getName());
				f.delete();
			}
		}
		
		/*
		// ugly stuff to fix stanfords's tokenizer bug (only for eng text)
		try {
		fileList = getFiles(new File(outputFolder));
		
		for (int i = 0; i < fileList.size(); ++i) {
			String name = fileList.get(i).getName();
			if (name.contains(".en.")) deleteEmptyLines(fileList.get(i));
		}
		*/
		
		// rename the files back to normal names
		try {
		fileList = getFiles(new File(outputFolder));
		for (File f : fileList) {
			String newName = f.getName().substring(0, f.getName().length() - 12);
			f.renameTo(new File(f.getParent() + "/" + newName));
		}
		
		} catch (Exception e) {
			System.err.println("Error while renaming processed files");
			e.printStackTrace();
			System.exit(0);
		}
		
	}
	
	public static void deleteEmptyLines(File in) throws IOException {
		String outPath = in.getPath();
		in.renameTo(new File(outPath + ".temp"));
				
		in = new File(outPath + ".temp");
				
		File out = new File(outPath);
		out.createNewFile();
		
		BufferedReader fileReader = new BufferedReader(new FileReader(in));
		BufferedWriter fileWriter = new BufferedWriter(new FileWriter(out));
			
		String line = null;
		
		while((line = fileReader.readLine()) != null) {
				
			if (line.length() > 1) {
					
				fileWriter.write(line.toLowerCase());
				fileWriter.newLine();
			}
		}
		
		fileReader.close();
		fileWriter.close();
			
		in.delete();
	}
		
	public static File tokenizeFrenchFile(File in, File out) {
		String line = null;
		FrenchTokenizerAutomatonTest tokenizer = new FrenchTokenizerAutomatonTest();
		try {
			BufferedReader fileReader = new BufferedReader(new FileReader(in));
			BufferedWriter fileWriter = new BufferedWriter(new FileWriter(out));
			
			while((line = fileReader.readLine()) != null) {
				fileWriter.write(tokenizer.runAutomaton(line));
				fileWriter.newLine();
			}
			fileReader.close();
			fileWriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return out;
	}
	
	
	// not used
	public static void tokenizeEnglishFile2(File in) throws FileNotFoundException, IOException {
		File out = new File(in.getAbsolutePath() + ".tok");

		DocumentPreprocessor dp = new DocumentPreprocessor(in.getAbsolutePath());
		
		try {
			BufferedWriter fileWriter = new BufferedWriter(new FileWriter(out));
			
			for (List sentence : dp) { 
				String line = "";
				for (int i = 0; i < sentence.size(); i++) {
					line += PTBTokenizer.ptbToken2Text(sentence.get(i).toString());
					line += " ";	
				}
				line = line.substring(0, line.length() - 1);
				
				fileWriter.write(line);
				fileWriter.newLine();
		    }
			
			fileWriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void tokenizeEnglishFile(File in, File out) {
		try {
			Reader fr = new FileReader(in.getCanonicalPath());
			Tokenizer<CoreLabel> ptbt = new PTBTokenizer<CoreLabel>(fr, new CoreLabelTokenFactory(), "tokenizeNLs=true,invertible=true,ptb3Escaping=false");
			
			BufferedWriter fileWriter = new BufferedWriter(new FileWriter(out));
			
			String line = "";
			String nl = PTBTokenizer.getNewlineToken();
			
			/*
			while (ptbt.hasNext()) {
				String s = ptbt.next().toString();
				
				if (!s.toString().equals(nl)) {
	        		line += s + " ";
				} else {
					line = line.substring(0, line.length() - 1);
	        	
					fileWriter.write(line);
					fileWriter.newLine();
	        	
					line = "";
				}
			}
			// write last line if non-empty
			if (line.length() > 0) {
				line = line.substring(0, line.length() - 1);
				fileWriter.write(line);
				fileWriter.newLine();
			}
			*/
			
			/*
			 *  based on example from Stanford NLP page, we have to trim
			 *  the line variable, as labels sometimes turn to strings with
			 *  additional whitespaces
			 */
			  
			for (CoreLabel label; ptbt.hasNext(); ) {
		        
				label = ptbt.next();
				String s = label.toString();
				
		        if (!s.toString().equals(nl)) {
		        		line = line + s + " ";
		        } else {
		        	line = line.trim();
		        	
		        	fileWriter.write(line);
		        	fileWriter.newLine();
		        	
		        	line = "";
		        }
		    }
		    
			fileWriter.close();
			fr.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void normalizeMonoText(File in, File out) {
		// iterate over all lines
		try {
			BufferedReader fileReader = new BufferedReader(new FileReader(in));
			BufferedWriter fileWriter = new BufferedWriter(new FileWriter(out));
			
			String line = null;
			String prevLine = null;
			while((line = fileReader.readLine()) != null) {
				
				if (line.trim().length() > 1 && line.split(" ").length < maxSentenceLength) {
					
					fileWriter.write(line.toLowerCase());
					fileWriter.newLine();
				}
				
				prevLine = line;
			}
			fileReader.close();
			fileWriter.close();
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void normalizeBiText(File firstIn, File secondIn, File firstOut, File secondOut) {
		// iterate over all lines
		try {
			BufferedReader firstReader = new BufferedReader(new FileReader(firstIn));
			BufferedReader secondReader = new BufferedReader(new FileReader(secondIn));
			BufferedWriter firstWriter = new BufferedWriter(new FileWriter(firstOut));
			BufferedWriter secondWriter = new BufferedWriter(new FileWriter(secondOut));
			
			String firstLine = null;
			String secondLine = null;
			while((firstLine = firstReader.readLine()) != null) {
				secondLine = secondReader.readLine();
				
				if (secondLine.contains(" une assistance et un soutien aux victimes")) {
					System.out.println("WOW!");
					
					System.out.println(secondLine.trim());
					System.out.println(secondLine.trim().length());
				}
				
				if (firstLine.trim().length() > 1 &&
					secondLine.trim().length() > 1 &&
					firstLine.split(" ").length < maxSentenceLength &&
					secondLine.split(" ").length < maxSentenceLength) {
					
					String s = secondLine.trim();
					
					firstWriter.write(firstLine.toLowerCase());
					firstWriter.newLine();
					secondWriter.write(secondLine.toLowerCase());
					secondWriter.newLine();
				}
			}
			firstReader.close();
			secondReader.close();
			firstWriter.close();
			secondWriter.close();
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void lowercase(File in, File out) {
		try {
			BufferedReader fileReader = new BufferedReader(new FileReader(in));
			BufferedWriter fileWriter = new BufferedWriter(new FileWriter(out));
			
			String line = null;
			while((line = fileReader.readLine()) != null) {
				fileWriter.write(line.toLowerCase());
				fileWriter.newLine();
			}
			fileReader.close();
			fileWriter.close();
				
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static String usage() {
		StringBuilder sb = new StringBuilder();
	    String nl = System.getProperty("line.separator");
	    sb.append("Expects following format of the arguments: ").append(nl);
	    sb.append("-output directory").append(nl);
	    sb.append("-bitext (true or false)").append(nl);
	    sb.append("-max sentence length)").append(nl);
	    sb.append("-input folders").append(nl);
	    sb.append("Folders should contain files with proper suffixes.").append(nl);
	    sb.append("For bilingual text folder should contain exactly two files with the same names, but different fixed suffixes.").append(nl);
	    sb.append("For example: folder \"data\\train\" for files ");
	    sb.append("\"data\\train\\europarl-v7.fr-en.fr\", \"data\\train\\europarl-v7.fr-en.en\"").append(nl);
	    sb.append("Each folder should have two files only for bitext files!");
	    sb.append("We assume that we use french as source and english as target language.").append(nl);
	    
	    return sb.toString();    
	}
	
	public static void main(String[] args) {
		try {
			outputDirectory = args[0];
			biText = Boolean.parseBoolean(args[1]);
			maxSentenceLength = Integer.parseInt(args[2]);
			
			for (int i = 3; i < args.length; ++i) {
				preprocessFolder(args[i]);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(usage());
		}
		return;
	}
}
