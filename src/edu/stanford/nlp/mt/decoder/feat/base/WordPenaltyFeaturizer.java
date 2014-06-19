package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;

/**
 * 
 * @author danielcer
 * 
 */
public class WordPenaltyFeaturizer<TK> implements
    RuleFeaturizer<TK, String> {

  public static final String DEBUG_PROPERTY = "WordPenaltyFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));
  public static final String FEATURE_NAME = "WordPenalty";
  static public final double MOSES_WORD_PENALTY_MUL = -1.0;

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<TK, String> f) {
    if (f.targetPhrase == null) {
      return null;
    } else {
      List<FeatureValue<String>> features = Generics.newLinkedList();
      features.add(new FeatureValue<String>(FEATURE_NAME, MOSES_WORD_PENALTY_MUL
        * f.targetPhrase.size()));
      return features;
    }
  }

  @Override
  public void initialize() {
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
