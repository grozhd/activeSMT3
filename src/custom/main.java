package custom;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Properties;
import java.util.Scanner;
import java.security.Permission;

import org.apache.uima.util.FileUtils;


import edu.berkeley.nlp.lm.io.MakeKneserNeyArpaFromText;
import edu.berkeley.nlp.wordAlignment.Main;
import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.mt.train.PhraseExtract;
import edu.stanford.nlp.mt.tune.OnlineTuner;


public class main {
	
	static class SystemExitControl {
		 
	    public static class ExitTrappedException extends SecurityException {
	    }
	 
	    public static void forbidSystemExitCall() {
	        final SecurityManager securityManager = new SecurityManager() {
	            @Override
	            public void checkPermission(Permission permission) {
	                if (permission.getName().contains("exitVM")) {
	                    throw new ExitTrappedException();
	                }
	            }
	        };
	        System.setSecurityManager(securityManager);
	    }
	 
	    public static void enableSystemExitCall() {
	        System.setSecurityManager(null);
	    }
	}
	
	private static Properties props; 
	
	/*
	 *  Loads properties from a property file (default: pipeline.props) and creates some
	 *  more properties (filenames, folders, etc.) for the ease of further notation.
	 */
	private static void loadProperties()  throws Exception {
		// load properties
		InputStream input = new FileInputStream("pipeline.props");
		props = new Properties();
		props.load(input);
		
		// if output folder not specified, set it to current working directory
		if (props.getProperty("outputFolder").isEmpty()) {
			props.setProperty("outputFolder", System.getProperty("user.dir"));
		}
		
		
		// get the SetNames and extract files for devSet
		File devFolder = new File(props.getProperty("devCorpusFolder"));	
		for (File f : devFolder.listFiles()) {
			String nameParts[] = f.getName().split("\\.");
			String suffix = nameParts[nameParts.length - 1];
			if (suffix.equals(props.getProperty("sourceSuffix"))) {
				props.setProperty("devSourceFile", f.getPath());
			} else {
				props.setProperty("devTargetFile", f.getPath());
			}
			props.setProperty("devSetName", nameParts[0]);
		}
		
		// get the SetNames and extract files for testSet
		File testFolder = new File(props.getProperty("testCorpusFolder"));
		for (File f : testFolder.listFiles()) {
			String nameParts[] = f.getName().split("\\.");
			String suffix = nameParts[nameParts.length - 1];
			if (suffix.equals(props.getProperty("sourceSuffix"))) {
				props.setProperty("testSourceFile", f.getPath());
			} else {
				props.setProperty("testTargetFile", f.getPath());
			}
			props.setProperty("testSetName", nameParts[0]);
		}

		// get the training files
		File fullCorpusFolder = new File(props.getProperty("fullCorpusFolder"));
		File currentCorpusFolder = new File(props.getProperty("currentCorpusFolder"));
		for (File f : fullCorpusFolder.listFiles()) {
			String[] nameParts = f.getName().split("\\.");
			String fSuffix = nameParts[nameParts.length - 1];
			if (fSuffix.equals(props.getProperty("sourceSuffix"))) {
				props.setProperty("fullSourceCorpus", f.getPath());
				props.setProperty("currentSourceCorpus", currentCorpusFolder.getPath() + "/" + f.getName());
			} else {
				props.setProperty("fullTargetCorpus", f.getPath());
				props.setProperty("currentTargetCorpus", currentCorpusFolder.getPath() + "/" + f.getName());
			}
		}

	}
	
	/*
	 *  Runs preprocessing for all the data. 
	 *  Corpora to be preprocessed can be specified in properties file.
	 */
	private static void preprocessData() {
		System.out.println("Preprocessing...");
		
		String maxLength = props.getProperty("maxSentenceLength"); 
		String outputFolder = props.getProperty("outputFolder");
		boolean preprocessAll = Boolean.parseBoolean(props.getProperty("preprocessAll"));
		
		// training data
		if (preprocessAll || Boolean.parseBoolean(props.getProperty("preprocessTraining"))) {
			Preprocessing.main(new String[] {outputFolder, "true", maxLength, props.getProperty("currentCorpusFolder")});
		}
			
		// dev data
		if (preprocessAll || Boolean.parseBoolean(props.getProperty("preprocessDev"))) {
			Preprocessing.main(new String[] {outputFolder, "true", maxLength, props.getProperty("devCorpusFolder")});								 
		}
				
		// test data
		if (preprocessAll || Boolean.parseBoolean(props.getProperty("preprocessTest"))) {
			Preprocessing.main(new String[] {outputFolder, "true", maxLength, props.getProperty("testCorpusFolder")});								 
		}

		// mono data
		if (preprocessAll || Boolean.parseBoolean(props.getProperty("preprocessTest"))) {
			Preprocessing.main(new String[] {outputFolder, "false", maxLength, props.getProperty("monoCorpusFolder")});								 
		}
}
	
	/*
	 *  Builds a language model from the monoCorpusFile
	 */
	private static void buildLM() {
		// get the name of monolingual file
		File monoFolder = new File(props.getProperty("monoCorpusFolder"));
		File trainFolder = new File(props.getProperty("currentCorpusFolder"));
		
		try {
			
			File monoFile = null;
			monoFile = monoFolder.listFiles()[0];
			
			File trainFile = null;
			for (File f : trainFolder.listFiles()) {
				String fileName = f.getName();
				String fileSuffix = fileName.substring(fileName.length() - 2, fileName.length());
				if (fileSuffix.equals(props.getProperty("targetSuffix"))) trainFile = f; 
			}
			
			
			MakeKneserNeyArpaFromText.main(new String[] {props.getProperty("lmOrder"), 
														 props.getProperty("lmOutputFile"),
														 monoFile.getPath(),
														 trainFile.getPath()});
		
		} catch (Exception e) {
			System.err.println("Error in buildLM, check your folder for monoCorpusFolder");
			e.printStackTrace();
		}
	}
	
	/*
	 * Extracts phrases from the set to be translated.
	 */
	private static void extractPhrases(String extractSet) throws IOException {
	
		// Using param set 3: berkeleyaligner output folder
		
		String[] params = new String[30];
		// set inputDir to the folder created by berkeleyAligner
		params[0] = "-threads";
		params[1] = props.getProperty("threads");
		
		// set extractors
		params[2] = "-extractors";
		params[3] = props.getProperty("extractors");
		
		/*	set EXTRACT_SET parameteres
		 *  "-fCorpus $CORPUS_SRC -eCorpus $CORPUS_TGT -feAlign $CORPUS_FE -efAlign $CORPUS_EF -symmetrization grow-diag"
		 */
		String CORPUS_SRC = props.getProperty("currentSourceCorpus");
		String CORPUS_TGT = props.getProperty("currentTargetCorpus");
		String FSuffix = props.getProperty("sourceSuffix");
		String ESuffix = props.getProperty("targetSuffix");
		String CORPUS_FE = props.getProperty("alignFolder") + "/training." + FSuffix + "-" + ESuffix + ".A3"; 
		String CORPUS_EF = props.getProperty("alignFolder") + "/training." + ESuffix + "-" + FSuffix + ".A3"; 
		
		params[4] = "-fCorpus";
		params[5] = CORPUS_SRC;
		params[6] = "-eCorpus";
		params[7] = CORPUS_TGT;
		params[8] = "-feAlign";
		params[9] = CORPUS_FE;
		params[10] = "-efAlign";
		params[11] = CORPUS_EF;
		params[12] = "-symmetrization";
		params[13] = props.getProperty("symmetrization");
		
		/*		set fFilterCorpus (set to tune set source)
		 *  	TODO: change to the argument of function
		 */
		
		params[14] = "-fFilterCorpus";
		String setName;
		if (extractSet.equals("dev")) {
			params[15] = props.getProperty("devSourceFile");
			setName = props.getProperty("devSetName");
		} else {
			params[15] = props.getProperty("testSourceFile");
			setName = props.getProperty("testSetName");
		}
		
		/*		SET LO_ARGS
		 * 		String LO_ARGS = "-hierarchicalOrientationModel true -orientationModelType msd2-bidirectional-fe";
		 */
		params[16] = "-hierarchicalOrientationModel";
		params[17] = props.getProperty("hierarchicalOrientationModel");
		params[18] = "-orientationModelType";
		params[19] = props.getProperty("orientationModelType");
		
		/*		SET OTHER_EXTRACT_OPTS
		 * 		String MAX_PHRASE_LEN = "5";
		 *		String OTHER_EXTRACT_OPTS = "-split 2 -phiFilter 1e-4 -endAtLine 90000000 -maxELen " + MAX_PHRASE_LEN;
		 */
		params[20] = "-split";
		params[21] = props.getProperty("split");
		params[22] = "-phiFilter";
		params[23] = props.getProperty("phiFilter");
		params[24] = "-endAtLine";
		params[25] = props.getProperty("endAtLine");
		params[26] = "-maxELen";
		params[27] = props.getProperty("maxELen");
		
		/*	TODO: pipelening (output folder should be named appropriately)
		 *  TODO: ensure logger existance
		 *  set outputDir to empty folder
		 */
		params[28] = "-outputDir";
		params[29] = setName + ".tables";
		
		
		/*
		 * 	DEBUG OUTPUT
		 *  for (String s : params) System.out.println(s);
		 *  System.out.println( props.getProperty("testExtractFile") );
		 */
		
		
		// create necessary files
		File outputFolder = new File(params[29]);
		outputFolder.mkdirs();
		File errLogFile = new File(outputFolder.getPath() + "/" + setName + ".log");
		if (!errLogFile.getParentFile().exists()) {
			errLogFile.getParentFile().mkdirs();
		}
		errLogFile.createNewFile();
		
		// redirect errStream
		PrintStream stdErr = System.err;
		PrintStream errStream = new PrintStream(new FileOutputStream(errLogFile));
	    System.setErr(errStream);
		
		PhraseExtract.main(params);
		
		// reset errStream
		System.setErr(stdErr);
	}

	
	private static void runTuning() throws FileNotFoundException {
		File file  = new File(props.getProperty("devSetName") + ".tuning.log");
	    PrintStream printStream = new PrintStream(new FileOutputStream(file));
	    System.setErr(printStream);
	    
	    // get dev files
	    File frDevFile = new File(props.getProperty("devSourceFile"));
	    File enDevFile = new File(props.getProperty("devTargetFile"));
	    
	    String[] args = new String[19];
	    
	    // set TUNE_FILE (aka TUNE_SET)
	    args[0] = frDevFile.getPath();
	    
	    // set TUNE_REF
	    args[1] = enDevFile.getPath();
	    
	    // set TUNE_INI_FILE
	    args[2] = props.getProperty("iniDevFilePath");
	    
	    // set $INITIAL_WTS file
	    args[3] = props.getProperty("wtsFilePath");
	    
	    // set tunername
	    args[4] = "-n";
	    args[5] = "baseline";
	    
	    /*		set ONLINE_OPTS
	     *  	-e(pochs num) -ef(expected numb features) -b(batch size) -uw (uniform initial weights)
	     */
	    
	    args[6] = "-e";
	    args[7] = props.getProperty("epochsNum");
	    
	    
	    // TODO: put this in props
	    String[] ONLINE_OPTS = "-ef 20 -b 20 -uw -m bleu-smooth -o pro-sgd -of 1,5000,50,0.5,Infinity,0.02,adagradl1f,0.1".split(" ");
	    
	    for(int i = 0; i < ONLINE_OPTS.length; i++) {
	    	args[8 + i] = ONLINE_OPTS[i];
	    }
	    
	    OnlineTuner.main(args); 
	    printStream.close();
		
	}
	
	/*
	 * Translates the test file.
	 */
	private static void decode(long numberOfSentences) throws Exception {
		// redirect input stream to the DECODE_FILE
		InputStream stdIn = System.in;  // save standart input stream
		File decodeFile = new File(props.getProperty("testSourceFile"));
		InputStream inStream = new FileInputStream(decodeFile);
		System.setIn(inStream);
		
		// get the test set namme
		String testSetName = props.getProperty("testSetName");
		
		// redirect output stream to "$RUNNAME".trans
		PrintStream stdOut = System.out; // save standart output stream
		File outFile  = new File(testSetName + "." + numberOfSentences + ".baseline.trans");
		props.setProperty("decodeFile", outFile.getPath());
		outFile.createNewFile();
	    PrintStream outStream = new PrintStream(new FileOutputStream(outFile));
	    System.setOut(outStream);
		
	    // redirect error stream to logs/$RUNNAME.log"
	    PrintStream stdErr = System.err; // save standart err stream
		File errFile = new File("logs/" + testSetName + ".baseline.log");
		errFile.createNewFile();
	    PrintStream errStream = new PrintStream(new FileOutputStream(errFile));
	    System.setErr(errStream);
	    
	    String[] args = new String[1];
	    /*	SETUP INI FILE
	     * 
	     */
	    String RUNNAME_INI = props.getProperty("iniTestFilePath");
	    args[0] = RUNNAME_INI;
	    
	    Phrasal.main(args);
	    
	    // reset streams
		System.setOut(stdOut);
		System.setErr(stdErr);
		System.setIn(stdIn);
		
	}
	
	
	/*
	 * Computes the BLEU score of the tranlsation and saves it to a file.
	 */
	private static void outputBleu(long numberOfSentences) throws IOException {
		String testSetName = props.getProperty("testSetName");
		
		// set stdin to candidate translations
		InputStream stdIn = System.in;  // save standart input stream
		File trans = new File(props.getProperty("decodeFile"));
		System.setIn(new FileInputStream(trans));
		
		// set stdout to results
		PrintStream stdOut = System.out; // save standart output stream
		File results = new File(testSetName + "." + numberOfSentences + ".baseline.bleu");
		results.createNewFile();
		System.setOut(new PrintStream(results));
		
		// set stderr to logs
		PrintStream stdErr = System.err; // save standart err stream
		File logBLEU = new File("logs/" + testSetName + ".bleu.log");
		logBLEU.createNewFile();
		System.setErr(new PrintStream(logBLEU));
		
		// set references folder
		String refPath = "refs/" + testSetName + "/ref0";
		File refFile = new File(refPath);
		
		File testFolder = new File(props.getProperty("testCorpusFolder"));
		File testFile = null;
		for (File f : testFolder.listFiles()) {
			if (f.getName().contains(".en")) testFile = f;
		}
		
		refFile.getParentFile().mkdirs();
		FileUtils.copyFile(testFile, refFile.getParentFile());
		
		refFile = refFile.getParentFile().listFiles()[0];
		refFile.renameTo(new File(refPath));
		
		String[] args = new String[1];
		args[0] = refPath;
		
		// List<List<Sequence<IString>>> referencesList = null;;
		// BLEUMetric<IString, String> metric = new BLEUMetric<IString, String>(referencesList, order);
	
		BLEUMetric.main(args);
		
		// reset streams
		System.setOut(stdOut);
		System.setErr(stdErr);
		System.setIn(stdIn);
	}
	
	/*
	 * Prepares the config files for tuner and decoder modules.
	 */
	private static void prepareConfig() throws IOException {
		// deal with fr-en.ini (change SETID to appropriate)
		File iniDefault = new File(props.getProperty("defTuneConfigFolder") + "/fr-en.ini");
		
		String devSetName = props.getProperty("devSetName");
		File iniNew = new File(props.getProperty("newTuneConfigFolder") + "/" + devSetName + ".baseline.ini");
		
		if (iniNew.exists()) iniNew.delete();
		iniNew.createNewFile();
		
		String content = new Scanner(iniDefault).useDelimiter("\\Z").next();
		content = content.replaceAll("SETID", devSetName);
			
		// we add n-best-list parameter
		content = content + "\n\n[n-best-list]\n" + props.getProperty("nBest") + "\n";
			
		FileWriter out = new FileWriter(iniNew);
		out.write(content);
		out.close();
			
		props.setProperty("iniDevFilePath", iniNew.getPath());
		
		devSetName = props.getProperty("testSetName");
		iniNew = new File(props.getProperty("newTuneConfigFolder") + "/" + devSetName + ".baseline.ini");
		
		if (iniNew.exists()) iniNew.delete();
		iniNew.createNewFile();
		
		content = new Scanner(iniDefault).useDelimiter("\\Z").next();
		content = content.replaceAll("SETID", devSetName);
			
		// in config for the test set we have to add weights
		String newWtsFile = "baseline.online.final.binwts";
		content = content + "\n\n[weights-file]\n" + newWtsFile + "\n";	
			
		out = new FileWriter(iniNew);
		out.write(content);
		out.close();
			
		props.setProperty("iniTestFilePath", iniNew.getPath());
		
		// deal with fr-en.initial.binwts
		File wtsDefault = new File(props.getProperty("defTuneConfigFolder") + "/fr-en.initial.binwts");
		File wtsNew = new File(props.getProperty("newTuneConfigFolder") + "/fr-en.initial.binwts");
		if (!wtsNew.exists()) {
			wtsNew.createNewFile();
			FileUtils.copyFile(wtsDefault, wtsNew.getParentFile());
		}
		
		props.setProperty("wtsFilePath", wtsNew.getPath());
		
		// create logs directory if it doesn't exist
		File logsFolder = new File("logs");
		if (!logsFolder.isDirectory()) {
			logsFolder.mkdir();
		}
			

	}
		
	public static void main(String args[]) throws Exception {
		loadProperties();
		prepareConfig();
	
		// get the number of sentences to train model with
		long sentenceNum = Long.parseLong(props.getProperty("numberOfSentences"));
		if (args.length > 0) {
			sentenceNum = Long.parseLong(args[0]);
		}
		
		
		File bigSourceCorpus = new File(props.getProperty("fullSourceCorpus"));
		File bigTargetCorpus = new File(props.getProperty("fullTargetCorpus"));
		
		File currentSourceCorpus = new File(props.getProperty("currentSourceCorpus"));
		File currentTargetCorpus = new File(props.getProperty("currentTargetCorpus"));
		
		
		//		BILINGUAL CORPUS PREPARATION
		// cut the numberOfSentences from the full training set
		if (Boolean.parseBoolean(props.getProperty("cutTrainSet"))) {
			currentSourceCorpus.createNewFile();
			currentTargetCorpus.createNewFile();
			
			DocumentBreaker.writeFirstNSentences(bigSourceCorpus, currentSourceCorpus, sentenceNum, 0);
			DocumentBreaker.writeFirstNSentences(bigTargetCorpus, currentTargetCorpus, sentenceNum, 0);
		}
			
	    //		PREPROCESSING
		preprocessData();
		
		
		//		ACTIVE LEARNING
		if (Boolean.parseBoolean(props.getProperty("activeLearningOn"))) {
			String[] ALargs = new String[6];
			ALargs[0] = currentSourceCorpus.getPath();
			ALargs[1] = currentTargetCorpus.getPath();
			ALargs[2] = props.getProperty("alSourceFile");
			ALargs[3] = props.getProperty("alTargetFile");		
			ALargs[4] = props.getProperty("ALStepSize");
			ALargs[5] = props.getProperty("ALRandomSelection");
			
			sentenceNum = ActiveLearning.main(ALargs);
		}
			
		/*		ALIGNMENT
		 *  there should be a folder specified in aligner.conf with
		 *  preprocessed corpus, files should have correct suffixes
		 */
		if (Boolean.parseBoolean(props.getProperty("align"))) {
			System.out.println("Alignment...");
				
			// preventing System.exit()
		    if (Boolean.parseBoolean(props.getProperty("forbidSystemCall"))) {
		        SystemExitControl.forbidSystemExitCall();
		        props.setProperty("forbidSystemCall", "false");
		    }
		       
		    try {
		        Main.main(new String[]{"++aligner.conf"});
		            
		    } catch (SystemExitControl.ExitTrappedException e) {
		        
		    }
		    SystemExitControl.enableSystemExitCall();
					
		}
			 
			
		//		LANGUAGE MODEL CREATION
		if (Boolean.parseBoolean(props.getProperty("buildLM"))) {
			System.out.println("Building language model...");
			buildLM();
		}
			
			
		//		PHRASE EXTRACTION FROM DEV SET
		if (Boolean.parseBoolean(props.getProperty("extractPhrases"))) {
			System.out.println("Extracting phrases from dev and test sets...");

			extractPhrases("dev");
			extractPhrases("test");	
		}
			
			
		//		TUNING
		if (Boolean.parseBoolean(props.getProperty("tune"))) {
			System.out.println("Tuning...");	
			runTuning();
		}
			
	
		//		DECODING
		if (Boolean.parseBoolean(props.getProperty("decode"))) {
			System.out.println("Decoding...");
			decode(sentenceNum);
		}
			
		//		OUTPUT RESULTS	
		if (Boolean.parseBoolean(props.getProperty("computeBLEU"))) {
			System.out.println("Generating BLEU score...");
			outputBleu(sentenceNum);
		}
			
	
		// copy bleu and trans files
		File resFolder = new File("results/" + sentenceNum);
		resFolder.mkdirs();
		File mainFolder = new File(System.getProperty("user.dir"));
		for (File f : mainFolder.listFiles()) {
			if (f.getName().contains(".trans") || f.getName().contains(".bleu")) {
				f.renameTo(new File(resFolder.getPath() + "/" + f.getName()));
			}
		}
			
		// delete all the non-necessary output files
		File[] files = mainFolder.listFiles();
		for (int i = 0; i < files.length; ++i) {
			File f = files[i];
			if (f.getName().contains("newstest") || f.getName().contains("baseline") || f.getName().contains("alignments")) {
					if (f.isDirectory()) {
						FileUtils.deleteRecursive(f);
					} else {
						f.delete();
					}
				}
		}
			
		}
	
		
}
