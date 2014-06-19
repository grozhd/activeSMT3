package edu.stanford.nlp.mt.base;

/**
 * A sequence of length 0.
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public class EmptySequence<TK> extends AbstractSequence<TK> {

  @Override
  public TK get(int i) {
    throw new IndexOutOfBoundsException(String.format(
        "Index: %d Sequence size: 0\n", i));
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public String toString() {
    return "";
  }
}
