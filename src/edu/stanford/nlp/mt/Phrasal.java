// Phrasal -- A Statistical Machine Translation Toolkit
// for Exploring New Model Features.
// Copyright (c) 2007-2010 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    java-nlp-user@lists.stanford.edu
//    http://nlp.stanford.edu/software/phrasal

package edu.stanford.nlp.mt;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.decoder.AbstractBeamInferer;
import edu.stanford.nlp.mt.decoder.AbstractBeamInfererBuilder;
import edu.stanford.nlp.mt.decoder.DTUDecoder;
import edu.stanford.nlp.mt.decoder.Inferer;
import edu.stanford.nlp.mt.decoder.InfererBuilderFactory;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilterFactory;
import edu.stanford.nlp.mt.decoder.util.BeamFactory;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.OutputSpace;
import edu.stanford.nlp.mt.decoder.util.OutputSpaceFactory;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.PhraseGeneratorFactory;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.ScorerFactory;
import edu.stanford.nlp.mt.metrics.Metrics;
import edu.stanford.nlp.mt.process.Postprocessor;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.ProcessorFactory;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerFactory;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.DTULinearDistortionFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.HierarchicalReorderingFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.LexicalReorderingFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.LinearFutureCostFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.UnknownWordFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.WordPenaltyFeaturizer;
import edu.stanford.nlp.mt.decoder.h.HeuristicFactory;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Phrasal: a phrase-based machine translation system from the Stanford University
 *          NLP group.
 *
 * NOTE: This object is not threadsafe. To enable programmatic multithreading with Phrasal,
 * specify the number of threads in the *.ini as usual, then use the threadId arguments
 * in the decode() functions to submit to the underlying threadpool. This design permits
 * storage of the LM and phrase table---among other large data structures---in shared memory.
 *
 * @author danielcer
 * @author Michel Galley
 * @author Spence Green
 *
 */
public class Phrasal {

  // TODO(spenceg): Add future cost delay as a parameter. Currently it must be set as a JVM parameter.
  // TODO(spenceg): Add input encoding option. Replace all instances of "UTF-8" in the codebase. 
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(Phrasal.class.getName()).append(" OPTS [ini_file] < input > output").append(nl).append(nl)
      .append("Phrasal: A phrase-based machine translation decoder from the Stanford NLP group.").append(nl).append(nl)
      .append("Command-line arguments override arguments specified in the optional ini_file:").append(nl)
      .append("  -").append(TRANSLATION_TABLE_OPT).append(" filename : Translation model file.").append(nl)
      .append("  -").append(LANGUAGE_MODEL_OPT).append(" filename : Language model file. For KenLM, prefix filename with 'kenlm:'").append(nl)
      .append("  -").append(OPTION_LIMIT_OPT).append(" num : Translation option limit.").append(nl)
      .append("  -").append(DISTORTION_WT_OPT).append(" num : Linear distortion weight.").append(nl)
      .append("  -").append(LANGUAGE_MODEL_WT_OPT).append(" num : Language model weight.").append(nl)
      .append("  -").append(TRANSLATION_MODEL_WT_OPT).append(" num [num] : Translation model weights.").append(nl)
      .append("  -").append(WORD_PENALTY_WT_OPT).append(" num : Word penalty weight.").append(nl)
      .append("  -").append(NBEST_LIST_OPT).append(" num : n-best list size.").append(nl)
      .append("  -").append(MOSES_NBEST_LIST_OPT).append(" filename : Generate Moses-format n-best lists.").append(nl)
      .append("  -").append(DISTINCT_NBEST_LIST_OPT).append(" boolean : Generate distinct n-best lists (default: false)").append(nl)
      .append("  -").append(FORCE_DECODE).append(" filename [filename] : Force decode to reference file(s).").append(nl)
      .append("  -").append(BEAM_SIZE).append(" num : Stack/beam size.").append(nl)
      .append("  -").append(SEARCH_ALGORITHM).append(" [cube|multibeam] : Inference algorithm (default:cube)").append(nl)
      .append("  -").append(DISTORTION_FILE).append(" model_type filename : Lexicalized re-ordering model.").append(nl)
      .append("  -").append(HIER_DISTORTION_FILE).append(" model_type filename : Hierarchical lexicalized re-ordering model.").append(nl)
      .append("  -").append(WEIGHTS_FILE).append(" filename : Load all model weights from file.").append(nl)
      .append("  -").append(CONFIG_FILE).append(" filename : Configuration file.").append(nl)
      .append("  -").append(MAX_SENTENCE_LENGTH).append(" num : Maximum input sentence length.").append(nl)
      .append("  -").append(MIN_SENTENCE_LENGTH).append(" num : Minimum input sentence length.").append(nl)
      .append("  -").append(DISTORTION_LIMIT).append(" num : Hard distortion limit.").append(nl)
      .append("  -").append(ADDITIONAL_FEATURIZERS).append(" class [class] : List of additional feature functions.").append(nl)
      .append("  -").append(DISABLED_FEATURIZERS).append(" class [class] : List of baseline featurizers to disable.").append(nl)
      .append("  -").append(NUM_THREADS).append(" num : Number of decoding threads (default: 1)").append(nl)
      .append("  -").append(USE_ITG_CONSTRAINTS).append(" boolean : Use ITG constraints for decoding (multibeam search only)").append(nl)
      .append("  -").append(RECOMBINATION_MODE).append(" name : Recombination mode [classic,exact,dtu] (default: classic).").append(nl)
      .append("  -").append(DROP_UNKNOWN_WORDS).append(" boolean : Drop unknown source words from the output (default: false)").append(nl)
      .append("  -").append(ADDITIONAL_PHRASE_GENERATOR).append(" class [class] : List of additional phrase tables.").append(nl)
      .append("  -").append(ALIGNMENT_OUTPUT_FILE).append(" filename : Output word-word alignments to file for each translation.").append(nl)
      .append("  -").append(PREPROCESSOR_FILTER).append(" language [opts] : Pre-processor to apply to source input.").append(nl)
      .append("  -").append(POSTPROCESSOR_FILTER).append(" language [opts] : Post-processor to apply to target output.").append(nl)
      .append("  -").append(SOURCE_CLASS_MAP).append(" filename : Feature API: Line-delimited source word->class mapping (TSV format).").append(nl)
      .append("  -").append(TARGET_CLASS_MAP).append(" filename : Feature API: Line-delimited target word->class mapping (TSV format).").append(nl)
      .append("  -").append(GAPS_OPT).append(" options : DTU: Enable Galley and Manning (2010) gappy decoding.").append(nl)
      .append("  -").append(MAX_PENDING_PHRASES_OPT).append(" num : DTU: Max number of pending phrases for decoding.").append(nl)
      .append("  -").append(GAPS_IN_FUTURE_COST_OPT).append(" boolean : DTU: Allow gaps in future cost estimate (default: true)").append(nl)
      .append("  -").append(LINEAR_DISTORTION_TYPE).append(" type : DTU: See ConcreteRule.LinearDistortionType (default: standard)").append(nl)
      .append("  -").append(PRINT_MODEL_SCORES).append(" boolean : Output model scores with translations (default: false)").append(nl)
      .append("  -").append(LOAD_SOURCE_CORENLP).append(" filename : Load source-side serialized CoreNLP annotations");
    return sb.toString();
  }

  private static final String TRANSLATION_TABLE_OPT = "ttable-file";
  private static final String LANGUAGE_MODEL_OPT = "lmodel-file";
  private static final String OPTION_LIMIT_OPT = "ttable-limit";
  private static final String DISTORTION_WT_OPT = "weight-d";
  private static final String LANGUAGE_MODEL_WT_OPT = "weight-l";
  private static final String TRANSLATION_MODEL_WT_OPT = "weight-t";
  private static final String WORD_PENALTY_WT_OPT = "weight-w";
  public static final String NBEST_LIST_OPT = "n-best-list";
  private static final String MOSES_NBEST_LIST_OPT = "moses-n-best-list";
  private static final String DISTINCT_NBEST_LIST_OPT = "distinct-n-best-list";
  private static final String FORCE_DECODE = "force-decode";
  private static final String BEAM_SIZE = "stack";
  private static final String SEARCH_ALGORITHM = "search-algorithm";
  private static final String DISTORTION_FILE = "distortion-file";
  private static final String HIER_DISTORTION_FILE = "hierarchical-distortion-file";
  private static final String WEIGHTS_FILE = "weights-file";
  private static final String CONFIG_FILE = "config-file";
  private static final String MAX_SENTENCE_LENGTH = "max-sentence-length";
  private static final String MIN_SENTENCE_LENGTH = "min-sentence-length";
  private static final String DISTORTION_LIMIT = "distortion-limit";
  private static final String ADDITIONAL_FEATURIZERS = "additional-featurizers";
  private static final String DISABLED_FEATURIZERS = "disabled-featurizers";
  private static final String NUM_THREADS = "threads";
  private static final String USE_ITG_CONSTRAINTS = "use-itg-constraints";
  private static final String RECOMBINATION_MODE = "recombination-mode";
  private static final String GAPS_OPT = "gaps";
  private static final String MAX_PENDING_PHRASES_OPT = "max-pending-phrases";
  private static final String GAPS_IN_FUTURE_COST_OPT = "gaps-in-future-cost";
  private static final String LINEAR_DISTORTION_TYPE = "linear-distortion-type";
  private static final String DROP_UNKNOWN_WORDS = "drop-unknown-words";
  private static final String ADDITIONAL_PHRASE_GENERATOR = "additional-phrase-generator";
  private static final String ALIGNMENT_OUTPUT_FILE = "alignment-output-file";
  private static final String PREPROCESSOR_FILTER = "preprocessor-filter";
  private static final String POSTPROCESSOR_FILTER = "postprocessor-filter";
  private static final String SOURCE_CLASS_MAP = "source-class-map";
  private static final String TARGET_CLASS_MAP = "target-class-map";
  private static final String PRINT_MODEL_SCORES = "print-model-scores";
  public static final String LOAD_SOURCE_CORENLP = "source-corenlp";

  private static final Set<String> REQUIRED_FIELDS = Generics.newHashSet();
  private static final Set<String> OPTIONAL_FIELDS = Generics.newHashSet();
  private static final Set<String> ALL_RECOGNIZED_FIELDS = Generics.newHashSet();
  static {
    REQUIRED_FIELDS.addAll(Arrays.asList(TRANSLATION_TABLE_OPT));
    OPTIONAL_FIELDS.addAll(Arrays.asList(WEIGHTS_FILE,
        DISTORTION_FILE, DISTORTION_LIMIT, 
        ADDITIONAL_FEATURIZERS, DISABLED_FEATURIZERS,
        OPTION_LIMIT_OPT, NBEST_LIST_OPT, MOSES_NBEST_LIST_OPT,
        DISTINCT_NBEST_LIST_OPT, FORCE_DECODE,
        RECOMBINATION_MODE, HIER_DISTORTION_FILE, SEARCH_ALGORITHM,
        BEAM_SIZE, WEIGHTS_FILE, MAX_SENTENCE_LENGTH,
        MIN_SENTENCE_LENGTH, USE_ITG_CONSTRAINTS,
        NUM_THREADS, GAPS_OPT, GAPS_IN_FUTURE_COST_OPT,
        LINEAR_DISTORTION_TYPE, MAX_PENDING_PHRASES_OPT,
        DROP_UNKNOWN_WORDS, ADDITIONAL_PHRASE_GENERATOR,
        LANGUAGE_MODEL_OPT, DISTORTION_WT_OPT, LANGUAGE_MODEL_WT_OPT,
        TRANSLATION_MODEL_WT_OPT, WORD_PENALTY_WT_OPT, 
        ALIGNMENT_OUTPUT_FILE, PREPROCESSOR_FILTER, POSTPROCESSOR_FILTER,
        SOURCE_CLASS_MAP,TARGET_CLASS_MAP, PRINT_MODEL_SCORES,
        LOAD_SOURCE_CORENLP));
    ALL_RECOGNIZED_FIELDS.addAll(REQUIRED_FIELDS);
    ALL_RECOGNIZED_FIELDS.addAll(OPTIONAL_FIELDS);
  }

  /**
   * Number of decoding threads. Setting this parameter to 0 enables
   * multithreading inside the main decoding loop. Generally, it is better
   * to set the desired number of threads here (i.e., set this parameter >= 1).
   */
  private int numThreads = 1;

  /**
   * Hard distortion limit for phrase-based decoder
   */
  private int distortionLimit = 5;

  /**
   * DTU options
   * 
   * TODO(spenceg): Remove static members. The Phrasal object itself is not threadsafe.
   */
  private static List<String> gapOpts = null;
  public static boolean withGaps = false;

  /**
   * Inference objects, one per thread
   */
  public List<Inferer<IString, String>> inferers;

  /**
   * Holds the model weights, one per inferer. The model weights have a shared feature index.
   */
  private List<Scorer<String>> scorers;

  /**
   * Phrase table / translation model
   */
  private PhraseGenerator<IString,String> phraseGenerator;

  /**
   * Whether to filter unknown words in the output
   */
  private boolean dropUnknownWords = false;

  /**
   * @return true if unknown words are dropped, and false otherwise.
   */
  public boolean isDropUnknownWords() { return dropUnknownWords; }
  
  /**
   * n-best list options
   */
  private boolean generateMosesNBestList = true;
  private PrintStream nbestListWriter;
  private int nbestListSize;
  
  /**
   * Internal alignment options
   */
  private PrintStream alignmentWriter;
  
  /**
   * References for force decoding
   */
  private List<List<Sequence<IString>>> forceDecodeReferences;

  /**
   * Hard limits on inputs to be decoded
   */
  private int maxSentenceSize = Integer.MAX_VALUE;
  private int minSentenceSize = 0;

  /**
   * Output model scores to console.
   */
  private boolean printModelScores = false;
  
  /**
   * Recombination configuration.
   */
  private String recombinationMode = RecombinationFilterFactory.CLASSIC_RECOMBINATION;

  /**
   * Pre/post processing filters.
   */
  private Preprocessor preprocessor;
  private Postprocessor postprocessor;
  
  public Preprocessor getPreprocessor() { return preprocessor; }
  public Postprocessor getPostprocessor() { return postprocessor; }
  
  /**
   * Access the decoder's scorer, which contains the model weights. THere is one scorer
   * per thread.
   *
   * @return the scorer
   */
  public Scorer<String> getScorer(int threadId) {
    if(threadId >= 0 && threadId < numThreads) {
      return scorers.get(threadId);
    }
    throw new RuntimeException("Illegal thread id: " + String.valueOf(threadId));
  }

  /**
   * @return the number of threads specified in the ini file.
   */
  public int getNumThreads() { return numThreads; }
  
  /**
   * Access the decoder's phrase table.
   * 
   * @return
   */
  public PhraseGenerator<IString,String> getPhraseTable() { return phraseGenerator; }
  
  // TODO(spenceg): Remove static members. The Phrasal object itself is not threadsafe.
  public static void initStaticMembers(Map<String, List<String>> config) {
    withGaps = config.containsKey(GAPS_OPT);
    gapOpts = withGaps ? config.get(GAPS_OPT) : null;
    FlatPhraseTable.createIndex(withGaps);
    if (config.containsKey(GAPS_IN_FUTURE_COST_OPT))
      DTUDecoder.gapsInFutureCost = Boolean.parseBoolean(config.get(
          GAPS_IN_FUTURE_COST_OPT).get(0));
    if (config.containsKey(DISTINCT_NBEST_LIST_OPT))
      if (!AbstractBeamInferer.DISTINCT_SURFACE_TRANSLATIONS)
        AbstractBeamInferer.DISTINCT_SURFACE_TRANSLATIONS = Boolean.parseBoolean(config.get(
            DISTINCT_NBEST_LIST_OPT).get(0));
    if (config.containsKey(LINEAR_DISTORTION_TYPE))
      ConcreteRule.setLinearDistortionType(config.get(
          LINEAR_DISTORTION_TYPE).get(0));
    else if (withGaps)
      ConcreteRule
          .setLinearDistortionType(ConcreteRule.LinearDistortionType.last_contiguous_segment
              .name());
  }

  @SuppressWarnings("unchecked")
  public Phrasal(Map<String, List<String>> config) throws IOException,
      InstantiationException, IllegalAccessException, IllegalArgumentException,
      SecurityException, InvocationTargetException, NoSuchMethodException,
      ClassNotFoundException {
    // Check for required parameters	  
    if (!config.keySet().containsAll(REQUIRED_FIELDS)) {
      Set<String> missingFields = Generics.newHashSet(REQUIRED_FIELDS);
      missingFields.removeAll(config.keySet());
      throw new RuntimeException(String.format(
          "The following required fields are missing: %s%n", missingFields));
    }
    // Check for unrecognized parameters
    if (!ALL_RECOGNIZED_FIELDS.containsAll(config.keySet())) {
      Set<String> extraFields = Generics.newHashSet(config.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_FIELDS);
      throw new RuntimeException(String.format(
          "The following fields are unrecognized: %s%n", extraFields));
    }

    numThreads = config.containsKey(NUM_THREADS) ? Integer.parseInt(config.get(NUM_THREADS).get(0)) : 1;
    if (numThreads < 1) throw new RuntimeException("Number of threads must be positive: " + numThreads);
    System.err.printf("Number of threads: %d%n", numThreads);

    if (withGaps) {
      recombinationMode = RecombinationFilterFactory.DTU_RECOMBINATION;
    } else if (config.containsKey(RECOMBINATION_MODE)) {
      recombinationMode = config.get(RECOMBINATION_MODE).get(0);
    }
    
    if (config.containsKey(PRINT_MODEL_SCORES)) {
      printModelScores = Boolean.valueOf(config.get(PRINT_MODEL_SCORES).get(0));
    }
    
    // Pre/post processor filters. These may be accessed programmatically, but they
    // are only applied automatically to text read from the console.
    if (config.containsKey(PREPROCESSOR_FILTER)) {
      List<String> parameters = config.get(PREPROCESSOR_FILTER);
      if (parameters.size() == 0) throw new RuntimeException("Preprocessor configuration requires at least one argument");
      String language = parameters.get(0);
      String[] options = parameters.size() > 1 ? parameters.get(1).split("\\s+") : (String[]) null;
      preprocessor = ProcessorFactory.getPreprocessor(language, options);
      System.err.printf("Preprocessor filter: %s%n", preprocessor.getClass().getName());
    }
    if (config.containsKey(POSTPROCESSOR_FILTER)) {
      List<String> parameters = config.get(POSTPROCESSOR_FILTER);
      if (parameters.size() == 0) throw new RuntimeException("Postprocessor configuration requires at least one argument");
      String language = parameters.get(0);
      String[] options = parameters.size() > 1 ? parameters.get(1).split("\\s+") : (String[]) null;
      postprocessor = ProcessorFactory.getPostprocessor(language, options);
      System.err.printf("Postprocessor filter: %s%n", postprocessor.getClass().getName());
    }
    
    // Word->class maps
    if (config.containsKey(SOURCE_CLASS_MAP)) {
      List<String> parameters = config.get(SOURCE_CLASS_MAP);
      if (parameters.size() == 0) throw new RuntimeException("Source class map requires a file argument");
      SourceClassMap map = SourceClassMap.getInstance();
      for (String filename : parameters) {
        map.load(filename);
        System.err.println("Loaded source class map: " + filename);
      }
    }
    if (config.containsKey(TARGET_CLASS_MAP)) {
      List<String> parameters = config.get(TARGET_CLASS_MAP);
      if (parameters.size() == 0) throw new RuntimeException("Target class map requires a file argument");
      TargetClassMap map = TargetClassMap.getInstance();
      for (String filename : parameters) {
        map.load(filename);
        System.err.println("Loaded target class map: " + filename);
      }
    }
    
    // Source CoreNLP annotations
    if (config.containsKey(LOAD_SOURCE_CORENLP)) {
      List<String> parameters = config.get(LOAD_SOURCE_CORENLP);
      if (parameters.size() == 0) throw new RuntimeException("Source CoreNLP requires a file argument");
      CoreNLPCache.loadSerialized(parameters.get(0));
    }
    
    if (config.containsKey(FORCE_DECODE)) {
      forceDecodeReferences = Metrics.readReferences(config.get(FORCE_DECODE)
          .toArray(new String[config.get(FORCE_DECODE).size()]));
    }

    // int distortionLimit = -1;
    if (config.containsKey(DISTORTION_LIMIT)) {
      List<String> strDistortionLimit = config.get(DISTORTION_LIMIT);
      if (strDistortionLimit.size() != 1) {
        throw new RuntimeException(String.format(
            "Parameter '%s' takes one and only one argument", DISTORTION_LIMIT));
      }
      try {
        distortionLimit = Integer.parseInt(strDistortionLimit.get(0));
      } catch (NumberFormatException e) {
        throw new RuntimeException(
            String
                .format(
                    "Argument '%s' to parameter '%s' can not be parsed as an integer value%n",
                    strDistortionLimit.get(0), DISTORTION_LIMIT));
      }
    }
    
    // DTU decoding
    FeaturizerFactory.GapType gapT = !withGaps ? FeaturizerFactory.GapType.none
        : ((gapOpts.size() > 1) ? FeaturizerFactory.GapType.both
            : FeaturizerFactory.GapType.source);
    String gapType = gapT.name();
    System.err.println("Gap type: " + gapType);

    // Phrase table(s)
    String phraseTable;
    if (config.get(TRANSLATION_TABLE_OPT).size() == 1) {
      phraseTable = config.get(TRANSLATION_TABLE_OPT).get(0);
    } else if (config.get(TRANSLATION_TABLE_OPT).size() == 4) {
      List<String> ptOpts = config.get(TRANSLATION_TABLE_OPT);
      System.err
          .printf(
              "Ignoring Moses factor & phrase feature count information: %s, %s, %s%n",
              ptOpts.get(0), ptOpts.get(1), ptOpts.get(2));
      phraseTable = ptOpts.get(3);
    } else {
      throw new RuntimeException("Unsupported configuration "
          + config.get(TRANSLATION_TABLE_OPT));
    }

    if (withGaps) {
      // Support for gaps:
      if (gapOpts.size() < 1 || gapOpts.size() > 2)
        throw new UnsupportedOperationException();
      int maxSourcePhraseSpan = Integer.parseInt(gapOpts.get(0));
      DTUTable.setMaxPhraseSpan(maxSourcePhraseSpan);

      int maxTargetPhraseSpan = (gapOpts.size() > 1) ? Integer.parseInt(gapOpts
          .get(1)) : -1;
      if (maxTargetPhraseSpan == -1) {
        System.err.println("Phrases with target gaps not loaded into memory.");
        DTUTable.maxNumberTargetSegments = 1;
      }
      if (gapT == FeaturizerFactory.GapType.target
          || gapT == FeaturizerFactory.GapType.both) {
        DTUHypothesis.setMaxTargetPhraseSpan(maxTargetPhraseSpan);
        //AbstractBeamInferer.DISTINCT_SURFACE_TRANSLATIONS = true; // TODO: restore?
      }

      // Support for floating phrases:
      if (config.containsKey(MAX_PENDING_PHRASES_OPT)) {
        List<String> floatOpts = config.get(MAX_PENDING_PHRASES_OPT);
        if (floatOpts.size() != 1)
          throw new UnsupportedOperationException();
        int maxPendingPhrases = Integer.parseInt(floatOpts.get(0));
        DTUHypothesis.setMaxPendingPhrases(maxPendingPhrases);
      }
    }


    if (config.containsKey(DROP_UNKNOWN_WORDS)) {
      dropUnknownWords = Boolean.parseBoolean(config.get(DROP_UNKNOWN_WORDS).get(0));
    }

    String optionLimit = config.get(OPTION_LIMIT_OPT).get(0);
    System.err.printf("Phrase table: %s Unknown words policy: %s%n", phraseTable, (dropUnknownWords ? "Drop" : "Keep"));

    if (phraseTable.startsWith("bitext:")) {
      phraseGenerator = (optionLimit == null ? PhraseGeneratorFactory.<String>factory(
          false, PhraseGeneratorFactory.NEW_DYNAMIC_GENERATOR, phraseTable) : PhraseGeneratorFactory.<String>factory(false, PhraseGeneratorFactory.NEW_DYNAMIC_GENERATOR,
          phraseTable.replaceFirst("^bitext:", ""),
          optionLimit));
    } else if (phraseTable.endsWith(".db") || phraseTable.contains(".db:")) {

      System.err.printf("Dyanamic pt%n========================");
      phraseGenerator = (optionLimit == null ? PhraseGeneratorFactory.<String>factory(
          false, PhraseGeneratorFactory.DYNAMIC_GENERATOR, phraseTable) : PhraseGeneratorFactory.<String>factory(false, PhraseGeneratorFactory.DYNAMIC_GENERATOR,
          phraseTable, optionLimit));
    } else {
      String generatorName = withGaps ? PhraseGeneratorFactory.DTU_GENERATOR
          : PhraseGeneratorFactory.PSEUDO_PHARAOH_GENERATOR;
      phraseGenerator = (optionLimit == null ? PhraseGeneratorFactory.<String>factory(
          false, generatorName, phraseTable)
          : PhraseGeneratorFactory.<String>factory(false, generatorName, phraseTable,
              optionLimit));
    }
    if (config.get(ADDITIONAL_PHRASE_GENERATOR) != null) {
       List<PhraseGenerator<IString,String>> pgens = Generics.newLinkedList();
       pgens.add(phraseGenerator);
       for (String pgenClasspath : config.get(ADDITIONAL_PHRASE_GENERATOR)) {
          PhraseGenerator<IString,String> pgen;
          try {
             pgen = (PhraseGenerator<IString,String>)Class.forName(pgenClasspath).
                getConstructor().newInstance();
          } catch (ClassNotFoundException e) {
             throw new RuntimeException("Invalid PhraseGenerator: "+pgenClasspath);
          }
          pgens.add(pgen);
       }
       phraseGenerator = new CombinedPhraseGenerator<IString,String>(pgens, CombinedPhraseGenerator.Type.CONCATENATIVE, Integer.parseInt(optionLimit));
    }

    // TODO(spenceg) For better or worse, all phrase tables use the source index in FlatPhraseTable
    // Pass it to the UnknownWord generator. Would be better for the source index to be located
    // in a common place.
    phraseGenerator = new CombinedPhraseGenerator<IString,String>(
             Arrays.asList(phraseGenerator, new UnknownWordPhraseGenerator<IString, String>(dropUnknownWords, FlatPhraseTable.sourceIndex)),
             CombinedPhraseGenerator.Type.STRICT_DOMINANCE, Integer.parseInt(optionLimit));
    
    FlatPhraseTable.lockIndex();

    System.err.printf("Phrase table limit (ttable-limit): %d%n",
        ((CombinedPhraseGenerator<IString,String>) phraseGenerator).getPhraseLimit());

    // Lexicalized reordering model
    DerivationFeaturizer<IString, String> lexReorderFeaturizer = null;

    if (config.containsKey(DISTORTION_FILE)
        || config.containsKey(HIER_DISTORTION_FILE)) {
      if (config.containsKey(DISTORTION_FILE)
          && config.containsKey(HIER_DISTORTION_FILE))
        throw new UnsupportedOperationException(
            "Two distortion files instead of one. "
                + "To use more than one, please use " + ADDITIONAL_FEATURIZERS
                + " field.");
      boolean stdDistFile = config.containsKey(DISTORTION_FILE);
      List<String> strDistortionFile = stdDistFile ? config
          .get(DISTORTION_FILE) : config.get(HIER_DISTORTION_FILE);
      String modelType;
      String modelFilename;
      if (strDistortionFile.size() == 2) {
        modelType = strDistortionFile.get(0);
        modelFilename = strDistortionFile.get(1);

      } else {
        throw new RuntimeException(
            String
                .format(
                    "Parameter '%s' takes two arguments: distortion-model-type & model-filename)",
                    DISTORTION_FILE));
      }
      lexReorderFeaturizer = stdDistFile ? new LexicalReorderingFeaturizer(
          new LexicalReorderingTable(modelFilename, modelType))
          : new HierarchicalReorderingFeaturizer(modelFilename, modelType);
    }

    List<Featurizer<IString, String>> additionalFeaturizers = Generics.newArrayList();
    if (config.containsKey(ADDITIONAL_FEATURIZERS)) {
      List<String> tokens = config.get(ADDITIONAL_FEATURIZERS);
      
      String featurizerName = null;
      String args = null;
      for (String token : tokens) {
        Featurizer<IString, String> featurizer = null;
        if (featurizerName == null) {
          if (token.endsWith("()")) {
            String name = token.replaceFirst("\\(\\)$", "");
            Class<Featurizer<IString, String>> featurizerClass = FeaturizerFactory
                .loadFeaturizer(name);
            featurizer = (Featurizer<IString, String>) featurizerClass
                .newInstance();
            additionalFeaturizers.add(featurizer);
          } else if (token.contains("(")) {
            if (token.endsWith(")")) {
              featurizerName = token.replaceFirst("\\(.*", "");
              args = token.replaceFirst("^.*\\(", "");
              args = args.substring(0, args.length() - 1);
              args = args.replaceAll("\\s*,\\s*", ",");
              args = args.replaceAll("^\\s+", "");
              args = args.replaceAll("\\s+$", "");
              String[] argsList = args.split(",");
              System.err.printf("Additional featurizer: %s.%nArgs: %s%n",
                  featurizerName, Arrays.toString(argsList));
              Class<Featurizer<IString, String>> featurizerClass = FeaturizerFactory
                  .loadFeaturizer(featurizerName);
              
              featurizer = (Featurizer<IString, String>) featurizerClass
                  .getConstructor(argsList.getClass()).newInstance(
                      new Object[] { argsList });
              additionalFeaturizers.add(featurizer);
              featurizerName = null;
              args = null;
            } else {
              featurizerName = token.replaceFirst("\\(.*", "");
              args = token.replaceFirst(".*\\(", "");
            }
          } else {
            System.err.printf(
                "Error: '(' expected immediately after feature name %s", token);
            System.err
                .printf("Note that no whitespace between '(' and the associated feature name is allowed%n");
            System.exit(-1);
          }
        } else {
          if (token.endsWith(")")) {
            args += " " + token.substring(0, token.length() - 1);
            args = args.replaceAll("\\s*,\\s*", ",");
            args = args.replaceAll("^\\s+", "");
            args = args.replaceAll("\\s+$", "");
            String[] argsList = args.split(",");
            System.err.printf("args: %s%n", Arrays.toString(argsList));
            Class<Featurizer<IString, String>> featurizerClass = FeaturizerFactory
                .loadFeaturizer(featurizerName);
            featurizer = (Featurizer<IString, String>) featurizerClass
                .getConstructor(argsList.getClass()).newInstance(
                    (Object) argsList);
            additionalFeaturizers.add(featurizer);
            featurizerName = null;
            args = null;
          } else {
            args += " " + token;
          }
        }
      }
      if (featurizerName != null) {
        System.err.printf("Error: no ')' found for featurizer %s%n",
            featurizerName);
        System.exit(-1);
      }
    }

    // Create Featurizer
    String lgModel = null;
    if (config.containsKey(LANGUAGE_MODEL_OPT)) {
      lgModel = config.get(LANGUAGE_MODEL_OPT).get(0);
      System.err.printf("Language model: %s%n", lgModel);
    }

    final String linearDistortion = withGaps ? DTULinearDistortionFeaturizer.class.getName() 
        : LinearFutureCostFeaturizer.class.getName();
    CombinedFeaturizer<IString, String> featurizer;
    if (lgModel != null) {
      featurizer = FeaturizerFactory.factory(
        FeaturizerFactory.PSEUDO_PHARAOH_GENERATOR,
        makePair(FeaturizerFactory.LINEAR_DISTORTION_PARAMETER,
            linearDistortion),
        makePair(FeaturizerFactory.GAP_PARAMETER, gapType),
        makePair(FeaturizerFactory.ARPA_LM_PARAMETER, lgModel));
    } else {
      featurizer = FeaturizerFactory.factory(
          FeaturizerFactory.PSEUDO_PHARAOH_GENERATOR,
          makePair(FeaturizerFactory.LINEAR_DISTORTION_PARAMETER,
              linearDistortion),
          makePair(FeaturizerFactory.GAP_PARAMETER, gapType));
    }

    if (config.containsKey(DISABLED_FEATURIZERS)) {
      Set<String> disabledFeaturizers = Generics.newHashSet();
      for (String f : config.get(DISABLED_FEATURIZERS))
        disabledFeaturizers.add(f);
      featurizer.deleteFeaturizers(disabledFeaturizers);
    }

    if (lexReorderFeaturizer != null) {
      additionalFeaturizers.add(lexReorderFeaturizer);
    }

    if (!additionalFeaturizers.isEmpty()) {
      List<Featurizer<IString, String>> allFeaturizers = Generics.newArrayList();
      allFeaturizers.addAll(featurizer.featurizers);
      allFeaturizers.addAll(additionalFeaturizers);
      featurizer = new CombinedFeaturizer<IString, String>(allFeaturizers);
    }
    
    // Link the final featurizer and the phrase table
    phraseGenerator.setFeaturizer(featurizer);

    // Create Scorer / weight vector
    Counter<String> weightVector = new ClassicCounter<String>();

    if (config.containsKey(WEIGHTS_FILE)) {
      System.err.printf("Weights file: %s%n", config.get(WEIGHTS_FILE).get(0));
      weightVector = IOTools.readWeights(config.get(WEIGHTS_FILE).get(0));

    } else {
      if (config.containsKey(TRANSLATION_MODEL_WT_OPT)) {
        List<String> inlineWts = config.get(TRANSLATION_MODEL_WT_OPT);
        List<String> featureNames = phraseGenerator.getFeatureNames();
        if (inlineWts.size() > featureNames.size()) {
          throw new RuntimeException(String.format("%d weights specified by %s, but translation model has only %d features",
              inlineWts.size(), TRANSLATION_MODEL_WT_OPT,
              featureNames.size()));
        }
        int i = 0;
        for (String wtValue : inlineWts) {
          weightVector.setCount(featureNames.get(i++), Double.parseDouble(wtValue));
        }
      }
      if (config.containsKey(LANGUAGE_MODEL_WT_OPT)) {
        weightVector.setCount(NGramLanguageModelFeaturizer.FEATURE_NAME,
          Double.parseDouble(config.get(LANGUAGE_MODEL_WT_OPT).get(0)));
      }
      if (config.containsKey(DISTORTION_WT_OPT)) {
        weightVector.setCount(LinearFutureCostFeaturizer.FEATURE_NAME,
          Double.parseDouble(config.get(DISTORTION_WT_OPT).get(0)));

        if (config.get(DISTORTION_WT_OPT).size() > 1) {
          int numAdditionalWts = config.get(DISTORTION_WT_OPT).size() - 1;
          if (lexReorderFeaturizer == null) {
            throw new RuntimeException(
                String
                    .format(
                        "Additional weights given for parameter %s but no lexical reordering file was specified",
                        DISTORTION_WT_OPT));
          }
          if (lexReorderFeaturizer instanceof LexicalReorderingFeaturizer) {
            LexicalReorderingFeaturizer mosesLexReorderFeaturizer = (LexicalReorderingFeaturizer) lexReorderFeaturizer;
            if (numAdditionalWts != mosesLexReorderFeaturizer.mlrt.positionalMapping.length) {
              throw new RuntimeException(
                  String
                      .format(
                          "%d re-ordering weights given with parameter %s, but %d expected",
                          numAdditionalWts, DISTORTION_WT_OPT,
                          mosesLexReorderFeaturizer.mlrt.positionalMapping.length));
            }
            for (int i = 0; i < mosesLexReorderFeaturizer.mlrt.positionalMapping.length; i++) {
              weightVector.setCount(mosesLexReorderFeaturizer.featureTags[i],
                  Double.parseDouble(config.get(DISTORTION_WT_OPT).get(i + 1)));
            }
          }
        }
      }

      if (config.containsKey(WORD_PENALTY_WT_OPT)) {
        weightVector.setCount(WordPenaltyFeaturizer.FEATURE_NAME,
            Double.parseDouble(config.get(WORD_PENALTY_WT_OPT).get(0)));
      }

      weightVector.setCount(UnknownWordFeaturizer.FEATURE_NAME, 1.0);

      if (config.containsKey(TRANSLATION_MODEL_WT_OPT)) {
        System.err.printf("Warning: Ignoring old translation model weights set with %s", TRANSLATION_MODEL_WT_OPT);
      }
    }

    if (config.containsKey(MAX_SENTENCE_LENGTH)) {
      try {
        maxSentenceSize = Integer.parseInt(config.get(MAX_SENTENCE_LENGTH).get(
            0));
        if (maxSentenceSize == 0)
          maxSentenceSize = Integer.MAX_VALUE;
      } catch (NumberFormatException e) {
        throw new RuntimeException(String.format(
            "Argument %s to %s can not be parsed as an integer",
            config.get(MAX_SENTENCE_LENGTH), MAX_SENTENCE_LENGTH));
      }
    }

    if (config.containsKey(MIN_SENTENCE_LENGTH)) {
      try {
        minSentenceSize = Integer.parseInt(config.get(MIN_SENTENCE_LENGTH).get(
            0));
      } catch (NumberFormatException e) {
        throw new RuntimeException(String.format(
            "Argument %s to %s can not be parsed as an integer",
            config.get(MIN_SENTENCE_LENGTH), MIN_SENTENCE_LENGTH));
      }
    }

    System.err.printf("WeightConfig: '%s' %s%n", Counters.toBiggestValuesFirstString(weightVector, 100), (weightVector.size() > 100 ? "..." : ""));


    // Create Recombination Filter
    RecombinationFilter<Derivation<IString, String>> filter = RecombinationFilterFactory
        .factory(recombinationMode, featurizer.getNestedFeaturizers());

    // Create Search Heuristic
    RuleFeaturizer<IString, String> isolatedPhraseFeaturizer = featurizer;
    SearchHeuristic<IString, String> heuristic = HeuristicFactory.factory(
        isolatedPhraseFeaturizer,
        withGaps ? HeuristicFactory.ISOLATED_DTU_SOURCE_COVERAGE
            : HeuristicFactory.ISOLATED_PHRASE_SOURCE_COVERAGE);

    // Create Inferers and scorers
    inferers = Generics.newArrayList(numThreads);
    scorers = Generics.newArrayList(numThreads);

    boolean dtuDecoder = (gapT != FeaturizerFactory.GapType.none);

    String searchAlgorithm = config.containsKey(SEARCH_ALGORITHM) ?
      config.get(SEARCH_ALGORITHM).get(0).trim() : InfererBuilderFactory.DEFAULT_INFERER;
    if (dtuDecoder) {
      searchAlgorithm = InfererBuilderFactory.DTU_DECODER;
    }
    System.err.printf("Search algorithm: %s%n", searchAlgorithm);
    
    for (int i = 0; i < numThreads; i++) {
      // Configure InfererBuilder
      AbstractBeamInfererBuilder<IString, String> infererBuilder = (AbstractBeamInfererBuilder<IString, String>) 
          InfererBuilderFactory.factory(searchAlgorithm);
      try {
        infererBuilder.setFilterUnknownWords(dropUnknownWords);
        infererBuilder
            .setIncrementalFeaturizer((CombinedFeaturizer<IString, String>) featurizer
                .clone());
        infererBuilder
            .setPhraseGenerator((PhraseGenerator<IString,String>) phraseGenerator
                .clone());
        Scorer<String> scorer = ScorerFactory.factory(ScorerFactory.SPARSE_SCORER, weightVector, null);
        infererBuilder.setScorer(scorer);
        scorers.add(scorer);
        infererBuilder
            .setSearchHeuristic((SearchHeuristic<IString, String>) heuristic
                .clone());
        infererBuilder
            .setRecombinationFilter((RecombinationFilter<Derivation<IString, String>>) filter
                .clone());
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }

      // Silently ignored by the cube pruning decoder
      infererBuilder.setBeamType(BeamFactory.BeamType.sloppybeam);

      if (distortionLimit != -1) {
        infererBuilder.setMaxDistortion(distortionLimit);
      }

      if (config.containsKey(USE_ITG_CONSTRAINTS)) {
        infererBuilder.useITGConstraints(Boolean.parseBoolean(config.get(
            USE_ITG_CONSTRAINTS).get(0)));
      }

      if (config.containsKey(BEAM_SIZE)) {
        try {
          int beamSize = Integer.parseInt(config.get(BEAM_SIZE).get(0));
          infererBuilder.setBeamCapacity(beamSize);
        } catch (NumberFormatException e) {
          throw new RuntimeException(
              String
                  .format(
                      "Beam size %s, as specified by argument %s, can not be parsed as an integer value%n",
                      config.get(BEAM_SIZE).get(0), BEAM_SIZE));
        }
      }
      inferers.add(infererBuilder.build());
    }
    System.err.printf("Inferer Count: %d%n", inferers.size());

    // determine if we need to generate n-best lists
    List<String> nbestOpt = config.get(NBEST_LIST_OPT);
    if (nbestOpt != null) {
      if (nbestOpt.size() == 1) {
        nbestListSize = Integer.parseInt(nbestOpt.get(0));
        assert nbestListSize >= 0;
        System.err.printf("Generating n-best lists (size: %d)%n",
            nbestListSize);

      } else if (nbestOpt.size() == 2) {
        String nbestListFilename = nbestOpt.get(0);
        nbestListSize = Integer.parseInt(nbestOpt.get(1));
        assert nbestListSize >= 0;
        nbestListWriter = IOTools.getWriterFromFile(nbestListFilename);
        System.err.printf("Generating n-best lists to: %s (size: %d)%n",
            nbestListFilename, nbestListSize);

      } else {
        throw new RuntimeException(
            String.format("%s requires 1 or 2 arguments, not %d", NBEST_LIST_OPT,
                nbestOpt.size()));
      }

    } else {
      nbestListSize = -1;
      nbestListWriter = null;
    }
    
    List<String> mosesNbestOpt = config.get(MOSES_NBEST_LIST_OPT);
    if (mosesNbestOpt != null && mosesNbestOpt.size() > 0) {
      generateMosesNBestList = Boolean.parseBoolean(mosesNbestOpt.get(0));
    }
        
    // Determine if we need to generate an alignment file
    List<String> alignmentOpt = config.get(ALIGNMENT_OUTPUT_FILE);
    if (alignmentOpt != null && alignmentOpt.size() == 1) {
      alignmentWriter = IOTools.getWriterFromFile(alignmentOpt.get(0));
    }
  }

  private static String makePair(String label, String value) {
    return String.format("%s:%s", label, value);
  }

  /**
   * Lightweight container for decoder input.
   *
   * @author Spence Green
   *
   */
  private static class DecoderInput {
    public final Sequence<IString> source;
    public final int sourceInputId;

    public DecoderInput(Sequence<IString> seq, int sourceInputId) {
      this.source = seq;
      this.sourceInputId = sourceInputId;
    }
  }

  /**
   * Lightweight container for decoder output.
   *
   * @author Spence Green
   *
   */
  private static class DecoderOutput {
    public final List<RichTranslation<IString, String>> translations;
    public final Sequence<IString> bestTranslation;
    public final int sourceInputId;
    public final int sourceLength;

    public DecoderOutput(int sourceLength, List<RichTranslation<IString, String>> translations, Sequence<IString> bestTranslation, int sourceInputId) {
      this.sourceLength = sourceLength;
      this.translations = translations;
      this.bestTranslation = bestTranslation;
      this.sourceInputId = sourceInputId;
    }
  }

  /**
   * Wrapper class to submit this decoder instance to the thread pool.
   *
   * @author Spence Green
   *
   */
  private class PhrasalProcessor implements ThreadsafeProcessor<DecoderInput,DecoderOutput> {
    private final int infererId;
    private int childInfererId;

    /**
     * Constructor.
     *
     * @param parentInfererId - the bast infererId for this instance. Calls to newInstance()
     * will increment from this value.
     */
    public PhrasalProcessor(int parentInfererId) {
      this.infererId = parentInfererId;
      this.childInfererId = parentInfererId+1;
    }

    @Override
    public DecoderOutput process(DecoderInput input) {
      // Generate n-best list
      List<RichTranslation<IString, String>> translations = 
          decode(input.source, input.sourceInputId, infererId);
      
      // Select and process the best translation
      Sequence<IString> bestTranslation = null;
      if (translations.size() > 0) {
        bestTranslation = translations.get(0).translation;
        if (postprocessor != null) {
          try {
            bestTranslation = postprocessor.process(bestTranslation).e();
          } catch (Exception e) {
            // The postprocessor exploded. Silently ignore and return
            // the unprocessed translation.
            bestTranslation = translations.get(0).translation;
          }
        }
      }
      return new DecoderOutput(input.source.size(), translations, bestTranslation, input.sourceInputId);
    }

    @Override
    public ThreadsafeProcessor<DecoderInput, DecoderOutput> newInstance() {
      return new PhrasalProcessor(childInfererId++);
    }
  }

  /**
   * Output the result of decodeFromConsole(), and write to the n-best list
   * if necessary.
   *
   * NOTE: This call is *not* threadsafe.
   *
   * @param translations n-best list
   * @param bestTranslation if post-processing has been applied, then this is post-processed
   *        sequence at the top of the n-best list
   * @param sourceInputId
   */
  private void processConsoleResult(List<RichTranslation<IString, String>> translations,
      Sequence<IString> bestTranslation, int sourceLength, int sourceInputId) {
    if (translations.size() > 0) {
      RichTranslation<IString,String> bestTranslationInfo = translations.get(0);
      if (printModelScores) {
        System.out.printf("%e\t%s%n", bestTranslationInfo.score, bestTranslation.toString());
      } else {
        System.out.println(bestTranslation.toString());
      }
      
      // log additional information to stderr
      System.err.printf("Best Translation: %s%n", bestTranslation);
      System.err.printf("Final score: %.3f%n", (float) bestTranslationInfo.score);

      // Output the n-best list if necessary
      if (nbestListWriter != null) {
        IOTools.writeNbest(translations, sourceInputId, generateMosesNBestList, nbestListWriter);
      }
      
      // Output the alignments if necessary
      if (alignmentWriter != null) {
        for (RichTranslation<IString,String> translation : translations) {
          alignmentWriter.printf("%d %s %s%n", sourceInputId, FlatPhraseTable.FIELD_DELIM, 
              translation.alignmentString());
        }
      }

    } else {
      // Decoder failure. Print an empty line.
      System.out.println();
      System.err.printf("<<< decoder failure for id: %d >>>%n", sourceInputId);
    }
  }

  /**
   * Decode input from inputStream and either write 1-best translations to stdout or
   * return them in a <code>List</code>.
   * 
   * @param inputStream 
   * @param outputToConsole if true, output the 1-best translations to the console. Otherwise,
   *                        return them in a <code>List</code>
   * @throws IOException
   */
  public List<RichTranslation<IString,String>> decode(InputStream inputStream, boolean outputToConsole) throws IOException {
    System.err.println("Entering main translation loop");
    final MulticoreWrapper<DecoderInput,DecoderOutput> wrapper =
        new MulticoreWrapper<DecoderInput,DecoderOutput>(numThreads, new PhrasalProcessor(0));
    final LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        inputStream, "UTF-8"));
    final List<RichTranslation<IString,String>> bestTranslationList = outputToConsole ? null :
      new ArrayList<RichTranslation<IString,String>>();
    
    final long startTime = System.nanoTime();
    int sourceInputId = 0;
    for (String line; (line = reader.readLine()) != null; ++sourceInputId) {
      Sequence<IString> source = preprocessor == null ? IStrings.tokenize(line) :
        preprocessor.process(line.trim());
      if (source.size() > maxSentenceSize || source.size() < minSentenceSize) {
        System.err.printf("Skipping: %s%n", line);
        System.err.printf("Tokens: %d (min: %d max: %d)%n", source.size(), minSentenceSize,
            maxSentenceSize);
        continue;
      }

      wrapper.put(new DecoderInput(source, sourceInputId));
      while(wrapper.peek()) {
        DecoderOutput result = wrapper.poll();
        if (outputToConsole) {
          processConsoleResult(result.translations, result.bestTranslation, result.sourceLength, result.sourceInputId);
        } else {
          RichTranslation<IString,String> best = result.translations.size() > 0 ? result.translations.get(0) : null;
          bestTranslationList.add(best);
        }
      }
    }

    // Finished reading the input. Wait for threadpool to finish, then process
    // last few translations.
    wrapper.join();
    while(wrapper.peek()) {
      DecoderOutput result = wrapper.poll();
      if (outputToConsole) {
        processConsoleResult(result.translations, result.bestTranslation, result.sourceLength, result.sourceInputId);
      } else {
        RichTranslation<IString,String> best = result.translations.size() > 0 ? result.translations.get(0) : null;
        bestTranslationList.add(best);
      }
    }

    double totalTime = ((double) System.nanoTime() - startTime) / 1e9;
    double segmentsPerSec = (double) sourceInputId / totalTime;
    System.err.printf("Decoding at %.2f segments/sec (total: %.2f sec)%n", segmentsPerSec, totalTime);
    return bestTranslationList;
  }

  /**
   * Decode a tokenized input string. Returns an n-best list of translations as
   * specified by the decoders <code>nbestListSize</code> parameter.
   *
   * NOTE: This call is threadsafe.
   *
   * @param source
   * @param sourceInputId
   * @param threadId -- Inferer object to use (one per thread)
   */
  public List<RichTranslation<IString, String>> decode(Sequence<IString> source,
      int sourceInputId, int threadId) {
    List<Sequence<IString>> targets = 
        forceDecodeReferences == null ? null : forceDecodeReferences.get(sourceInputId);
    return decode(source, sourceInputId, threadId, nbestListSize, targets, false);
  }
  
  /**
   * Decode a tokenized input string. Returns an n-best list of translations
   * specified by the parameter.
   *
   * NOTE: This call is threadsafe.
   *
   * @param source
   * @param sourceInputId
   * @param threadId -- Inferer object to use (one per thread)
   * @param numTranslations number of translations to generate
   * 
   */
  public List<RichTranslation<IString, String>> decode(Sequence<IString> source,
      int sourceInputId, int threadId, int numTranslations, List<Sequence<IString>> targets, 
      boolean targetsArePrefixes) {
    assert threadId >= 0 && threadId < numThreads;
    assert sourceInputId >= 0;

    // Output space of the decoder
    OutputSpace<IString, String> outputSpace = OutputSpaceFactory.getOutputSpace(source, sourceInputId, 
        targets, targetsArePrefixes, phraseGenerator.longestSourcePhrase(), phraseGenerator.longestTargetPhrase());

    List<RichTranslation<IString, String>> translations =
        new ArrayList<RichTranslation<IString, String>>(1);

    if (numTranslations > 1) {
      translations = inferers
          .get(threadId).nbest(
              source,
              sourceInputId,
              outputSpace,
              outputSpace.getAllowableSequences(), numTranslations);

      // Return an empty n-best list
      if (translations == null) translations = new ArrayList<RichTranslation<IString,String>>(1);

    } else {
      // The 1-best translation in this case is potentially different from
      // calling nbest() with a list size of 1. Therefore, this call is *not* a special
      // case of the condition above.
      RichTranslation<IString, String> translation = inferers.get(threadId).translate(
          source,
          sourceInputId,
          outputSpace,
          outputSpace.getAllowableSequences());
      if (translation != null) {
        translations.add(translation);
      }
    }
    return translations;
  }

  /**
   * Free resources and cleanup.
   */
  private void shutdown() {
    if (nbestListWriter != null) {
      System.err.println("Closing n-best writer");
      nbestListWriter.close();
    }
    
    if (alignmentWriter != null) {
      System.err.println("Closing alignment writer");
      alignmentWriter.close();
    }
  }

  /**
   * Parse a Phrasal ini file.
   *
   * @param filename
   * @throws IOException
   */
  public static Map<String, List<String>> readConfig(String filename) throws IOException {
    Map<String, List<String>> config = Generics.newHashMap();
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    for (String line; (line = reader.readLine()) != null;) {
      line = line.trim().replaceAll("#.*$", "");
      if (line.length() == 0)
        continue;
      if (line.charAt(0) != '[' || line.charAt(line.length() - 1) != ']') {
        reader.close();
        throw new RuntimeException(
            String
                .format(
                    "Expected bracketing of option name by '[',']', line: %d label: %s",
                    reader.getLineNumber(), line));
      }
      String nextArgLine = line;

      while (nextArgLine != null) {
        String key = line.substring(1, nextArgLine.length() - 1);
        nextArgLine = null;
        List<String> entries = new ArrayList<String>();
        while ((line = reader.readLine()) != null) {
          if (line.matches("^\\s*$"))
            break;
          if (line.startsWith("[")) {
            nextArgLine = line;
            break;
          }
          if (line.charAt(0) == '#')
            break;
          line = line.replaceAll("#.*$", "");
          String[] fields = line.split("\\s+");
          entries.addAll(Arrays.asList(fields));
        }
        if (!entries.isEmpty())
          config.put(key, entries);
      }
    }
    reader.close();  
    return config;
  }

  /**
   * Read a combination of config file and other command line arguments.
   *
   * @param options
   * @return
   * @throws IOException
   */
  private static Map<String, List<String>> readArgs(Properties options) throws IOException {
    Map<String, List<String>> configArgs = Generics.newHashMap();
    Map<String, List<String>> configFile = Generics.newHashMap();
    for (Map.Entry<Object, Object> e : options.entrySet()) {
      String key = e.getKey().toString();
      String value = e.getValue().toString();
      if (CONFIG_FILE.equals(key)) {
        configFile.putAll(readConfig(value));
      } else {
        configArgs.put(key, Arrays.asList(value.split("\\s+")));
      }
    }
    Map<String, List<String>> configFinal = Generics.newHashMap();
    configFinal.putAll(configFile);
    configFinal.putAll(configArgs);
    return configFinal;
  }

  /**
   * Load an instance of Phrasal from an ini file.
   *
   * @param phrasalIniFile
   * @throws IOException
   */
  public static Phrasal loadDecoder(String phrasalIniFile) throws IOException {
    Map<String, List<String>> config = Phrasal.readConfig(phrasalIniFile);
    return loadDecoder(config);
  }

  /**
   * Load an instance of Phrasal from a parsed ini file.
   * 
   * @param config
   * @return
   */
  public static Phrasal loadDecoder(Map<String, List<String>> config) {
    try {
      Phrasal.initStaticMembers(config);
      final Phrasal phrasal = new Phrasal(config);
      
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          phrasal.shutdown();
        }
      });
      
      return phrasal;

    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    } catch (SecurityException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    throw new RuntimeException("Could not load Phrasal from config file!");
  }
  
  /**
   * Run Phrasal from the command line.
   *
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    Properties options = StringUtils.argsToProperties(args);
    String configFile = options.containsKey("") ? (String) options.get("") : null;
    if ((options.size() == 0 && configFile == null) ||
        options.containsKey("help") || options.containsKey("h")) {
      System.err.println(usage());
      System.exit(-1);
    }

    // by default, exit on uncaught exception
    Thread
        .setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
          @Override
          public void uncaughtException(Thread t, Throwable ex) {
            System.err.println("Uncaught exception from thread: " + t.getName());
            ex.printStackTrace();
            System.exit(-1);
          }
        });

    Map<String, List<String>> config = configFile == null ? readArgs(options) :
      readConfig(configFile);
    Phrasal p = Phrasal.loadDecoder(config);
    p.decode(System.in, true);
  }
}
