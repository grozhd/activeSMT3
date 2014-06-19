package custom;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class ActiveLearning {
	
	public static final int MAX_NGRAM_LENGTH = 4;
	public static final String VOCABULARY_SIZE_FILE = "vocabularySize";
	
	private static int ALStepSize;
	
	private static Map<String, Integer> coveredNGrams;
	private static Map<String, Integer> newNGrams;
	private static Map<String, Integer> sentenceIndex;
	
	private static List<String> sourceMonoText;
	private static List<String> targetMonoText;
	
	private static List<String> sourceBiText;
	
	private static List<String> newSourceSentences;
	private static List<String> newTargetSentences;
	
	private static int biCorpusSentenceCounter;
	
	/*
	 * Sorts Map by values. Used to get the most frequent nGrams.
	 */
	static <K,V extends Comparable<? super V>> SortedSet<Map.Entry<K,V>> entriesSortedByValues(Map<K,V> map) {
        SortedSet<Map.Entry<K,V>> sortedEntries = new TreeSet<Map.Entry<K,V>>(
            new Comparator<Map.Entry<K,V>>() {
                @Override public int compare(Map.Entry<K,V> e1, Map.Entry<K,V> e2) {
                    int res = e2.getValue().compareTo(e1.getValue());
                    return res != 0 ? res : 1; // Special fix to preserve items with equal values
                }
            }
        );
        sortedEntries.addAll(map.entrySet());
        return sortedEntries;
    }
	
	private static String usage() {
		StringBuilder sb = new StringBuilder();
	    String nl = System.getProperty("line.separator");
	    sb.append("Expects following arguments:").append(nl); 
	    sb.append("args[0] = path to the French (source) part of bilingual corpus,").append(nl); 
	    sb.append("args[1] = path to the English (target) part of bilingual corpus,").append(nl);
	    sb.append("args[2] = path to the French (source) monolingual data for active learning,").append(nl);
	    sb.append("args[3] = path to the English (target) translations of generated active learning data,").append(nl);
	    sb.append("args[4] = step size (number of sentences to be added on each iteration).").append(nl);
	    sb.append("(OPTIONAL) args[5] = random sentence selection instead of AL (true/false)");
	    
	    return sb.toString();    
	}
	
	/*
	 * Extracts all nGrams of length from 1 to MAX_NGRAM_LENGTH
	 * from a given sentence.
	 */
	private static HashSet<String> breakDownSentence(String sentence) {
		String[] tokens;
		HashSet<String> sentenceNGrams = new HashSet<String>();
		
		tokens = sentence.split(" ");
		
		for (int nGramLength = 1; nGramLength <= MAX_NGRAM_LENGTH; ++nGramLength) {
		for (int tokenNum = 0; tokenNum <= tokens.length - nGramLength; ++tokenNum) {
			String nGramText = "";
			for (int i = 0; i < nGramLength; ++i) {
				nGramText += tokens[tokenNum + i] + " ";
			}
			nGramText = nGramText.trim();
			sentenceNGrams.add(nGramText);
		}
		}
		
		return sentenceNGrams;
	}
	
	/*
	 * Extracts nGrams from the sourceMonoText.
	 */
	private static void buildCoveredNGrams(List<String> sentenceList) {
		biCorpusSentenceCounter = 0;

		for (String sentence : sentenceList) {
			HashSet<String> sentenceNGrams = breakDownSentence(sentence);
			for (String nGramText : sentenceNGrams) {
					coveredNGrams.put(nGramText, 1);
			}
			biCorpusSentenceCounter++;
		}
		
		biCorpusSentenceCounter += ALStepSize;
	}
	
	/*
	 * Extracts nGrams from the sourceMonoText. Computes the count for repeating
	 * nGrams. Stores the index of the occurrence of each nGram in sentenceIndex.
	 */
	private static void buildNewNGrams() {
		
		for (int counter = 0; counter < sourceMonoText.size(); ++counter) {
			String sentence = sourceMonoText.get(counter);
			
			HashSet<String> sentenceNGrams = breakDownSentence(sentence);
			for (String nGramText : sentenceNGrams) {
				if (!coveredNGrams.containsKey(nGramText)) {
				
					if (newNGrams.containsKey(nGramText)) {
						Integer count = newNGrams.get(nGramText);
						newNGrams.put(nGramText, ++count);
					} else {
						newNGrams.put(nGramText, 1);
						sentenceIndex.put(nGramText, counter);
					}
				}
			}
			
		}
		
	}
	
	/*
	 * Reads file and returns the list of it's strings.
	 */
	private static List<String> loadData(File textFile) throws IOException {
		BufferedReader fileReader = new BufferedReader(new FileReader(textFile));
		
		String sentence;
		List<String> text = new ArrayList<String>();
		
		while((sentence = fileReader.readLine()) != null) {
			text.add(sentence);
		}
		
		fileReader.close();
		return text;
	}
	
	/*
	 *  Function adds sentences based on newNGrams and sentenceIdex from sourceMonoText
	 *  and targetMonoText to be added to biFiles.
	 */
	private static void selectSentences() {
		int addedSentencesNum = 0;
		newSourceSentences = new ArrayList<String>();
		newTargetSentences = new ArrayList<String>();
		
		for (Map.Entry<String, Integer> e : entriesSortedByValues(newNGrams)) {
			String triggerNGram = e.getKey();
			
			String[] punct = new String[]{",", ".", "!", ")", "(", "?", ":", ";", "'", "\\", "/", "$", "@", "[", "]", "{", "}", "=", "+", "-", "—", "_"};
			boolean noPunct = true;
			
			for (String s : punct) {
				if (triggerNGram.contains(s)) {
					noPunct = false;
					break;
				}
			}
			
			
			if (noPunct) {
			
				int sentenceNum = sentenceIndex.get(triggerNGram);
				
				String sourceSentence = sourceMonoText.get(sentenceNum);
				
				
				
				if (!newSourceSentences.contains(sourceSentence)) {
					newSourceSentences.add(sourceSentence);
	
					String targetSentence = targetMonoText.get(sentenceNum);
					newTargetSentences.add(targetSentence);
					
					++addedSentencesNum;
					
					if (addedSentencesNum >= ALStepSize) break;
				}
			}
			
			
		}
	}
	
	/*
	 * Selects random sentences for baseline comparison.
	 */
	private static void selectRandomSentences() {
		int addedSentencesNum = 0;
		newSourceSentences = new ArrayList<String>();
		newTargetSentences = new ArrayList<String>();
		
		List<String> randomSentences = new ArrayList<String>(sourceMonoText);
		Collections.shuffle(randomSentences);
		
		HashSet<String> coveredSentences = new HashSet<String>(sourceBiText);
		
		for (String randomSentence : randomSentences) {
			
			if (!coveredSentences.contains(randomSentence)) {
			
				int sentenceNum = sourceMonoText.indexOf(randomSentence);
				
				newSourceSentences.add(sourceMonoText.get(sentenceNum));
				newTargetSentences.add(targetMonoText.get(sentenceNum));
				
				++addedSentencesNum;
				
				if (addedSentencesNum >= ALStepSize) break;
			}
		}
	}
	
	/*
	 * Appends newSourceSentences and newTargetSentences to sourceFile
	 * and targetFile accordingly.
	 */
	private static void writeNewSentences(File sourceFile, File targetFile) throws IOException {
		BufferedWriter sourceWriter = new BufferedWriter(new FileWriter(sourceFile.getPath(), true));
		BufferedWriter targetWriter = new BufferedWriter(new FileWriter(targetFile.getPath(), true));
		
		for (int sentenceNum = 0; sentenceNum < newSourceSentences.size(); ++sentenceNum) {
			sourceWriter.write(newSourceSentences.get(sentenceNum));
			targetWriter.write(newTargetSentences.get(sentenceNum));
			
			sourceWriter.newLine();
			targetWriter.newLine();
			
		}
		
		sourceWriter.close();
		targetWriter.close();
	}
	
	/*
	 * Writes vocabulary size to a file.
	 * Vocabluary size = number of unique 1-grams in training data.
	 */
	private static void writeVocabularySize() throws IOException {
		int count = 0;
		for (String s : coveredNGrams.keySet()) {
			if (s.split(" ").length == 1) {
				count++;
			}
		}
		
		File vocabSizeFile = new File(VOCABULARY_SIZE_FILE);
		vocabSizeFile.createNewFile();
		FileWriter fileWriter = new FileWriter(vocabSizeFile, true);
		fileWriter.write(biCorpusSentenceCounter + "\t" + count + "\n");
		fileWriter.close();
	}
	
	/*
	 * Debug function to print the state of the variables.
	 */
	private static void debugPrint(boolean randomLearning) {
		int counter = 0;
		
		if (!randomLearning) {
			for (Map.Entry<String, Integer> e : entriesSortedByValues(newNGrams)) {
				System.err.println(e);
				System.err.println(newSourceSentences.get(counter));
				counter++;
				if (counter >= 10) break;
			}
		} else {
			for (String e : newSourceSentences) {
				System.err.println(e);
				counter++;
				if (counter >= 10) break;
			}
		}
	}
	
	public static int main(String args[]) throws IOException {
		if (args.length != 5 && args.length != 6) {
			System.err.println(usage());
			return 0;
		} else {
			
			File biSourceFile = new File(args[0]);
			File biTargetFile = new File(args[1]);
			File monoSourceFile = new File(args[2]);
			File monoTargetFile = new File(args[3]);
			ALStepSize = Integer.parseInt(args[4]);
			
			boolean randomLearning = false;
			if (args.length == 6) {
				randomLearning = Boolean.parseBoolean(args[5]);
			}
			
			coveredNGrams = new TreeMap<String, Integer>();
			newNGrams = new TreeMap<String, Integer>();
			sentenceIndex = new TreeMap<String, Integer>();
			
			sourceMonoText = new ArrayList<String>();
			targetMonoText = new ArrayList<String>();
			
			sourceMonoText = loadData(monoSourceFile);
			targetMonoText = loadData(monoTargetFile);
			
			sourceBiText = loadData(biSourceFile);
			
			
			buildCoveredNGrams(sourceBiText);
			
			if (!randomLearning) {	
				buildNewNGrams();
				selectSentences();
			} else {
				selectRandomSentences();
			}
			
			writeNewSentences(biSourceFile, biTargetFile);

			//debugPrint(randomLearning);
			
			writeVocabularySize();
			
			return biCorpusSentenceCounter;
			
		}
		
		
		
	}
	
}
