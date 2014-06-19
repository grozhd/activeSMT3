package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;

/**
 * Interface for data structures that query and score rules
 * for specific inputs.
 * 
 * @author Daniel Cer
 * 
 * @param <TK>
 */
public interface PhraseGenerator<TK,FV> extends Cloneable {
  
  /**
	 * Return a list of scored rules mapped into the source sequence.
	 */
  public List<ConcreteRule<TK,FV>> getRules(
      Sequence<TK> source, List<Sequence<TK>> targets, int sourceInputId, Scorer<FV> scorer);

  public Object clone() throws CloneNotSupportedException;

  public int longestSourcePhrase();
  
  public int longestTargetPhrase();
  
  public void setFeaturizer(RuleFeaturizer<TK, FV> featurizer);
  
  public List<String> getFeatureNames();
}
