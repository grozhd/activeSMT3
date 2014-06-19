package edu.stanford.nlp.mt.base;

import java.util.ArrayList;

import edu.stanford.nlp.mt.decoder.util.Derivation;

/**
 * 
 * @author danielcer
 * 
 * @param <V>
 */
public class FastFeaturizableHash<V> {
  final long offset;
  final ArrayList<V> values;

  /**
	 * 
	 */
  public FastFeaturizableHash() {
    offset = Derivation.nextId.get();
    values = new ArrayList<V>(500000);
  }

  /**
	 * 
	 */
  public void put(Featurizable<IString, String> f, V value) {
    int idx = (int) (f.derivation.id - offset);
    values.ensureCapacity(idx + 1);
    while (values.size() <= idx)
      values.add(null);
    values.set(idx, value);
  }

  /**
	 * 
	 */
  public V get(Featurizable<IString, String> f) {
    if (f == null)
      return null;
    int idx = (int) (f.derivation.id - offset);
    if (idx >= values.size())
      return null;
    return values.get(idx);
  }
}
