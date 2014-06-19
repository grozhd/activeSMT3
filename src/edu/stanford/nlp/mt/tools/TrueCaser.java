package edu.stanford.nlp.mt.tools;

import edu.stanford.nlp.ling.CoreLabel;

import java.util.List;

/**
 * @author Michel Galley
 */
public interface TrueCaser {

  /**
   * Provide a model to truecaser, if applicable.
   *
   * @param model Model file, e.g., CRF serialized model or ARPA file.
   */
  public void init(String model);

  /**
   * Truecase a given tokenized sentence.
   * 
   * @param tokens Tokenized sentence.
   * @param id Sentence number.
   */
  public String[] trueCase(String[] tokens, int id);
}
