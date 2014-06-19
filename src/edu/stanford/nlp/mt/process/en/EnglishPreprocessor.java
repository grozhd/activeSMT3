package edu.stanford.nlp.mt.process.en;

import java.util.Locale;

import edu.stanford.nlp.mt.process.CoreNLPPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;

/**
 * Pre-processes English raw text.
 * 
 * @author Spence Green
 *
 */
public class EnglishPreprocessor extends CoreNLPPreprocessor {

  public EnglishPreprocessor() {
    super(PTBTokenizer.coreLabelFactory());
    tf.setOptions("invertible=true,ptb3Escaping=false,asciiQuotes=true,splitAssimilations=false");
  }

  @Override
  public String toUncased(String input) {
    return input.toLowerCase(Locale.ENGLISH);
  }
}
